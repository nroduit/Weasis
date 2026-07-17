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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.SeriesFilter.Mode;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SeriesFilterTest {

  @Test
  void default_text_mode_is_inactive_and_matches_everything() {
    SeriesFilter filter = new SeriesFilter();
    assertFalse(filter.isActive());
    assertTrue(filter.test(series("CT", 1, "Chest", "CHEST"), study("Thorax")));
  }

  @Test
  void null_series_never_matches() {
    assertFalse(new SeriesFilter().test(null, study("Thorax")));
  }

  @Test
  void text_mode_matches_series_description_case_insensitively() {
    SeriesFilter filter = new SeriesFilter();
    filter.setText("CHEST");
    assertTrue(filter.isActive());
    assertTrue(filter.test(series("CT", 1, "Chest CT", null), study("x")));
    assertFalse(filter.test(series("CT", 2, "Abdomen", null), study("x")));
  }

  @Test
  void text_mode_matches_series_number_body_part_and_study_description() {
    SeriesFilter number = new SeriesFilter();
    number.setText("305");
    assertTrue(number.test(series("CT", 305, "Chest", null), study("x")));

    SeriesFilter bodyPart = new SeriesFilter();
    bodyPart.setText("head");
    assertTrue(bodyPart.test(series("CT", 1, "Scan", "HEAD"), study("x")));

    SeriesFilter studyDesc = new SeriesFilter();
    studyDesc.setText("neuro");
    assertTrue(studyDesc.test(series("MR", 1, "Scan", null), study("NEURO PROTOCOL")));
  }

  @Test
  void text_mode_matches_series_and_study_instance_uid() {
    DicomSeries series = new DicomSeries("1.2.3.4.5.99");
    series.setTagNoNull(TagD.get(Tag.SeriesInstanceUID), "1.2.3.4.5.99");
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(
            TagD.getUID(Level.STUDY), "1.2.3.4.5.88", DicomModel.study.tagView());

    SeriesFilter bySeriesUid = new SeriesFilter();
    bySeriesUid.setText("5.99");
    assertTrue(bySeriesUid.test(series, study));

    SeriesFilter byStudyUid = new SeriesFilter();
    byStudyUid.setText("5.88");
    assertTrue(byStudyUid.test(series, study));
  }

  @Test
  void modality_mode_keeps_only_selected_modalities() {
    SeriesFilter filter = new SeriesFilter();
    filter.setMode(Mode.MODALITY);
    filter.setModalities(List.of("CT", "MR"));
    assertTrue(filter.isActive());
    assertTrue(filter.test(series("CT", 1, "Chest", null), study("x")));
    assertTrue(filter.test(series("MR", 2, "Brain", null), study("x")));
    assertFalse(filter.test(series("US", 3, "Abdomen", null), study("x")));
    assertFalse(filter.test(series(null, 4, "None", null), study("x")));
  }

  @Test
  void date_mode_keeps_only_the_selected_study() {
    MediaSeriesGroup studyA = study("A");
    MediaSeriesGroup studyB = study("B");
    SeriesFilter filter = new SeriesFilter();
    filter.setMode(Mode.DATE);
    filter.setStudy(studyA);
    assertTrue(filter.isActive());
    assertTrue(filter.test(series("CT", 1, "s", null), studyA));
    assertFalse(filter.test(series("CT", 2, "s", null), studyB));
  }

  @Test
  void switching_mode_clears_the_previous_criterion() {
    SeriesFilter filter = new SeriesFilter();
    filter.setText("chest");
    assertTrue(filter.isActive());
    filter.setMode(Mode.MODALITY);
    assertFalse(filter.isActive());
    // Text is no longer applied in modality mode
    assertTrue(filter.test(series("CT", 1, "Abdomen", null), study("x")));
  }

  @Test
  void clear_resets_all_dimensions() {
    SeriesFilter filter = new SeriesFilter();
    filter.setMode(Mode.MODALITY);
    filter.setModalities(List.of("CT"));
    filter.clear();
    assertFalse(filter.isActive());
    assertTrue(filter.test(series("US", 9, "Abdomen", null), study("x")));
  }

  private static MediaSeriesGroup series(
      String modality, int number, String description, String bodyPart) {
    DicomSeries series = new DicomSeries(UIDUtils.createUID());
    series.setTagNoNull(TagD.get(Tag.Modality), modality);
    series.setTagNoNull(TagD.get(Tag.SeriesNumber), number);
    series.setTagNoNull(TagD.get(Tag.SeriesDescription), description);
    series.setTagNoNull(TagD.get(Tag.BodyPartExamined), bodyPart);
    return series;
  }

  private static MediaSeriesGroup study(String description) {
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(
            TagD.getUID(Level.STUDY), UIDUtils.createUID(), DicomModel.study.tagView());
    study.setTagNoNull(TagD.get(Tag.StudyDescription), description);
    return study;
  }
}
