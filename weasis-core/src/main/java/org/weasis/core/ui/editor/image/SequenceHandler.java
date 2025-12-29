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
import java.nio.file.Path;
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

/**
 * Handles drag-and-drop operations for media sequences and files. Supports transferring series
 * between components and importing files from external sources.
 */
public class SequenceHandler extends TransferHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(SequenceHandler.class);

  private final boolean sequenceFlavor;
  private final boolean fileFlavor;

  public SequenceHandler() {
    this(true, true);
  }

  /**
   * Creates a handler with configurable sequence and file support.
   *
   * @param sequence whether to support series drag-and-drop
   * @param files whether to support file drag-and-drop
   */
  public SequenceHandler(boolean sequence, boolean files) {
    super("series"); // NON-NLS
    this.sequenceFlavor = sequence;
    this.fileFlavor = files;
  }

  @Override
  public Transferable createTransferable(JComponent comp) {
    return comp instanceof SeriesThumbnail thumbnail ? getSeriesFromThumbnail(thumbnail) : null;
  }

  private Transferable getSeriesFromThumbnail(SeriesThumbnail thumbnail) {
    MediaSeries<?> series = thumbnail.getSeries();
    return series instanceof Series ? series : null;
  }

  @Override
  public boolean canImport(TransferSupport support) {
    if (!support.isDrop()) {
      return false;
    }
    return (sequenceFlavor && support.isDataFlavorSupported(Series.sequenceDataFlavor))
        || (fileFlavor && isFileFlavorSupported(support));
  }

  private boolean isFileFlavorSupported(TransferSupport support) {
    return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
        || support.isDataFlavorSupported(UriListFlavor.flavor);
  }

  @Override
  public boolean importData(TransferSupport support) {
    if (!canImport(support)) {
      return false;
    }

    if (fileFlavor) {
      List<Path> files = extractFiles(support);
      if (files != null) {
        return dropFiles(files);
      }
    }

    return sequenceFlavor && importDataExt(support);
  }

  private List<Path> extractFiles(TransferSupport support) {
    Transferable transferable = support.getTransferable();

    if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
      return extractJavaFileList(transferable);
    }

    if (support.isDataFlavorSupported(UriListFlavor.flavor)) {
      return extractUriList(transferable);
    }

    return null;
  }

  private List<Path> extractJavaFileList(Transferable transferable) {
    try {
      @SuppressWarnings("unchecked")
      List<File> files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
      return files.stream().map(File::toPath).toList();
    } catch (Exception e) {
      LOGGER.error("Failed to extract draggable files", e);
      return null;
    }
  }

  private List<Path> extractUriList(Transferable transferable) {
    try {
      String uriList = (String) transferable.getTransferData(UriListFlavor.flavor);
      return UriListFlavor.textURIListToPathList(uriList).stream().toList();
    } catch (Exception e) {
      LOGGER.error("Failed to extract draggable URIs", e);
      return null;
    }
  }

  /**
   * Extension point for subclasses to handle additional data flavors.
   *
   * @param support the transfer support
   * @return true if data was successfully imported
   */
  protected boolean importDataExt(TransferSupport support) {
    return false;
  }

  protected boolean dropFiles(List<Path> files) {
    files.forEach(file -> ViewerPluginBuilder.openSequenceInDefaultPlugin(file, true, true));
    return true;
  }
}
