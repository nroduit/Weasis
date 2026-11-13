/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class GrayLevelsTest {

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library unavailable, skipping");
  }

  @Test
  void single_channel_images_are_their_own_gray_levels() {
    ImageCV src = new ImageCV(2, 2, CvType.CV_16UC1);

    assertSame(src, GrayLevels.of(src));
  }

  @Test
  void gray_stored_as_color_keeps_its_values() {
    ImageCV src = new ImageCV(1, 2, CvType.CV_8UC3);
    src.put(0, 0, new byte[] {7, 7, 7, (byte) 200, (byte) 200, (byte) 200});

    assertArrayEquals(new byte[] {7, (byte) 200}, gray(src));
  }

  @Test
  void color_gives_its_rec_601_luma() {
    ImageCV src = new ImageCV(1, 3, CvType.CV_8UC3);
    // BGR: pure red, pure green, pure blue
    src.put(0, 0, new byte[] {0, 0, (byte) 255, 0, (byte) 255, 0, (byte) 255, 0, 0});

    assertArrayEquals(new byte[] {76, (byte) 150, 29}, gray(src));
  }

  @Test
  void other_color_types_and_null_have_no_gray_levels() {
    assertAll(
        () -> assertNull(GrayLevels.of(new ImageCV(1, 1, CvType.CV_16UC3))),
        () -> assertNull(GrayLevels.of(new ImageCV(1, 1, CvType.CV_8UC4))),
        () -> assertNull(GrayLevels.of(null)));
  }

  private static byte[] gray(ImageCV src) {
    PlanarImage gray = GrayLevels.of(src);
    assertEquals(CvType.CV_8UC1, gray.type());
    byte[] pixels = new byte[src.width()];
    gray.toMat().get(0, 0, pixels);
    return pixels;
  }
}
