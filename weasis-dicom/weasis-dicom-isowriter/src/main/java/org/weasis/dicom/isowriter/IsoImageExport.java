/*
 * Copyright (c) 2016 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.isowriter;

import com.formdev.flatlaf.util.SystemInfo;
import com.github.stephenc.javaisotools.iso9660.ConfigException;
import com.github.stephenc.javaisotools.iso9660.ISO9660RootDirectory;
import com.github.stephenc.javaisotools.iso9660.impl.CreateISO;
import com.github.stephenc.javaisotools.iso9660.impl.ISO9660Config;
import com.github.stephenc.javaisotools.iso9660.impl.ISOImageFileHandler;
import com.github.stephenc.javaisotools.joliet.impl.JolietConfig;
import com.github.stephenc.javaisotools.rockridge.impl.RockRidgeConfig;
import com.github.stephenc.javaisotools.sabre.HandlerException;
import com.github.stephenc.javaisotools.sabre.StreamHandler;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Properties;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.CheckTreeModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.LocalExport;

public class IsoImageExport extends LocalExport {

  private static final Logger LOGGER = LoggerFactory.getLogger(IsoImageExport.class);

  private static final String LAST_FOLDER = "last_folder"; // NON-NLS
  private static final String ADD_JPEG = "add_jpeg"; // NON-NLS
  private static final String ADD_VIEWER = "add_viewer"; // NON-NLS
  private static final String DICOM = "DICOM";

  private JCheckBox checkBoxAddWeasisViewer;
  private JCheckBox checkBoxAddJpeg;

  private File outputFile;

  public IsoImageExport(DicomModel dicomModel, CheckTreeModel treeModel) {
    super(Messages.getString("cd.dvd.image"), 25, dicomModel, treeModel);
    initialize(true);
  }

  @Override
  protected List<JComponent> getAdditionalOption() {
    getPreferences().put("force.dicomdir", Boolean.TRUE.toString());
    checkBoxAddJpeg = new JCheckBox(Messages.getString("add.jpeg.images"));
    checkBoxAddWeasisViewer =
        new JCheckBox(Messages.getString("add") + StringUtil.SPACE + AppProperties.WEASIS_NAME);
    comboBoxImgFormat.setModel(new DefaultComboBoxModel<>(new Format[] {Format.DICOM}));
    checkBoxAddWeasisViewer.setEnabled(SystemInfo.isWindows);

    return List.of(
        GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_LARGE),
        checkBoxAddJpeg,
        GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_LARGE),
        checkBoxAddWeasisViewer);
  }

  protected void initialize(boolean first) {
    if (first) {
      Properties pref = getPreferences();
      checkBoxAddJpeg.setSelected(Boolean.parseBoolean(pref.getProperty(ADD_JPEG, "false")));
      checkBoxAddWeasisViewer.setSelected(
          Boolean.parseBoolean(pref.getProperty(ADD_VIEWER, "false")));
    }
  }

  protected Properties getPreferences() {
    return ExportIsoFactory.EXPORT_PERSISTENCE;
  }

  public void resetSettingsToDefault() {
    initialize(false);
  }

  @Override
  public void closeAdditionalWindow() {
    Properties pref = getPreferences();
    pref.setProperty(ADD_JPEG, String.valueOf(checkBoxAddJpeg.isSelected()));
    pref.setProperty(ADD_VIEWER, String.valueOf(checkBoxAddWeasisViewer.isSelected()));
  }

  @Override
  public void resetToDefaultValues() {
    // Do nothing
  }

  String getLocalViewerPath() {
    return BundleTools.SYSTEM_PREFERENCES.getProperty("weasis.codebase.local", null);
  }

  @Override
  public void exportDICOM(final CheckTreeModel model, JProgressBar info) throws IOException {
    browseImgFile();
    if (outputFile != null) {
      final File exportFile = outputFile.getCanonicalFile();
      ExplorerTask<Boolean, String> task =
          new ExplorerTask<>(Messages.getString("exporting"), false) {

            @Override
            protected Boolean doInBackground() throws Exception {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_START, dicomModel, null, this));
              File exportDir =
                  FileUtil.createTempDir(
                      AppProperties.buildAccessibleTempDirectory("tmp", "burn")); // NON-NLS
              Properties pref = getPreferences();
              pref.setProperty(INC_DICOMDIR, Boolean.TRUE.toString());
              pref.setProperty(CD_COMPATIBLE, Boolean.TRUE.toString());
              writeDicom(this, exportDir, model, pref);
              File readmeFile = ResourceUtil.getResource("isowriter/README.htm"); // NON-NLS
              FileUtil.nioCopyFile(readmeFile, new File(exportDir, "README.HTM"));

              if (checkBoxAddJpeg.isSelected()) {
                writeOther(this, new File(exportDir, "JPEG"), model, Format.JPEG, new Properties());
              }

              Path appPath = Paths.get(getLocalViewerPath());
              if (checkBoxAddWeasisViewer.isEnabled()
                  && checkBoxAddWeasisViewer.isSelected()
                  && Files.isReadable(appPath)) {
                Path out = Paths.get(exportDir.toString(), "viewer");
                Files.createDirectory(out);
                Path in = appPath.getParent();
                copyFolder(in, out, StandardCopyOption.COPY_ATTRIBUTES);
                File autorun = ResourceUtil.getResource("isowriter/Autorun.inf"); // NON-NLS
                FileUtil.nioCopyFile(autorun, new File(exportDir, "AUTORUN.INF"));
                File run = ResourceUtil.getResource("isowriter/RUN.bat"); // NON-NLS
                FileUtil.nioCopyFile(run, new File(exportDir, "RUN.BAT"));
              }

              if (this.isCancelled()) {
                return false;
              }
              makeISO(exportDir, exportFile, true, true);
              return true;
            }

            @Override
            protected void done() {
              dicomModel.firePropertyChange(
                  new ObservableEvent(
                      ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, this));
            }
          };
      task.execute();
    }
  }

  // TODO use lib
  public void copyFolder(Path source, Path target, CopyOption... options) throws IOException {
    Files.walkFileTree(
        source,
        new SimpleFileVisitor<>() {

          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            Files.createDirectories(target.resolve(source.relativize(dir)));
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            Files.copy(file, target.resolve(source.relativize(file)), options);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  public void browseImgFile() {
    Properties pref = getPreferences();
    String lastFolder = pref.getProperty(LAST_FOLDER, null);
    if (lastFolder == null) {
      lastFolder = System.getProperty("user.home", "");
    }
    outputFile = new File(lastFolder, "cdrom-DICOM.iso");

    JFileChooser fileChooser = new JFileChooser(outputFile);
    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(false);
    FileFormatFilter filter = new FileFormatFilter("iso", "ISO"); // NON-NLS
    fileChooser.addChoosableFileFilter(filter);
    fileChooser.setFileFilter(filter);

    fileChooser.setSelectedFile(outputFile);
    File file;
    if (fileChooser.showSaveDialog(this) != 0 || (file = fileChooser.getSelectedFile()) == null) {
      outputFile = null;
    } else {
      outputFile = file;
      pref.setProperty(LAST_FOLDER, file.getParent());
    }
  }

  private File makeISO(
      File exportDir, File exportFile, boolean enableRockRidge, boolean enableJoliet) {
    // Directory hierarchy, starting from the root
    ISO9660RootDirectory root = new ISO9660RootDirectory();

    try {
      File[] files = exportDir.listFiles();
      if (files != null) {
        for (File file : files) {
          if (file.exists()) {
            if (file.isDirectory()) {
              root.addRecursively(file);
            } else {
              root.addFile(file);
            }
          }
        }
      }
    } catch (HandlerException e) {
      LOGGER.error("Error when adding files to ISO", e);
    }

    try {
      // ISO9660 support
      ISO9660Config iso9660Config = new ISO9660Config();
      iso9660Config.allowASCII(false);
      iso9660Config.setInterchangeLevel(1);
      iso9660Config.restrictDirDepthTo8(true);
      iso9660Config.setPublisher(AppProperties.WEASIS_NAME);
      iso9660Config.setVolumeID(DICOM);
      iso9660Config.setDataPreparer(DICOM);
      iso9660Config.forceDotDelimiter(false);

      RockRidgeConfig rrConfig = null;

      if (enableRockRidge) {
        // Rock Ridge support
        rrConfig = new RockRidgeConfig();
        rrConfig.setMkisofsCompatibility(false);
        rrConfig.hideMovedDirectoriesStore(true);
        rrConfig.forcePortableFilenameCharacterSet(true);
      }

      JolietConfig jolietConfig = null;
      if (enableJoliet) {
        // Joliet support
        jolietConfig = new JolietConfig();
        jolietConfig.setPublisher(AppProperties.WEASIS_NAME);
        jolietConfig.setVolumeID(DICOM);
        jolietConfig.setDataPreparer(DICOM);
        jolietConfig.forceDotDelimiter(false);
      }

      // Create ISO
      StreamHandler streamHandler = new ISOImageFileHandler(exportFile);
      CreateISO iso = new CreateISO(streamHandler, root);
      iso.process(iso9660Config, rrConfig, jolietConfig, null);
      return exportFile;

    } catch (ConfigException | HandlerException | FileNotFoundException e) {
      LOGGER.error("Error when building ISO", e);
    } finally {
      FileUtil.recursiveDelete(exportDir);
    }
    return null;
  }
}
