package com.oxygenxml.webapp.monitoring;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.RollingFileAppender;

import com.google.common.io.ByteStreams;


/**
 * The logs manager.
 * 
 * @author ctalau
 */
public class LogManager {

  /**
   * Comparator of log files by their ordinal number.
   * 
   * @author ctalau
   */
  private final class LogsOrderComparator implements Comparator<String> {
    
    public int compare(String log1, String log2) {
      int n1 = computeLogNumber(log1);
      int n2 = computeLogNumber(log2);
      return n1 - n2;
    }

    /**
     * Compute the ordinal number of a log file.
     * @param log The log file name.
     * @return The number.
     */
    private int computeLogNumber(String log) {
      int n = 0;
      String name = new File(log).getName();
      int extOffset = name.indexOf('.');
      if (extOffset != -1) {
        String ext = name.substring(extOffset);
        try {
          n = Integer.parseInt(ext);
        } catch (NumberFormatException e) {
          // Ignore - the index will be 0.
        }
      }
      return n;
    }
  }

  /**
   * The log file.
   */
  private String file;
  
  /**
   * The old levels for changed categories.
   */
  private final Map<String, Level> oldLevels = new HashMap<String, Level>();
  
  /**
   * Identifies the logged file.
   */
  public LogManager() {
    Logger rootLogger = Logger.getRootLogger();

    Enumeration<?> appenders = rootLogger.getAllAppenders();
    while (appenders.hasMoreElements()) {
      Appender ap = (Appender) appenders.nextElement();
      if (ap instanceof RollingFileAppender) {
        RollingFileAppender rfa = (RollingFileAppender) ap;
        file = rfa.getFile();
      }
    }
  }

  /**
   * Set the package or class to debug.
   * 
   * @param packageOrClass The package or class.
   * 
   * @throws IOException
   */
  public void setDebug(String packageOrClass) throws IOException {
    Logger logger = Logger.getLogger(packageOrClass);
    oldLevels.put(packageOrClass, logger.getLevel());
    logger.setLevel(Level.DEBUG);
  }
  
  /**
   * Set the package or class to its normal level.
   * 
   * @param packageOrClass The package or class.
   * 
   * @throws IOException
   */
  public void reset(String packageOrClass) {
    Logger logger = Logger.getLogger(packageOrClass);
    oldLevels.put(packageOrClass, logger.getLevel());
    logger.setLevel(oldLevels.get(packageOrClass));
    oldLevels.remove(packageOrClass);
  }

  /**
   * Resets all the packages to their normal levels.
   */
  public void resetAll() {
    Set<Entry<String, Level>> levels = oldLevels.entrySet();
    for (Entry<String, Level> level : levels) {
      Logger.getLogger(level.getKey()).setLevel(level.getValue());
    }
    oldLevels.clear();
  }
  
  /**
   * Writes the last log file to the output stream.
   * 
   * @param os The output stream.
   * 
   * @throws IOException
   */
  public void lastDay(OutputStream os) throws IOException {
    // TODO: dump last day of logs.
    FileInputStream in = new FileInputStream(file);
    try {
      ByteStreams.copy(in, os); 
    } finally {
      in.close();
    }
  }
  
  /**
   * Write all the logs to the output stream.
   * 
   * @param os The output stream.
   * 
   * @throws IOException
   */
  public void allLogs(OutputStream os) throws IOException {
    String[] logFiles = getLogFilesInOrder();
    ArrayUtils.reverse(logFiles);
    
    for (String log : logFiles) {
      FileInputStream in = new FileInputStream(log);
      try {
        ByteStreams.copy(in, os); 
      } finally {
        in.close();
      } 
    }
  }

  /**
   * @return The names of the log files in order.
   */
  private String[] getLogFilesInOrder() {
    final File logFile = new File(file);
    File logsDir = logFile.getParentFile();

    // Find the log files.
    String[] logFiles = logsDir.list(new FilenameFilter() {
      public boolean accept(File dir, String name) {
        return name.startsWith(logFile.getName());
      }
    });
    
    Arrays.sort(logFiles, new LogsOrderComparator());
    return logFiles;
  }
}
