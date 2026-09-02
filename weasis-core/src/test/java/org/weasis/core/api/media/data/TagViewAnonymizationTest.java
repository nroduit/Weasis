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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

/**
 * Tests {@link TagView#getFormattedText(TagReadable...)} with and without a bound {@link
 * IdentityMask} — the path used by annotation overlays, explorer labels and DICOM-field views.
 *
 * <p>Contract: without a mask every value is shown. Under the display profile a {@link
 * TagCategory#FREE_TEXT} tag is removed and the ordered fallback list moves on to the next tag with
 * a non-empty value.
 */
class TagViewAnonymizationTest {

  // Use stable IDs to avoid colliding with built-in TagW instances; values aren't part of
  // the public TagW dictionary so the IDs only need to be unique within this test.
  private final TagW phiTag = new TagW(990_101, "PhiTag", TagType.STRING);
  private final TagW phiTag2 = new TagW(990_102, "PhiTag2", TagType.STRING);
  private final TagW nonPhiTag = new TagW(990_103, "NonPhiTag", TagType.STRING);

  @AfterEach
  void resetFlags() {
    // Tests below mutate the per-instance category; reset so tests in this file remain
    // independent and don't leak state to other tests that share the static TagW instances.
    phiTag.setCategory(TagCategory.OTHER);
    phiTag2.setCategory(TagCategory.OTHER);
    nonPhiTag.setCategory(TagCategory.OTHER);
  }

  private static String masked(TagView view, TagReadable readable) {
    return IdentityMask.forProfile(MaskingProfile.display())
        .callMasked(() -> view.getFormattedText(readable));
  }

  // -- no mask: every value is shown -------------------------------------------

  @Test
  void getFormattedText_unmaskedReturnsValueEvenForClassifiedTag() {
    phiTag.setCategory(TagCategory.FREE_TEXT);
    TagView view = new TagView(phiTag);
    TagReadable readable = readableWith(phiTag, "Jane Doe");

    assertEquals("Jane Doe", view.getFormattedText(readable), "no mask, no suppression");
  }

  // -- display profile: removed tag is suppressed ------------------------------

  @Test
  void getFormattedText_maskedSuppressesRemovedTag() {
    phiTag.setCategory(TagCategory.FREE_TEXT);
    TagView view = new TagView(phiTag);
    TagReadable readable = readableWith(phiTag, "Jane Doe");

    assertEquals("", masked(view, readable), "a removed tag must not reach the output");
  }

  @Test
  void getFormattedText_maskedReturnsValueForUnclassifiedTag() {
    nonPhiTag.setCategory(TagCategory.OTHER); // explicit for documentation
    TagView view = new TagView(nonPhiTag);
    TagReadable readable = readableWith(nonPhiTag, "CT");

    assertEquals("CT", masked(view, readable), "an unclassified tag is shown under a mask");
  }

  // -- Multi-tag fallback: the action is evaluated per tag, not per view -------

  @Test
  void getFormattedText_multiTagFallbackSkipsRemovedAndReturnsNextValue() {
    phiTag.setCategory(TagCategory.FREE_TEXT);
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", nonPhiTag, "CT");

    assertEquals("CT", masked(view, readable), "loop must fall through to the next tag");
  }

  @Test
  void getFormattedText_multiTagAllRemovedReturnsEmpty() {
    phiTag.setCategory(TagCategory.FREE_TEXT);
    phiTag2.setCategory(TagCategory.FREE_TEXT);
    TagView view = new TagView(phiTag, phiTag2);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", phiTag2, "Smith");

    assertEquals("", masked(view, readable), "no fallback available -> empty string");
  }

  @Test
  void getFormattedText_multiTagReturnsFirstNonEmptyValueWhenUnmasked() {
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "Jane Doe", nonPhiTag, "CT");

    assertEquals("Jane Doe", view.getFormattedText(readable), "first non-empty tag wins");
  }

  @Test
  void getFormattedText_multiTagSkipsTagWithEmptyValue() {
    TagView view = new TagView(phiTag, nonPhiTag);
    TagReadable readable = readableWith2(phiTag, "", nonPhiTag, "CT");

    assertEquals("CT", view.getFormattedText(readable));
  }

  @Test
  void getFormattedText_noMatchingValueReturnsEmpty() {
    TagView view = new TagView(phiTag);
    TagReadable readable = mock(TagReadable.class);
    lenient().when(readable.containTagKey(any(TagW.class))).thenReturn(false);
    lenient().when(readable.getTagValue(any(TagW.class))).thenReturn(null);

    assertEquals("", view.getFormattedText(readable));
  }

  // -- containsTag (sanity: identity respects equals, not classification) -----

  @Test
  void containsTag_remainsConsistentAfterCategoryChange() {
    TagView view = new TagView(phiTag);

    phiTag.setCategory(TagCategory.FREE_TEXT);

    assertTrue(view.containsTag(phiTag), "containsTag uses equality (id+keyword), not category");
    assertNotEquals(TagCategory.OTHER, phiTag.getCategory(), "sanity: the category is set");
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
