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

    List<HiddenSpecialElement> loaded =
        SegVisibilityPolicy.elementsOf(
            series2Elements, List.of("series.crowded", "series.alone", "series.absent"));

    assertEquals(4, loaded.size(), "the absent series contributes nothing, the others all of it");
    assertEquals(4, SegVisibilityPolicy.countCrowding(loaded, List.of()));
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
