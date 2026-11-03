package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.json.MetricsModule;
import com.codahale.metrics.jvm.ClassLoadingGaugeSet;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.jvm.ThreadDump;
import com.codahale.metrics.jvm.ThreadStatesGaugeSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import ro.sync.ecss.extensions.api.webapp.plugin.ServletPluginExtension;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletException;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletResponse;

/**
 * Servlet that exposes monitoring information.
 * 
 * @author cristi_talau
 */
public class MonitoringServlet extends ServletPluginExtension {
  /**
   * Namespace used for metrics.
   */
  private static final String METRICS_NAMESPACE = "oxygenxml-web-author";

  /**
   * Metrics registry attribute name.
   */
  public static final String METRICS_REGISTRY_ATTR_NAME = "ro.sync.monitoring.registry";

  /**
   * Servlet context attribute with the {@link MetricFilter} that selects the metrics to write.
   * It has the name read by Dropwizard's (javax-based) MetricsServlet, used before.
   */
  public static final String METRIC_FILTER_ATTR_NAME = "com.codahale.metrics.servlets.MetricsServlet.metricFilter";

  /**
   * The responses must never be cached, they show the current state.
   */
  private static final String NO_CACHE = "must-revalidate,no-cache,no-store";

  /**
   * The metrics registry populated by Web Author.
   */
  private MetricRegistry registry;
  /**
   * Serializes the metrics as JSON, with the defaults of Dropwizard's (javax-based) MetricsServlet.
   */
  private ObjectMapper metricsMapper;
  /**
   * Writes the thread dumps, as Dropwizard's (javax-based) ThreadDumpServlet did.
   * <code>null</code> if the runtime environment does not allow thread dumps.
   */
  private ThreadDump threadDump;

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
    this.intervalSize = intervalSize;
    this.intervalUnit = intervalUnit;
  }

  @Override
  public void init() throws ServletException {
    ServletContext servletContext = getServletConfig().getServletContext();
    registry = (MetricRegistry) servletContext.getAttribute(METRICS_REGISTRY_ATTR_NAME);

    initReporter(registry);

    try {
      threadDump = new ThreadDump(ManagementFactory.getThreadMXBean());
    } catch (NoClassDefFoundError e) {
      // The runtime environment does not allow thread dumps.
      threadDump = null;
    }

    // Get the metrics registry populated by Web Author.
    registry.register("memory", new MemoryUsageGaugeSet());
    registry.register("classes", new ClassLoadingGaugeSet());
    registry.register("threads", new ThreadStatesGaugeSet());
    registry.register("gc", new GarbageCollectorMetricSet());
    
    registry.register("webauthor", new WebAuthorApiMetrics());
    
    Object filterAttr = servletContext.getAttribute(METRIC_FILTER_ATTR_NAME);
    MetricFilter filter = filterAttr instanceof MetricFilter ? (MetricFilter) filterAttr : MetricFilter.ALL;
    metricsMapper = new ObjectMapper().registerModule(
        new MetricsModule(TimeUnit.SECONDS, TimeUnit.SECONDS, false, filter));
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
      writeThreadDump(req, resp);
    } else if (req.getPathInfo().startsWith("/monitoring/metrics")) {
      writeMetrics(req, resp);
    }
  }

  /**
   * Writes a thread dump as plain text.
   *
   * @param req The request, whose "monitors" and "synchronizers" parameters say what to include (default true).
   * @param resp The response.
   * @throws IOException If the dump cannot be written.
   */
  private void writeThreadDump(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    boolean includeMonitors = getBooleanParam(req, "monitors", true);
    boolean includeSynchronizers = getBooleanParam(req, "synchronizers", true);
    resp.setStatus(HttpServletResponse.SC_OK);
    resp.setContentType("text/plain");
    resp.setHeader("Cache-Control", NO_CACHE);
    if (threadDump == null) {
      resp.getWriter().println("Sorry your runtime environment does not allow to dump threads.");
      return;
    }
    try (OutputStream output = resp.getOutputStream()) {
      threadDump.dump(includeMonitors, includeSynchronizers, output);
    }
  }

  /**
   * Writes all the metrics as JSON.
   *
   * @param req The request, whose "pretty" parameter asks for indented JSON.
   * @param resp The response.
   * @throws IOException If the metrics cannot be written.
   */
  private void writeMetrics(HttpServletRequest req, HttpServletResponse resp) throws IOException {
    resp.setContentType("application/json");
    resp.setHeader("Cache-Control", NO_CACHE);
    resp.setStatus(HttpServletResponse.SC_OK);
    ObjectWriter writer = getBooleanParam(req, "pretty", false)
        ? metricsMapper.writerWithDefaultPrettyPrinter()
        : metricsMapper.writer();
    try (OutputStream output = resp.getOutputStream()) {
      writer.writeValue(output, registry);
    }
  }

  /**
   * @param req The request.
   * @param name The name of the parameter.
   * @param defaultValue The value to use when the parameter is missing.
   * @return The value of the boolean parameter.
   */
  private static boolean getBooleanParam(HttpServletRequest req, String name, boolean defaultValue) {
    String value = req.getParameter(name);
    return value == null ? defaultValue : Boolean.parseBoolean(value);
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
