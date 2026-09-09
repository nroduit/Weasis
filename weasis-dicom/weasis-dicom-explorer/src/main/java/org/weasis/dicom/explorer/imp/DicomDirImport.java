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

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Dialog;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.api.util.HardwareInfo;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.*;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class DicomDirImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomDirImport.class);
  private static final List<String> DICOMDIR_NAMES =
      List.of("DICOMDIR", "dicomdir", "DICOMDIR.", "dicomdir."); // NON-NLS

  private static final String LAST_DICOM_DIR = "lastDicomDir";
  private final JTextField textField = new JTextField();
  private JCheckBox checkboxWriteInCache;

  public DicomDirImport() {
    super(Messages.getString("DicomDirImport.dicomdir"), 5);
    initGUI();
  }

  public void initGUI() {
    add(
        LocalImport.createSearchPanel(
            textField,
            Messages.getString("DicomDirImport.path"),
            LAST_DICOM_DIR,
            this::browseDicomDirFile));

    JButton btnCdrom =
        new JButton(
            Messages.getString("DicomDirImport.detect"), ResourceUtil.getIcon(OtherIcon.CDROM));
    btnCdrom.addActionListener(
        e -> {
          File dcmdir = getDcmDirFromMedia();
          if (dcmdir != null) {
            String path = dcmdir.getPath();
            textField.setText(path);
            LocalPersistence.getProperties().setProperty(LAST_DICOM_DIR, path);
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
      directory = LocalPersistence.getProperties().getProperty(LAST_DICOM_DIR, "");
    }
    SystemFileChooser fileChooser = getSystemFileChooser(directory);
    File selectedFile;
    if (fileChooser.showOpenDialog(this) == SystemFileChooser.APPROVE_OPTION
        && (selectedFile = fileChooser.getSelectedFile()) != null) {
      String path = selectedFile.getPath();
      textField.setText(path);
      LocalPersistence.getProperties().setProperty(LAST_DICOM_DIR, path);
    }
  }

  private static SystemFileChooser getSystemFileChooser(String directory) {
    SystemFileChooser fileChooser = new SystemFileChooser(directory);
    fileChooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(false);
    fileChooser.setApproveCallback(
        (selectedFiles, context) -> {
          if (selectedFiles.length > 0) {
            File f = selectedFiles[0];
            String name = f.getName().toUpperCase();
            if (name.equals("DICOMDIR") || name.startsWith("DICOMDIR.")) {
              return SystemFileChooser.APPROVE_OPTION;
            } else {
              context.showMessageDialog(
                  JOptionPane.WARNING_MESSAGE,
                  "Select a \"DICOMDIR\" filename.",
                  null,
                  0,
                  Messages.getString("HttpHeadersEditor.close"));
            }
          }
          return SystemFileChooser.CANCEL_OPTION;
        });
    return fileChooser;
  }

  @Override
  public void closeAdditionalWindow() {
    // No additional window to close
  }

  private OpeningViewer getOpeningViewer() {
    return OpeningViewer.ALL_PATIENTS;
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
          f = URIUtils.toFile(new URI(path));
          if (f.canRead()) {
            file = f;
          }
        } catch (Exception e) {
          LOGGER.error("Cannot read {}", path);
        }
      }
    }
    if (file != null) {
      LocalPersistence.getProperties().setProperty(LAST_DICOM_DIR, file.getPath());
      List<LoadSeries> loadSeries =
          loadDicomDir(file, dicomModel, checkboxWriteInCache.isSelected());

      if (loadSeries != null && !loadSeries.isEmpty()) {
        DicomModel.LOADING_EXECUTOR.execute(
            new LoadDicomDir(loadSeries, dicomModel, getOpeningViewer()));
      } else {
        LOGGER.error("Cannot import DICOM from {}", file);

        int response =
            JOptionPane.showConfirmDialog(
                WinUtil.getValidComponent(this),
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

  /**
   * Finds the DICOMDIR of the inserted media: optical discs first, then removable drives, as the
   * operating system reports them. Network drives and fixed disks are never probed, so a
   * disconnected share cannot stall the detection.
   *
   * @return the DICOMDIR file, or null when no media holds one
   */
  public static File getDcmDirFromMedia() {
    for (Path mount : mediaMounts()) {
      Path dicomdir = findDicomDir(mount);
      if (dicomdir != null) {
        return dicomdir.toFile();
      }
    }
    return null;
  }

  private static List<Path> mediaMounts() {
    List<Path> mounts = new ArrayList<>();
    if (HardwareInfo.isAvailable()) {
      HardwareInfo.opticalDrives().forEach(d -> addMount(mounts, d.mount()));
      HardwareInfo.removableDrives().forEach(d -> addMount(mounts, d.mount()));
    } else {
      legacyMediaDirectories().forEach(dir -> addMount(mounts, dir));
    }
    return mounts;
  }

  private static void addMount(List<Path> mounts, String mount) {
    try {
      Path path = Path.of(mount);
      if (!mounts.contains(path)) {
        mounts.add(path);
      }
    } catch (InvalidPathException e) {
      LOGGER.debug("Ignoring mount point {}", mount);
    }
  }

  /**
   * The DICOMDIR at the root of a mount, tolerating the lower-case and dotted names of some media.
   */
  static Path findDicomDir(Path mount) {
    for (String name : DICOMDIR_NAMES) {
      Path candidate = mount.resolve(name);
      if (Files.isReadable(candidate)) {
        return candidate;
      }
    }
    return null;
  }

  /** Where desktop environments mount media, when the hardware probe cannot list the drives. */
  private static List<String> legacyMediaDirectories() {
    List<String> dirs = new ArrayList<>();
    if (SystemInfo.isWindows) {
      for (File root : File.listRoots()) {
        dirs.add(root.getPath());
      }
    } else if (SystemInfo.isMacOS) {
      addChildren(dirs, "/Volumes"); // NON-NLS
    } else {
      String user = System.getProperty("user.name", "local"); // NON-NLS
      addChildren(dirs, "/run/media/" + user); // NON-NLS
      addChildren(dirs, "/media/" + user); // NON-NLS
      addChildren(dirs, "/media"); // NON-NLS
      addChildren(dirs, "/mnt"); // NON-NLS
    }
    return dirs;
  }

  private static void addChildren(List<String> dirs, String directory) {
    try (Stream<Path> children = Files.list(Path.of(directory))) {
      children.filter(Files::isDirectory).map(Path::toString).forEach(dirs::add);
    } catch (IOException | RuntimeException e) {
      LOGGER.debug("Cannot list {}", directory);
    }
  }
}
