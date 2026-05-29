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
 * Tests the {@link TagW} anonymization flag. The flag is consulted at display time by {@code
 * TagView.getFormattedText}, {@code InfoLayer.drawInfo}, and {@code DicomFieldsView}: when the
 * global "anonymize" toggle is on and a tag's anonymizationType equals 1, the tag's value is
 * suppressed from on-screen rendering.
 *
 * <p>The flag itself is the load-bearing piece for display-time PHI suppression; a regression here
 * would silently expose patient data in screenshots, screen recordings, or shared sessions.
 */
class TagWAnonymizationTest {

  @Test
  void newTag_defaultAnonymizationTypeIsZero() {
    TagW tag = new TagW("AnonTagDefault", TagType.STRING);

    assertEquals(0, tag.getAnonymizationType(), "new tags must default to display-allowed");
  }

  @Test
  void setAnonymizationType_roundTripPreservesValue() {
    TagW tag = new TagW("AnonTagRoundtrip", TagType.STRING);

    tag.setAnonymizationType(1);

    assertEquals(1, tag.getAnonymizationType());
  }

  @Test
  void setAnonymizationType_zeroDisablesSuppression() {
    TagW tag = new TagW("AnonTagToggle", TagType.STRING);
    tag.setAnonymizationType(1);

    tag.setAnonymizationType(0);

    assertEquals(0, tag.getAnonymizationType(), "must be re-settable back to 0");
  }

  @Test
  void setAnonymizationType_perInstanceNotShared() {
    // Critical: two separate tags must carry independent flags, otherwise toggling the profile
    // for PatientName would also affect Modality (and vice versa).
    TagW tagA = new TagW("AnonTagInstanceA", TagType.STRING);
    TagW tagB = new TagW("AnonTagInstanceB", TagType.STRING);

    tagA.setAnonymizationType(1);

    assertEquals(1, tagA.getAnonymizationType());
    assertEquals(0, tagB.getAnonymizationType(), "flag is per-instance");
  }

  @Test
  void setAnonymizationType_doesNotAffectEquality() {
    // equals/hashCode must be stable under flag changes; otherwise tag maps and tag sets break
    // every time the anonymization profile is toggled.
    TagW tagA = new TagW(99001, "AnonEqualsA", TagType.STRING);
    TagW tagB = new TagW(99001, "AnonEqualsA", TagType.STRING);

    tagA.setAnonymizationType(1); // flag differs

    assertEquals(tagA, tagB, "equality is by id+keyword, not flag");
    assertEquals(tagA.hashCode(), tagB.hashCode(), "hashCode stable under flag");
  }

  @Test
  void setAnonymizationType_distinctTagsRemainDistinct() {
    TagW tagA = new TagW(99002, "AnonDistinctA", TagType.STRING);
    TagW tagB = new TagW(99003, "AnonDistinctB", TagType.STRING);
    tagA.setAnonymizationType(1);
    tagB.setAnonymizationType(1);

    assertNotEquals(tagA, tagB);
  }
}
