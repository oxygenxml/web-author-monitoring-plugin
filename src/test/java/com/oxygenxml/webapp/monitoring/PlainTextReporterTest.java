package com.oxygenxml.webapp.monitoring;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import com.codahale.metrics.MetricRegistry;

public class PlainTextReporterTest {

  private PrintStream originalOut;

  @Before
  public void recordOut() {
    this.originalOut = System.out;
  }
  
  public void tearDown() {
    System.setOut(this.originalOut);
  }
  
  /**
   * <p><b>Description:</b> Test that instantiation works.</p>
   * <p><b>Bug ID:</b> WA-3775</p>
   *
   * @author cristi_talau
   *
   * @throws Exception
   */
  @Test
  public void testInstantiation() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PrintStream mockPrintStream = new PrintStream(out);
    System.setOut(mockPrintStream);
    
    MetricRegistry registry = Mockito.mock(MetricRegistry.class);
    PlainTextReporter reporter = new PlainTextReporter(registry, "Reporter", TimeUnit.SECONDS, TimeUnit.SECONDS);
    reporter.report();
    reporter.close();
    String log = new String(out.toByteArray(), StandardCharsets.US_ASCII);
    assertTrue(log.contains("timestamp"));
  }
}
