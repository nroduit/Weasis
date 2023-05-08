/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.UriListFlavor;

public class SequenceHandler extends TransferHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(SequenceHandler.class);
  protected final boolean sequenceFlavor;
  protected final boolean fileFlavor;

  public SequenceHandler() {
    this(true, true);
  }

  public SequenceHandler(boolean sequence, boolean files) {
    super("series"); // NON-NLS
    this.sequenceFlavor = sequence;
    this.fileFlavor = files;
  }

  @Override
  public Transferable createTransferable(JComponent comp) {
    if (comp instanceof SeriesThumbnail thumbnail) {
      MediaSeries<?> t = thumbnail.getSeries();
      if (t instanceof Series) {
        return t;
      }
    }
    return null;
  }

  @Override
  public boolean canImport(TransferSupport support) {
    if (!support.isDrop()) {
      return false;
    }
    if (sequenceFlavor && support.isDataFlavorSupported(Series.sequenceDataFlavor)) {
      return true;
    }
    return fileFlavor
        && (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
            || support.isDataFlavorSupported(UriListFlavor.flavor));
  }

  @Override
  public boolean importData(TransferSupport support) {
    if (!canImport(support)) {
      return false;
    }

    Transferable transferable = support.getTransferable();

    List<File> files = null;
    // Not supported by some OS
    if (fileFlavor && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      try {
        files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
      } catch (Exception e) {
        LOGGER.error("Get draggable files", e);
      }
      return dropFiles(files, support);
    }
    // When dragging a file or group of files
    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
    else if (fileFlavor && support.isDataFlavorSupported(UriListFlavor.flavor)) {
      try {
        // Files with spaces in the filename trigger an error
        // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
        String val = (String) transferable.getTransferData(UriListFlavor.flavor);
        files = UriListFlavor.textURIListToFileList(val);
      } catch (Exception e) {
        LOGGER.error("Get draggable URIs", e);
      }
      return dropFiles(files, support);
    }
    return sequenceFlavor && importDataExt(support);
  }

  protected boolean importDataExt(TransferSupport support) {
    return false;
  }

  protected boolean dropFiles(List<File> files, TransferSupport support) {
    if (files != null) {
      for (File file : files) {
        ViewerPluginBuilder.openSequenceInDefaultPlugin(file, true, true);
      }
      return true;
    }
    return false;
  }
}
