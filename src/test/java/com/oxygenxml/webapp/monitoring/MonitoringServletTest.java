package com.oxygenxml.webapp.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import ro.sync.ecss.extensions.api.webapp.access.WebappPluginWorkspace;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletConfig;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletOutputStream;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletResponse;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Tests for the metrics and thread dumps written by {@link MonitoringServlet}.
 */
public class MonitoringServletTest {

  /**
   * The registry that Web Author puts in the servlet context.
   */
  private MetricRegistry registry;

  /**
   * The servlet under test.
   */
  private MonitoringServlet servlet;

  /**
   * The body written in the response.
   */
  private ByteArrayOutputStream responseBody;

  /**
   * The response.
   */
  private HttpServletResponse resp;

  @Before
  public void setUp() throws Exception {
    WebappPluginWorkspace workspace = mock(WebappPluginWorkspace.class);
    Map<String, String> stats = new HashMap<>();
    for (String gauge : new String[] {
        "http_conn_pool_max", "http_conn_pool_pending", "http_conn_pool_leased", "http_conn_pool_available",
        "validator_cache_miss", "validator_cache_req", "validator_cache_avg_load_time_ms", "validator_cache_eviction"}) {
      stats.put(gauge, "7");
    }
    when(workspace.getMonitoringStats()).thenReturn(stats);
    PluginWorkspaceProvider.setPluginWorkspace(workspace);

    initServlet(null);

    responseBody = new ByteArrayOutputStream();
    resp = mock(HttpServletResponse.class);
    when(resp.getOutputStream()).thenReturn(new ServletOutputStream() {
      @Override
      public void write(int b) throws IOException {
        responseBody.write(b);
      }
    });
  }

  @After
  public void tearDown() {
    PluginWorkspaceProvider.setPluginWorkspace(null);
  }

  /**
   * Creates and initializes the servlet, with a new registry.
   *
   * @param metricFilter The value of the metric filter attribute in the servlet context.
   *
   * @throws Exception If it fails.
   */
  private void initServlet(Object metricFilter) throws Exception {
    registry = new MetricRegistry();
    ServletContext context = mock(ServletContext.class);
    when(context.getAttribute(MonitoringServlet.METRICS_REGISTRY_ATTR_NAME)).thenReturn(registry);
    when(context.getAttribute(MonitoringServlet.METRIC_FILTER_ATTR_NAME)).thenReturn(metricFilter);
    ServletConfig config = mock(ServletConfig.class);
    when(config.getServletContext()).thenReturn(context);

    // A long interval, so that the reporter does not log during the test.
    servlet = new MonitoringServlet(1, TimeUnit.HOURS);
    servlet.init(config);
  }

