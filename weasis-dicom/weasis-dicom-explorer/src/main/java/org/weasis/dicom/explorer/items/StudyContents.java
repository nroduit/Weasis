/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.ObjectDescriptor;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;

/**
 * Everything a study holds as the explorer lists it: its image series and the non-image objects
 * (KO, PR, SEG, RT...) that belong to it or reference its series. A headless snapshot read from the
 * {@link DicomModel} and the {@link HiddenSeriesManager} index, which the panes, the verbs and the
 * filters share so they never disagree on what a study contains.
 */
public final class StudyContents {

  private final MediaSeriesGroup study;
  private final String studyUID;
  private final List<SeriesItem> series;
  private final List<ObjectItem> objects;

  private StudyContents(
      MediaSeriesGroup study, String studyUID, List<SeriesItem> series, List<ObjectItem> objects) {
    this.study = study;
    this.studyUID = studyUID;
    this.series = Collections.unmodifiableList(series);
    this.objects = Collections.unmodifiableList(objects);
  }

  /** Reads the contents of {@code study} from the model and the hidden-series index. */
  public static StudyContents of(DicomModel model, MediaSeriesGroup study) {
    Objects.requireNonNull(model);
    Objects.requireNonNull(study);
    String studyUID = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);

    List<SeriesItem> series = new ArrayList<>();
    Set<String> seriesUIDs = new LinkedHashSet<>();
    for (MediaSeriesGroup child : model.getChildren(study)) {
      if (child instanceof DicomSeries dicomSeries && !DicomModel.isHiddenModality(dicomSeries)) {
        series.add(SeriesItem.of(dicomSeries, study));
        String uid = TagD.getTagValue(dicomSeries, Tag.SeriesInstanceUID, String.class);
        if (StringUtil.hasText(uid)) {
          seriesUIDs.add(uid);
        }
      }
    }

    // The order of the panes: series number, then the sorting rules of DicomSorter

    series.sort((a, b) -> DicomSorter.SERIES_COMPARATOR.compare(a.series(), b.series()));

