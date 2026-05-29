/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link ReferencedSeries} — the persisted series reference used by graphics serialization
 * (presentation states, key-image notes). Its job is to carry a stable series UUID and an ordered
 * list of referenced images; a null images list must NOT propagate as null because callers iterate
 * the list during deserialization.
 */
class ReferencedSeriesTest {

  @Test
  void defaultConstructor_generatesUuidAndEmptyImagesList() {
    ReferencedSeries s = new ReferencedSeries();

    assertAll(
        () -> assertNotNull(s.getUuid(), "UUID auto-generated"),
        () -> assertNotNull(s.getImages(), "images list never null"),
        () -> assertTrue(s.getImages().isEmpty()));
  }

  @Test
  void uuidConstructor_setsUuidAndStillEmptyList() {
    ReferencedSeries s = new ReferencedSeries("series-uuid-1");

    assertAll(
        () -> assertEquals("series-uuid-1", s.getUuid()),
        () -> assertTrue(s.getImages().isEmpty()));
  }

  @Test
  void uuidAndImagesConstructor_preservesBoth() {
    List<ReferencedImage> images = new ArrayList<>();
    images.add(new ReferencedImage("img-1"));
    images.add(new ReferencedImage("img-2"));

    ReferencedSeries s = new ReferencedSeries("series-uuid-1", images);

    assertAll(
        () -> assertEquals("series-uuid-1", s.getUuid()),
        () -> assertEquals(2, s.getImages().size()),
        () -> assertEquals("img-1", s.getImages().get(0).getUuid()),
        () -> assertEquals("img-2", s.getImages().get(1).getUuid()));
  }

  @Test
  void setImages_nullReplacesWithEmptyList() {
    // Critical for deserialization: if a saved presentation state has no <image> children, the
    // JAXB-set list may be null. The setter must coerce that to an empty mutable list so the
    // caller can iterate without NPE.
    ReferencedSeries s = new ReferencedSeries("series-uuid-1");
    s.setImages(new ArrayList<>(List.of(new ReferencedImage("img-1"))));

    s.setImages(null);

    assertAll(
        () -> assertNotNull(s.getImages(), "null replaced by empty list"),
        () -> assertTrue(s.getImages().isEmpty()));
  }

  @Test
  void setImages_preservesProvidedList() {
    ReferencedSeries s = new ReferencedSeries("series-uuid-1");
    List<ReferencedImage> images = new ArrayList<>();
    images.add(new ReferencedImage("img-X"));

    s.setImages(images);

    assertAll(
        () -> assertEquals(1, s.getImages().size()),
        () -> assertEquals("img-X", s.getImages().get(0).getUuid()));
  }

  @Test
  void getImages_returnedListIsMutable() {
    // Graphic serialization adds to this list incrementally — it must be mutable, not an
    // unmodifiable view returned by the getter.
    ReferencedSeries s = new ReferencedSeries("series-uuid-1");

    s.getImages().add(new ReferencedImage("img-1"));

    assertEquals(1, s.getImages().size());
  }

  @Test
  void equality_sameUuidSameClass_equal() {
    // ReferencedSeries inherits DefaultUUID.equals — same UUID + same class -> equal even if
    // images list differs (graphic state vs. metadata identity are separate concerns).
    ReferencedSeries a = new ReferencedSeries("series-uuid-1");
    ReferencedSeries b = new ReferencedSeries("series-uuid-1");
    b.setImages(new ArrayList<>(List.of(new ReferencedImage("img-1"))));

    assertAll(() -> assertEquals(a, b), () -> assertEquals(a.hashCode(), b.hashCode()));
  }
}
