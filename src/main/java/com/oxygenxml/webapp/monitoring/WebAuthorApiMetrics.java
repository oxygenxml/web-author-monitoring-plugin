package com.oxygenxml.webapp.monitoring;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricSet;
import com.google.common.collect.ImmutableMap;

import ro.sync.ecss.extensions.api.webapp.access.WebappPluginWorkspace;
import ro.sync.exml.workspace.api.PluginWorkspaceProvider;

/**
 * Metrics exported by Web Author through the API.
 * 
 * @author cristi_talau
 */
public class WebAuthorApiMetrics implements MetricSet{
  
  /**
   * Name of the gauge metrics exported by Web Author.
   */
  private static final List<String> gaugeNames = Arrays.asList(
      "http_conn_pool_max", "http_conn_pool_pending", "http_conn_pool_leased", "http_conn_pool_available");
  
  /**
   * The last metrics read from Web Author.
   */
  private Map<String, String> lastMetrics = null;
  
  /**
   * The timestamp when the last metrics were read.
   */
  private long lastMetricsTimestamp = 0;

  
  @Override
  public Map<String, Metric> getMetrics() {
    Map<String, Metric> metrics = new HashMap<String, Metric>();
    
    for (final String gauge: gaugeNames) {
      metrics.put(gauge, new Gauge<Integer>() {
        @Override
        public Integer getValue() {
          return Integer.valueOf(lastMetrics.get(gauge));
        }
      });
    }
    
    return metrics;
  }
  
  /**
   * @return The metrics from Web Author, possibly cached.
   */
  private Map<String, String> getWebAuthorMetrics() {
    long currentTimeMillis = System.currentTimeMillis();
    if (lastMetricsTimestamp < currentTimeMillis - 5 * 1000) {
      lastMetricsTimestamp = currentTimeMillis;
      lastMetrics = ((WebappPluginWorkspace)PluginWorkspaceProvider.getPluginWorkspace()).getMonitoringStats();
    }
    return lastMetrics;
  }

}
