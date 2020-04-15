package com.oxygenxml.webapp.monitoring;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;

import ro.sync.basic.util.StringUtil;
import ro.sync.ecss.extensions.api.webapp.access.WebappPluginWorkspace;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Tests for {@link MonitoringServlet}.
 */
public class MonitoringServletTest {
  private static PrintStream originalOut;
  private static ByteArrayOutputStream out;

  /**
   * Before class.
   */
  @BeforeClass
  public static void recordOut() {
    originalOut = System.out;
    out = new ByteArrayOutputStream();
    PrintStream mockPrintStream = new PrintStream(out) {
      @Override
      public void write(byte[] buf, int off, int len) {
        super.write(buf, off, len);
        originalOut.write(buf, off, len);
      }
    };
    System.setOut(mockPrintStream);
  }

  /**
   * After class.
   */
  @AfterClass
  public static void tearDown() {
    System.setOut(originalOut);
  }
  /**
   * Before each.
   */
  @Before
  public void before() {
    out.reset();
  }

  /**
   * <p><b>Description:</b>Assert that metrics are logger after a long request.</p>
   * <p><b>Bug ID:</b> WA-3853</p>
   *
   * @author bogdan_dumitru
   *
   * @throws Exception If it fails.
   */
  @Test
  public void testLogMetricsAfterLongRequest() throws Exception {
//    System.out.println(ConsoleAppender.class.getProtectionDomain().getCodeSource().getLocation());
    
    PluginWorkspaceProvider.setPluginWorkspace(mock(WebappPluginWorkspace.class));
    ServletConfig servletConfig = mock(ServletConfig.class);
    ServletContext context = mock(ServletContext.class);
    MetricRegistry registry = new MetricRegistry();

    when(servletConfig.getServletContext()).thenReturn(context);
    when(context.getAttribute(eq(MonitoringServlet.METRICS_REGISTRY_ATTR_NAME))).thenReturn(registry);

    MonitoringServlet servlet = new MonitoringServlet(100, TimeUnit.MILLISECONDS) {
      @Override
      public ServletConfig getServletConfig() {
        return servletConfig;
      }
    };
    MonitoringFilter filter = new MonitoringFilter() {
      @Override
      void logLargeDuration(ServletRequest request, long durationNanoSeconds) {
        super.logLargeDuration(request, Long.MAX_VALUE);
      }
    };
    FilterConfig filterConfig = mock(FilterConfig.class);
    when(filterConfig.getServletContext()).thenReturn(context);
    filter.init(filterConfig);
    servlet.init();

    Thread.sleep(200);

    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    when(request.getPathInfo()).thenReturn("/a-path");
    when(request.getRequestURL()).thenReturn(new StringBuffer(
        "https://staging-webapp.sync.ro/oxygen-xml-web-author/app/oxygen.html"));
    filter.doFilter(request, response, mock(FilterChain.class));

    Thread.sleep(400);

    String output = out.toString();
    assertTrue(output.contains("Long request"));
    assertTrue(output.contains("memory-heap-usage"));

    String afterLongRequest = output.substring(output.indexOf("Long request"));
    assertTrue(StringUtil.countMatches(afterLongRequest, "memory-heap-usage") > 2);
  }
}
