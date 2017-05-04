package com.oxygenxml.webapp.monitoring;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.Map;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

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
    FilterConfig filterConfig = Mockito.mock(FilterConfig.class);
    Mockito.when(filterConfig.getServletContext()).thenReturn(context );

    // Init the filter
    filter.init(filterConfig);
  }
  
  /**
   * @throws Exception If it fails.
   */
  @After
  public void tearDown() throws Exception {
    filter.destroy();
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

    ServletRequest request = Mockito.mock(HttpServletRequest.class);
    ServletResponse response = Mockito.mock(HttpServletResponse.class);
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

    ServletRequest request = Mockito.mock(HttpServletRequest.class);
    ServletResponse response = Mockito.mock(HttpServletResponse.class);
    // The servlet returns the wrong status code.
    FilterChain failingChain = new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
        ((HttpServletResponse)response).setStatus(HttpServletResponse.SC_BAD_REQUEST);
      }
    };
    
    // The clock used to time the metrics.
    Clock clock = Mockito.mock(Clock.class);
    filter.setClock(clock);
    
    // Perform several failing and non-failing requests.
    for (long i = 0; i < 30; i++) {
      filter.doFilter(request, response, failingChain);
      Mockito.when(clock.getTick()).thenReturn(1000L * 1000L * 1000L * i);
      filter.doFilter(request, response, Mockito.mock(FilterChain.class));
    }
    
    // Assert it is counted.
    Map<String, Meter> metrics = registry.getMeters();
    assertEquals(1, metrics.size());
    String label = metrics.keySet().iterator().next();
    assertTrue(label.contains(labelString));
    Meter timer = metrics.get(label);
    assertEquals(30, timer.getCount());

    // Assert that the error rate is around .5.
    Gauge<?> gauge = registry.getGauges().values().iterator().next();
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

    ServletRequest request = Mockito.mock(HttpServletRequest.class);
    ServletResponse response = Mockito.mock(HttpServletResponse.class);
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
