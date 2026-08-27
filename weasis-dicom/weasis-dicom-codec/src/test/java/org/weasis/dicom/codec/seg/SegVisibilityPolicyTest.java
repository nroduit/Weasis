/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.HiddenSpecialElement;

/**
 * The parts of {@link SegVisibilityPolicy} users configure and can get wrong: keywords and count.
 */
class SegVisibilityPolicyTest {

  @Test
  void keywordsAreSplitTrimmedAndStrippedOfSeparators() {
    assertEquals(
        List.of("tableremoval", "couch"),
        SegVisibilityPolicy.hideKeywords(" Table Removal , couch "));
  }

  @Test
  void blankEntriesAndBlankPreferenceYieldNoKeyword() {
    assertTrue(SegVisibilityPolicy.hideKeywords("  ").isEmpty());
    assertTrue(SegVisibilityPolicy.hideKeywords(null).isEmpty());
    assertEquals(List.of("couch"), SegVisibilityPolicy.hideKeywords(",, couch , -- "));
  }

  @Test
  void matchingIgnoresCaseAndSeparators() {
    List<String> keywords = SegVisibilityPolicy.hideKeywords("table removal");

    assertTrue(SegVisibilityPolicy.matchesAny("TableRemoval", keywords));
    assertTrue(SegVisibilityPolicy.matchesAny("Table_Removal", keywords));
    assertTrue(SegVisibilityPolicy.matchesAny("CT table-removal mask", keywords));
    assertFalse(SegVisibilityPolicy.matchesAny("Table", keywords));
  }

  @Test
  void nothingMatchesWithoutAValueOrAKeyword() {
    assertFalse(SegVisibilityPolicy.matchesAny(null, List.of("couch")));
    assertFalse(SegVisibilityPolicy.matchesAny("  ", List.of("couch")));
    assertFalse(SegVisibilityPolicy.matchesAny("couch", List.of()));
  }

  @Test
  void defaultKeywordsCoverTheUsualTableSegments() {
    List<String> keywords =
        SegVisibilityPolicy.hideKeywords(SegVisibilityPolicy.DEFAULT_HIDE_KEYWORDS);

    assertTrue(SegVisibilityPolicy.matchesAny("Table Removal", keywords));
    assertTrue(SegVisibilityPolicy.matchesAny("Patient support", keywords));
    // The plural has to match too: this is how Siemens labels the series.
    assertTrue(SegVisibilityPolicy.matchesAny("Table Segmentations", keywords));
    assertFalse(SegVisibilityPolicy.matchesAny("Left ventricle", keywords));
    assertFalse(
        SegVisibilityPolicy.matchesAny("Segmentations CT_Cardiac_PreProc", keywords),
        "an ordinary segmentation series must not be caught by the table keywords");
  }

  @Test
  void crowdingCountsOnlyTheSegmentationsTheKeywordsDoNotAlreadyHide() {
    // A study whose extra segmentations are all table removals is not crowded: those never reach
    // the screen, so they must not push the few real ones below the threshold.
    List<String> keywords = SegVisibilityPolicy.hideKeywords("table removal");
    List<HiddenSpecialElement> elements =
        List.of(seg("Left ventricle"), seg("Table removal"), seg("Table removal"));

    assertEquals(1, SegVisibilityPolicy.countCrowding(elements, keywords));
    assertEquals(3, SegVisibilityPolicy.countCrowding(elements, List.of()));
  }

  @Test
  void nonSegmentationElementsOfTheSeriesAreNotCounted() {
    List<HiddenSpecialElement> elements =
        List.of(seg("Left ventricle"), mock(HiddenSpecialElement.class));

    assertEquals(1, SegVisibilityPolicy.countCrowding(elements, List.of()));
  }

  @Test
  void crowdingSpansEverySeriesOfThePatient() {
    // A cardiac study spreads its segmentations over several series; counting each on its own
    // leaves the single-file ones — the table segmentation among them — showing while the crowded
    // ones hide, so the threshold must see the whole patient.
    Map<String, Set<HiddenSpecialElement>> series2Elements =
        Map.of(
            "series.crowded", Set.of(seg("VOI a"), seg("VOI b"), seg("VOI c")),
            "series.alone", Set.of(seg("Table Segmentations")),
            "series.other.patient", Set.of(seg("VOI d")));

    assertEquals(
        4,
        SegVisibilityPolicy.countCrowding(
            series2Elements,
            Map.of(),
            List.of("series.crowded", "series.alone", "series.absent"),
            List.of()),
        "the absent series contributes nothing, the others all of it");
  }

