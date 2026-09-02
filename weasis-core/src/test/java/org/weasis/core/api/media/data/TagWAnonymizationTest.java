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

import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagW.TagType;

/**
 * Tests the {@link TagW} category. It is what a {@link MaskingProfile} resolves an action from, so
 * a regression here would silently expose patient data in screenshots, screen recordings, or shared
 * sessions — or hide clinical information that every profile means to keep.
 */
class TagWAnonymizationTest {

  @Test
  void newTag_defaultCategoryIsOther() {
    TagW tag = new TagW("AnonTagDefault", TagType.STRING);

    assertEquals(TagCategory.OTHER, tag.getCategory(), "new tags must default to display-allowed");
  }

  @Test
  void setCategory_roundTripPreservesValue() {
    TagW tag = new TagW("AnonTagRoundtrip", TagType.STRING);

    tag.setCategory(TagCategory.FREE_TEXT);

    assertEquals(TagCategory.FREE_TEXT, tag.getCategory());
  }

  @Test
  void setCategory_otherDisablesSuppression() {
    TagW tag = new TagW("AnonTagToggle", TagType.STRING);
    tag.setCategory(TagCategory.FREE_TEXT);

    tag.setCategory(TagCategory.OTHER);

    assertEquals(TagCategory.OTHER, tag.getCategory(), "must be re-settable back to OTHER");
  }

  @Test
  void setCategory_perInstanceNotShared() {
    // Critical: two separate tags must carry independent flags, otherwise toggling the profile
    // for PatientName would also affect Modality (and vice versa).
    TagW tagA = new TagW("AnonTagInstanceA", TagType.STRING);
    TagW tagB = new TagW("AnonTagInstanceB", TagType.STRING);

    tagA.setCategory(TagCategory.FREE_TEXT);

    assertEquals(TagCategory.FREE_TEXT, tagA.getCategory());
    assertEquals(TagCategory.OTHER, tagB.getCategory(), "the category is per-instance");
  }

  @Test
  void setCategory_doesNotAffectEquality() {
    // equals/hashCode must be stable under category changes; otherwise tag maps and tag sets break
    // every time the anonymization profile is toggled.
    TagW tagA = new TagW(99001, "AnonEqualsA", TagType.STRING);
    TagW tagB = new TagW(99001, "AnonEqualsA", TagType.STRING);

    tagA.setCategory(TagCategory.FREE_TEXT); // category differs

    assertEquals(tagA, tagB, "equality is by id+keyword, not category");
    assertEquals(tagA.hashCode(), tagB.hashCode(), "hashCode stable under category");
  }

  @Test
  void setCategory_distinctTagsRemainDistinct() {
    TagW tagA = new TagW(99002, "AnonDistinctA", TagType.STRING);
    TagW tagB = new TagW(99003, "AnonDistinctB", TagType.STRING);
    tagA.setCategory(TagCategory.FREE_TEXT);
    tagB.setCategory(TagCategory.FREE_TEXT);

    assertNotEquals(tagA, tagB);
  }

  @Test
  void setCategory_nullFallsBackToOther() {
    TagW tag = new TagW("AnonTagNull", TagType.STRING);
    tag.setCategory(TagCategory.FREE_TEXT);

    tag.setCategory(null);

    assertEquals(TagCategory.OTHER, tag.getCategory());
  }
}
