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
   * The path to the servlet that serves monitoring threads.
   */
  private static final String MONITORING_THREADS = "/monitoring/threads";
  /**
   * The path to the servlet that serves metrics.
   */
  private static final String MONITORING_METRICS = "/monitoring/metrics";
  /**
   * The path to the servlet that serves log information.
   */
  private static final String MONITORING_LOGS = "/monitoring/logs";
  
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
   * The logs manager.
   */
  private final LogManager logManager;
  
  /**
   * Constructor.
   */
  public MonitoringServlet() {
    threadDumpServlet = new ThreadDumpServlet();
    monitoringManager = new MonitoringManager();
    logManager = new LogManager();
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
    if (req.getPathInfo().startsWith(MONITORING_THREADS)) {
      threadDumpServlet.service(req, resp);
    } else if (req.getPathInfo().startsWith(MONITORING_METRICS)) {
      metricsServlet.service(req, resp);
    } else if (req.getPathInfo().startsWith(MONITORING_LOGS)) {
      String logsEndpoint = req.getPathInfo().substring(MONITORING_LOGS.length());
      if (logsEndpoint.startsWith("/lastday")) {
        logManager.lastDay(resp.getOutputStream());
      } else if (logsEndpoint.startsWith("/all")) {
        // Force a download, since the file is too large to be presented by a browser.
        resp.setHeader("Content-Type", "application/force-download");
        resp.setHeader("Content-Disposition", "attachment; filename=\"oxygen.log\"");
        resp.setHeader("Content-Description", "File-Transfer");
        logManager.allLogs(resp.getOutputStream());
      }
    }
  }
  
  @Override
  public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
    if (req.getPathInfo().startsWith(MONITORING_LOGS + "/set_debug")) {
      String packageOrClass = req.getParameter("name");
      logManager.setDebug(packageOrClass);
    } else if (req.getPathInfo().startsWith(MONITORING_LOGS + "/reset")) {
      String packageOrClass = req.getParameter("name");
      if (packageOrClass != null) {
        logManager.reset(packageOrClass);
      } else{
        logManager.resetAll();
      }
    }
  }
}
