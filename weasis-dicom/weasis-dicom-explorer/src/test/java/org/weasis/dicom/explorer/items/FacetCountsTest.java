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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URISyntaxException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FacetCountsTest {

  @Test
  void counts_the_series_of_the_patient_per_facet_value() throws URISyntaxException {
    DicomModel model = new DicomModel();
    String pseudoUID = "PSEUDO-" + UIDUtils.createUID(); // NON-NLS
    MediaSeriesGroup patient =
        new MediaSeriesGroupNode(TagW.PatientPseudoUID, pseudoUID, DicomModel.patient.tagView());
    String studyUID = UIDUtils.createUID();
    MediaSeriesGroup study =
        new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, DicomModel.study.tagView());
    study.setTag(TagD.get(Tag.StudyInstanceUID), studyUID);
    model.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
    model.addHierarchyNode(patient, study);
    String ctUID = UIDUtils.createUID();
    DicomSeries ct = series(ctUID, "CT");
    DicomSeries ct2 = series(UIDUtils.createUID(), "CT");
    DicomSeries mr = series(UIDUtils.createUID(), "MR");
    mr.setSeriesLoader(loader(true));
    model.addHierarchyNode(study, ct);
    model.addHierarchyNode(study, ct2);
    model.addHierarchyNode(study, mr);
    // A KO of the study referencing two images of the first CT, registered as the loader does
    Attributes attrs = KoFixtures.koAttributes(studyUID, ctUID, 2);
    KOSpecialElement ko = new KOSpecialElement(new DicomMediaIO(attrs));
    for (int tag :
        new int[] {Tag.SOPInstanceUID, Tag.SeriesInstanceUID, Tag.StudyInstanceUID, Tag.Modality}) {
      ko.setTag(TagD.get(tag), attrs.getString(tag));
    }
    model.addHierarchyNode(study, series(attrs.getString(Tag.SeriesInstanceUID), "KO"));
    HiddenSeriesManager manager = HiddenSeriesManager.getInstance();
    String koSeries = attrs.getString(Tag.SeriesInstanceUID);
    manager.series2Elements.computeIfAbsent(koSeries, _ -> new CopyOnWriteArraySet<>()).add(ko);
    manager
        .patient2Series
        .computeIfAbsent(pseudoUID, _ -> new CopyOnWriteArraySet<>())
        .add(koSeries);
    manager.reference2Series.computeIfAbsent(ctUID, _ -> new CopyOnWriteArraySet<>()).add(koSeries);

    FacetCounts counts = FacetCounts.of(model, patient);

    assertAll(
        () -> assertEquals(3, counts.total()),
        () -> assertEquals(Map.of("CT", 2, "MR", 1), counts.modalities()),
        () -> assertEquals(Map.of(ObjectKind.KEY_OBJECT, 1), counts.objectKinds()),
        () -> assertEquals(1, counts.interestAny()),
        () -> assertEquals(Map.of(InterestSource.KEY_OBJECT, 1), counts.interestSources()),
        () ->
            assertEquals(
                Map.of(DownloadState.COMPLETE, 2, DownloadState.PENDING, 1),
                counts.downloadStates()),
        () -> assertTrue(counts.hasTransfers()),
        () -> assertFalse(counts.isEmpty()));
  }

  @Test
  void is_empty_without_a_patient() {
    FacetCounts counts = FacetCounts.of(new DicomModel(), null);
    assertAll(
        () -> assertTrue(counts.isEmpty()),
        () -> assertFalse(counts.hasTransfers()),
        () -> assertEquals(FacetCounts.EMPTY, counts));
  }

  private static DicomSeries series(String uid, String modality) {
    DicomSeries series = new DicomSeries(uid);
    series.setTag(TagD.get(Tag.SeriesInstanceUID), uid);
    series.setTag(TagD.get(Tag.Modality), modality);
    series.setTag(TagD.get(Tag.SeriesNumber), 1);
    return series;
  }

  private static SeriesImporter loader(boolean stopped) {
    return new SeriesImporter() {
      @Override
      public boolean isStopped() {
        return stopped;
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
}
