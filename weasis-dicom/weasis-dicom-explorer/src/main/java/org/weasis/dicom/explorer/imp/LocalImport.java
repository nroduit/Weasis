/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import java.awt.Dimension;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;

/**
 * Local DICOM import page for importing files and folders.
 *
 * <p>This class provides UI components and functionality to import DICOM data from local file
 * system, supporting both individual files and directory imports with recursive scanning option.
 */
public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

  public static final String LAST_RECURSIVE_MODE = "last.recursive.mode";
  public static final String LAST_OPEN_FILES = "last.open.files";
  public static final String LAST_OPEN_DIR = "lastOpenDir";

  private static final String MULTI_DIR_MESSAGE = "LocalImport.multi_dir";
  private static final String DIR_SUFFIX = ".dir";

  private final JCheckBox checkboxSearch =
      new JCheckBox(Messages.getString("LocalImport.recursive"), true);
  private final JTextField textFieldFiles = new JTextField();
  private final JTextField textFieldFolders = new JTextField();
  private File[] selectedFiles;
  private File[] selectedFolders;

  public LocalImport() {
    super(Messages.getString("LocalImport.local_dev"));
    setComponentPosition(0);
    initGUI();
  }

  public void initGUI() {
    add(
        createSearchPanel(
            textFieldFiles,
            Messages.getString("LocalImport.files"),
            LAST_OPEN_FILES,
            this::browseFiles));
    add(GuiUtils.boxVerticalStrut(BLOCK_SEPARATOR));
    add(
        createSearchPanel(
            textFieldFolders,
            Messages.getString("LocalImport.folders"),
            LAST_OPEN_DIR,
            this::browseFolders));

    Properties props = LocalPersistence.getProperties();
    boolean recursive = LangUtil.emptyToTrue(props.getProperty(LAST_RECURSIVE_MODE));
    checkboxSearch.setSelected(recursive);
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR, checkboxSearch));

    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  static JPanel createSearchPanel(
      JTextField textField, String title, String key, Runnable browseAction) {
    JLabel label = new JLabel(title + StringUtil.COLON);
    JButton btnSearch = new JButton(ResourceUtil.getIcon(ActionIcon.MORE_H));
    btnSearch.addActionListener(_ -> browseAction.run());

    configureTextField(textField, key);
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, btnSearch);
    return GuiUtils.getHorizontalBoxLayoutPanel(
        label, GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_SMALL), textField);
  }

  private static void configureTextField(JTextField textField, String key) {
    Properties props = LocalPersistence.getProperties();
    textField.setText(props.getProperty(key, ""));
    textField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    Dimension preferredSize = textField.getPreferredSize();
    preferredSize.width = 200;
    textField.setPreferredSize(preferredSize);
    textField.setMaximumSize(new Dimension(Short.MAX_VALUE, preferredSize.height));
  }

  private void browseFiles() {
    browseFileSystem(true);
  }

  private void browseFolders() {
    browseFileSystem(false);
  }

  private void browseFileSystem(boolean filesOnly) {
    String lastItemKey = filesOnly ? LAST_OPEN_FILES : LAST_OPEN_DIR;
    String initialDirectory = getInitialDirectory(lastItemKey);

    SystemFileChooser fileChooser = new SystemFileChooser(initialDirectory);
    fileChooser.setFileSelectionMode(
        filesOnly ? SystemFileChooser.FILES_ONLY : SystemFileChooser.DIRECTORIES_ONLY);
    fileChooser.setMultiSelectionEnabled(true);
    if (fileChooser.showOpenDialog(WinUtil.getParentWindow(this))
        == SystemFileChooser.APPROVE_OPTION) {
      File[] selected = fileChooser.getSelectedFiles();
      if (selected != null && selected.length > 0) {
        handleSelection(selected, filesOnly, lastItemKey);
      }
    }
  }

  private String getInitialDirectory(String lastItemKey) {
    String path =
        (LAST_OPEN_FILES.equals(lastItemKey) ? textFieldFiles : textFieldFolders).getText().trim();

    if (StringUtil.hasText(path) && !path.equals(Messages.getString(MULTI_DIR_MESSAGE))) {
      return directoryPath(path);
    }

    Properties props = LocalPersistence.getProperties();
    String dir = props.getProperty(lastItemKey + DIR_SUFFIX, "");
    if (StringUtil.hasText(dir)) {
      return dir;
    }
    return directoryPath(props.getProperty(lastItemKey, ""));
  }

  /**
   * Returns the path itself when it points to a directory, or its parent path when it points to a
   * file. This ensures the file chooser always opens at a valid directory.
   */
  private static String directoryPath(String path) {
    if (!StringUtil.hasText(path)) {
      return path;
    }
    File f = new File(path);
    if (f.isFile()) {
      File parent = f.getParentFile();
      return parent != null ? parent.getPath() : path;
    }
    return path;
  }

  private void handleSelection(File[] selected, boolean filesOnly, String lastItemKey) {
    JTextField textField = filesOnly ? textFieldFiles : textFieldFolders;
    boolean isSingleSelection = selected.length == 1;

    // Update UI
    if (isSingleSelection) {
      textField.setText(selected[0].getPath());
    } else {
      textField.setText(Messages.getString(MULTI_DIR_MESSAGE));
    }

    // Store selection
    if (filesOnly) {
      selectedFiles = selected;
    } else {
      selectedFolders = selected;
    }
    // Save to preferences
    saveSelection(lastItemKey, selected, isSingleSelection);
  }

  private void saveSelection(String key, File[] selected, boolean isSingleSelection) {
    Properties props = LocalPersistence.getProperties();
    if (isSingleSelection) {
      // Single selection: store the exact path for display; derive chooser dir from it
      String path = selected[0].getPath();
      props.setProperty(key, path);
      props.setProperty(key + DIR_SUFFIX, directoryPath(path));
    } else {
      // Multi selection: nothing to display – clear the display value so the text field
      // stays empty after a restart; store the common parent for the file chooser only
      props.setProperty(key, "");
      File commonParent = commonParent(selected);
      props.setProperty(key + DIR_SUFFIX, commonParent != null ? commonParent.getPath() : "");
    }
  }

  /**
   * Returns the deepest directory that is a common ancestor of all given files. Falls back to the
   * parent of the first item when no deeper common ancestor exists.
   */
  private static File commonParent(File[] files) {
    File parent = files[0].getParentFile();
    if (parent == null) {
      return null;
    }
    for (int i = 1; i < files.length; i++) {
      File p = files[i].getParentFile();
      while (p != null && !isAncestorOrSelf(p, parent) && !isAncestorOrSelf(parent, p)) {
        parent = parent.getParentFile();
        if (parent == null) {
          return null;
        }
      }
      // Keep the deeper one if one is an ancestor of the other
      if (p != null && isAncestorOrSelf(parent, p)) {
        parent = p;
      }
    }
    return parent;
  }

  private static boolean isAncestorOrSelf(File ancestor, File file) {
    File current = file;
    while (current != null) {
      if (current.equals(ancestor)) {
        return true;
      }
      current = current.getParentFile();
    }
    return false;
  }

  @Override
  public void closeAdditionalWindow() {
    Properties props = LocalPersistence.getProperties();
    props.setProperty(LAST_RECURSIVE_MODE, String.valueOf(checkboxSearch.isSelected()));

    // Skip only the multi-selection placeholder – the real parent path is already stored.
    String filesText = textFieldFiles.getText().trim();
    if (!filesText.equals(Messages.getString(MULTI_DIR_MESSAGE))) {
      props.setProperty(LAST_OPEN_FILES, filesText);
      props.setProperty(LAST_OPEN_FILES + DIR_SUFFIX, directoryPath(filesText));
    }
    String foldersText = textFieldFolders.getText().trim();
    if (!foldersText.equals(Messages.getString(MULTI_DIR_MESSAGE))) {
      props.setProperty(LAST_OPEN_DIR, foldersText);
      props.setProperty(LAST_OPEN_DIR + DIR_SUFFIX, directoryPath(foldersText));
    }
  }

  private OpeningViewer getOpeningViewer() {
    return OpeningViewer.ALL_PATIENTS;
  }

  @Override
  public void resetToDefaultValues() {
    // No default values to reset
  }

  public void setImportPath(String path) {
    textFieldFolders.setText(path);
  }

  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    List<File> filesToImport = collectFilesToImport();

    if (!filesToImport.isEmpty()) {
      File[] filesArray = filesToImport.toArray(File[]::new);

      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(
              filesArray, checkboxSearch.isSelected(), dicomModel, getOpeningViewer()));
      resetSelection();
    }
  }

  private List<File> collectFilesToImport() {
    List<File> files = new ArrayList<>();

    // Add selected files
    if (selectedFiles != null) {
      files.addAll(List.of(selectedFiles));
    } else {
      File file = getFileFromTextField(textFieldFiles);
      if (file != null && file.isFile()) {
        files.add(file);
      }
    }

    // Add selected folders
    if (selectedFolders != null) {
      files.addAll(List.of(selectedFolders));
    } else {
      File folder = getFileFromTextField(textFieldFolders);
      if (folder != null) {
        files.add(folder);
      }
    }
    return files;
  }

  private File getFileFromTextField(JTextField textField) {
    String path = textField.getText().trim();

    if (!StringUtil.hasText(path) || path.equals(Messages.getString(MULTI_DIR_MESSAGE))) {
      return null;
    }

    File file = tryCreateFile(path);
    if (file != null && file.canRead()) {
      return file;
    }

    // Try as URI
    try {
      return URIUtils.toFile(new URI(path));
    } catch (Exception e) {
      LOGGER.error("Cannot import DICOM from {}", path);

      return null;
    }
  }

  private File tryCreateFile(String path) {
    try {
      return new File(path);
    } catch (Exception e) {
      LOGGER.debug("Cannot create file from path: {}", path);
      return null;
    }
  }

  private void resetSelection() {
    selectedFiles = null;
    selectedFolders = null;
  }
}
