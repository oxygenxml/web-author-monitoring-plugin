package com.oxygenxml.webapp.monitoring;

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
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
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
   * Logging category to use for metrics.
   */
  private static final String METRICS_LOGGER_CATEGORY = "com.oxygenxml.metrics";

  /**
   * Logger used to write metrics.
   */
  private final Logger metricsLogger = LogManager.getLogger(METRICS_LOGGER_CATEGORY);
  
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
    configureLogger(loggerContext);

    this.mapper = new ObjectMapper().registerModule(
        new MetricsModule(rateUnit, durationUnit, false));
  }

  private void configureLogger(LoggerContext ctx) {
    final Configuration config = ctx.getConfiguration();
    
    Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();
    
    ConsoleAppender appender = ConsoleAppender.createDefaultAppenderForLayout(layout);
    appender.start();
    config.addAppender(appender);
    
    AppenderRef ref = AppenderRef.createAppenderRef("MetricsConsoleAppender", null, null);
    AppenderRef[] refs = new AppenderRef[] {ref};

    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, 
        METRICS_LOGGER_CATEGORY, "true", refs, null, config, null);
    
    loggerConfig.addAppender(appender, null, null);
    config.addLogger(METRICS_LOGGER_CATEGORY, loggerConfig);
    ctx.updateLoggers();
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
      metricsLogger.info(metricsJson);
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
