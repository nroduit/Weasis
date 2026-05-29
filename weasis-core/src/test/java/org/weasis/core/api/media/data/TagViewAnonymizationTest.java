/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

/**
 * Tests {@link TagView#getFormattedText(boolean, TagReadable...)} — the display-time PHI
 * suppression path used by InfoLayer overlays and DICOM-field views.
 *
 * <p>Contract: when {@code anonymize=true} and a tag's {@code anonymizationType == 1}, the tag is
 * skipped (its value never reaches the formatted output). When {@code anonymize=false} the flag is
 * ignored. Multi-tag {@link TagView} acts as an ordered fallback list: the first tag with a
 * non-empty value wins.
 */
class TagViewAnonymizationTest {

  // Use stable IDs to avoid colliding with built-in TagW instances; values aren't part of
  // the public TagW dictionary so the IDs only need to be unique within this test.
  private final TagW phiTag = new TagW(990_101, "PhiTag", TagType.STRING);
  private final TagW phiTag2 = new TagW(990_102, "PhiTag2", TagType.STRING);
  private final TagW nonPhiTag = new TagW(990_103, "NonPhiTag", TagType.STRING);

  @AfterEach
  void resetFlags() {
    // Tests below mutate the per-instance anonymization flag; reset so tests in this file remain
    // independent and don't leak state to other tests that share the static TagW instances.
    phiTag.setAnonymizationType(0);
    phiTag2.setAnonymizationType(0);
    nonPhiTag.setAnonymizationType(0);
  }

  // -- anonymize=false: flag is ignored -------------------------------------

  @Test
  void getFormattedText_anonymizeFalseReturnsValueEvenForFlaggedTag() {
    phiTag.setAnonymizationType(1);
    TagView view = new TagView(phiTag);
    TagReadable readable = readableWith(phiTag, "Jane Doe");

    String result = view.getFormattedText(false, readable);

    assertEquals("Jane Doe", result, "anonymize=false bypasses the suppression check");
  }

  // -- anonymize=true: flagged tag is suppressed ----------------------------

  @Test
  void getFormattedText_anonymizeTrueSuppressesFlaggedTag() {
    phiTag.setAnonymizationType(1);
    TagView view = new TagView(phiTag);
    TagReadable readable = readableWith(phiTag, "Jane Doe");

    String result = view.getFormattedText(true, readable);

    assertEquals(
        "",
        result,
        "anonymize=true with anonymizationType=1 must suppress the value (return empty)");
  }

  @Test
  void getFormattedText_anonymizeTrueReturnsValueForNonFlaggedTag() {
    nonPhiTag.setAnonymizationType(0); // explicit for documentation
    TagView view = new TagView(nonPhiTag);
    TagReadable readable = readableWith(nonPhiTag, "CT");

    String result = view.getFormattedText(true, readable);

    assertEquals("CT", result, "non-PHI tag is shown even when anonymize=true");
  }

  // -- Multi-tag fallback: flag is evaluated per tag, not per view ---------

  @Test
  void getFormattedText_multiTagFallbackSkipsFlaggedAndReturnsNextValue() {
    // View lists phiTag (flagged) first, then nonPhiTag (not flagged). With anonymize=true the
    // first is skipped and the loop falls through to the second.
    phiTag.setAnonymizationType(1);
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", nonPhiTag, "CT");

    String result = view.getFormattedText(true, readable);

    assertEquals("CT", result, "loop must fall through to the next non-flagged tag");
  }

  @Test
  void getFormattedText_multiTagAllFlaggedReturnsEmpty() {
    phiTag.setAnonymizationType(1);
    phiTag2.setAnonymizationType(1);
    TagView view = new TagView(phiTag, phiTag2);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", phiTag2, "Smith");

    String result = view.getFormattedText(true, readable);

    assertEquals("", result, "no fallback available -> empty string");
  }

  @Test
  void getFormattedText_multiTagReturnsFirstNonEmptyValueWhenNotAnonymizing() {
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", nonPhiTag, "CT");

    String result = view.getFormattedText(false, readable);

    assertEquals("Jane Doe", result, "without anonymize, first non-empty tag wins");
  }

  @Test
  void getFormattedText_multiTagSkipsTagWithEmptyValue() {
    // phiTag has no value (empty string), nonPhiTag does. With anonymize=false the empty value
    // is skipped via StringUtil.hasText and the loop continues.
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "", nonPhiTag, "CT");

    String result = view.getFormattedText(false, readable);

    assertEquals("CT", result);
  }

  @Test
  void getFormattedText_noMatchingValueReturnsEmpty() {
    // None of the taggables hold the tag's value.
    TagView view = new TagView(phiTag);
    TagReadable readable = mock(TagReadable.class);
    lenient().when(readable.containTagKey(any(TagW.class))).thenReturn(false);
    lenient().when(readable.getTagValue(any(TagW.class))).thenReturn(null);

    String result = view.getFormattedText(false, readable);

    assertEquals("", result);
  }

  // -- containsTag (sanity: identity respects equals, not anonymization) ---

  @Test
  void containsTag_remainsConsistentAfterFlagChange() {
    TagView view = new TagView(phiTag);

    phiTag.setAnonymizationType(1);

    org.junit.jupiter.api.Assertions.assertTrue(
        view.containsTag(phiTag), "containsTag uses equality (id+keyword), not flag");
    assertNotEquals(0, phiTag.getAnonymizationType(), "sanity: flag is set");
  }

  // -- helpers --------------------------------------------------------------

  private static TagReadable readableWith(TagW tag, Object value) {
    TagReadable r = mock(TagReadable.class);
    lenient().when(r.getTagValue(tag)).thenReturn(value);
    lenient().when(r.containTagKey(tag)).thenReturn(true);
    return r;
  }

  private static TagReadable readableWith2(TagW tag1, Object value1, TagW tag2, Object value2) {
    TagReadable r = mock(TagReadable.class);
    lenient().when(r.getTagValue(tag1)).thenReturn(value1);
    lenient().when(r.getTagValue(tag2)).thenReturn(value2);
    lenient().when(r.containTagKey(tag1)).thenReturn(true);
    lenient().when(r.containTagKey(tag2)).thenReturn(true);
    return r;
  }
}
