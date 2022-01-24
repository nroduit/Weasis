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

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Dialog;
import java.awt.Dimension;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
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

  private static final String lastDICOMDIR = "lastDicomDir";

  private JTextField textField;
  private JCheckBox chckbxWriteInCache;

  public DicomDirImport() {
    super(Messages.getString("DicomDirImport.dicomdir"), 5);
    initGUI();
  }

  public void initGUI() {
    JLabel lblImportAFolder =
        new JLabel(Messages.getString("DicomDirImport.path") + StringUtil.COLON);
    textField = new JTextField();
    textField.setText(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, ""));
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    Dimension dim = textField.getPreferredSize();
    dim.width = 200;
    textField.setPreferredSize(dim);
    textField.setMaximumSize(new Dimension(Short.MAX_VALUE, dim.height));

    JButton btnSearch = new JButton(ResourceUtil.getIcon(ActionIcon.MORE_H));
    btnSearch.addActionListener(e -> browseImgFile());
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnSearch);

    add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            lblImportAFolder, GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_SMALL), textField));

    JButton btncdrom =
        new JButton(
            Messages.getString("DicomDirImport.detect"), ResourceUtil.getIcon(OtherIcon.CDROM));
    btncdrom.addActionListener(
        e -> {
          File dcmdir = getDcmDirFromMedia();
          if (dcmdir != null) {
            String path = dcmdir.getPath();
            textField.setText(path);
            Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, path);
          }
        });
    chckbxWriteInCache = new JCheckBox(Messages.getString("DicomDirImport.cache"));

    add(GuiUtils.getFlowLayoutPanel(btncdrom));
    add(GuiUtils.getFlowLayoutPanel(chckbxWriteInCache));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  public void browseImgFile() {
    String directory = getImportPath();
    if (directory == null) {
      directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDICOMDIR, "");
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
            // NON-NLS
            return f.getName().equalsIgnoreCase("dicomdir") // NON-NLS
                || f.getName().equalsIgnoreCase("dicomdir.");
          }
        });
    File selectedFile = null;
    if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
        || (selectedFile = fileChooser.getSelectedFile()) == null) {
      return;
    } else {
      String path = selectedFile.getPath();
      textField.setText(path);
      Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDICOMDIR, path);
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
    List<LoadSeries> loadSeries = loadDicomDir(file, dicomModel, chckbxWriteInCache.isSelected());

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
          if (file != null) {
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
      File file, DicomModel dicomModel, boolean writeIncache) {
    List<LoadSeries> loadSeries = null;
    if (file != null) {
      if (file.canRead()) {
        DicomDirLoader dirImport = new DicomDirLoader(file, dicomModel, writeIncache);
        loadSeries = dirImport.readDicomDir();
      }
    }
    return loadSeries;
  }

  public static File getDcmDirFromMedia() {
    final List<File> dvs = new ArrayList<>();
    try {
      if (AppProperties.OPERATING_SYSTEM.startsWith("win")) { // NON-NLS
        dvs.addAll(Arrays.asList(File.listRoots()));
      } else if (AppProperties.OPERATING_SYSTEM.startsWith("mac")) { // NON-NLS
        dvs.addAll(Arrays.asList(new File("/Volumes").listFiles()));
      } else {
        dvs.addAll(Arrays.asList(new File("/media").listFiles()));
        dvs.addAll(Arrays.asList(new File("/mnt").listFiles()));
        File userDir = new File("/media/" + System.getProperty("user.name", "local")); // NON-NLS
        if (userDir.exists()) {
          dvs.addAll(Arrays.asList(userDir.listFiles()));
        }
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
