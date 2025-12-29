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

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import javax.swing.tree.DefaultMutableTreeNode;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.main.PatientPane;
import org.weasis.dicom.explorer.main.SeriesPane;
import org.weasis.dicom.explorer.main.StudyPane;
import org.weasis.dicom.explorer.pref.download.DicomExplorerPrefView;

/**
 * Utility class for sorting DICOM entities (patients, studies, series) with configurable ordering.
 */
public final class DicomSorter {
  private static final String SR_MODALITY = "SR";
  private static final String DOC_MODALITY = "DOC";
  private static final String ENCAPSULATED_SOP_CLASS_PREFIX = "1.2.840.10008.5.1.4.1.1.88";
  private static final String DOCUMENT_SOP_CLASS_PREFIX = "1.2.840.10008.5.1.4.1.1.104";
  private static final String ZERO_PADDING = "%010d"; // NON-NLS
  private static final String NULL_STACK_PLACEHOLDER = "zzz"; // NON-NLS

  /** Chronological sorting order for studies. */
  public enum SortingTime {
    CHRONOLOGICAL(0, Messages.getString("chrono.order")),
    INVERSE_CHRONOLOGICAL(1, Messages.getString("reverse.chrono.order"));

    private final int id;
    private final String title;

    SortingTime(int id, String title) {
      this.id = id;
      this.title = title;
    }

    public int getId() {
      return id;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }

    public static SortingTime valueOf(int id) {
      return Arrays.stream(values()).filter(s -> s.id == id).findFirst().orElse(CHRONOLOGICAL);
    }
  }

  public static final Comparator<Object> PATIENT_COMPARATOR =
      Comparator.nullsLast(
          (o1, o2) -> {
            MediaSeriesGroup patient1 = extractPatient(o1);
            MediaSeriesGroup patient2 = extractPatient(o2);

            return patient1 == null || patient2 == null
                ? compareObjectWithNull(patient1, patient2)
                : comparePatients(patient1, patient2);
          });

  public static final Comparator<Object> STUDY_COMPARATOR =
      Comparator.nullsLast(
          (o1, o2) -> {
            MediaSeriesGroup study1 = extractStudy(o1);
            MediaSeriesGroup study2 = extractStudy(o2);

            return study1 == null || study2 == null
                ? compareObjectWithNull(study1, study2)
                : compareStudies(study1, study2);
          });

  public static final Comparator<Object> SERIES_COMPARATOR =
      Comparator.nullsLast(
          (o1, o2) -> {
            MediaSeriesGroup series1 = extractSeries(o1);
            MediaSeriesGroup series2 = extractSeries(o2);

            return series1 == null || series2 == null
                ? compareObjectWithNull(series1, series2)
                : compareSeries(series1, series2);
          });

  private DicomSorter() {}

  private static MediaSeriesGroup extractPatient(Object obj) {
    return switch (obj) {
      case PatientPane patientPane -> patientPane.getCurrentPatient().orElse(null);
      case DefaultMutableTreeNode node
          when node.getUserObject() instanceof MediaSeriesGroup group ->
          group;
      case MediaSeriesGroup group -> group;
      default -> null;
    };
  }

  private static MediaSeriesGroup extractStudy(Object obj) {
    return switch (obj) {
      case StudyPane studyPane -> studyPane.getDicomStudy();
      case DefaultMutableTreeNode node
          when node.getUserObject() instanceof MediaSeriesGroup group ->
          group;
      case MediaSeriesGroup group -> group;
      default -> null;
    };
  }

  private static MediaSeriesGroup extractSeries(Object obj) {
    return switch (obj) {
      case SeriesPane seriesPane -> seriesPane.getDicomSeries();
      case DefaultMutableTreeNode node
          when node.getUserObject() instanceof MediaSeriesGroup group ->
          group;
      case MediaSeriesGroup group -> group;
      default -> null;
    };
  }

  private static int comparePatients(MediaSeriesGroup patient1, MediaSeriesGroup patient2) {
    String name1 = TagD.getTagValue(patient1, Tag.PatientName, String.class);
    String name2 = TagD.getTagValue(patient2, Tag.PatientName, String.class);

    if (name1 == null || name2 == null) {
      return compareObjectWithNull(name1, name2);
    }
    return StringUtil.collator.compare(name1, name2);
  }

  private static int compareStudies(MediaSeriesGroup study1, MediaSeriesGroup study2) {
    int dateComparison = compareStudyDates(study1, study2);
    if (dateComparison != 0) {
      return dateComparison;
    }
    int idComparison = compareStudyIds(study1, study2);
    return idComparison != 0 ? idComparison : compareStudyDescriptions(study1, study2);
  }

  private static int compareSeries(MediaSeriesGroup series1, MediaSeriesGroup series2) {
    boolean isEncap1 = isEncapsulatedOrSR(series1);
    boolean isEncap2 = isEncapsulatedOrSR(series2);

    if (isEncap1 != isEncap2) {
      // Force SR to be at the end
      return isEncap1 ? 1 : -1;
    }
    return Comparator.comparing(DicomSorter::getSeriesNumber)
        .thenComparing(DicomSorter::getSplitSeriesNumber)
        .thenComparing(DicomSorter::getSeriesDateTime)
        .thenComparing(DicomSorter::getSeriesDescription)
        .thenComparing(DicomSorter::compareByMediaElements)
        .compare(series1, series2);
  }

