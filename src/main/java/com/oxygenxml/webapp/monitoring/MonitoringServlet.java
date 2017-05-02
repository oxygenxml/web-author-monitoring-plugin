package com.oxygenxml.webapp.monitoring;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.internal.StaticCredentialsProvider;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import com.blacklocus.metrics.CloudWatchReporter;
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
   * Logger for logging.
   */
  private static final Logger logger = Logger.getLogger(MonitoringServlet.class.getName());

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
    MetricRegistry registry = (MetricRegistry) servletContext.getAttribute("ro.sync.monitoring.registry");
    
    initReporter(registry);
    
    threadDumpServlet.init();

    // Get the metrics registry populated by Web Author.
    registry.register("memory", new MemoryUsageGaugeSet());
    
    metricsServlet = new MetricsServlet(registry);
    metricsServlet.init(getServletConfig());
  }

  /**
   * Initialize the reporter.
   * 
   * @param registry The metrics registry.
   */
  private void initReporter(MetricRegistry registry) {
    reporter = this.getGraphiteReporter(registry);
    if (reporter == null) {
      reporter = this.getCloudWatchReporter(registry);
    }
    if (reporter == null) {
      reporter = this.getLog4jReporter(registry);
    }
    reporter.start(1, TimeUnit.MINUTES);
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
   * @param registry The metrics registry.
   * 
   * @return The graphite reporter, or null if not configured.
   */
  private GraphiteReporter getGraphiteReporter(MetricRegistry registry) {
    InetSocketAddress graphiteServer = getGraphiteServer();
    if (graphiteServer != null) {
      // Start a reporter to send data to the graphite server.
      GraphiteUDP graphite = new GraphiteUDP(graphiteServer);
      return GraphiteReporter.forRegistry(registry)
                                          .prefixedWith("oxygenxml-web-author")
                                          .convertRatesTo(TimeUnit.SECONDS)
                                          .convertDurationsTo(TimeUnit.MILLISECONDS)
                                          .filter(MetricFilter.ALL)
                                          .build(graphite);
      
    }
    return null;
  }
  
  /**
   * Initialize the CloudWatch reporter.
   * 
   * @param registry The metrics registry.
   * 
   * @return The reporter, or null if not configured.
   */
  private CloudWatchReporter getCloudWatchReporter(MetricRegistry registry) {
    String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
    String secretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
    
    if (accessKeyId != null && secretAccessKey != null) {
      ClientConfiguration configuration = new ClientConfiguration();
      String proxy = System.getenv("https_proxy");
      if (proxy != null) {
        URL proxyUrl;
        try {
          proxyUrl = new URL(proxy);
          int port = proxyUrl.getPort();
          configuration.setProxyHost(proxyUrl.getHost());
          configuration.setProxyPort(port == -1 ? 443 : port);
        } catch (MalformedURLException e) {
          logger.debug("Invalid proxy address: " + proxy, e);
        }
      }
      
      AmazonCloudWatchAsyncClient awsClient = new AmazonCloudWatchAsyncClient(
          new StaticCredentialsProvider(
              new BasicAWSCredentials(accessKeyId, secretAccessKey)),
          configuration
          );
      awsClient.setRegion(RegionUtils.getRegion(System.getenv("AWS_DEFAULT_REGION")));
      
      return new CloudWatchReporterBuilder()
          .withNamespace("oxygenxml-web-author")
          .withRegistry(registry)
          .withClient(awsClient)
          .build();
    }
    
    return null;
  }
  
  /**
   * Initialize a reporter that writes 
   * 
   * @param registry The metrics registry.
   * @return The reporter, or null if not configured.
   */
  private PlainTextReporter getLog4jReporter(MetricRegistry registry) {
    return new PlainTextReporter(registry, 
        "oxygenxml-web-author", TimeUnit.MILLISECONDS, TimeUnit.MILLISECONDS);
  }

  /**
   * @return The configured Graphite server address.
   */
  private InetSocketAddress getGraphiteServer() {
    String graphiteServer = System.getenv("GRAPHITE_SERVER");
    
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
  
  @Override
  public boolean requiresAuthorization() {
    return true;
  }
}
