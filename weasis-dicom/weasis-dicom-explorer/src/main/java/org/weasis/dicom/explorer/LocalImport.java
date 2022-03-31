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
import java.awt.Dimension;
import java.io.File;
import java.net.URI;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

  public static final String LAST_OPEN_VIEWER_MODE = "last.open.viewer.mode";
  public static final String LAST_RECURSIVE_MODE = "last.recursive.mode";
  private static final String LAST_OPEN_DIR = "lastOpenDir";

  private final JComboBox<OpeningViewer> openingViewerJComboBox =
      new JComboBox<>(OpeningViewer.values());
  private final JCheckBox checkboxSearch =
      new JCheckBox(Messages.getString("LocalImport.recursive"), true);
  private final JTextField textField = new JTextField();
  private File[] files;

  public LocalImport() {
    super(Messages.getString("LocalImport.local_dev"));
    setComponentPosition(0);
    initGUI();
  }

  public void initGUI() {
    JButton btnSearch = new JButton(ResourceUtil.getIcon(ActionIcon.MORE_H));
    btnSearch.addActionListener(e -> browseImgFile());
    add(
        buildSearchPanel(
            textField, btnSearch, Messages.getString("LocalImport.imp_files"), LAST_OPEN_DIR));

    checkboxSearch.setSelected(
        LangUtil.geEmptytoTrue(
            Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_RECURSIVE_MODE)));

    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, checkboxSearch));

    add(buildOpenViewerPanel(openingViewerJComboBox, LAST_OPEN_VIEWER_MODE));
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  static JPanel buildSearchPanel(
      JTextField textField, JButton btnSearch, String title, String key) {
    JLabel lblImportAFolder = new JLabel(title + StringUtil.COLON);
    textField.setText(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(key, ""));
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    Dimension dim = textField.getPreferredSize();
    dim.width = 200;
    textField.setPreferredSize(dim);
    textField.setMaximumSize(new Dimension(Short.MAX_VALUE, dim.height));
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnSearch);
    return GuiUtils.getHorizontalBoxLayoutPanel(
        lblImportAFolder, GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_SMALL), textField);
  }

  static JPanel buildOpenViewerPanel(JComboBox<OpeningViewer> comboBox, String key) {
    JLabel labelOpenPatient =
        new JLabel(Messages.getString("DicomExplorer.open_win") + StringUtil.COLON);
    comboBox.setSelectedItem(
        OpeningViewer.getOpeningViewer(
            Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(key), OpeningViewer.ONE_PATIENT));
    return GuiUtils.getFlowLayoutPanel(
        ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, labelOpenPatient, comboBox);
  }

  public void browseImgFile() {
    String directory = getImportPath();
    if (directory == null) {
      directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(LAST_OPEN_DIR, "");
    }
    JFileChooser fileChooser = new JFileChooser(directory);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fileChooser.setMultiSelectionEnabled(true);
    // FileFormatFilter.setImageDecodeFilters(fileChooser);
    File[] selectedFiles;
    if (fileChooser.showOpenDialog(WinUtil.getParentWindow(this))
            == JFileChooser.APPROVE_OPTION // Use parent because this has large size
        && (selectedFiles = fileChooser.getSelectedFiles()) != null
        && selectedFiles.length > 0) {
      files = null;
      String lastDir;
      if (selectedFiles.length == 1) {
        lastDir = selectedFiles[0].getPath();
        textField.setText(lastDir);
      } else {
        files = selectedFiles;
        lastDir = files[0].getParent();
        textField.setText(Messages.getString("LocalImport.multi_dir"));
      }
      if (StringUtil.hasText(lastDir)) {
        Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_OPEN_DIR, lastDir);
      }
    }
  }

  @Override
  public void closeAdditionalWindow() {
    OpeningViewer openingViewer =
        Objects.requireNonNullElse(
            (OpeningViewer) openingViewerJComboBox.getSelectedItem(), OpeningViewer.ONE_PATIENT);
    Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_OPEN_VIEWER_MODE, openingViewer.name());
    Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(
        LAST_RECURSIVE_MODE, String.valueOf(checkboxSearch.isSelected()));
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }

  private String getImportPath() {
    String path = textField.getText().trim();
    if (!path.equals("") && !path.equals(Messages.getString("LocalImport.multi_dir"))) {
      return path;
    }
    return null;
  }

  public void setImportPath(String path) {
    textField.setText(path);
  }

  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    if (files == null) {
      String path = getImportPath();
      if (path != null) {
        File file = new File(path);
        if (file.canRead()) {
          files = new File[] {file};
        } else {
          try {
            file = new File(new URI(path));
            if (file.canRead()) {
              files = new File[] {file};
            }
          } catch (Exception e) {
            LOGGER.error("Cannot import DICOM from {}", path);
          }
        }
      }
    }
    if (files != null) {
      String lastDir;
      if (files.length == 1) {
        lastDir = files[0].getPath();
      } else {
        lastDir = files[0].getParent();
      }
      if (StringUtil.hasText(lastDir)) {
        Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(LAST_OPEN_DIR, lastDir);
      }
      OpeningViewer openingViewer =
          Objects.requireNonNullElse(
              (OpeningViewer) openingViewerJComboBox.getSelectedItem(), OpeningViewer.ONE_PATIENT);
      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(files, checkboxSearch.isSelected(), dicomModel, openingViewer));
      files = null;
    }
  }
}
