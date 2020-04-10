package com.oxygenxml.webapp.monitoring;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.layout.PatternLayout;

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

/**
 * Reporter that writes log messages in the "com.oxygenxml.metrics" logger on the INFO level.
 * 
 * @author cristi_talau
 */
public class PlainTextReporter extends ScheduledReporter {
  /**
   * Logger for logging.
   */
  private static final Logger logger = LogManager.getLogger(MonitoringServlet.class.getName());

  /**
   * The JSON object mapper.
   */
  private final ObjectMapper mapper;

  /**
   * Logger used to write metrics.
   */
  private final org.apache.logging.log4j.core.Logger metricsLogger;

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

    LoggerContext loggerContext = LoggerContext.getContext(false);
    loggerContext.addPropertyChangeListener(new PropertyChangeListener() {
      @Override
      public void propertyChange(PropertyChangeEvent evt) {
        if ("config".equals(evt.getPropertyName())) {
          configureLogger(metricsLogger);
        }
      }
    });
    metricsLogger = loggerContext.getLogger("com.oxygenxml.metrics");
    configureLogger(metricsLogger);

    this.mapper = new ObjectMapper().registerModule(
        new MetricsModule(rateUnit, durationUnit, false));
  }

  private void configureLogger(org.apache.logging.log4j.core.Logger loggerImpl) {
    Layout<? extends Serializable> layout = PatternLayout
        .newBuilder()
        .build();
    ConsoleAppender appender = ConsoleAppender
        .newBuilder()
        .setLayout(layout)
        .setName("Console")
        .build();
    loggerImpl.addAppender(appender);
    loggerImpl.setAdditive(false);
    loggerImpl.setLevel(Level.ALL);
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
    addMetrics(gauges, metrics);
    addMetrics(counters, metrics);
    addMetrics(histograms, metrics);
    addMetrics(meters, metrics);
    addMetrics(timers, metrics);
    metrics.put("timestamp", System.currentTimeMillis());
    
    try {
      String metricsJson = AccessController.doPrivileged(
          (PrivilegedExceptionAction<String>) () -> mapper.writer().writeValueAsString(metrics));
      metricsLogger.info(metricsJson + '\n');
    } catch (Exception e) {
      logger.error(e, e);
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
}
