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
 * Tests {@link ReferencedImage} — the persisted image reference used in graphics serialization.
 *
 * <p>Each instance carries:
 *
 * <ul>
 *   <li>a UUID identifying the source image (inherited from {@code DefaultUUID})
 *   <li>a list of frame indices (zero-based; DICOM Frame = index + 1)
 * </ul>
 *
 * <p>An off-by-one bug in frame mapping would silently anchor a graphic to the wrong frame in a
 * multi-frame DICOM (cine, US, MR diffusion stack). The setFrames-null path is exercised because
 * JAXB-deserialized "no frames" attribute produces a null list before the setter normalises.
 */
class ReferencedImageTest {

  @Test
  void defaultConstructor_generatesUuidAndEmptyFramesList() {
    ReferencedImage img = new ReferencedImage();

    assertAll(
        () -> assertNotNull(img.getUuid()),
        () -> assertNotNull(img.getFrames(), "frames list never null"),
        () -> assertTrue(img.getFrames().isEmpty()));
  }

  @Test
  void uuidConstructor_setsUuidAndEmptyFramesList() {
    ReferencedImage img = new ReferencedImage("img-uuid-1");

    assertAll(
        () -> assertEquals("img-uuid-1", img.getUuid()),
        () -> assertTrue(img.getFrames().isEmpty()));
  }

  @Test
  void uuidAndFramesConstructor_preservesBoth() {
    ReferencedImage img = new ReferencedImage("img-uuid-1", List.of(0, 1, 5));

    assertAll(
        () -> assertEquals("img-uuid-1", img.getUuid()),
        () -> assertEquals(List.of(0, 1, 5), img.getFrames()));
  }

  @Test
  void setFrames_nullReplacesWithEmptyMutableList() {
    // JAXB-deserialised "no frames" XML attribute can leave the list null; the setter must
    // coerce it so downstream graphic anchoring code can iterate without NPE.
    ReferencedImage img = new ReferencedImage("img-uuid-1", List.of(0, 1));

    img.setFrames(null);

    assertAll(
        () -> assertNotNull(img.getFrames()),
        () -> assertTrue(img.getFrames().isEmpty()),
        () -> {
          // Returned list must be mutable so further graphic edits can extend it.
          List<Integer> frames = img.getFrames();
          frames.add(7);
          assertEquals(List.of(7), img.getFrames());
        });
  }

  @Test
  void setFrames_preservesProvidedListOrder() {
    // Frame order matters for cine-stack annotations: a measurement attached to frames [3, 5]
    // must NOT silently sort to [5, 3] or vice versa.
    ReferencedImage img = new ReferencedImage("img-uuid-1");
    List<Integer> frames = new ArrayList<>();
    frames.add(5);
    frames.add(3);

    img.setFrames(frames);

    assertEquals(List.of(5, 3), img.getFrames(), "insertion order preserved");
  }

  @Test
  void getFrames_returnedListIsMutable() {
    ReferencedImage img = new ReferencedImage("img-uuid-1");

    img.getFrames().add(42);

    assertEquals(List.of(42), img.getFrames());
  }

  @Test
  void frameIndexZeroIsValid() {
    // Frame 0 is the first DICOM frame (index 0 → DICOM frame 1, per the class javadoc).
    // The accessor must NOT special-case 0 as "no frame".
    ReferencedImage img = new ReferencedImage("img-uuid-1", List.of(0));

    assertEquals(List.of(0), img.getFrames());
  }

  @Test
  void equality_sameUuidSameClass_equal() {
    ReferencedImage a = new ReferencedImage("img-uuid-1");
    ReferencedImage b = new ReferencedImage("img-uuid-1", List.of(0, 1));

    // Inherited equals is UUID-based; frame lists do not affect identity equality.
    assertEquals(a, b);
  }
}