  private static int compareStudyDates(MediaSeriesGroup study1, MediaSeriesGroup study2) {
    LocalDateTime date1 = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, study1);
    LocalDateTime date2 = TagD.dateTime(Tag.StudyDate, Tag.StudyTime, study2);

    if (date1 == null || date2 == null) {
      return compareObjectWithNull(date1, date2);
    }

    return getStudyDateSorting() == SortingTime.CHRONOLOGICAL
        ? date1.compareTo(date2)
        : date2.compareTo(date1);
  }

  private static int compareStudyIds(MediaSeriesGroup study1, MediaSeriesGroup study2) {
    String id1 = TagD.getTagValue(study1, Tag.StudyID, String.class);
    String id2 = TagD.getTagValue(study2, Tag.StudyID, String.class);

    return id1 == null || id2 == null
        ? compareObjectWithNull(id1, id2)
        : StringUtil.collator.compare(id1, id2);
  }

  private static int compareStudyDescriptions(MediaSeriesGroup study1, MediaSeriesGroup study2) {
    String desc1 = TagD.getTagValue(study1, Tag.StudyDescription, String.class);
    String desc2 = TagD.getTagValue(study2, Tag.StudyDescription, String.class);

    return desc1 == null || desc2 == null
        ? compareObjectWithNull(desc1, desc2)
        : StringUtil.collator.compare(desc1, desc2);
  }

  private static Integer getSplitSeriesNumber(MediaSeriesGroup series) {
    return Optional.ofNullable(TagD.getTagValue(series, TagW.SplitSeriesNumber, Integer.class))
        .orElse(0); // Put series without split number first
  }

  private static Integer getSeriesNumber(MediaSeriesGroup series) {
    return Optional.ofNullable(TagD.getTagValue(series, Tag.SeriesNumber, Integer.class))
        .orElse(Integer.MAX_VALUE); // Put null series numbers at the end
  }

  private static LocalDateTime getSeriesDateTime(MediaSeriesGroup series) {
    return Optional.ofNullable(TagD.dateTime(Tag.SeriesDate, Tag.SeriesTime, series))
        .orElse(LocalDateTime.MAX); // Put null dates at the end
  }

  private static String getSeriesDescription(MediaSeriesGroup series) {
    return Optional.ofNullable(TagD.getTagValue(series, Tag.SeriesDescription, String.class))
        .orElse("");
  }

  private static int compareByMediaElements(MediaSeriesGroup series1, MediaSeriesGroup series2) {
    if (!(series1 instanceof Series<?> s1) || !(series2 instanceof Series<?> s2)) {
      return 0;
    }

    MediaElement media1 = s1.getMedia(MEDIA_POSITION.FIRST, null, null);
    MediaElement media2 = s2.getMedia(MEDIA_POSITION.FIRST, null, null);

    return media1 == null || media2 == null
        ? compareObjectWithNull(media1, media2)
        : compareMediaElements(media1, media2);
  }

  private static int compareObjectWithNull(Object o1, Object o2) {
    return o1 == null ? (o2 == null ? 0 : 1) : -1;
  }

  private static int compareMediaElements(MediaElement media1, MediaElement media2) {
    return Comparator.comparing(DicomSorter::getMediaDateTime)
        .thenComparing(DicomSorter::getSliceLocation)
        .thenComparing(DicomSorter::getStackId)
        .compare(media1, media2);
  }

  private static LocalDateTime getMediaDateTime(MediaElement media) {
    return Stream.of(
            TagD.dateTime(Tag.AcquisitionDate, Tag.AcquisitionTime, media),
            TagD.dateTime(Tag.ContentDate, Tag.ContentTime, media),
            TagD.dateTime(Tag.DateOfSecondaryCapture, Tag.TimeOfSecondaryCapture, media))
        .filter(Objects::nonNull)
        .findFirst()
        .orElse(LocalDateTime.MAX);
  }

  private static Double getSliceLocation(MediaElement media) {
    return Optional.ofNullable(TagD.getTagValue(media, Tag.SliceLocation, Double.class))
        .orElse(Double.MAX_VALUE);
  }

  private static String getStackId(MediaElement media) {
    String stackId = TagD.getTagValue(media, Tag.StackID, String.class);
    if (stackId == null) {
      return NULL_STACK_PLACEHOLDER;
    }

    return parseNumericStackId(stackId).orElse(stackId);
  }

  private static Optional<String> parseNumericStackId(String stackId) {
    try {
      int numericId = Integer.parseInt(stackId);
      return Optional.of(String.format(ZERO_PADDING, numericId));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  private static boolean isEncapsulatedOrSR(MediaSeriesGroup series) {
    String sopClassUID = TagD.getTagValue(series, Tag.SOPClassUID, String.class);
    if (sopClassUID != null
        && (sopClassUID.startsWith(ENCAPSULATED_SOP_CLASS_PREFIX)
            || sopClassUID.startsWith(DOCUMENT_SOP_CLASS_PREFIX))) {
      return true;
    }
    String modality = TagD.getTagValue(series, Tag.Modality, String.class);
    return SR_MODALITY.equals(modality) || DOC_MODALITY.equals(modality);
  }

  public static SortingTime getStudyDateSorting() {
    int key =
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getIntProperty(
                DicomExplorerPrefView.STUDY_DATE_SORTING,
                SortingTime.INVERSE_CHRONOLOGICAL.getId());
    return SortingTime.valueOf(key);
  }
}
