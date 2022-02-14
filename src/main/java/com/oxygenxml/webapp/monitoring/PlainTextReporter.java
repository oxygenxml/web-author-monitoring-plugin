package com.oxygenxml.webapp.monitoring;

import java.security.AccessControlException;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.Timer;
import com.codahale.metrics.json.MetricsModule;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import ro.sync.security.Sandbox;


/**
 * Reporter that writes log messages in the {@code NO_LAYOUT_LOGGER.METRICS} logger on the INFO level.
 * 
 * A logger or parent logger (NO_LAYOUT_LOGGER) and corresponding appender must be configured in WebAuthor.
 * The pattern of the appender must be set to log the message only (i.e. {@code %m%n})
 * 
 * @author cristi_talau
 */
@Slf4j
public class PlainTextReporter extends ScheduledReporter {

  /**
   * The name of the logger 
   */
  public static final String METRICS_LOGGER_NAME = "NO_LAYOUT_LOGGER.METRICS";
  
  private static final Logger metricsLog = LoggerFactory.getLogger(METRICS_LOGGER_NAME);

  /**
   * The JSON object mapper.
   */
  private final ObjectMapper mapper;

  /**
   * Constructor.
   * 
   * @param registry The registry to take metrics from.
   * @param name The name of the reporter.
   * @param rateUnit The unit for rates.
   * @param durationUnit The unit for durations.
   */
  public PlainTextReporter(MetricRegistry registry,
      String name,
      TimeUnit rateUnit,
      TimeUnit durationUnit) {
    super(registry, name, MetricFilter.ALL, rateUnit, durationUnit);


    this.mapper = new ObjectMapper().registerModule(
        new MetricsModule(rateUnit, durationUnit, false));
  }
  
  /**
   * @see {@link ScheduledReporter#report()}
   */
  @Override
  public void report(@SuppressWarnings("rawtypes") SortedMap<String, Gauge> gauges, 
      SortedMap<String, Counter> counters,
      SortedMap<String, Histogram> histograms, 
      SortedMap<String, Meter> meters, 
      SortedMap<String, Timer> timers) {

    Map<String, Object> metrics = new HashMap<>();
    addGaugeMetricsWithAllPermissions(gauges, metrics);
    addMetrics(counters, metrics);
    addMetrics(histograms, metrics);
    addMetrics(meters, metrics);
    addMetrics(timers, metrics);
    metrics.put("timestamp", System.currentTimeMillis());
    
    try {
      // Metrics serialization requires full privileges since it queries the thread state
      // of the VM.
      String metricsJson = Sandbox.runWithAllPerms((PrivilegedExceptionAction<String>) // NOSONAR 
          () -> mapper.writer().writeValueAsString(metrics));
      metricsLog.info(metricsJson);
    } catch (Exception e) {
      log.error(e.getMessage(), e);
    }
  }

  /**
   * Add some metrics to the final map, rewriting the keys to be AWS CloudWatch friendly.
   * @param metrics
   * @param allMetrics
   */
  private <T> void addMetrics(Map<String, T> metrics, Map<String, Object> allMetrics) {
    for (Entry<String, T> entry : metrics.entrySet()) {
      allMetrics.put(entry.getKey().replace('.', '-'), entry.getValue());
    }
  }
  
  /**
   * Add some metrics to the final map, rewriting the keys to be AWS CloudWatch friendly.
   * @param metrics The metrics to add.
   * @param allMetrics The map where to put the values.
   */
  private void addGaugeMetricsWithAllPermissions(@SuppressWarnings("rawtypes") Map<String, Gauge> metrics, 
      Map<String, Object> allMetrics) {
    Sandbox.runWithAllPerms((PrivilegedAction<String>) // NOSONAR 
      () -> {
        metrics.forEach((name, gauge) ->
          addGaugeValue(name, gauge, allMetrics));
        return "";
      });
  }
  
  private void addGaugeValue(String name, @SuppressWarnings("rawtypes") Gauge gauge, 
      Map<String, Object> allMetrics) {
    try {
      allMetrics.put(name, gauge.getValue());
    } catch (AccessControlException e) {
      log.error("Error serializing metric: {}", name, e);
    }
  }
}
