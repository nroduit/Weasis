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
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Dimension;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.LoadDicomDir;
import org.weasis.dicom.explorer.LoadLocalDicom;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.wado.LoadSeries;

/**
 * The import from the local device in one page: a path field fed by the system file chooser (files
 * and folders at once on macOS, files or a folder elsewhere) or by the DICOMDIR detection of an
 * inserted medium. A selected DICOMDIR is read through its directory records, everything else is
 * scanned; the two kinds may be mixed in one selection.
 */
public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

  public static final String LAST_RECURSIVE_MODE = "last.recursive.mode";
  public static final String LAST_IMPORT_PATH = "last.import.path";

  /** The folder of the last import, also the folder the export placeholders point to. */
  public static final String LAST_OPEN_DIR = "lastOpenDir";

  private final JTextField pathField = new JTextField();
  private final JCheckBox recursive = new JCheckBox(Messages.getString("LocalImport.recursive"));
  private final JCheckBox copyToCache = new JCheckBox(Messages.getString("LocalImport.cache"));
  private List<File> selection = List.of();

  public LocalImport() {
    super(Messages.getString("LocalImport.local_dev"), 0);
    initGUI();
  }

  private void initGUI() {
    Properties props = LocalPersistence.getProperties();
    pathField.setText(props.getProperty(LAST_IMPORT_PATH, ""));
    pathField.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
    Dimension size = pathField.getPreferredSize();
    size.width = 200;
    pathField.setPreferredSize(size);
    pathField.setMaximumSize(new Dimension(Short.MAX_VALUE, size.height));
    recursive.setSelected(LangUtil.emptyToTrue(props.getProperty(LAST_RECURSIVE_MODE)));

    JLabel label = new JLabel(Messages.getString("LocalImport.path") + StringUtil.COLON);
    add(
        GuiUtils.getHorizontalBoxLayoutPanel(
            label, GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_SMALL), pathField));
    add(GuiUtils.boxVerticalStrut(ITEM_SEPARATOR));
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, createBrowseButtons()));
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, recursive));
    add(GuiUtils.getFlowLayoutPanel(ITEM_SEPARATOR_SMALL, 0, copyToCache));
    add(GuiUtils.boxYLastElement(LAST_FILLER_HEIGHT));
  }

  /**
   * One Browse button where the system dialog takes files and folders together (macOS); a Files and
   * a Folder button where it takes one kind at a time. The media button looks for a DICOMDIR.
   */
  private JButton[] createBrowseButtons() {
    List<JButton> buttons = new ArrayList<>();
    if (SystemInfo.isMacOS) {
      buttons.add(
          browseButton(
              Messages.getString("LocalImport.browse"), SystemFileChooser.FILES_AND_DIRECTORIES));
    } else {
      buttons.add(
          browseButton(
              Messages.getString("LocalImport.browse_files"), SystemFileChooser.FILES_ONLY));
      buttons.add(
          browseButton(
              Messages.getString("LocalImport.browse_folder"), SystemFileChooser.DIRECTORIES_ONLY));
    }
    JButton media =
        new JButton(Messages.getString("LocalImport.media"), ResourceUtil.getIcon(OtherIcon.CDROM));
    media.addActionListener(_ -> detectMedia());
    buttons.add(media);
    return buttons.toArray(JButton[]::new);
  }

  private JButton browseButton(String label, int selectionMode) {
    JButton button = new JButton(label);
    button.addActionListener(_ -> browse(selectionMode));
    return button;
  }

  private void browse(int selectionMode) {
    SystemFileChooser chooser = new SystemFileChooser(initialDirectory());
    chooser.setFileSelectionMode(selectionMode);
    chooser.setMultiSelectionEnabled(true);
    if (chooser.showOpenDialog(WinUtil.getParentWindow(this)) == SystemFileChooser.APPROVE_OPTION) {
      File[] selected = chooser.getSelectedFiles();
      if (selected != null && selected.length > 0) {
        setSelection(List.of(selected));
      }
    }
  }

  private void detectMedia() {
    File dicomdir = DicomDirImport.getDcmDirFromMedia();
    if (dicomdir == null) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("LocalImport.no_media"),
          getTitle(),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    setSelection(List.of(dicomdir));
    copyToCache.setSelected(true);
  }

  private String initialDirectory() {
    File typed = typedFile();
    if (typed != null) {
      return directoryOf(typed);
    }
    return LocalPersistence.getProperties().getProperty(LAST_OPEN_DIR, "");
  }

  private static String directoryOf(File file) {
    if (file.isFile()) {
      File parent = file.getParentFile();
      return parent != null ? parent.getPath() : file.getPath();
    }
    return file.getPath();
  }

  /** Shows a selection in the field: the path of a single item, a count otherwise. */
  private void setSelection(List<File> files) {
    selection = List.copyOf(files);
    pathField.setText(
        files.size() == 1
            ? files.getFirst().getPath()
            : Messages.format("LocalImport.items", files.size()));
    File first = files.getFirst();
    Properties props = LocalPersistence.getProperties();
    props.setProperty(LAST_OPEN_DIR, directoryOf(first));
    props.setProperty(LAST_IMPORT_PATH, files.size() == 1 ? first.getPath() : "");
  }

  /** Fills the field with a path to import, as the CD import does with the folder of a medium. */
  public void setImportPath(String path) {
    selection = List.of();
    pathField.setText(path);
  }

  private File typedFile() {
    String path = pathField.getText().trim();
    if (!StringUtil.hasText(path) || !selection.isEmpty()) {
      return null;
    }
    File file = new File(path);
    if (file.canRead()) {
      return file;
    }
    try {
      return URIUtils.toFile(new URI(path));
    } catch (Exception e) {
      LOGGER.debug("Not a file path nor a file URI: {}", path);
      return null;
    }
  }

  /** The files to import: the chooser selection, or the typed path. */
  private List<File> filesToImport() {
    if (!selection.isEmpty()) {
      return selection;
    }
    File typed = typedFile();
    if (typed == null) {
      LOGGER.error("Cannot import DICOM from {}", pathField.getText());
      return List.of();
    }
    return List.of(typed);
  }

  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    List<File> files = filesToImport();
    if (files.isEmpty()) {
      return;
    }
    List<File> toScan = new ArrayList<>();
    for (File file : files) {
      if (DicomDirImport.isDicomDir(file.toPath())) {
        List<LoadSeries> series =
            DicomDirImport.loadDicomDir(file, dicomModel, copyToCache.isSelected());
        if (series != null && !series.isEmpty()) {
          DicomModel.LOADING_EXECUTOR.execute(
              new LoadDicomDir(series, dicomModel, OpeningViewer.ALL_PATIENTS));
        } else {
          // an empty or unreadable directory: scan the file set itself
          LOGGER.warn("No series in {}, scanning its folder", file);
          toScan.add(file.getParentFile() != null ? file.getParentFile() : file);
        }
      } else {
        toScan.add(file);
      }
    }
    if (!toScan.isEmpty()) {
      DicomModel.LOADING_EXECUTOR.execute(
          new LoadLocalDicom(
              toScan.toArray(File[]::new),
              recursive.isSelected(),
              dicomModel,
              OpeningViewer.ALL_PATIENTS));
    }
    selection = List.of();
  }

  @Override
  public void closeAdditionalWindow() {
    Properties props = LocalPersistence.getProperties();
    props.setProperty(LAST_RECURSIVE_MODE, String.valueOf(recursive.isSelected()));
    if (selection.isEmpty()) {
      String path = pathField.getText().trim();
      props.setProperty(LAST_IMPORT_PATH, path);
      File file = new File(path);
      if (StringUtil.hasText(path) && file.exists()) {
        props.setProperty(LAST_OPEN_DIR, directoryOf(file));
      }
    }
  }

  @Override
  public void resetToDefaultValues() {
    // nothing to reset
  }
}
