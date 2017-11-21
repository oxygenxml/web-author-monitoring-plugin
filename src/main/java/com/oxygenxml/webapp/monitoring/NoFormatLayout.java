package com.oxygenxml.webapp.monitoring;

import org.apache.log4j.Layout;
import org.apache.log4j.spi.LoggingEvent;

/**
 * Layout that applies no format to the message.
 * 
 * @author cristi_talau
 */
public class NoFormatLayout extends Layout {
  @Override
  public boolean ignoresThrowable() {
    return false;
  }
  
  @Override
  public String format(LoggingEvent event) {
    return event.getRenderedMessage();
  }
  
  @Override
  public void activateOptions() {
    // Nothing to do here.
  }
}