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
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.JViewport;
import javax.swing.event.ListSelectionListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageStatus;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.AcquireMediaInfo;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.core.bean.SeriesGroup.Type;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.acquire.explorer.gui.central.SeriesButton;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.SequenceHandler;

/**
 * Central thumbnail pane for acquired media elements. Handles drag-and-drop operations for series
 * and files.
 *
 * @param <E> the media element type
 */
public class AcquireCentralThumbnailPane<E extends MediaElement> extends AThumbnailListPane<E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireCentralThumbnailPane.class);

  public AcquireCentralThumbnailPane(List<E> list, JIThumbnailCache thumbCache) {
    super(new AcquireCentralThumbnailList<>(thumbCache));
    setList(list);
    setTransferHandler(new SeriesHandler());
  }

  public void setAcquireTabPanel(AcquireTabPanel acquireTabPanel) {
    if (thumbnailList instanceof AcquireCentralThumbnailList<?> centralList) {
      centralList.setAcquireTabPanel(acquireTabPanel);
    }
  }

  public void addListSelectionListener(ListSelectionListener listener) {
    thumbnailList.addListSelectionListener(listener);
  }

  public void addElements(List<E> elements) {
    if (elements != null) {
      IThumbnailModel<E> model = thumbnailList.getThumbnailListModel();
      elements.forEach(model::addElement);
    }
  }

  public void setList(List<E> elements) {
    IThumbnailModel<E> model = thumbnailList.getThumbnailListModel();
    model.clear();
    if (elements != null) {
      elements.forEach(model::addElement);
    }

    JViewport viewport = getViewport();
    if (viewport != null) {
      viewport.setView(thumbnailList.asComponent());
    }
  }

  private class SeriesHandler extends SequenceHandler {

    @Override
    public boolean canImport(TransferSupport support) {
      if (!super.canImport(support)) {
        return false;
      }
      return !AcquireManager.getInstance().getAcquireExplorer().getImportPanel().isLoading();
    }

    @Override
    protected boolean importDataExt(TransferSupport support) {
      Transferable transferable = support.getTransferable();
      try {
        Object data = transferable.getTransferData(Series.sequenceDataFlavor);
        if (data instanceof Series<?> series) {
          MediaElement media = series.getMedia(0, null, null);
          if (media != null) {
            addMediaToSeries(media);
          }
        }
        return true;
      } catch (UnsupportedFlavorException | IOException e) {
        LOGGER.error("Failed to drop thumbnail", e);
        return false;
      }
    }

    @Override
    protected boolean dropFiles(List<Path> files) {
      if (files == null || files.isEmpty()) {
        return false;
      }

      files.stream()
          .map(path -> ViewerPluginBuilder.getMedia(path, true))
          .filter(reader -> reader != null && !reader.getMediaFragmentMimeType().contains("dicom"))
          .map(MediaReader::getMediaElement)
          .filter(Objects::nonNull)
          .flatMap(Arrays::stream)
          .forEach(this::addMediaToSeries);
      return true;
    }

    private void addMediaToSeries(MediaElement... medias) {
      if (medias == null || medias.length == 0) {
        return;
      }

      SeriesGroup selectedGroup = getSelectedSeriesGroup();
      SeriesGroup targetGroup = null;

      for (MediaElement media : medias) {
        Type type = Type.fromMimeType(media);
        if (type == null) {
          continue;
        }
        AcquireMediaInfo info = AcquireManager.findByMedia(media);
        if (info != null) {
          targetGroup = determineTargetGroup(selectedGroup, type);
          importAndNotify(info, targetGroup);
        }
      }

      if (targetGroup != null) {
        AcquireManager.getInstance().notifySeriesSelection(targetGroup);
      }
    }

    private SeriesGroup getSelectedSeriesGroup() {
      if (thumbnailList instanceof AcquireCentralThumbnailList<?> centralList) {
        SeriesButton selected = centralList.getSelectedSeries();
        return selected != null ? selected.getSeries() : null;
      }
      return null;
    }

    private SeriesGroup determineTargetGroup(SeriesGroup selectedGroup, Type mediaType) {
      if (selectedGroup != null && mediaType.equals(selectedGroup.getType())) {
        return selectedGroup;
      }
      return new SeriesGroup(mediaType);
    }

    private void importAndNotify(AcquireMediaInfo info, SeriesGroup group) {
      AcquireManager.importMedia(info, group);
      info.setStatus(AcquireImageStatus.SUBMITTED);
      AcquireManager.getInstance().notifySeriesSelection(group);
    }
  }
}
