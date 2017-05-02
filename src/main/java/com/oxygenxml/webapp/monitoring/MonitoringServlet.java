package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.blacklocus.metrics.CloudWatchReporterBuilder;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteUDP;
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
public class MonitoringServlet extends WebappServletPluginExtension {

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
   * Reporter that sends monitoring data to a graphite server.
   */
  private ScheduledReporter reporter = null;
  
  /**
   * Constructor.
   */
  public MonitoringServlet() {
    threadDumpServlet = new ThreadDumpServlet();
    monitoringManager = new MonitoringManager();
  }

  @Override
  public void init() throws ServletException {
    ServletContext servletContext = getServletConfig().getServletContext();
    monitoringManager.contextInitialized(new ServletContextEvent(servletContext));
    this.initGraphiteReporter(servletContext);
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
  
  /**
   * Initialize the graphite reporter.
   * 
   * @param servletContext The servlet context.
   */
  private void initGraphiteReporter(ServletContext servletContext) {
    InetSocketAddress graphiteServer = getGraphiteServer();
    MetricRegistry registry = (MetricRegistry) servletContext.getAttribute(MonitoringManager.METRICS_REGISTRY_ATTR);
    if (graphiteServer != null) {
      // Start a reporter to send data to the graphite server.
      GraphiteUDP graphite = new GraphiteUDP(graphiteServer);
      reporter = GraphiteReporter.forRegistry(registry)
                                          .prefixedWith("oxygenxml-web-author")
                                          .convertRatesTo(TimeUnit.SECONDS)
                                          .convertDurationsTo(TimeUnit.MILLISECONDS)
                                          .filter(MetricFilter.ALL)
                                          .build(graphite);
    }
    System.out.println("aws credentials set!");
    ClientConfiguration configuration = new ClientConfiguration();
    configuration.setProxyHost(/*proxy host*/"");
    configuration.setProxyPort(/*proxy port (int)*/);
    
    AmazonCloudWatchAsyncClient awsClient = new AmazonCloudWatchAsyncClient(
        new StaticCredentialsProvider(
            new BasicAWSCredentials(/*accessKey*/"", /*secret*/"")),
        configuration
    );
    awsClient.setRegion(RegionUtils.getRegion(/*region*/""));
    
    reporter = new CloudWatchReporterBuilder()
      .withNamespace("webapp-metrics"/*ExampleMetrics.class.getSimpleName()*/)
      .withRegistry(registry)
      .withClient(awsClient)
    .build();
  reporter.start(1, TimeUnit.SECONDS);
    
  }
  
  /**
   * @return The configured Graphite server address.
   */
  private InetSocketAddress getGraphiteServer() {
    String graphiteServer = "localhost:2003";

    if (graphiteServer == null || graphiteServer.trim().length() == 0) {
      return null;
    }

    String[] graphiteServerHostAndPort = graphiteServer.split(":");
    String host = graphiteServerHostAndPort[0];
    int port = 2003;
    if (graphiteServerHostAndPort.length == 2) {
      port = Integer.valueOf(graphiteServerHostAndPort[1]);
    }

    return new InetSocketAddress(host, port);
  }
}
