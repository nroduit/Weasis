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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;

@DisplayName("PixelAttestation")
class PixelAttestationTest {

  private static ImageElement image(String burnedIn, PixelAttestation own) {
    ImageElement image = mock(ImageElement.class);
    lenient().when(image.getTagValue(TagD.get(Tag.BurnedInAnnotation))).thenReturn(burnedIn);
    lenient().when(image.getTagValue(TagW.PixelAttestation)).thenReturn(own);
    return image;
  }

  private static MediaSeries<?> series(PixelAttestation attestation) {
    MediaSeries<?> series = mock(MediaSeries.class);
    lenient().when(series.getTagValue(TagW.PixelAttestation)).thenReturn(attestation);
    return series;
  }

  @Test
  @DisplayName("nothing attested covers nothing")
  void noAttestation() {
    assertFalse(PixelAttestation.covers(image(null, null), series(null)));
  }

  @Test
  @DisplayName("an attestation on the image or on its series covers a silent image")
  void attestationCoversUnknown() {
    PixelAttestation review = PixelAttestation.of("nicolas", "dicom-conformance 2026-09-25");
    assertAll(
        () -> assertTrue(PixelAttestation.covers(image(null, review), series(null))),
        () -> assertTrue(PixelAttestation.covers(image(null, null), series(review))),
        () -> assertEquals(review, PixelAttestation.effective(image(null, null), series(review))));
  }

  @Test
  @DisplayName("an image that declares burned-in identity is never covered")
  void declaredBurnInWins() {
    PixelAttestation review = PixelAttestation.of("nicolas", null);
    assertFalse(PixelAttestation.covers(image("YES", review), series(review)));
  }

  @Test
  @DisplayName("an attestation names who reviewed, and drops a blank reason")
  void namesTheReviewer() {
    assertAll(
        () -> assertThrows(IllegalArgumentException.class, () -> PixelAttestation.of(" ", "x")),
        () -> assertNull(PixelAttestation.of("nicolas", "  ").reason()),
        () -> assertEquals("nicolas", PixelAttestation.of(" nicolas ", null).by()));
  }
}
