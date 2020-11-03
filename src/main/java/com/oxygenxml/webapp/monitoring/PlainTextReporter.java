package com.oxygenxml.webapp.monitoring;

import java.io.Serializable;
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

import ro.sync.security.Sandbox;

/**
 * Reporter that writes log messages in the "com.oxygenxml.metrics" logger on the INFO level.
 * 
 * @author cristi_talau
 */
public class PlainTextReporter extends ScheduledReporter {
  /**
   * Logger for logging.
   */
  private static final Logger logger = LogManager.getLogger(PlainTextReporter.class);

  
  /**
   * Logger used to write metrics.
   */
  private static final Logger metricsLogger = LogManager.getLogger(PlainTextReporter.class);

  /**
   * The custom appender name.
   */
  private static final String CUSTOM_APPENDER_NAME = "MetricsAppender";

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

    if (!wasExplicitlyConfigured(metricsLogger)) {
      configureLogger(metricsLogger);
    }

    this.mapper = new ObjectMapper().registerModule(
        new MetricsModule(rateUnit, durationUnit, false));
  }
  /**
   * Check if the given logger have an appender with {@link CUSTOM_APPENDER_NAME} name.
   * @param aLogger The logger to check.
   * @return <code>true</code> if the given logger have an appender with {@link CUSTOM_APPENDER_NAME} name.
   */
  private static boolean wasExplicitlyConfigured(Logger aLogger) {
    org.apache.logging.log4j.core.Logger loggerImpl = LoggerContext.getContext(false).getLogger(aLogger.getName());
    return loggerImpl.getAppenders().containsKey(CUSTOM_APPENDER_NAME)
        || (loggerImpl.isAdditive() && loggerImpl.getParent().getAppenders().containsKey(CUSTOM_APPENDER_NAME));
  }

  /**
   * Configure the given logger to have a simple layout.
   * @param aLogger The logger to configure.
   */
  private static void configureLogger(Logger aLogger) {
    LoggerContext ctx = LoggerContext.getContext(false);
    final Configuration config = ctx.getConfiguration();
    
    Layout<? extends Serializable> layout = PatternLayout.createDefaultLayout();
    ConsoleAppender appender = ConsoleAppender.createDefaultAppenderForLayout(layout);
    appender.start();
    config.addAppender(appender);
    AppenderRef ref = AppenderRef.createAppenderRef(CUSTOM_APPENDER_NAME, null, null);
    AppenderRef[] refs = new AppenderRef[] {ref};

    LoggerConfig loggerConfig = LoggerConfig.createLogger(false, Level.ALL, 
        aLogger.getName(), "true", refs, null, config, null);
    
    loggerConfig.addAppender(appender, null, null);
    config.removeLogger(aLogger.getName());
    config.addLogger(aLogger.getName(), loggerConfig);
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
      // Metrics serialization requires full privileges since it queries the thread state
      // of the VM.
      String metricsJson = Sandbox.runWithAllPerms((PrivilegedExceptionAction<String>) // NOSONAR 
          () -> mapper.writer().writeValueAsString(metrics));
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
