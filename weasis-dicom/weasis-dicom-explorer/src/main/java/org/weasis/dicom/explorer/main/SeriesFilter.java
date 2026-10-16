/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;
import org.weasis.dicom.explorer.items.SeriesItem;
import org.weasis.dicom.explorer.items.StudyContents;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.RegionGroup;

/**
 * Stateful filter of the series shown by the DICOM explorer, made of <b>facets</b> combined with
 * AND: free text over series and study tags, one study (date), a set of modalities, the kinds of
 * non-image objects referencing the series, and the download state. The {@link Mode} names the
 * facet the filter field of the explorer edits; the other facets are set by code and by the
 * commands until the filter bar shows them as chips.
 */
public class SeriesFilter {

  /** The facet the filter field edits. */
  public enum Mode {
    TEXT(Messages.getString("DicomExplorer.filter_mode_text")),
    DATE(Messages.getString("DicomExplorer.filter_mode_date")),
    MODALITY(Messages.getString("DicomExplorer.filter_mode_modality"));

    private final String title;

    Mode(String title) {
      this.title = title;
    }

    public String getTitle() {
      return title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  private static final int[] SERIES_TEXT_TAGS = {
    Tag.SeriesDescription,
    Tag.Modality,
    Tag.BodyPartExamined,
    Tag.ProtocolName,
    Tag.SeriesInstanceUID
  };
  private static final int[] STUDY_TEXT_TAGS = {
    Tag.StudyDescription, Tag.StudyID, Tag.AccessionNumber, Tag.StudyInstanceUID
  };

  private Mode mode = Mode.TEXT;
  private String text = StringUtil.EMPTY_STRING;
  private final Set<String> modalities = new LinkedHashSet<>();
  private MediaSeriesGroup study;
  private final Set<MediaSeriesGroup> studies = new LinkedHashSet<>();
  private final Set<ObjectKind> objectKinds = EnumSet.noneOf(ObjectKind.class);
  private final Set<DownloadState> downloadStates = EnumSet.noneOf(DownloadState.class);
  private boolean interestAny;
  private final Set<InterestSource> interestSources = EnumSet.noneOf(InterestSource.class);

  public Mode getMode() {
    return mode;
  }

  /**
   * Switches the facet the field edits, clearing the criteria of the field facets (text, study,
   * modalities) since the field shows one of them at a time; the object and state facets stay.
   */
  public void setMode(Mode mode) {
    this.mode = Objects.requireNonNull(mode);
    clearFieldFacets();
  }

  /** Returns true when at least one facet carries a criterion. */
  public boolean isActive() {
    return !text.isEmpty()
        || study != null
        || !studies.isEmpty()
        || !modalities.isEmpty()
        || !objectKinds.isEmpty()
        || !downloadStates.isEmpty()
        || interestAny
        || !interestSources.isEmpty();
  }

  /**
   * The images-of-interest facet: {@code any} keeps the series with at least one image of interest
   * whatever points at it, {@code sources} those pointed at by one of these sources; both empty
   * means no such criterion.
   */
  public boolean isInterestAny() {
    return interestAny;
  }

  public Set<InterestSource> getInterestSources() {
    return Collections.unmodifiableSet(interestSources);
  }

  public void setInterest(boolean any, Collection<InterestSource> sources) {
    interestAny = any;
    interestSources.clear();
    if (sources != null) {
      sources.stream().filter(Objects::nonNull).forEach(interestSources::add);
    }
  }

  /** Returns true when the facet the field edits carries a criterion. */
  public boolean isFieldActive() {
    return switch (mode) {
      case TEXT -> !text.isEmpty();
      case DATE -> study != null;
      case MODALITY -> !modalities.isEmpty();
    };
  }

  /**
   * The studies the timeline strip selected: a series must belong to one of them; empty means no
   * such criterion. Independent of the single study of the {@link Mode#DATE} field.
   */
  public Set<MediaSeriesGroup> getStudies() {
    return Collections.unmodifiableSet(studies);
  }

  public void setStudies(Collection<MediaSeriesGroup> selected) {
    studies.clear();
    if (selected != null) {
      selected.stream().filter(Objects::nonNull).forEach(studies::add);
    }
  }

  /** The kinds of objects a series must be referenced by; empty means no such criterion. */
  public Set<ObjectKind> getObjectKinds() {
    return Collections.unmodifiableSet(objectKinds);
  }

  public void setObjectKinds(Collection<ObjectKind> kinds) {
    objectKinds.clear();
    if (kinds != null) {
      kinds.stream().filter(Objects::nonNull).forEach(objectKinds::add);
    }
  }

  /** The download states a series must be in; empty means no such criterion. */
  public Set<DownloadState> getDownloadStates() {
    return Collections.unmodifiableSet(downloadStates);
  }

  public void setDownloadStates(Collection<DownloadState> states) {
    downloadStates.clear();
    if (states != null) {
      states.stream().filter(Objects::nonNull).forEach(downloadStates::add);
    }
  }

  public String getText() {
    return text;
  }

  public void setText(String value) {
    this.text = value == null ? StringUtil.EMPTY_STRING : value.trim().toLowerCase();
  }

  public Set<String> getModalities() {
    return Set.copyOf(modalities);
  }

  public void setModalities(Collection<String> values) {
    modalities.clear();
    if (values != null) {
      values.stream().filter(StringUtil::hasText).forEach(modalities::add);
    }
  }

  public MediaSeriesGroup getStudy() {
    return study;
  }

  public void setStudy(MediaSeriesGroup study) {
    this.study = study;
  }

  /** Clears every facet. */
  public void clear() {
    clearFieldFacets();
    studies.clear();
    objectKinds.clear();
    downloadStates.clear();
    interestAny = false;
    interestSources.clear();
  }

  private void clearFieldFacets() {
    text = StringUtil.EMPTY_STRING;
    modalities.clear();
    study = null;
  }

  /**
   * @param series the series to test
   * @param study the study owning the series (used for study-level text search and date selection)
   * @return true if the series passes every active facet
   */
  public boolean test(MediaSeriesGroup series, MediaSeriesGroup study) {
    if (series == null) {
      return false;
    }
    return (text.isEmpty() || matchesText(series, study))
        && (this.study == null || this.study.equals(study))
        && (studies.isEmpty() || studies.contains(study))
        && (modalities.isEmpty() || modalities.contains(modalityOf(series)))
        && matchesObjects(series)
        && matchesDownloadState(series)
        && matchesInterest(series);
  }

  private boolean matchesInterest(MediaSeriesGroup series) {
    if (!interestAny && interestSources.isEmpty()) {
      return true;
    }
    if (!(series instanceof DicomSeries dicomSeries)) {
      return false;
    }
    Interest interest = Interest.of(dicomSeries);
    if (interest.isEmpty()) {
      return false;
    }
    if (interestAny) {
      return true;
    }
    for (InterestSource source : interestSources) {
      if (interest.count(source) > 0) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesObjects(MediaSeriesGroup series) {
    if (objectKinds.isEmpty()) {
      return true;
    }
    Set<ObjectKind> kinds =
        StudyContents.kindsReferencing(
            TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class));
    for (ObjectKind kind : objectKinds) {
      if (kinds.contains(kind)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesDownloadState(MediaSeriesGroup series) {
    if (downloadStates.isEmpty()) {
      return true;
    }
    return series instanceof DicomSeries dicomSeries
        && downloadStates.contains(SeriesItem.downloadStateOf(dicomSeries));
  }

  private boolean matchesText(MediaSeriesGroup series, MediaSeriesGroup study) {
    for (int tag : SERIES_TEXT_TAGS) {
      if (contains(TagD.getTagValue(series, tag, String.class))) {
        return true;
      }
    }
    Integer number = TagD.getTagValue(series, Tag.SeriesNumber, Integer.class);
    if (number != null && contains(number.toString())) {
      return true;
    }
    if (matchesAnatomy(anatomyOf(series))) {
      return true;
    }
    for (int tag : STUDY_TEXT_TAGS) {
      if (contains(TagD.getTagValue(study, tag, String.class))) {
        return true;
      }
    }
    return false;
  }

  // The meaning of the region, or the name of a region group it lies in: "chest" finds a lung CT
  private boolean matchesAnatomy(AnatomicRegion anatomy) {
    if (anatomy == null) {
      return false;
    }
    if (contains(anatomy.getRegion().getCodeMeaning())) {
      return true;
    }
    for (RegionGroup group : RegionGroup.values()) {
      if (anatomy.isIn(group)
          && (contains(group.getLabel()) || contains(group.name().replace('_', ' ')))) {
        return true;
      }
    }
    return false;
  }

  /**
   * The anatomy of the first image when loaded (Anatomic Region Sequence, else Body Part Examined),
   * otherwise the Body Part Examined of the series.
   */
  static AnatomicRegion anatomyOf(MediaSeriesGroup series) {
    if (series instanceof MediaSeries<?> images
        && images.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null) instanceof MediaElement m
        && m.getTagValue(TagW.AnatomicRegion) instanceof AnatomicRegion region) {
      return region;
    }
    String bodyPart = TagD.getTagValue(series, Tag.BodyPartExamined, String.class);
    if (!StringUtil.hasText(bodyPart)) {
      return null;
    }
    Attributes attributes = new Attributes(1);
    attributes.setString(Tag.BodyPartExamined, VR.CS, bodyPart);
    return AnatomicRegion.read(attributes);
  }

  private static String modalityOf(MediaSeriesGroup series) {
    return TagD.getTagValue(series, Tag.Modality, String.class);
  }

  private boolean contains(String value) {
    return value != null && value.toLowerCase().contains(text);
  }
}
