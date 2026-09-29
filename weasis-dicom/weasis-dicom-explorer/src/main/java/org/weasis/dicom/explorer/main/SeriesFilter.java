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
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.ref.AnatomicRegion;
import org.weasis.dicom.ref.RegionGroup;

/**
 * Stateful, single-mode filter for the DICOM explorer thumbnail grid. Only the active {@link
 * Mode}'s criterion is applied: free-text over series/study tags, a selected study (date) or a set
 * of modalities.
 */
public class SeriesFilter {

  /** The dimension the filter currently acts on. */
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

  public Mode getMode() {
    return mode;
  }

  /**
   * Switches the active dimension, clearing any previously entered criterion (modes are exclusive).
   */
  public void setMode(Mode mode) {
    this.mode = Objects.requireNonNull(mode);
    clear();
  }

  /** Returns true when the active mode carries a criterion. */
  public boolean isActive() {
    return switch (mode) {
      case TEXT -> !text.isEmpty();
      case DATE -> study != null;
      case MODALITY -> !modalities.isEmpty();
    };
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

  public void clear() {
    text = StringUtil.EMPTY_STRING;
    modalities.clear();
    study = null;
  }

  /**
   * @param series the series to test
   * @param study the study owning the series (used for study-level text search and date selection)
   * @return true if the series passes the active filter
   */
  public boolean test(MediaSeriesGroup series, MediaSeriesGroup study) {
    if (series == null) {
      return false;
    }
    return switch (mode) {
      case TEXT -> text.isEmpty() || matchesText(series, study);
      case DATE -> this.study == null || this.study.equals(study);
      case MODALITY -> modalities.isEmpty() || modalities.contains(modalityOf(series));
    };
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
