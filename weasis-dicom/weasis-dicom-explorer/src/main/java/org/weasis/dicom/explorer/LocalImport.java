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
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.internal.Activator;

public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

  private static final String lastDirKey = "lastOpenDir";

  private JCheckBox chckbxSearch;
  private JTextField textField;
  private File[] files;

  public LocalImport() {
    super(Messages.getString("LocalImport.local_dev"));
    setComponentPosition(0);
    initGUI();
  }

  public void initGUI() {
    JLabel lblImportAFolder =
        new JLabel(Messages.getString("LocalImport.imp_files") + StringUtil.COLON);
    textField = new JTextField();
    textField.setText(Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDirKey, ""));
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    Dimension dim = textField.getPreferredSize();
    dim.width = 200;
    textField.setPreferredSize(dim);
    textField.setMaximumSize(new Dimension(Short.MAX_VALUE, dim.height));

    JButton button = new JButton(" ... ");
    button.addActionListener(e -> browseImgFile());
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, button);

    add(
        GuiUtils.getHorizontalBoxPanel(
            lblImportAFolder, GuiUtils.createHorizontalStrut(ITEM_SEPARATOR_SMALL), textField));

    chckbxSearch = new JCheckBox(Messages.getString("LocalImport.recursive"));
    chckbxSearch.setSelected(true);

    add(GuiUtils.getComponentsInJPanel(chckbxSearch));
    add(GuiUtils.getBoxYLastElement(LAST_FILLER_HEIGHT));
  }

  public void browseImgFile() {
    String directory = getImportPath();
    if (directory == null) {
      directory = Activator.IMPORT_EXPORT_PERSISTENCE.getProperty(lastDirKey, "");
    }
    JFileChooser fileChooser = new JFileChooser(directory);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fileChooser.setMultiSelectionEnabled(true);
    // FileFormatFilter.setImageDecodeFilters(fileChooser);
    File[] selectedFiles = null;
    if (fileChooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION
        || (selectedFiles = fileChooser.getSelectedFiles()) == null
        || selectedFiles.length == 0) {
      return;
    } else {
      files = null;
      String lastDir = null;
      if (selectedFiles.length == 1) {
        lastDir = selectedFiles[0].getPath();
        textField.setText(lastDir);
      } else {
        files = selectedFiles;
        lastDir = files[0].getParent();
        textField.setText(Messages.getString("LocalImport.multi_dir"));
      }
      if (StringUtil.hasText(lastDir)) {
        Activator.IMPORT_EXPORT_PERSISTENCE.setProperty(lastDirKey, lastDir);
      }
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
    if (path != null
        && !path.equals("")
        && !path.equals(Messages.getString("LocalImport.multi_dir"))) {
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
      LoadLocalDicom dicom = new LoadLocalDicom(files, chckbxSearch.isSelected(), dicomModel);
      DicomModel.LOADING_EXECUTOR.execute(dicom);
      files = null;
    }
  }
}
