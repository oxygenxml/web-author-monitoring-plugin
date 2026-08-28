package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.codahale.metrics.Clock;
import com.codahale.metrics.ExponentiallyDecayingReservoir;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.RatioGauge;
import com.codahale.metrics.Timer;
import com.codahale.metrics.Timer.Context;
import com.google.common.annotations.VisibleForTesting;

import jakarta.ws.rs.Path;
import lombok.extern.slf4j.Slf4j;
import ro.sync.ecss.extensions.api.webapp.plugin.ServletFilter;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletConfig;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletException;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.FilterChain;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletResponse;
import ro.sync.exml.plugin.PluginExtension;
import ro.sync.servlet.RESTDocumentControllers;
import ro.sync.servlet.RESTDocumentManager;
import ro.sync.servlet.monitoring.MonitoringManager;

/**
 * Custom filter that monitors the frequency of error occurrences and the
 * histogram of request durations.
 * 
 * @author cristi_talau
 */
@Slf4j
public class MonitoringFilter implements ServletFilter, PluginExtension {
 
  /**
   * Label used for edit requests.
   */
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
   * REST path of the edit actions.
   */
  private static final String EDIT_PATH = RESTDocumentControllers.class.getAnnotation(Path.class).value();

  /**
   * REST path of the document loading endpoint.
   */
  private static final String DOC_LOAD_PATH = RESTDocumentManager.class.getAnnotation(Path.class).value() + "/load";

  /**
   * Map between the REST method identifier and its duration Timer.
   */
  private Map<String, Timer> durations;

  /**
   * Map between the REST method identifier and its error meter.
   */
  private Map<String, Meter> errors;

  /**
   * The metric registry.
   */
  private MetricRegistry registry = null;
  /**
   * The clock used by metrics.
   */
  private Clock clock = Clock.defaultClock();

  /**
   * The monitoring manager.
   */
  private MonitoringManager monitoringManager;

  /**
   * The servlet context.
   */
  private ServletContext servletContext;
  
  @Override
  public void init(ServletConfig filterConfig) throws ServletException {
    servletContext = filterConfig.getServletContext();
    monitoringManager = new MonitoringManager();
    
    jakarta.servlet.ServletContext jakartaServletContext = new ServletContextOxytToJakartaPartialAdapter(servletContext);
    monitoringManager.contextInitialized(new jakarta.servlet.ServletContextEvent(jakartaServletContext));
    registry = (MetricRegistry) filterConfig.getServletContext().getAttribute(MonitoringServlet.METRICS_REGISTRY_ATTR_NAME);
    
    durations = new ConcurrentHashMap<>();
    errors = new ConcurrentHashMap<>();
  }

  @Override
  public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
    // Split requests by URL into different metrics.
    String label = computeLabel(request);
    if (label != null) {
      Timer duration = getDurationTimer(label);
      Context context = duration.time();
      try {
        chain.doFilter(request, response);
      } finally {
        int status = response.getStatus();
        if (status != HttpServletResponse.SC_OK && status != HttpServletResponse.SC_NO_CONTENT) {
          Meter errorRate = getErrorMeter(label);
          errorRate.mark();
        }
        long durationNanoSeconds = context.stop();
        logLargeDuration(request, durationNanoSeconds);
      }
    } else {
      // Do not monitor non-rest requests.
      chain.doFilter(request, response);
    }

  }

  /**
   * Log requests whose duration took too long.
   * 
   * @param request The request.
   * @param durationNanoSeconds The duration.
   */
  void logLargeDuration(ServletRequest request, long durationNanoSeconds) {
    if (durationNanoSeconds > 100 * 1000L * 1000L && request instanceof HttpServletRequest) {
      HttpServletRequest httpServletRequest = (HttpServletRequest)request;
      String url = httpServletRequest.getRequestURL().toString();
      String queryString = httpServletRequest.getQueryString();
      String urlString = queryString != null ? url + "?" + queryString : url; 
      double nano = 1000. * 1000. * 1000.;
      log.warn("Long request: " + urlString + " - took " + (durationNanoSeconds / nano) + "seconds");
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
        // Cannot happen.
      }

      if (url != null) {
        if (url.getPath().startsWith(httpRequest.getContextPath() + EDIT_PATH)) {
          // We group all edits together.
          label = EDIT_LABEL;
        } else if (url.getPath().startsWith(httpRequest.getContextPath() + DOC_LOAD_PATH)) {
          label = DOC_LOAD_LABEL;
        } else {
          label = OTHERS_LABEL;
        }
      }
    }
    return label;
  }

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
