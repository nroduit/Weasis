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
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.imp.LocalImport;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.dicom.explorer.main.SeriesSelectionModel;

/**
 * Handles DICOM series drag-and-drop operations and file imports for a specific view canvas.
 * Manages series transfer between viewers and coordinates series opening in appropriate plugins.
 */
public class DicomSeriesHandler extends SequenceHandler {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomSeriesHandler.class);

  private final ViewCanvas<DicomImageElement> viewCanvas;

  public DicomSeriesHandler(ViewCanvas<DicomImageElement> viewCanvas) {
    super(true, true);
    this.viewCanvas = viewCanvas;
  }

  @Override
  protected boolean importDataExt(TransferSupport support) {
    try {
      var series = extractSeries(support.getTransferable());
      if (series == null) {
        return false;
      }

      var selectedPlugin = findSelectedPlugin();
      if (selectedPlugin == null) {
        return false;
      }

      var plugin = GuiUtils.getUICore().getViewerFactory(selectedPlugin);
      if (plugin == null) {
        return false;
      }

      var model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
      if (shouldBuildNewPlugin(plugin, series)) {
        ViewerPluginBuilder.openSequenceInDefaultPlugin(series, model, true, true);
        return true;
      }

      return handleDicomSeries(series, model, plugin, selectedPlugin);

    } catch (Exception e) {
      LOGGER.error("Error importing draggable series", e);
      return false;
    }
  }

  @Override
  protected boolean dropFiles(List<Path> files) {
    return dropDicomFiles(files);
  }

  /**
   * Handles dropping DICOM files for import.
   *
   * @param paths the list of file paths to import
   * @return true if import was initiated successfully
   */
  public static boolean dropDicomFiles(List<Path> paths) {
    if (paths == null || paths.isEmpty()) {
      return false;
    }
    return getDicomExplorer()
        .map(
            explorer -> {
              var model = explorer.getDataExplorerModel();
              var openingViewer =
                  OpeningViewer.getOpeningViewerByLocalKey(LocalImport.LAST_OPEN_VIEWER_MODE);
              var files = paths.stream().map(Path::toFile).toArray(File[]::new);
              DicomModel.LOADING_EXECUTOR.execute(
                  new LoadLocalDicom(files, true, model, openingViewer));
              return true;
            })
        .orElse(false);
  }

  private DicomSeries extractSeries(Transferable transferable) {
    try {
      if (transferable != null
          && transferable.getTransferData(Series.sequenceDataFlavor)
              instanceof DicomSeries series) {
        return series;
      }
    } catch (Exception e) {
      LOGGER.error("Failed to extract series from transferable", e);
    }
    return null;
  }

  private DicomViewerPlugin findSelectedPlugin() {
    return GuiUtils.getUICore().getViewerPlugins().stream()
        .filter(DicomViewerPlugin.class::isInstance)
        .map(DicomViewerPlugin.class::cast)
        .filter(plugin -> plugin.isContainingView(viewCanvas))
        .findFirst()
        .orElse(null);
  }

  private boolean shouldBuildNewPlugin(SeriesViewerFactory plugin, DicomSeries series) {
    return plugin instanceof MimeSystemAppFactory || !plugin.canReadMimeType(series.getMimeType());
  }

  private boolean handleDicomSeries(
      DicomSeries series,
      DataExplorerModel model,
      SeriesViewerFactory plugin,
      DicomViewerPlugin selectedPlugin) {
    if (series == null || !(model instanceof TreeModel treeModel)) {
      return false;
    }

    try {
      executeWithOpeningSeries(
          getSelectionModel(),
          () -> {
            if (canAddToCurrentPlugin(series, model, plugin, selectedPlugin, treeModel)) {
              addSeriesToPlugin(series, selectedPlugin);
            } else {
              openInAppropriatePlugin(series, model, plugin);
            }
          });
      return true;
    } catch (Exception e) {
      LOGGER.error("Error handling DICOM series", e);
      return false;
    }
  }

  private boolean canAddToCurrentPlugin(
      DicomSeries series,
      DataExplorerModel model,
      SeriesViewerFactory plugin,
      DicomViewerPlugin selectedPlugin,
      TreeModel treeModel) {
    var parentGroup = treeModel.getParent(series, model.getTreeModelNodeForNewPlugin());
    if (parentGroup == null) {
      return false;
    }

    return parentGroup.equals(selectedPlugin.getGroupID())
        && plugin.canReadSeries(series)
        && plugin.canAddSeries();
  }

  private void addSeriesToPlugin(DicomSeries series, DicomViewerPlugin selectedPlugin) {
    if (isTileMode(selectedPlugin)) {
      selectedPlugin.addSeries(series);
    } else {
      viewCanvas.setSeries(series);
      // Getting the focus has a delay, and so it will trigger the view selection later
      if (Boolean.TRUE.equals(selectedPlugin.isContainingView(viewCanvas))) {
        selectedPlugin.setSelectedImagePaneFromFocus(viewCanvas);
      }
    }
  }

  private boolean isTileMode(DicomViewerPlugin selectedPlugin) {
    return SynchData.Mode.TILE.equals(selectedPlugin.getSynchView().getSynchData().getMode());
  }

  private void openInAppropriatePlugin(
      DicomSeries series, DataExplorerModel model, SeriesViewerFactory plugin) {
    if (plugin.canReadSeries(series) || plugin.canAddSeries()) {
      ViewerPluginBuilder.openSequenceInPlugin(plugin, series, model, true, true);
    } else {
      openDicomSeriesInViewer(series, model);
    }
  }

  /**
   * Opens a DICOM series in the appropriate viewer plugin.
   *
   * @param series the DICOM series to open
   * @param model the data explorer model
   */
  public static void openDicomSeriesInViewer(DicomSeries series, DataExplorerModel model) {
    var plugin = GuiUtils.getUICore().getViewerFactory(series.getMimeType());
    if (plugin != null) {
      Map<String, Object> props = Collections.synchronizedMap(new HashMap<>());
      props.put(ViewerPluginBuilder.CMP_ENTRY_BUILD_NEW_VIEWER, true);
      props.put(ViewerPluginBuilder.BEST_DEF_LAYOUT, false);
      props.put(ViewerPluginBuilder.OPEN_IN_SELECTION, true);
      var builder = new ViewerPluginBuilder(plugin, List.of(series), model, props);
      ViewerPluginBuilder.openSequenceInPlugin(builder);
    }
  }

  private SeriesSelectionModel getSelectionModel() {
    return getDicomExplorer().map(DicomExplorer::getSelectionList).orElse(null);
  }

  private static Optional<DicomExplorer> getDicomExplorer() {
    var dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    return dicomView instanceof DicomExplorer explorer ? Optional.of(explorer) : Optional.empty();
  }

  private void executeWithOpeningSeries(SeriesSelectionModel selectionModel, Runnable action) {
    if (selectionModel != null) {
      selectionModel.setOpeningSeries(true);
      try {
        action.run();
      } finally {
        selectionModel.setOpeningSeries(false);
      }
    } else {
      action.run();
    }
  }
}