  @Test
  void anAnnouncedSeriesCountsBeforeItHasDeliveredAnything() {
    // The whole point: over WADO the files arrive one at a time, so counting only what is loaded
    // leaves the first ones visible for minutes. The manifest already knows there will be 30.
    Map<String, SegVisibilityPolicy.AnnouncedSegSeries> announced =
        Map.of("series.downloading", announce("Segmentations CT_Cardiac", 30));

    assertEquals(
        30,
        SegVisibilityPolicy.countCrowding(
            Map.of("series.downloading", Set.of(seg("VOI a"))),
            announced,
            List.of("series.downloading"),
            List.of()),
        "the announcement stands in for the 29 instances still to come");
  }

  @Test
  void aSeriesCountsWhicheverOfLoadedAndAnnouncedIsLarger() {
    // Announcements are a floor, never a cap: a manifest that under-reports, or a series the user
    // added files to by hand, must still be counted by what it actually holds.
    Map<String, Set<HiddenSpecialElement>> series2Elements =
        Map.of("series.a", Set.of(seg("VOI a"), seg("VOI b"), seg("VOI c")));

    assertEquals(
        3,
        SegVisibilityPolicy.countCrowding(
            series2Elements,
            Map.of("series.a", announce("Segmentations", 1)),
            List.of("series.a"),
            List.of()));
  }

  @Test
  void anAnnouncedSeriesTheKeywordsHideIsNotCounted() {
    // Same exclusion as for loaded segmentations, decided on the only description a manifest
    // carries: a study announcing 30 table segmentations is not crowded by them.
    List<String> keywords = SegVisibilityPolicy.hideKeywords("table segmentation");

    assertEquals(
        0,
        SegVisibilityPolicy.countCrowding(
            Map.of(),
            Map.of("series.table", announce("SEG Table Segmentations", 30)),
            List.of("series.table"),
            keywords));
  }

  @Test
  void announcingTheSameSeriesTwiceCountsItOnce() {
    // A manifest re-read, or completed by a QIDO query, announces its series again.
    String patient = "patient.announce.twice";
    SegVisibilityPolicy.announce(patient, "series.a", "Segmentations", 4);
    SegVisibilityPolicy.announce(patient, "series.a", "Segmentations", 4);
    try {
      assertEquals(
          4,
          SegVisibilityPolicy.countCrowding(
              Map.of(), SegVisibilityPolicy.announcedOf(patient), List.of("series.a"), List.of()));
    } finally {
      SegVisibilityPolicy.forgetPatient(patient);
    }
    assertTrue(
        SegVisibilityPolicy.announcedOf(patient).isEmpty(), "closing the patient forgets it");
  }

  @Test
  void renamingThePatientCarriesItsAnnouncementsOver() {
    // The manifest and the first downloaded instance can disagree on the pseudo UID; DicomModel
    // then merges the nodes and the segmentations look themselves up under the new one.
    SegVisibilityPolicy.announce("patient.old", "series.a", "Segmentations", 5);
    try {
      SegVisibilityPolicy.renamePatient("patient.old", "patient.new");

      assertTrue(SegVisibilityPolicy.announcedOf("patient.old").isEmpty());
      assertEquals(
          5,
          SegVisibilityPolicy.countCrowding(
              Map.of(),
              SegVisibilityPolicy.announcedOf("patient.new"),
              List.of("series.a"),
              List.of()));
    } finally {
      SegVisibilityPolicy.forgetPatient("patient.old");
      SegVisibilityPolicy.forgetPatient("patient.new");
    }
  }

  private static SegVisibilityPolicy.AnnouncedSegSeries announce(
      String description, int instances) {
    return new SegVisibilityPolicy.AnnouncedSegSeries(description, instances);
  }

  @Test
  void invalidateAdvancesTheGeneration() {
    // The contract SegSpecialElement caches its computed default against.
    long before = SegVisibilityPolicy.generation();

    SegVisibilityPolicy.invalidate();

    assertTrue(SegVisibilityPolicy.generation() > before);
  }

  /** Segmentation whose keyword match is decided by its Series Description alone. */
  private static SegSpecialElement seg(String seriesDescription) {
    Attributes dcm = new Attributes();
    dcm.setString(Tag.SeriesDescription, VR.LO, seriesDescription);
    DicomMediaIO reader = mock(DicomMediaIO.class);
    when(reader.getDicomObject()).thenReturn(dcm);
    SegSpecialElement seg = mock(SegSpecialElement.class);
    when(seg.getMediaReader()).thenReturn(reader);
    when(seg.getSegAttributes()).thenReturn(Map.of());
    return seg;
  }
}
