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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.HiddenSpecialElement;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.StudyContents;
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
  void text_mode_matches_the_resolved_anatomy_and_its_region_groups() {
    SeriesFilter chest = new SeriesFilter();
    chest.setText("chest");
    SeriesFilter liver = new SeriesFilter();
    liver.setText("liver");
    SeriesFilter extremity = new SeriesFilter();
    extremity.setText("lower extremity");

    assertAll(
        () -> assertTrue(chest.test(series("CT", 1, "Scan", "LUNG"), study("x")), "lung in chest"),
        () -> assertTrue(chest.test(series("CT", 2, "Scan", "CHESTABDPELVIS"), study("x"))),
        () -> assertFalse(chest.test(series("CT", 3, "Scan", "LIVER"), study("x"))),
        () -> assertTrue(liver.test(series("CT", 4, "Scan", "liver"), study("x"))),
        () -> assertTrue(extremity.test(series("MR", 5, "Scan", "KNEE"), study("x"))));
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

  @Test
  void facets_combine_with_and_and_a_mode_switch_keeps_the_code_facets() {
    SeriesFilter filter = new SeriesFilter();
    filter.setText("chest");
    filter.setModalities(List.of("CT"));
    assertTrue(filter.test(series("CT", 1, "Chest CT", null), study("x")));
    assertFalse(
        filter.test(series("MR", 1, "Chest MR", null), study("x")), "text yes, modality no");
    assertFalse(filter.test(series("CT", 1, "Abdomen", null), study("x")), "modality yes, text no");

    filter.setDownloadStates(List.of(DownloadState.COMPLETE));
    filter.setMode(Mode.MODALITY);
    assertAll(
        () -> assertTrue(filter.getText().isEmpty(), "field facets cleared by the mode switch"),
        () -> assertTrue(filter.getModalities().isEmpty()),
        () -> assertEquals(Set.of(DownloadState.COMPLETE), filter.getDownloadStates()),
        () -> assertTrue(filter.isActive()),
        () -> assertFalse(filter.isFieldActive()));
    filter.clear();
    assertFalse(filter.isActive());
  }

  @Test
  void studies_facet_keeps_the_series_of_the_selected_studies_only() {
    MediaSeriesGroup a = study("A");
    MediaSeriesGroup b = study("B");
    MediaSeriesGroup c = study("C");
    SeriesFilter filter = new SeriesFilter();
    filter.setStudies(List.of(a, b));
    assertAll(
        () -> assertTrue(filter.isActive()),
        () -> assertEquals(Set.of(a, b), filter.getStudies()),
        () -> assertTrue(filter.test(series("CT", 1, "x", null), a)),
        () -> assertTrue(filter.test(series("CT", 1, "x", null), b)),
        () -> assertFalse(filter.test(series("CT", 1, "x", null), c)));
    filter.setMode(Mode.TEXT);
    assertEquals(Set.of(a, b), filter.getStudies(), "a field mode switch keeps the strip facet");
    filter.clear();
    assertTrue(filter.getStudies().isEmpty());
  }

  @Test
  void download_state_facet_follows_the_series_loader() {
    SeriesFilter filter = new SeriesFilter();
    filter.setDownloadStates(List.of(DownloadState.PENDING));
    DicomSeries series = series("CT", 1, "Chest", null);
    assertFalse(filter.test(series, study("x")), "no loader: complete");
    series.setSeriesLoader(stoppedLoader());
    assertTrue(filter.test(series, study("x")));
  }

  @Test
  void object_facet_keeps_the_series_referenced_by_an_object_of_a_kind() {
    String referenced = UIDUtils.createUID();
    String koSeries = UIDUtils.createUID();
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    manager
        .reference2Series
        .computeIfAbsent(referenced, _ -> new CopyOnWriteArraySet<>())
        .add(koSeries);
    manager
        .series2Elements
        .computeIfAbsent(koSeries, _ -> new CopyOnWriteArraySet<>())
        .add(keyObject());

    SeriesFilter filter = new SeriesFilter();
    filter.setObjectKinds(List.of(ObjectKind.KEY_OBJECT, ObjectKind.SEGMENTATION));
    DicomSeries withKo = series("CT", 1, "Chest", null);
    withKo.setTag(TagD.get(Tag.SeriesInstanceUID), referenced);
    DicomSeries without = series("CT", 2, "Chest", null);
    without.setTag(TagD.get(Tag.SeriesInstanceUID), UIDUtils.createUID());

    assertAll(
        () -> assertTrue(filter.isActive()),
        () -> assertTrue(filter.test(withKo, study("x"))),
        () -> assertFalse(filter.test(without, study("x"))),
        () ->
            assertEquals(Set.of(ObjectKind.KEY_OBJECT), StudyContents.kindsReferencing(referenced)),
        () -> assertEquals(Set.of(), StudyContents.kindsReferencing(null)));
    filter.setObjectKinds(List.of(ObjectKind.SEGMENTATION));
    assertFalse(filter.test(withKo, study("x")), "a KO is not a SEG");
  }

  private static HiddenSpecialElement keyObject() {
    try {
      Attributes a = new Attributes();
      a.setString(Tag.SOPClassUID, VR.UI, UID.KeyObjectSelectionDocumentStorage);
      a.setString(Tag.SOPInstanceUID, VR.UI, UIDUtils.createUID());
      a.setString(Tag.Modality, VR.CS, "KO");
      KOSpecialElement ko = new KOSpecialElement(new DicomMediaIO(a));
      ko.setTag(TagD.get(Tag.Modality), "KO");
      ko.setTag(TagD.get(Tag.SOPInstanceUID), a.getString(Tag.SOPInstanceUID));
      return ko;
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }

  private static SeriesImporter stoppedLoader() {
    return new SeriesImporter() {
      @Override
      public boolean isStopped() {
        return true;
      }

      @Override
      public JProgressBar getProgressBar() {
        return null;
      }

      @Override
      public boolean stop() {
        return true;
      }

      @Override
      public void resume() {}

      @Override
      public void setPriority() {}
    };
  }

  private static DicomSeries series(
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