  /**
   * <p><b>Description:</b> The metric filter from the servlet context selects the metrics to write,
   * as with the Dropwizard MetricsServlet that was used before.</p>
   * <p><b>Bug ID:</b> WA-10428</p>
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testMetricFilter() throws Exception {
    initServlet((MetricFilter) (name, metric) -> name.startsWith("memory."));
    registry.counter("test-counter").inc();

    servlet.doGet(request("/monitoring/metrics", new HashMap<>()), resp);

    JsonNode metrics = new ObjectMapper().readTree(responseBody.toString(StandardCharsets.UTF_8.name()));
    assertTrue(metrics.get("gauges").has("memory.heap.used"));
    assertFalse(metrics.get("gauges").has("threads.count"));
    assertFalse(metrics.get("counters").has("test-counter"));
  }

  /**
   * <p><b>Description:</b> The metrics are written as compact JSON, with the defaults
   * of the Dropwizard MetricsServlet that was used before.</p>
   * <p><b>Bug ID:</b> WA-10428</p>
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testMetrics() throws Exception {
    registry.timer("test-timer").update(2, TimeUnit.SECONDS);
    registry.histogram("test-histogram").update(5);

    servlet.doGet(request("/monitoring/metrics", new HashMap<>()), resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(resp).setContentType("application/json");
    verify(resp).setHeader("Cache-Control", "must-revalidate,no-cache,no-store");

    String body = responseBody.toString(StandardCharsets.UTF_8.name());
    assertFalse("The JSON should not be indented by default: " + body, body.contains("\n"));

    JsonNode metrics = new ObjectMapper().readTree(body);
    // The metrics registered by the servlet.
    assertTrue(metrics.get("gauges").has("memory.heap.used"));
    assertTrue(metrics.get("gauges").has("threads.count"));
    assertEquals(7, metrics.get("gauges").get("webauthor.http_conn_pool_max").get("value").asInt());

    // Durations and rates in seconds, as MetricsServlet did by default.
    JsonNode timer = metrics.get("timers").get("test-timer");
    assertEquals(1, timer.get("count").asInt());
    assertEquals(2.0, timer.get("max").asDouble(), 0.001);
    assertEquals("seconds", timer.get("duration_units").asText());
    assertEquals("calls/second", timer.get("rate_units").asText());

    // No samples, as MetricsServlet did by default.
    assertFalse(metrics.get("histograms").get("test-histogram").has("values"));
  }

  /**
   * <p><b>Description:</b> The "pretty" parameter asks for indented JSON.</p>
   * <p><b>Bug ID:</b> WA-10428</p>
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testMetricsPretty() throws Exception {
    Map<String, String> params = new HashMap<>();
    params.put("pretty", "true");

    servlet.doGet(request("/monitoring/metrics", params), resp);

    String body = responseBody.toString(StandardCharsets.UTF_8.name());
    assertTrue("The JSON should be indented: " + body, body.contains("\n"));
    assertTrue(new ObjectMapper().readTree(body).has("gauges"));
  }

  /**
   * <p><b>Description:</b> The thread dump is written as plain text and contains the current thread.</p>
   * <p><b>Bug ID:</b> WA-10428</p>
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testThreadDump() throws Exception {
    servlet.doGet(request("/monitoring/threads", new HashMap<>()), resp);

    verify(resp).setStatus(HttpServletResponse.SC_OK);
    verify(resp).setContentType("text/plain");
    verify(resp).setHeader("Cache-Control", "must-revalidate,no-cache,no-store");

    String body = responseBody.toString(StandardCharsets.UTF_8.name());
    assertTrue(body, body.contains("\"" + Thread.currentThread().getName() + "\""));
    assertTrue(body, body.contains(MonitoringServletTest.class.getName() + ".testThreadDump"));
  }

  /**
   * <p><b>Description:</b> The locked monitors are included by default and left out
   * when the "monitors" parameter is false.</p>
   * <p><b>Bug ID:</b> WA-10428</p>
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testThreadDumpMonitors() throws Exception {
    // Dropwizard's ThreadDump never prints the most recently locked monitor of a thread (its loop starts at
    // index 1), so the lock holder takes an inner lock too and the test checks the outer one.
    Object lock = new Object();
    Object innerLock = new Object();
    String lockId = Integer.toHexString(System.identityHashCode(lock));

    // The current thread does not report its own monitors, so another thread holds the lock.
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    Thread lockHolder = new Thread(() -> {
      synchronized (lock) {
        synchronized (innerLock) {
          locked.countDown();
          try {
            release.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }, "monitoring-test-lock-holder");
    lockHolder.start();
    try {
      assertTrue(locked.await(10, TimeUnit.SECONDS));

      servlet.doGet(request("/monitoring/threads", new HashMap<>()), resp);
      String body = responseBody.toString(StandardCharsets.UTF_8.name());
      assertTrue(body, body.contains("\"monitoring-test-lock-holder\""));
      assertTrue("The lock should be in the dump: " + body, body.contains("java.lang.Object@" + lockId));

      responseBody.reset();
      Map<String, String> params = new HashMap<>();
      params.put("monitors", "false");
      servlet.doGet(request("/monitoring/threads", params), resp);
      body = responseBody.toString(StandardCharsets.UTF_8.name());
      assertTrue(body, body.contains("\"monitoring-test-lock-holder\""));
      assertFalse("The lock should not be in the dump: " + body, body.contains("java.lang.Object@" + lockId));
    } finally {
      release.countDown();
      lockHolder.join(10_000);
    }
  }

  /**
   * @param pathInfo The path info of the request.
   * @param params The request parameters.
   *
   * @return A GET request for the monitoring servlet.
   */
  private static HttpServletRequest request(String pathInfo, Map<String, String> params) {
    HttpServletRequest req = mock(HttpServletRequest.class);
    when(req.getPathInfo()).thenReturn(pathInfo);
    for (Map.Entry<String, String> param : params.entrySet()) {
      when(req.getParameter(param.getKey())).thenReturn(param.getValue());
    }
    return req;
  }
}
