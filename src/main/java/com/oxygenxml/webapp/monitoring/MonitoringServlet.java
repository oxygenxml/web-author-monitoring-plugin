package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.ThreadDumpServlet;

import ro.sync.ecss.extensions.api.webapp.plugin.WebappServletPluginExtension;

/**
 * Servlet that exposes monitoring information.
 * 
 * @author cristi_talau
 */
public class MonitoringServlet extends WebappServletPluginExtension {
  /**
   * Namespace used for metrics.
   */
  private static final String METRICS_NAMESPACE = "oxygenxml-web-author";

  /**
   * Metrics registry attribute name.
   */
  public static final String METRICS_REGISTRY_ATTR_NAME = "ro.sync.monitoring.registry";

  /**
   * Unerlying servlet to which we delegate for thread dumps.
   */
  private final ThreadDumpServlet threadDumpServlet;
  /**
   * Unerlying servlet to which we delegate for metrics serialization as JSON.
   */
  private MetricsServlet metricsServlet;

  /**
   * The interval between metrics logs.
   */
  private int intervalSize;
  /**
   * The interval between metrics logs.
   */
  private TimeUnit intervalUnit;
  
  /**
   * Constructor.
   */
  public MonitoringServlet() {
    this(1, TimeUnit.MINUTES);
  }

  MonitoringServlet(int intervalSize, TimeUnit intervalUnit) {
    this.threadDumpServlet = new ThreadDumpServlet();
    this.intervalSize = intervalSize;
    this.intervalUnit = intervalUnit;
  }

  @Override
  public void init() throws ServletException {
    ServletContext servletContext = getServletConfig().getServletContext();
    MetricRegistry registry = (MetricRegistry) servletContext.getAttribute(METRICS_REGISTRY_ATTR_NAME);
    
    initReporter(registry);
    
    threadDumpServlet.init();

    // Get the metrics registry populated by Web Author.
    registry.register("memory", new MemoryUsageGaugeSet());
    registry.register("classes", new ClassLoadingGaugeSet());
    registry.register("threads", new ThreadStatesGaugeSet());
    registry.register("gc", new GarbageCollectorMetricSet());
    
    registry.register("webauthor", new WebAuthorApiMetrics());
    
    metricsServlet = new MetricsServlet(registry);
    metricsServlet.init(getServletConfig());
  }

  /**
   * Initialize the reporter.
   * 
   * @param registry The metrics registry.
   */
  private void initReporter(MetricRegistry registry) {
    ScheduledReporter reporter = this.getLog4jReporter(registry);
    reporter.start(intervalSize, intervalUnit);
  }
  
  @Override
  public String getPath() {
    return "monitoring";
  }

  @Override
  public void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {
    if (req.getPathInfo().startsWith("/monitoring/threads")) {
      threadDumpServlet.service(req, resp);
    } else if (req.getPathInfo().startsWith("/monitoring/metrics")) {
      metricsServlet.service(req, resp);
    }
  }
  
  /**
   * Initialize a reporter that writes 
   * 
   * @param registry The metrics registry.
   * @return The reporter, or null if not configured.
   */
  private PlainTextReporter getLog4jReporter(MetricRegistry registry) {
    return new PlainTextReporter(registry, 
        METRICS_NAMESPACE, TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS);
  }
  
  @Override
  public boolean requiresAuthorization() {
    return true;
  }
}
