/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.exp;

import com.formdev.flatlaf.util.SystemFileChooser;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Tag;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.main.DicomExplorer;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageIOHandler;

/**
 * One-click export of a DICOM study to {@code tipo/paciente/fecha/estudioId} as JPEG or PNG images
 * for clinical reports.
 */
public final class StudyFolderExport {

  private static final Logger LOGGER = LoggerFactory.getLogger(StudyFolderExport.class);

  public static final String STUDY_EXPORT_ROOT = "study.export.root";
  public static final String STUDY_EXPORT_FORMAT = "study.export.format";
  public static final String STUDY_EXPORT_JPEG_QUALITY = "study.export.jpeg.quality";

  public static final String FORMAT_JPEG = "JPEG";
  public static final String FORMAT_PNG = "PNG";

  private static final DateTimeFormatter DATE_FOLDER = DateTimeFormatter.BASIC_ISO_DATE;

  private StudyFolderExport() {}

  public static void exportSelectedStudy(Component parent, DicomModel model, DicomExplorer explorer) {
    Objects.requireNonNull(model);
    Objects.requireNonNull(explorer);

    MediaSeriesGroup study = resolveStudy(model, explorer);
    if (study == null) {
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("StudyFolderExport.no_study"),
          Messages.getString("StudyFolderExport.title"),
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    File root = resolveExportRoot(parent);
    if (root == null) {
      return;
    }

    MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
    Path destination;
    try {
      destination = buildStudyFolder(root.toPath(), model, patient, study);
      Files.createDirectories(destination);
    } catch (Exception e) {
      LOGGER.error("Cannot create study export folder", e);
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("StudyFolderExport.error_dir"),
          Messages.getString("StudyFolderExport.title"),
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    WProperties pref = LocalPersistence.getProperties();
    boolean png = FORMAT_PNG.equalsIgnoreCase(pref.getProperty(STUDY_EXPORT_FORMAT, FORMAT_JPEG));
    int jpegQuality = StringUtil.getInt(pref.getProperty(STUDY_EXPORT_JPEG_QUALITY), 85);
    LocalExport.Format format = png ? LocalExport.Format.PNG : LocalExport.Format.JPEG;

    List<DicomSeries> seriesList = collectImageSeries(model, study);
    if (seriesList.isEmpty()) {
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("StudyFolderExport.no_images"),
          Messages.getString("StudyFolderExport.title"),
          JOptionPane.WARNING_MESSAGE);
      return;
    }

    final Path dest = destination;
    ExplorerTask<Boolean, String> task =
        new ExplorerTask<>(Messages.getString("StudyFolderExport.exporting"), false) {
          @Override
          protected Boolean doInBackground() {
            model.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.LOADING_START, model, null, this));
            int written = 0;
            Set<String> usedNames = new LinkedHashSet<>();
            for (DicomSeries series : seriesList) {
              if (isCancelled()) {
                return false;
              }
              for (DicomImageElement img : series.getMedias(null, null)) {
                if (isCancelled()) {
                  return false;
                }
                if (writeImage(img, dest, format, jpegQuality, usedNames)) {
                  written++;
                }
              }
            }
            LOGGER.info("Exported {} images from study to {}", written, dest);
            return written > 0;
          }

          @Override
          protected void done() {
            model.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.LOADING_STOP, model, null, this));
            try {
              Boolean ok = get();
              if (Boolean.TRUE.equals(ok)) {
                JOptionPane.showMessageDialog(
                    parent,
                    Messages.getString("StudyFolderExport.done") + StringUtil.COLON_AND_SPACE + dest,
                    Messages.getString("StudyFolderExport.title"),
                    JOptionPane.INFORMATION_MESSAGE);
              }
            } catch (Exception e) {
              LOGGER.error("Study folder export failed", e);
              JOptionPane.showMessageDialog(
                  parent,
                  Messages.getString("StudyFolderExport.error"),
                  Messages.getString("StudyFolderExport.title"),
                  JOptionPane.ERROR_MESSAGE);
            }
          }
        };
    task.execute();
  }

  static MediaSeriesGroup resolveStudy(DicomModel model, DicomExplorer explorer) {
    if (!explorer.getSelectionList().isEmpty()) {
      DicomSeries series = explorer.getSelectionList().getFirst();
      return model.getParent(series, DicomModel.study);
    }
    MediaSeriesGroup patient = explorer.getSelectedPatient();
    if (patient == null) {
      return null;
    }
    Collection<MediaSeriesGroup> studies = model.getChildren(patient);
    if (studies == null || studies.isEmpty()) {
      return null;
    }
    if (studies.size() == 1) {
      return studies.iterator().next();
    }
    // Prefer study of the first series pane under the selected patient
    DicomSeries first = explorer.getSelectionList().getFirstElement();
    if (first != null) {
      return model.getParent(first, DicomModel.study);
    }
    return studies.iterator().next();
  }

  private static File resolveExportRoot(Component parent) {
    WProperties pref = LocalPersistence.getProperties();
    String configured = pref.getProperty(STUDY_EXPORT_ROOT, "");
    if (StringUtil.hasText(configured)) {
      File root = new File(configured);
      if (root.isDirectory() || root.mkdirs()) {
        return root;
      }
    }

    int choice =
        JOptionPane.showConfirmDialog(
            parent,
            Messages.getString("StudyFolderExport.choose_root"),
            Messages.getString("StudyFolderExport.title"),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE);
    if (choice != JOptionPane.OK_OPTION) {
      return null;
    }

    SystemFileChooser chooser = new SystemFileChooser(configured);
    chooser.setFileSelectionMode(SystemFileChooser.DIRECTORIES_ONLY);
    chooser.setMultiSelectionEnabled(false);
    if (chooser.showSaveDialog(WinUtil.getParentWindow(parent)) != SystemFileChooser.APPROVE_OPTION
        || chooser.getSelectedFile() == null) {
      return null;
    }
    File selected = chooser.getSelectedFile();
    File root = selected.isDirectory() ? selected : selected.getParentFile();
    if (root == null) {
      return null;
    }
    pref.setProperty(STUDY_EXPORT_ROOT, root.getAbsolutePath());
    return root;
  }

  static Path buildStudyFolder(
      Path exportRoot, DicomModel model, MediaSeriesGroup patient, MediaSeriesGroup study) {
    String type = folderName(resolveStudyType(model, study), 40);
    String patientFolder = folderName(resolvePatientName(patient), 40);
    String dateFolder = folderName(resolveStudyDate(study), 16);
    String studyId = folderName(resolveStudyId(study), 40);

    Path base = exportRoot.resolve(type).resolve(patientFolder).resolve(dateFolder).resolve(studyId);
    return uniqueDirectory(base);
  }

  private static Path uniqueDirectory(Path base) {
    if (!Files.exists(base)) {
      return base;
    }
    for (int i = 2; i < 1000; i++) {
      Path candidate = base.resolveSibling(base.getFileName().toString() + "_" + i);
      if (!Files.exists(candidate)) {
        return candidate;
      }
    }
    return base.resolveSibling(base.getFileName().toString() + "_" + System.currentTimeMillis());
  }

  static String resolveStudyType(DicomModel model, MediaSeriesGroup study) {
    String[] modalities = TagD.getTagValue(study, Tag.ModalitiesInStudy, String[].class);
    if (modalities != null && modalities.length > 0) {
      TreeSet<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
      set.addAll(Arrays.asList(modalities));
      set.removeIf(s -> !StringUtil.hasText(s));
      if (!set.isEmpty()) {
        return String.join("-", set);
      }
    }
    TreeSet<String> fromSeries = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (MediaSeriesGroup child : model.getChildren(study)) {
      String modality = TagD.getTagValue(child, Tag.Modality, String.class);
      if (StringUtil.hasText(modality)) {
        fromSeries.add(modality.trim());
      }
    }
    if (!fromSeries.isEmpty()) {
      return String.join("-", fromSeries);
    }
    return "UNKNOWN"; // NON-NLS
  }

  static String resolvePatientName(MediaSeriesGroup patient) {
    if (patient == null) {
      return "UNKNOWN"; // NON-NLS
    }
    String name = TagD.getTagValue(patient, Tag.PatientName, String.class);
    if (StringUtil.hasText(name)) {
      return name;
    }
    String id = TagD.getTagValue(patient, Tag.PatientID, String.class);
    return StringUtil.hasText(id) ? id : "UNKNOWN"; // NON-NLS
  }

  static String resolveStudyDate(MediaSeriesGroup study) {
    LocalDate date = TagD.getTagValue(study, Tag.StudyDate, LocalDate.class);
    if (date != null) {
      return DATE_FOLDER.format(date);
    }
    String raw = TagD.getTagValue(study, Tag.StudyDate, String.class);
    if (StringUtil.hasText(raw)) {
      String digits = raw.replaceAll("\\D", "");
      if (digits.length() >= 8) {
        return digits.substring(0, 8);
      }
    }
    return "NODATE"; // NON-NLS
  }

  static String resolveStudyId(MediaSeriesGroup study) {
    String studyId = TagD.getTagValue(study, Tag.StudyID, String.class);
    if (StringUtil.hasText(studyId)) {
      return studyId;
    }
    String accession = TagD.getTagValue(study, Tag.AccessionNumber, String.class);
    if (StringUtil.hasText(accession)) {
      return accession;
    }
    String uid = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String hash = LocalExport.makeFileIDs(uid);
    return StringUtil.hasText(hash) ? hash : "UNKNOWN"; // NON-NLS
  }

  private static List<DicomSeries> collectImageSeries(DicomModel model, MediaSeriesGroup study) {
    List<DicomSeries> list = new ArrayList<>();
    for (MediaSeriesGroup child : model.getChildren(study)) {
      if (child instanceof DicomSeries series) {
        boolean hasImage = false;
        for (DicomImageElement ignored : series.getMedias(null, null)) {
          hasImage = true;
          break;
        }
        if (hasImage) {
          list.add(series);
        }
      }
    }
    return list;
  }

  private static boolean writeImage(
      DicomImageElement img,
      Path destinationDir,
      LocalExport.Format format,
      int jpegQuality,
      Set<String> usedNames) {
    try {
      SimpleOpManager manager = img.buildSimpleOpManager(false, true, true, true, 1.0);
      Optional<PlanarImage> image = manager.getFirstNodeInputImage();
      if (image.isEmpty()) {
        return false;
      }
      PlanarImage rimage = manager.process().orElse(null);
      if (rimage == null) {
        rimage = image.get();
      }
      boolean mustBeReleased = !Objects.equals(rimage, image.get());
      image = Optional.of(rimage);

      String baseName = uniqueFileName(instanceFileName(img), usedNames);
      File destinationFile =
          destinationDir.resolve(baseName + "." + format.getExtension()).toFile();

      if (format == LocalExport.Format.PNG) {
        ImageIOHandler.writePNG(image.get().toMat(), destinationFile.toPath());
      } else {
        MatOfInt map = new MatOfInt();
        map.fromArray(Imgcodecs.IMWRITE_JPEG_QUALITY, jpegQuality);
        ImageIOHandler.writeImage(image.get().toMat(), destinationFile.toPath(), map);
      }
      if (mustBeReleased) {
        ImageConversion.releasePlanarImage(image.get());
      }
      return true;
    } catch (Exception e) {
      LOGGER.error("Cannot export image {}", img, e);
      return false;
    }
  }

  private static String uniqueFileName(String base, Set<String> usedNames) {
    String name = base;
    int i = 2;
    while (!usedNames.add(name)) {
      name = base + "_" + i++;
    }
    return name;
  }

  private static String instanceFileName(MediaElement img) {
    String iUid = LocalExport.makeFileIDs(TagD.getTagValue(img, Tag.SOPInstanceUID, String.class));
    Integer instance = TagD.getTagValue(img, Tag.InstanceNumber, Integer.class);
    if (instance != null) {
      String val = instance.toString();
      if (val.length() < 5) {
        char[] chars = new char[5 - val.length()];
        Arrays.fill(chars, '0');
        return new String(chars) + val + "-" + iUid;
      }
      return val + "-" + iUid;
    }
    return iUid != null ? iUid : "image"; // NON-NLS
  }

  static String folderName(String str, int length) {
    String value = FileUtil.getValidFileNameWithoutHTML(str == null ? "" : str);
    value = value.replace('^', ' ').replaceAll("\\s+", " ").trim();
    value = StringUtil.getTruncatedString(value, length, Suffix.UNDERSCORE).trim();
    if (!StringUtil.hasText(value)) {
      return "UNKNOWN"; // NON-NLS
    }
    return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
  }
}
