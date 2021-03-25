package com.oxygenxml.webapp.monitoring;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;

import ro.sync.servlet.monitoring.MonitoringManager;

public class MonitoringLifecycleHandler {
  
  private static MonitoringLifecycleHandler instance;
  
  private MonitoringManager monitoringManager;
  
  private MonitoringLifecycleHandler() {};
  
  public static MonitoringLifecycleHandler getInstance() {
    if(instance == null) {
      instance = new MonitoringLifecycleHandler();
    }
    return instance;
  }

  /**
   * Mekes sure the monitoring manager is initialized.
   * 
   * @param servletContext the servlet context.
   */
  public void ensureInitialized(ServletContext servletContext) {
    if(monitoringManager == null) {
      monitoringManager = new MonitoringManager();
      monitoringManager.contextInitialized(new ServletContextEvent(servletContext));
    }
  }

  public void destroy(ServletContext servletContext) {
    monitoringManager.contextDestroyed(new ServletContextEvent(servletContext));
  }
}
