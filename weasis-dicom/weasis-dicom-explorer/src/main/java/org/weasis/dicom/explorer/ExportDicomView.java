/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import javax.swing.JProgressBar;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.task.CircularProgressBar;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomElement;
import org.weasis.dicom.codec.DicomElement.DicomExportParameters;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.param.DicomProgress;

public class ExportDicomView extends AbstractItemDialogPage implements ExportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(ExportDicomView.class);
  protected final File exportDir;
  protected final DicomModel dicomModel;
  protected final ExportTree exportTree;
  protected final ExecutorService executor =
      ThreadUtil.buildNewFixedThreadExecutor(3, "Dicom Send task"); // NON-NLS

  public ExportDicomView(
      String title, int position, DicomModel dicomModel, CheckTreeModel treeModel) {
    this(title, position, dicomModel, treeModel, null);
  }

  public ExportDicomView(
      String title, int position, DicomModel dicomModel, CheckTreeModel treeModel, File exportDir) {
    super(title, position);
    this.dicomModel = dicomModel;
    this.exportTree = new ExportTree(treeModel);
    this.exportDir =
        exportDir == null
            ? FileUtil.createTempDir(
                AppProperties.buildAccessibleTempDirectory("tmp", "send")) // NON-NLS
            : exportDir;
  }

  public ExportDicomView initGUI() {
    exportTree.setBorder(UIManager.getBorder("ScrollPane.border"));
    add(exportTree);
    return this;
  }

  @Override
  public void closeAdditionalWindow() {
    executor.shutdown();
  }

  @Override
  public void resetToDefaultValues() {}

  @Override
  public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {

    ExplorerTask<Boolean, String> task =
        new ExplorerTask<>(getTitle(), false) {

          @Override
          protected Boolean doInBackground() throws Exception {
            return sendDicomFiles(model, this);
          }

          @Override
          protected void done() {
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
          }
        };
    executor.execute(task);
  }

  private boolean sendDicomFiles(CheckTreeModel model, ExplorerTask<Boolean, String> t) {
    dicomModel.firePropertyChange(
        new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, dicomModel, null, t));
    File exportDir =
        FileUtil.createTempDir(
            AppProperties.buildAccessibleTempDirectory("tmp", "send")); // NON-NLS
    try {
      writeDicom(t, exportDir, model);

      if (t.isCancelled()) {
        return false;
      }

      List<String> files = new ArrayList<>();
      files.add(exportDir.getAbsolutePath());

      DicomProgress dicomProgress = getDicomProgress(t);
      t.addCancelListener(dicomProgress);
      return exportAction(files, dicomProgress);
    } finally {
      FileUtil.recursiveDelete(exportDir);
    }
  }

  private static DicomProgress getDicomProgress(ExplorerTask<Boolean, String> t) {
    final CircularProgressBar progressBar = t.getBar();
    DicomProgress dicomProgress = new DicomProgress();
    dicomProgress.addProgressListener(
        p ->
            GuiExecutor.execute(
                () -> {
                  int c =
                      p.getNumberOfCompletedSuboperations() + p.getNumberOfFailedSuboperations();
                  int r = p.getNumberOfRemainingSuboperations();
                  progressBar.setValue((c * 100) / (c + r));
                }));
    return dicomProgress;
  }

  protected boolean exportAction(List<String> files, DicomProgress dicomProgress) {
    return true;
  }

  private void writeDicom(ExplorerTask<Boolean, String> task, File writeDir, CheckTreeModel model) {
    synchronized (this) {
      ArrayList<String> uids = new ArrayList<>();
      TreePath[] paths = model.getCheckingPaths();
      for (TreePath treePath : paths) {
        if (task.isCancelled()) {
          return;
        }
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();

        if (node.getUserObject() instanceof DicomImageElement img) {
          String iuid = TagD.getTagValue(img, Tag.SOPInstanceUID, String.class);
          int index = uids.indexOf(iuid);
          if (index == -1) {
            uids.add(iuid);
          } else {
            // Write only once the file for multi-frames
            continue;
          }

          String path = LocalExport.buildPath(img, false, false, node, null);
          saveFile(writeDir, img, iuid, path);
        } else if (node.getUserObject() instanceof DicomElement dcm) {
          String iuid = TagD.getTagValue((TagReadable) dcm, Tag.SOPInstanceUID, String.class);

          String path = LocalExport.buildPath((MediaElement) dcm, false, false, node, null);
          saveFile(writeDir, dcm, iuid, path);
        } else if (node.getUserObject() instanceof MediaSeries<?> s)
          saveOtherMediaSeries(writeDir, s, node);
      }
    }
  }

  private static void saveOtherMediaSeries(
      File writeDir, MediaSeries<?> s, DefaultMutableTreeNode node) {
    if (LangUtil.getNULLtoFalse((Boolean) s.getTagValue(TagW.ObjectToSave))) {
      Series<?> series = (Series<?>) s.getTagValue(CheckTreeModel.SourceSeriesForPR);
      if (series != null) {
        String seriesInstanceUID = UIDUtils.createUID();
        for (MediaElement dcm : series.getMedias(null, null)) {
          GraphicModel grModel = (GraphicModel) dcm.getTagValue(TagW.PresentationModel);
          if (grModel != null && grModel.hasSerializableGraphics()) {
            String path = LocalExport.buildPath(dcm, false, false, node, null);
            LocalExport.buildAndWritePR(
                dcm, false, new File(writeDir, path), null, node, seriesInstanceUID);
          }
        }
      }
    }
  }

  private void saveFile(File writeDir, DicomElement dcm, String iuid, String path) {
    File destinationDir = new File(writeDir, path);
    destinationDir.mkdirs();
    DicomExportParameters dicomExportParameters = new DicomExportParameters(null, true, null, 0, 0);
    dcm.saveToFile(new File(destinationDir, iuid), dicomExportParameters);
  }
}
