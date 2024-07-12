/*
 * Copyright (c) 2024 Weasis Team and other contributors.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.launcher.Launcher;
import org.weasis.core.ui.launcher.Launcher.Configuration;
import org.weasis.core.ui.launcher.Placeholder;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;

public class DicomExportAction {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomExportAction.class);

  protected final Launcher launcher;
  protected final DicomModel dicomModel;

  protected static final BiFunction<String, ImageViewerEventManager<?>, String> folderReplacement =
      (val, _) -> {
        if (Placeholder.DICOM_WADO_FOLDER.equals(val)) {
          return DicomMediaIO.DICOM_EXPORT_DIR.getPath();
        } else if (Placeholder.DICOM_QR_FOLDER.equals(val)) {
          return AppProperties.APP_TEMP_DIR
              + File.separator
              + "tmp" // NON-NLS
              + File.separator
              + "qr"; // NON-NLS
        } else if (Placeholder.DICOM_LAST_FOLDER.equals(val)) {
          return LocalPersistence.getProperties().getProperty(LocalImport.LAST_OPEN_DIR, "");
        }
        return null;
      };

  protected static final BiFunction<String, ImageViewerEventManager<?>, String> tagReplacement =
      (tag, manager) -> {
        if (manager == null) {
          return null;
        }

        ViewCanvas<?> view = manager.getSelectedViewPane();
        if (view != null && view.getSeries() instanceof DicomSeries series) {
          ImageElement image = view.getImage();
          TagW tagW = TagW.get(tag);
          if (tagW == null) {
            return null;
          }
          if (image != null && image.containTagKey(tagW)) {
            return tagW.getFormattedTagValue(image.getTagValue(tagW), null);
          }
          if (series.containTagKey(tagW)) {
            return tagW.getFormattedTagValue(series.getTagValue(tagW), null);
          }

          MediaSeriesGroup study = null;
          MediaSeriesGroup patient = null;
          Object tagValue = series.getTagValue(TagW.ExplorerModel);
          if (tagValue instanceof DicomModel model) {
            study = model.getParent(series, DicomModel.study);
            patient = model.getParent(series, DicomModel.patient);
          }
          if (study != null && study.containTagKey(tagW)) {
            return tagW.getFormattedTagValue(study.getTagValue(tagW), null);
          }
          if (patient != null && patient.containTagKey(tagW)) {
            return tagW.getFormattedTagValue(patient.getTagValue(tagW), null);
          }
        }
        return null;
      };

  protected static final Placeholder DICOM_FOLDER_PLACEHOLDER =
      new Placeholder("\\{dicom:(\\S+)}", folderReplacement); // NON-NLS
  protected static final Placeholder DICOM_TAG_PLACEHOLDER =
      new Placeholder("\\{tag:(\\S+)}", tagReplacement); // NON-NLS

  public DicomExportAction(Launcher launcher, DicomModel dicomModel) {
    this.launcher = Objects.requireNonNull(launcher);
    this.dicomModel = Objects.requireNonNull(dicomModel);
  }

  public void execute() throws IOException {
    Configuration config = launcher.getConfiguration();
    CheckTreeModel treeModel = new CheckTreeModel(dicomModel);
    ExportDicomView view =
        new ExportDicomView(Messages.getString("dicom.selection"), 0, dicomModel, treeModel);
    JScrollPane scrollPane = new JScrollPane(view.initGUI());
    scrollPane.setPreferredSize(GuiUtils.getDimension(650, 500));
    scrollPane.setBorder(BorderFactory.createEmptyBorder());

    int option =
        JOptionPane.showConfirmDialog(
            UICore.getInstance().getApplicationWindow(),
            scrollPane,
            view.getTitle(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

    Set<Path> sources = new HashSet<>();
    if (option == JOptionPane.OK_OPTION) {
      TreePath[] paths = treeModel.getCheckingPaths();
      for (TreePath treePath : paths) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode) treePath.getLastPathComponent();
        if (node.getUserObject() instanceof DicomImageElement img) {
          sources.add(img.getFile().toPath());
        }
      }
    }

    if (sources.isEmpty()) {
      return;
    }

    dicomModel.firePropertyChange(
        new ObservableEvent(BasicAction.LOADING_START, dicomModel, null, this));
    try {
      Path destination = getSelectionTempFolder().toPath();
      config.setSource(destination);
      copyFilesOrDirectories(sources, destination);
    } catch (IOException e) {
      LOGGER.error("Error copying files", e);
    } finally {
      dicomModel.firePropertyChange(
          new ObservableEvent(BasicAction.LOADING_STOP, dicomModel, null, this));
    }
  }

  protected static File getSelectionTempFolder() {
    return FileUtil.createTempDir(
        AppProperties.buildAccessibleTempDirectory("tmp", "sel")); // NON-NLS
  }

  protected static void copyFilesOrDirectories(Set<Path> sources, Path destination)
      throws IOException {
    for (Path source : sources) {
      if (Files.isDirectory(source)) {
        FileUtil.copyFolder(source, destination, StandardCopyOption.REPLACE_EXISTING);
      } else {
        Files.copy(
            source, destination.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
      }
    }
  }

  public static String resolvePlaceholders(
      String template, ImageViewerEventManager<?> eventManager) {
    String result = DICOM_FOLDER_PLACEHOLDER.resolvePlaceholders(template, eventManager);
    return DICOM_TAG_PLACEHOLDER.resolvePlaceholders(result, eventManager);
  }
}
