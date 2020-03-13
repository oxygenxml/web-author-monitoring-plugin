package com.oxygenxml.webapp.monitoring;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import ro.sync.ecss.extensions.api.node.AuthorDocument;
import ro.sync.ecss.extensions.api.webapp.AuthorDocumentModel;
import ro.sync.ecss.extensions.api.webapp.access.WebappEditingSessionLifecycleListener;
import ro.sync.ecss.extensions.api.webapp.access.WebappPluginWorkspace;
import ro.sync.exml.plugin.workspace.WorkspaceAccessPluginExtension;
import ro.sync.exml.workspace.api.standalone.StandalonePluginWorkspace;

/**
 * Class that monitors the size of the opened documents.
 * 
 * @author cristi_talau
 */
public class DocSizeMonitor implements WorkspaceAccessPluginExtension {
  /**
   * Logger for logging.
   */
  private static final Logger logger = LogManager.getLogger(DocSizeMonitor.class.getName());

  /**
   * Threshold for large documents size.
   */
  private static final int LARGE_DOCUMENT_THRESHOLD = 10 * 1000;
  
  /**
   * Listener that records the size of the opened documents.
   *  
   * @author cristi_talau
   */
  private final class DocSizeMonitorListener extends WebappEditingSessionLifecycleListener {

    @Override
    public void editingSessionStarted(String id, AuthorDocumentModel model) {
      AuthorDocument documentNode = model.getAuthorDocumentController().getAuthorDocumentNode();
      int size = documentNode.getEndOffset() - documentNode.getStartOffset();
      if (size > LARGE_DOCUMENT_THRESHOLD) {
        logger.warn("Large document: " + documentNode.getSystemID() + " - " + (size / 1000) + "KB");
      }
    }
  }

  @Override
  public boolean applicationClosing() {
    return true;
  }

  @Override
  public void applicationStarted(StandalonePluginWorkspace pluginWorkspace) {
    ((WebappPluginWorkspace)pluginWorkspace).addEditingSessionLifecycleListener(new DocSizeMonitorListener());
  }
}
