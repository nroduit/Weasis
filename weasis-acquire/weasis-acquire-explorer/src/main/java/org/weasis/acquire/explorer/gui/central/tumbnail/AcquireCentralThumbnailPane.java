/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import javax.swing.JComponent;
import javax.swing.event.ListSelectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.acquire.explorer.gui.central.SeriesButton;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.SequenceHandler;

public class AcquireCentralThumbnailPane<E extends MediaElement> extends AThumbnailListPane<E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireCentralThumbnailPane.class);

  public AcquireCentralThumbnailPane(List<E> list, JIThumbnailCache thumbCache) {
    super(new AcquireCentralThumbnailList<>(thumbCache));
    setList(list);
    setTransferHandler(new SeriesHandler());
  }

  public void setAcquireTabPanel(AcquireTabPanel acquireTabPanel) {
    ((AcquireCentralThumbnailList<?>) this.thumbnailList).setAcquireTabPanel(acquireTabPanel);
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    this.thumbnailList.addListSelectionListener(listener);
  }

  public void addElements(List<E> elements) {
    if (elements != null) {
      IThumbnailModel<E> model = this.thumbnailList.getThumbnailListModel();
      elements.forEach(model::addElement);
      repaintList();
    }
  }

  public void setList(List<E> elements) {
    IThumbnailModel<E> model = this.thumbnailList.getThumbnailListModel();
    model.clear();
    if (elements != null) {
      elements.forEach(model::addElement);
      repaintList();
    }
  }

  public void repaintList() {
    // Repaint the scroll pane correctly (otherwise not all the elements of JList are repainted)
    if (thumbnailList.asComponent() instanceof JComponent component) {
      component.updateUI();
    }
  }

  private class SeriesHandler extends SequenceHandler {

    @Override
    public boolean canImport(TransferSupport support) {
      boolean result = super.canImport(support);
      if (result
          && AcquireManager.getInstance().getAcquireExplorer().getImportPanel().isLoading()) {
        result = false;
      }
      return result;
    }

    protected boolean importDataExt(TransferSupport support) {
      Transferable transferable = support.getTransferable();
      try {
        Object object = transferable.getTransferData(Series.sequenceDataFlavor);
        if (object instanceof Series<?> series) {
          MediaElement media = series.getMedia(0, null, null);
          addToSeries(media);
        }
      } catch (UnsupportedFlavorException | IOException e) {
        LOGGER.error("Drop thumbnail", e);
      }

      return true;
    }

    private void addToSeries(MediaElement media) {
      if (media instanceof ImageElement imageElement) {
        AcquireCentralThumbnailList<?> thumbnailList =
            (AcquireCentralThumbnailList<?>) AcquireCentralThumbnailPane.this.thumbnailList;
        AcquireImageInfo info = AcquireManager.findByImage(imageElement);
        if (info != null) {
          SeriesGroup seriesGroup =
              Optional.ofNullable(thumbnailList.getSelectedSeries())
                  .map(SeriesButton::getSeries)
                  .orElse(null);
          AcquireManager.importImage(info, seriesGroup);
        }
      }
    }

    @Override
    protected boolean dropFiles(List<File> files, TransferSupport support) {
      if (files == null) {
        return false;
      }
      for (File file : files) {
        MediaReader<?> reader = ViewerPluginBuilder.getMedia(file, false);
        if (reader != null && !reader.getMediaFragmentMimeType().contains("dicom")) { // NON-NLS
          MediaElement[] medias = reader.getMediaElement();
          if (medias != null) {
            for (MediaElement mediaElement : medias) {
              addToSeries(mediaElement);
            }
          }
        }
      }
      return true;
    }
  }
}
