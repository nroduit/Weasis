/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public class DicomSeriesHandler extends SequenceHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomSeriesHandler.class);
  protected final ViewCanvas<DicomImageElement> viewCanvas;

  public DicomSeriesHandler(ViewCanvas<DicomImageElement> viewCanvas) {
    super(true, true);
    this.viewCanvas = viewCanvas;
  }

  @Override
  protected boolean importDataExt(TransferSupport support) {
    Transferable transferable = support.getTransferable();
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    DataExplorerModel model;
    SeriesSelectionModel selList = null;
    if (dicomView != null) {
      selList = ((DicomExplorer) dicomView).getSelectionList();
    }
    DicomViewerPlugin selPlugin = null;
    for (ViewerPlugin<?> p : GuiUtils.getUICore().getViewerPlugins()) {
      if (p instanceof DicomViewerPlugin v && v.isContainingView(viewCanvas)) {
        selPlugin = v;
      }
    }
    if (selPlugin == null) {
      return false;
    }

    Series seq;
    try {
      seq = (Series) transferable.getTransferData(Series.sequenceDataFlavor);
      model = (DataExplorerModel) seq.getTagValue(TagW.ExplorerModel);
      SeriesViewerFactory plugin = GuiUtils.getUICore().getViewerFactory(selPlugin);
      if (plugin == null) {
        return false;
      }
      boolean buildNewPlugin =
          plugin instanceof MimeSystemAppFactory || !plugin.canReadMimeType(seq.getMimeType());

      if (buildNewPlugin) {
        ViewerPluginBuilder.openSequenceInDefaultPlugin(seq, model, true, true);
        return true;
      } else if (seq instanceof DicomSeries && model instanceof TreeModel treeModel) {
        if (selList != null) {
          selList.setOpeningSeries(true);
        }

        MediaSeriesGroup p1 = treeModel.getParent(seq, model.getTreeModelNodeForNewPlugin());
        MediaSeriesGroup p2 = null;
        if (p1 == null) {
          return false;
        }
        if (p1.equals(selPlugin.getGroupID())) {
          p2 = p1;
        }

        boolean readable = plugin.canReadSeries(seq);
        boolean addSeries = plugin.canAddSeries();
        if (!p1.equals(p2) || !readable || !addSeries) {
          if (readable || addSeries) {
            ViewerPluginBuilder.openSequenceInPlugin(plugin, seq, model, true, true);
          } else {
            Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
            props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, true);
            props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
            props.put(ViewerPluginBuilder.OPEN_IN_SELECTION, true);

            String mime = seq.getMimeType();
            plugin = GuiUtils.getUICore().getViewerFactory(mime);
            if (plugin != null) {
              ArrayList<MediaSeries<MediaElement>> list = new ArrayList<>(1);
              list.add(seq);
              ViewerPluginBuilder builder = new ViewerPluginBuilder(plugin, list, model, props);
              ViewerPluginBuilder.openSequenceInPlugin(builder);
            }
          }
          return false;
        }
      } else {
        // Not a DICOM Series
        return false;
      }
    } catch (Exception e) {
      LOGGER.error("Get draggable series", e);
      return false;
    } finally {
      if (selList != null) {
        selList.setOpeningSeries(false);
      }
    }
    if (selList != null) {
      selList.setOpeningSeries(true);
    }

    if (SynchData.Mode.TILE.equals(selPlugin.getSynchView().getSynchData().getMode())) {
      selPlugin.addSeries(seq);
      if (selList != null) {
        selList.setOpeningSeries(false);
      }
      return true;
    }

    viewCanvas.setSeries(seq);
    // Getting the focus has a delay and so it will trigger the view selection later
    if (Boolean.TRUE.equals(selPlugin.isContainingView(viewCanvas))) {
      selPlugin.setSelectedImagePaneFromFocus(viewCanvas);
    }
    if (selList != null) {
      selList.setOpeningSeries(false);
    }
    return true;
  }

  @Override
  protected boolean dropFiles(List<File> files, TransferSupport support) {
    return dropDicomFiles(files);
  }

  public static boolean dropDicomFiles(List<File> files) {
    if (files != null) {
      DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
      if (dicomView == null) {
        return false;
      }
      DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
      OpeningViewer openingViewer =
          OpeningViewer.getOpeningViewerByLocalKey(LocalImport.LAST_OPEN_VIEWER_MODE);
      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(files.toArray(File[]::new), true, model, openingViewer));
      return true;
    }
    return false;
  }
}
