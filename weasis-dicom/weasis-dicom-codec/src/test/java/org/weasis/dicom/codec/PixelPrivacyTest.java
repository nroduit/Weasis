/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.TagReadable;

@DisplayName("PixelPrivacy")
class PixelPrivacyTest {

  private static TagReadable readable(String burnedIn, String features) {
    TagReadable t = mock(TagReadable.class);
    lenient().when(t.getTagValue(TagD.get(Tag.BurnedInAnnotation))).thenReturn(burnedIn);
    lenient().when(t.getTagValue(TagD.get(Tag.RecognizableVisualFeatures))).thenReturn(features);
    return t;
  }

  @Test
  @DisplayName("an absent attribute is UNKNOWN, never CLEAN")
  void absentIsUnknown() {
    assertAll(
        () -> assertEquals(PixelPrivacy.UNKNOWN, PixelPrivacy.of(readable(null, null))),
        () -> assertEquals(PixelPrivacy.UNKNOWN, PixelPrivacy.of(null)));
  }

  @Test
  @DisplayName("YES on either attribute means BURNED_IN")
  void yesMeansBurnedIn() {
    assertAll(
        () -> assertEquals(PixelPrivacy.BURNED_IN, PixelPrivacy.of(readable("YES", null))),
        () -> assertEquals(PixelPrivacy.BURNED_IN, PixelPrivacy.of(readable(null, "YES"))),
        () -> assertEquals(PixelPrivacy.BURNED_IN, PixelPrivacy.of(readable("yes", "NO"))),
        () -> assertEquals(PixelPrivacy.BURNED_IN, PixelPrivacy.of(readable(" YES ", null))));
  }

  @Test
  @DisplayName("an explicit NO is CLEAN")
  void noMeansClean() {
    assertAll(
        () -> assertEquals(PixelPrivacy.CLEAN, PixelPrivacy.of(readable("NO", null))),
        () -> assertEquals(PixelPrivacy.CLEAN, PixelPrivacy.of(readable("NO", "NO"))));
  }

  @Test
  @DisplayName("review is required for a declared burn-in whatever the modality")
  void reviewRequiredWhenDeclared() {
    assertTrue(PixelPrivacy.requiresReview(readable("YES", null), "CT"));
  }

  @Test
  @DisplayName("silence on a high-risk modality still requires review")
  void reviewRequiredForUnknownHighRiskModality() {
    assertAll(
        () -> assertTrue(PixelPrivacy.requiresReview(readable(null, null), "US")),
        () -> assertTrue(PixelPrivacy.requiresReview(readable(null, null), "XA")),
        () -> assertTrue(PixelPrivacy.requiresReview(readable(null, null), "SC")));
  }

  @Test
  @DisplayName("silence on a low-risk modality does not")
  void noReviewForUnknownLowRiskModality() {
    assertAll(
        () -> assertFalse(PixelPrivacy.requiresReview(readable(null, null), "CT")),
        () -> assertFalse(PixelPrivacy.requiresReview(readable(null, null), "MR")),
        () -> assertFalse(PixelPrivacy.requiresReview(readable(null, null), null)));
  }

  @Test
  @DisplayName("an explicit NO never requires review")
  void noReviewWhenDeclaredClean() {
    assertFalse(PixelPrivacy.requiresReview(readable("NO", "NO"), "US"));
  }
}
