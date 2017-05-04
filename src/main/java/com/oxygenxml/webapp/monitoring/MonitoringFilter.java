package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.ws.rs.Path;

import com.codahale.metrics.Clock;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.google.common.annotations.VisibleForTesting;

import ro.sync.exml.plugin.PluginExtension;
import ro.sync.servlet.RESTDocumentControllers;
import ro.sync.servlet.RESTDocumentManager;

/**
 * Custom filter that monitors the frequency of error occurrences and the
 * histogram of request durations.
 * 
 * @author cristi_talau
 */
public class MonitoringFilter implements Filter, PluginExtension {
  /**
   * Label used for edit requests.
   */
  @VisibleForTesting
  static final String EDIT_LABEL = "edit";
  
  /**
   * Label used for document load requests.
   */
  @VisibleForTesting
  static final String DOC_LOAD_LABEL = "doc-load";
  
  /**
   * Label used for all other requests.  
   */
  private static final String OTHERS_LABEL = "others";
  /**
   * Map between the REST method identifier and its duration Timer.
   */
  private Map<String, Timer> durations;

  /**
   * Map between the REST method identifier and its error meter.
   */
  private HashMap<String, Meter> errors;

  /**
   * The metric registry.
   */
  private MetricRegistry registry = null;
  /**
   * The clock used by metrics.
   */
  private Clock clock = Clock.defaultClock();

  /**
   * REST path of the edit actions.
   */
  private static final String editPath = RESTDocumentControllers.class.getAnnotation(Path.class).value();

  /**
   * REST path of the document loading endpoint.
   */
  private static final String docLoadPath = RESTDocumentManager.class.getAnnotation(Path.class).value() + "/load";

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    registry = (MetricRegistry) filterConfig.getServletContext().getAttribute(MonitoringServlet.METRICS_REGISTRY_ATTR_NAME);
    durations = new HashMap<String, Timer>();
    errors = new HashMap<String, Meter>();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
    // Split requests by URL into different metrics.
    String label = computeLabel(request);
    if (label != null) {
      Timer duration = getDurationTimer(label);
      Context context = duration.time();
      ResponseWrapper responseWrapper = new ResponseWrapper((HttpServletResponse) response);
      try {
        chain.doFilter(request, responseWrapper);
      } finally {
        int status = responseWrapper.getStatus();
        if (status != HttpServletResponse.SC_OK && status != HttpServletResponse.SC_NO_CONTENT) {
          Meter errorRate = getErrorMeter(label);
          errorRate.mark();
        }
        context.stop();
      }
    } else {
      // Do not monitor non-rest requests.
      chain.doFilter(request, response);
    }

  }

  /**
   * Compute a label for the request derived from the URL path.
   * 
   * @param request
   *          The request.
   * 
   * @return The label, or null if the request should not be monitored.
   */
  @VisibleForTesting
  String computeLabel(ServletRequest request) {
    // This label identifies the REST endpoint.
    String label = null;

    // For methods handled by our servlets, we have a pathInfo.
    HttpServletRequest httpRequest = (HttpServletRequest) request;
    String pathInfo = httpRequest.getPathInfo();
    if (pathInfo != null) {
      URL url = null;
      try {
        url = new URL(httpRequest.getRequestURL().toString());
      } catch (MalformedURLException e) {
      }

      if (url.getPath().startsWith(httpRequest.getContextPath() + editPath)) {
        // We group all edits together.
        label = EDIT_LABEL;
      } else if (url.getPath().startsWith(httpRequest.getContextPath() + docLoadPath)) {
        label = DOC_LOAD_LABEL;
      } else {
        label = OTHERS_LABEL;
      }
    }
    return label;
  }

  /**
   * Wrapper for the HTTP response sent to the servlet, so that we can intercept
   * the status code.
   * 
   * @author cristi_talau
   */
  private static class ResponseWrapper extends HttpServletResponseWrapper {
    /**
     * Constructor.
     * 
     * @param response
     *          The response to wrap.
     */
    public ResponseWrapper(HttpServletResponse response) {
      super(response);
    }

    /**
     * The HTTP status message.
     */
    private int httpStatus = SC_OK;

    @Override
    public void sendError(int sc) throws IOException {
      httpStatus = sc;
      super.sendError(sc);
    }

    @Override
    public void sendError(int sc, String msg) throws IOException {
      httpStatus = sc;
      super.sendError(sc, msg);
    }

    @Override
    public void setStatus(int sc) {
      httpStatus = sc;
      super.setStatus(sc);
    }

    /**
     * @return The httpStatus of the response.
     */
    public int getStatus() {
      return httpStatus;
    }
  };

  /**
   * Returns the meter for errors and registers it.
   * 
   * @param label
   *          The label.
   * @return The meter corresponding to the label passed as argument.
   */
  private Meter getErrorMeter(final String label) {
    Meter errorRate = errors.get(label);
    if (errorRate == null) {
      errorRate = registry.register("errors.rate." + label, new Meter(clock));
      // Register also a gauge indicating the error percentage.
      registry.register("errors.percentage." + label, new RatioGauge() {

        @Override
        protected Ratio getRatio() {
          return Ratio.of(errors.get(label).getFifteenMinuteRate(), durations.get(label).getFifteenMinuteRate());
        }
      });
      errors.put(label, errorRate);
    }
    return errorRate;
  }

  /**
   * Returns and registers the timer that corresponds to a label.
   * 
   * @param label
   *          The label.
   * @return The Timer corresponding to the label.
   */
  private Timer getDurationTimer(String label) {
    Timer duration = durations.get(label);
    if (duration == null) {
      duration = registry.register("duration." + label, 
          new Timer(new ExponentiallyDecayingReservoir(), clock));
      durations.put(label, duration);
    }
    return duration;
  }

  @Override
  public void destroy() {
    // Noting to do.
  }
  
  /**
   * Set the clock.
   * 
   * @param clock The clock.
   */
  @VisibleForTesting
  void setClock(Clock clock) {
    this.clock = clock;
  }
}
