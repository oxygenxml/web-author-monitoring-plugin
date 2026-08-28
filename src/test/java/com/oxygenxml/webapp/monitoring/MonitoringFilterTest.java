package com.oxygenxml.webapp.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import ro.sync.ecss.extensions.api.webapp.access.WebappPluginWorkspace;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletConfig;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletContext;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletException;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.ServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.FilterChain;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletRequest;
import ro.sync.ecss.extensions.api.webapp.plugin.servlet.http.HttpServletResponse;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;
import ro.sync.servlet.monitoring.ActiveWebSocketsGauge;

/**
 * Tests for the monitoring filter.
 * @author cristi_talau
 */
public class MonitoringFilterTest {

  /**
   * The monitoring filter to be tested.
   */
  private MonitoringFilter filter;
  
  /**
   * The registry used to collect the metrics.
   */
  private MetricRegistry registry;

  /**
   * @throws Exception If it fails.
   */
  @Before
  public void setUp() throws Exception {
    WebappPluginWorkspace pluginWorkspace = Mockito.mock(WebappPluginWorkspace.class);
    PluginWorkspaceProvider.setPluginWorkspace(pluginWorkspace);
    
    filter = Mockito.spy(new MonitoringFilter());
    
    registry = new MetricRegistry();
    ServletContext context = Mockito.mock(ServletContext.class);
    Mockito.when(context.getAttribute(MonitoringServlet.METRICS_REGISTRY_ATTR_NAME)).thenReturn(registry);
    Mockito.when(context.getAttribute(ActiveWebSocketsGauge.class.getName())).thenReturn(new ActiveWebSocketsGauge());
    ServletConfig servletConfig = Mockito.mock(ServletConfig.class);
    Mockito.when(servletConfig.getServletContext()).thenReturn(context);

    // Init the filter
    filter.init(servletConfig);
  }
  
  /**
   * @throws Exception If it fails.
   */
  @After
  public void tearDown() throws Exception {
  }
  
  /**
   * Creates a response mock that records the status code set on it, the same way
   * a real response does. Initially, the status is 200 OK.
   * 
   * @return The response mock.
   */
  private static HttpServletResponse createResponse() {
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    AtomicInteger status = new AtomicInteger(HttpServletResponse.SC_OK);
    Mockito.doAnswer(invocation -> {
      status.set(invocation.getArgument(0));
      return null;
    }).when(response).setStatus(Mockito.anyInt());
    Mockito.when(response.getStatus()).thenAnswer(invocation -> status.get());
    return response;
  }
  
  /**
   * <p><b>Description:</b> Test that the requests are counted correctly.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testRequestMonitoring() throws Exception {
    String labelString = "label";
    Mockito.doReturn(labelString).when(filter).computeLabel(Mockito.<ServletRequest>any());

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getRequestURL()).thenReturn(new StringBuffer("/path/to/rest/endpoint"));
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    
    // Perform a request
    filter.doFilter(request, response, chain);
    
    // Assert it is counted.
    Map<String, Timer> metrics = registry.getTimers();
    assertEquals(1, metrics.size());
    String label = metrics.keySet().iterator().next();
    assertTrue(label.contains(labelString));
    Timer timer = metrics.get(label);
    assertEquals(1, timer.getCount());
    
    // Assert it is counted again.
    filter.doFilter(request, response, chain);
    assertEquals(2, timer.getCount());
  }
  
  
  /**
   * <p><b>Description:</b> Assert that error related metrics are correctly
   * computed.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testErrorMonitoring() throws Exception {
    String labelString = "label";
    Mockito.doReturn(labelString).when(filter).computeLabel(Mockito.<ServletRequest>any());

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    // The servlet returns the wrong status code.
    FilterChain failingChain = new FilterChain() {
      @Override
      public void doFilter(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    };
    
    // The clock used to time the metrics.
    Clock clock = Mockito.mock(Clock.class);
    filter.setClock(clock);
    
    // Perform several failing and non-failing requests.
    for (long i = 0; i < 30; i++) {
      filter.doFilter(request, createResponse(), failingChain);
      Mockito.when(clock.getTick()).thenReturn(1000L * 1000L * 1000L * i);
      filter.doFilter(request, createResponse(), Mockito.mock(FilterChain.class));
    }
    
    // Assert it is counted.
    Map<String, Meter> metrics = registry.getMeters();
    assertEquals(1, metrics.size());
    String label = metrics.keySet().iterator().next();
    assertTrue(label.contains(labelString));
    Meter timer = metrics.get(label);
    assertEquals(30, timer.getCount());

    // Assert that the error rate is around .5.
    Gauge<?> gauge = registry.getGauges().get("errors.percentage." + labelString);
    assertEquals(.5, (Double)gauge.getValue(), .1);
  }
  
  /**
   * <p><b>Description:</b> Test that the static requests are not counted.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testMonitoringStatic() throws Exception {
    Mockito.doReturn(null).when(filter).computeLabel(Mockito.<ServletRequest>any());

    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    HttpServletResponse response = Mockito.mock(HttpServletResponse.class);
    FilterChain chain = Mockito.mock(FilterChain.class);
    
    // Perform a request
    filter.doFilter(request, response, chain);
    
    // Assert it is counted.
    Map<String, Metric> metrics = registry.getMetrics();
    assertEquals(0, metrics.size());
  }

  
  /**
   * <p><b>Description:</b> Test the computation of the edit label.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testEditLabel() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getContextPath()).thenReturn("/webapp");
    Mockito.when(request.getRequestURL()).thenReturn(
        new StringBuffer().append("http://localhost/webapp/rest/" + WebappPluginWorkspace.restApiVersion + "/edit/doc1/insert"));
    Mockito.when(request.getPathInfo()).thenReturn("edit/doc1/insert");
    
    String label = filter.computeLabel(request);
    assertEquals(MonitoringFilter.EDIT_LABEL, label);
  }
  
  /**
   * <p><b>Description:</b> Test the computation of the load request label.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testLoadLabel() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getContextPath()).thenReturn("/webapp");
    Mockito.when(request.getRequestURL()).thenReturn(
        new StringBuffer().append("http://localhost/webapp/rest/" + WebappPluginWorkspace.restApiVersion + "/doc/load/url1"));
    Mockito.when(request.getPathInfo()).thenReturn("/doc/load/url1");
    
    String label = filter.computeLabel(request);
    assertEquals("doc-load", label);
  }
  
  /**
   * <p><b>Description:</b> Test the computation of a static request label.</p>
   * <p><b>Bug ID:</b> EXM-31437</p>
   *
   * @author cristi_talau
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testStaticLabel() throws Exception {
    HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
    Mockito.when(request.getContextPath()).thenReturn("/webapp");
    Mockito.when(request.getRequestURL()).thenReturn(
        new StringBuffer().append("http://localhost/webapp/static/oxygen.html"));
    Mockito.when(request.getPathInfo()).thenReturn(null);
    
    String label = filter.computeLabel(request);
    assertNull(label);
  }
}
