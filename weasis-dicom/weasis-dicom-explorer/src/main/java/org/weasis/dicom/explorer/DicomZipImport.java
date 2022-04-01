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
import java.net.URI;
import java.util.List;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ClosableURLConnection;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.api.util.URLParameters;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomZipImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomZipImport.class);

  private static final String LAST_DICOM_ZIP = "lastDicomZip";
  private static final String LAST_DICOM_ZIP_OPEN_MODE = "last.dicom.zip.open.mode";

  private final JComboBox<OpeningViewer> openingViewerJComboBox =
      new JComboBox<>(OpeningViewer.values());
  private File selectedFile;
  private final JLabel fileLabel = new JLabel();

  public DicomZipImport() {
    super(Messages.getString("DicomZipImport.title"), 3);
    initGUI();
  }

  public void initGUI() {
    JButton btnOpen = new JButton(Messages.getString("DicomZipImport.select_file"));
    btnOpen.addActionListener(e -> browseImgFile());
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, btnOpen, fileLabel));

    add(LocalImport.buildOpenViewerPanel(openingViewerJComboBox, LAST_DICOM_ZIP_OPEN_MODE));
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  public void browseImgFile() {
    String directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DICOM_ZIP, "");

    JFileChooser fileChooser = new JFileChooser(directory);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(false);
    fileChooser.setFileFilter(new FileFormatFilter("zip", "ZIP")); // NON-NLS
    if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
        || (selectedFile = fileChooser.getSelectedFile()) == null) {
      fileLabel.setText("");
    } else {
      Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DICOM_ZIP, selectedFile.getParent());
      fileLabel.setText(selectedFile.getPath());
    }
  }

  @Override
  public void closeAdditionalWindow() {
    OpeningViewer openingViewer =
        Objects.requireNonNullElse(
            (OpeningViewer) openingViewerJComboBox.getSelectedItem(), OpeningViewer.ONE_PATIENT);
    Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DICOM_ZIP_OPEN_MODE, openingViewer.name());
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }

  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    loadDicomZip(selectedFile, dicomModel);
  }

  public static void loadDicomZip(File file, DicomModel dicomModel) {
    if (file != null && file.canRead()) {
      File dir =
          FileUtil.createTempDir(
              AppProperties.buildAccessibleTempDirectory("tmp", "zip")); // NON-NLS
      try {
        FileUtil.unzip(file, dir);
      } catch (IOException e) {
        LOGGER.error("unzipping", e);
      }
      File dicomdir = new File(dir, "DICOMDIR");
      if (dicomdir.canRead()) {
        DicomDirLoader dirImport = new DicomDirLoader(dicomdir, dicomModel, false);
        List<LoadSeries> loadSeries = dirImport.readDicomDir();
        if (loadSeries != null && !loadSeries.isEmpty()) {
          DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, dicomModel));
        } else {
          LOGGER.error("Cannot import DICOM from {}", file);
        }
      } else {
        LoadLocalDicom dicom =
            new LoadLocalDicom(new File[] {dir}, true, dicomModel, OpeningViewer.ONE_PATIENT);
        DicomModel.LOADING_EXECUTOR.execute(dicom);
      }
    }
  }

  public static void loadDicomZip(String uri, DicomModel dicomModel) {
    if (StringUtil.hasText(uri)) {
      File tempFile = null;
      try {
        URI u = new URI(uri);
        if (u.toString().startsWith("file:")) { // NON-NLS
          tempFile = new File(u.getPath());
        } else {
          tempFile = File.createTempFile("dicom_", ".zip", AppProperties.APP_TEMP_DIR); // NON-NLS
          ClosableURLConnection urlConnection =
              NetworkUtil.getUrlConnection(
                  u.toURL(), new URLParameters(BundleTools.SESSION_TAGS_FILE));
          FileUtil.writeStreamWithIOException(urlConnection.getInputStream(), tempFile);
        }
      } catch (Exception e) {
        LOGGER.error("Loading DICOM Zip", e);
      }
      loadDicomZip(tempFile, dicomModel);
    }
  }
}
