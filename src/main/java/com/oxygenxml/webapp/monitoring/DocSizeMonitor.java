package com.oxygenxml.webapp.monitoring;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class DocSizeMonitor implements WorkspaceAccessPluginExtension {
 
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
        log.warn("Large document: {} - {}KB", documentNode.getSystemID(), (size / 1000));
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
