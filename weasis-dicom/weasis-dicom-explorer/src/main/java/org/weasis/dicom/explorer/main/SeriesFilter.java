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
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;

/**
 * Stateful, single-mode filter for the DICOM explorer thumbnail grid. Only the active {@link
 * Mode}'s criterion is applied: free-text over series/study tags, a selected study (date) or a set
 * of modalities.
 */
public class SeriesFilter {

  /** The dimension the filter currently acts on. */
  public enum Mode {
    TEXT,
    DATE,
    MODALITY
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
    for (int tag : STUDY_TEXT_TAGS) {
      if (contains(TagD.getTagValue(study, tag, String.class))) {
        return true;
      }
    }
    return false;
  }

  private static String modalityOf(MediaSeriesGroup series) {
    return TagD.getTagValue(series, Tag.Modality, String.class);
  }

  private boolean contains(String value) {
    return value != null && value.toLowerCase().contains(text);
  }
}
