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

import java.awt.Dialog;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.filechooser.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.internal.Activator;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomDirImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);

  private static final String LAST_DICOM_DIR = "lastDicomDir";

  private final JTextField textField = new JTextField();
  private JCheckBox checkboxWriteInCache;

  public DicomDirImport() {
    super(Messages.getString("DicomDirImport.dicomdir"), 5);
    initGUI();
  }

  public void initGUI() {
    JButton btnSearch = new JButton(ResourceUtil.getIcon(ActionIcon.MORE_H));
    btnSearch.addActionListener(e -> browseDicomDirFile());
    add(
        LocalImport.buildSearchPanel(
            textField, btnSearch, Messages.getString("DicomDirImport.path"), LAST_DICOM_DIR));

    JButton btnCdrom =
        new JButton(
            Messages.getString("DicomDirImport.detect"), ResourceUtil.getIcon(OtherIcon.CDROM));
    btnCdrom.addActionListener(
        e -> {
          File dcmdir = getDcmDirFromMedia();
          if (dcmdir != null) {
            String path = dcmdir.getPath();
            textField.setText(path);
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DICOM_DIR, path);
          }
        });
    checkboxWriteInCache = new JCheckBox(Messages.getString("DicomDirImport.cache"));

    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, btnCdrom));
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, checkboxWriteInCache));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  public void browseDicomDirFile() {
    String directory = getImportPath();
    if (directory == null) {
      directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_DICOM_DIR, "");
    }
    JFileChooser fileChooser = new JFileChooser(directory);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(false);
    fileChooser.setFileFilter(
        new FileFilter() {

          @Override
          public String getDescription() {
            return "DICOMDIR";
          }

          @Override
          public boolean accept(File f) {
            if (f.isDirectory()) {
              return true;
            }
            return f.getName().equalsIgnoreCase("dicomdir") // NON-NLS
                || f.getName().equalsIgnoreCase("dicomdir."); // NON-NLS
          }
        });
    File selectedFile;
    if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION
        && (selectedFile = fileChooser.getSelectedFile()) != null) {
      String path = selectedFile.getPath();
      textField.setText(path);
      Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DICOM_DIR, path);
    }
  }

  @Override
  public void closeAdditionalWindow() {
    // Do nothing
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }

  private String getImportPath() {
    String path = textField.getText().trim();
    if (StringUtil.hasText(path)) {
      return path;
    }
    return null;
  }

  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    File file = null;
    String path = getImportPath();
    if (path != null) {
      File f = new File(path);
      if (f.canRead()) {
        file = f;
      } else {
        try {
          f = new File(new URI(path));
          if (f.canRead()) {
            file = f;
          }
        } catch (Exception e) {
          LOGGER.error("Cannot read {}", path);
        }
      }
    }
    if (file != null) {
      Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_DICOM_DIR, file.getPath());
      List<LoadSeries> loadSeries =
          loadDicomDir(file, dicomModel, checkboxWriteInCache.isSelected());

      if (loadSeries != null && !loadSeries.isEmpty()) {
        DicomModel.LOADING_EXECUTOR.execute(new LoadDicomDir(loadSeries, dicomModel));
      } else {
        LOGGER.error("Cannot import DICOM from {}", file);

        int response =
            JOptionPane.showConfirmDialog(
                this,
                Messages.getString("DicomExplorer.mes_import_manual"),
                this.getTitle(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == JOptionPane.YES_OPTION) {
          Dialog dialog = WinUtil.getParentDialog(this);
          if (dialog instanceof DicomImport dcmImport) {
            dcmImport.setCancelVeto(true); // Invalidate if closing the dialog
            dcmImport.showPage(Messages.getString("DicomImport.imp_dicom"));
            AbstractItemDialogPage page = dcmImport.getCurrentPage();
            if (page instanceof LocalImport localImport) {
              localImport.setImportPath(file.getParent());
            }
          }
        }
      }
    }
  }

  public static List<LoadSeries> loadDicomDir(
      File file, DicomModel dicomModel, boolean writeInCache) {
    List<LoadSeries> loadSeries = null;
    if (file != null && file.canRead()) {
      DicomDirLoader dirImport = new DicomDirLoader(file, dicomModel, writeInCache);
      loadSeries = dirImport.readDicomDir();
    }
    return loadSeries;
  }

  private static void addFiles(List<File> dvs, File folder) {
    if (folder.canRead()) {
      File[] files = folder.listFiles();
      if (files != null) {
        Collections.addAll(dvs, files);
      }
    }
  }

  public static File getDcmDirFromMedia() {
    final List<File> dvs = new ArrayList<>();
    try {
      if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { // NON-NLS
        dvs.addAll(Arrays.asList(File.listRoots()));
      } else if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { // NON-NLS
        addFiles(dvs, new File("/Volumes"));
      } else {
        addFiles(dvs, new File("/media"));
        addFiles(dvs, new File("/mnt"));
        addFiles(dvs, new File("/media/" + System.getProperty("user.name", "local"))); // NON-NLS
      }
    } catch (Exception e) {
      LOGGER.error("Error when reading device directories: {}", e.getMessage());
    }

    Collections.reverse(dvs);
    String[] dicomdir = {"DICOMDIR", "dicomdir", "DICOMDIR.", "dicomdir."}; // NON-NLS

    for (File drive : dvs) {
      // Detect read-only media
      if (drive.canRead() && !drive.isHidden()) {
        for (String s : dicomdir) {
          File f = new File(drive, s);
          if (f.canRead() && !f.canWrite()) {
            return f;
          }
        }
      }
    }

    return null;
  }
}