    List<ObjectItem> objects = new ArrayList<>();
    MediaSeriesGroup patient = model.getParent(study, DicomModel.patient);
    for (HiddenSpecialElement element :
        HiddenSeriesManager.getHiddenElementsFromPatient(HiddenSpecialElement.class, patient)) {
      ObjectDescriptor descriptor = ObjectDescriptor.describe(element);
      if (belongsTo(descriptor, studyUID, seriesUIDs)) {
        objects.add(new ObjectItem(element, descriptor, study));
      }
    }
    objects.sort(StudyContents::byDateThenLabel);
    return new StudyContents(study, studyUID, series, objects);
  }

  /**
   * The object of that SOP Instance UID among the loaded studies, empty when none: what {@code
   * dicom:open --object} and {@code explorer:select --object} resolve.
   */
  public static Optional<ObjectItem> findObject(DicomModel model, String sopInstanceUID) {
    if (!StringUtil.hasText(sopInstanceUID)) {
      return Optional.empty();
    }
    for (MediaSeriesGroup patient : patientsOf(model)) {
      for (MediaSeriesGroup study : studiesOf(model, patient)) {
        for (ObjectItem item : of(model, study).objects()) {
          if (sopInstanceUID.equals(item.descriptor().sopInstanceUID())) {
            return Optional.of(item);
          }
        }
      }
    }
    return Optional.empty();
  }

  /** The studies of a patient in the order the explorer shows them. */
  public static List<MediaSeriesGroup> studiesOf(DicomModel model, MediaSeriesGroup patient) {
    List<MediaSeriesGroup> studies = new ArrayList<>(model.getChildren(patient));
    studies.sort(DicomSorter.STUDY_COMPARATOR);
    return studies;
  }

  /** The patients of the model in the order the explorer lists them. */
  public static List<MediaSeriesGroup> patientsOf(DicomModel model) {
    List<MediaSeriesGroup> patients =
        new ArrayList<>(model.getChildren(MediaSeriesGroupNode.rootNode));
    patients.sort(DicomSorter.PATIENT_COMPARATOR);
    return patients;
  }

  /**
   * An object belongs to the study it is stored in, and to any study whose series it references.
   */
  private static boolean belongsTo(
      ObjectDescriptor descriptor, String studyUID, Set<String> seriesUIDs) {
    if (studyUID != null && studyUID.equals(descriptor.studyUID())) {
      return true;
    }
    for (String uid : descriptor.referencedSeriesUIDs()) {
      if (seriesUIDs.contains(uid)) {
        return true;
      }
    }
    return false;
  }

  private static int byDateThenLabel(ObjectItem a, ObjectItem b) {
    if (a.date() != null && b.date() != null) {
      int c = b.date().compareTo(a.date()); // most recent first
      if (c != 0) {
        return c;
      }
    } else if (a.date() != null || b.date() != null) {
      return a.date() == null ? 1 : -1;
    }
    return String.CASE_INSENSITIVE_ORDER.compare(
        StringUtil.getEmptyStringIfNull(a.label()), StringUtil.getEmptyStringIfNull(b.label()));
  }

  public MediaSeriesGroup study() {
    return study;
  }

  public String studyUID() {
    return studyUID;
  }

  /** The image series of the study, in the order the explorer shows them. */
  public List<SeriesItem> series() {
    return series;
  }

  /** The non-image objects of the study, most recent first. */
  public List<ObjectItem> objects() {
    return objects;
  }

  public boolean hasObjects() {
    return !objects.isEmpty();
  }

  /** Series and objects, series first. */
  public List<ExplorerItem> items() {
    List<ExplorerItem> items = new ArrayList<>(series.size() + objects.size());
    items.addAll(series);
    items.addAll(objects);
    return items;
  }

  /** The objects of the given kind. */
  public List<ObjectItem> objects(ObjectKind kind) {
    return objects.stream().filter(o -> o.kind() == kind).toList();
  }

  /** The objects that reference the given image series, most recent first. */
  public List<ObjectItem> objectsReferencing(String seriesInstanceUID) {
    return objects.stream().filter(o -> o.references(seriesInstanceUID)).toList();
  }

  /** How many objects of each kind the study holds; kinds without objects are absent. */
  /**
   * The objects as the explorer shows them: every object, or every object but the rejection notes
   * when the site does not list them ({@code weasis.dicom.explorer.rejected}).
   */
  public List<ObjectItem> visibleObjects(boolean rejectionsListed) {
    return rejectionsListed
        ? objects
        : objects.stream().filter(o -> o.kind() != ObjectKind.REJECTION_NOTE).toList();
  }

  /** {@link #countByKind()} over {@link #visibleObjects(boolean)}. */
  public Map<ObjectKind, Integer> visibleCountByKind(boolean rejectionsListed) {
    Map<ObjectKind, Integer> counts = new EnumMap<>(ObjectKind.class);
    for (ObjectItem item : visibleObjects(rejectionsListed)) {
      counts.merge(item.kind(), 1, Integer::sum);
    }
    return counts;
  }

  public Map<ObjectKind, Integer> countByKind() {
    Map<ObjectKind, Integer> counts = new EnumMap<>(ObjectKind.class);
    for (ObjectItem item : objects) {
      counts.merge(item.kind(), 1, Integer::sum);
    }
    return counts;
  }

  /**
   * The kinds of the objects that reference the image series {@code seriesInstanceUID}, read from
   * the hidden-series index alone (no study needed); empty when none.
   */
  public static Set<ObjectKind> kindsReferencing(String seriesInstanceUID) {
    if (!StringUtil.hasText(seriesInstanceUID)) {
      return Set.of();
    }
    Set<String> origins = HiddenSeriesManager.getInstance().reference2Series.get(seriesInstanceUID);
    if (origins == null || origins.isEmpty()) {
      return Set.of();
    }
    Set<ObjectKind> kinds = EnumSet.noneOf(ObjectKind.class);
    for (String origin : origins) {
      Set<HiddenSpecialElement> elements =
          HiddenSeriesManager.getInstance().series2Elements.get(origin);
      if (elements != null) {
        for (HiddenSpecialElement element : elements) {
          kinds.add(ObjectDescriptor.kindOf(element));
        }
      }
    }
    return kinds;
  }

  /** The series items whose download is not complete. */
  public List<SeriesItem> notDownloaded() {
    return series.stream().filter(s -> s.downloadState() != DownloadState.COMPLETE).toList();
  }
}
