package com.oxygenxml.webapp.monitoring;

import java.io.IOException;

import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.ThreadDumpServlet;

import ro.sync.ecss.extensions.api.webapp.plugin.WebappServletPluginExtension;
import ro.sync.servlet.monitoring.MonitoringManager;

/**
 * Servlet that exposes monitoring information.
 * 
 * @author cristi_talau
 */
public class MonitoringServlet extends WebappServletPluginExtension{

  /**
   * Unerlying servlet to which we delegate for thread dumps.
   */
  private final ThreadDumpServlet threadDumpServlet;
  /**
   * Unerlying servlet to which we delegate for metrics serialization as JSON.
   */
  private MetricsServlet metricsServlet;
  /**
   * The monitoring manager.
   */
  private final MonitoringManager monitoringManager;
  
  /**
   * Constructor.
   */
  public MonitoringServlet() {
    threadDumpServlet = new ThreadDumpServlet();
    monitoringManager = new MonitoringManager();
  }

  @Override
  public void init() throws ServletException {
    monitoringManager.contextInitialized(new ServletContextEvent(getServletConfig().getServletContext()));
    threadDumpServlet.init();

    // Get the metrics registry populated by Web Author.
    MetricRegistry registry = (MetricRegistry) getServletConfig().getServletContext().getAttribute(
        "ro.sync.monitoring.registry");
    registry.register("memory", new MemoryUsageGaugeSet());
    
    metricsServlet = new MetricsServlet(registry);
    metricsServlet.init(getServletConfig());
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
}
