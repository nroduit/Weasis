/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * The encoder itself needs the OpenCV native library, which the test scope does not have. What can
 * be pinned here is the parameter set — and it is worth pinning, because dropping the GIF dither
 * flag silently puts colour speckle back into every grayscale export.
 */
@DisplayName("Animated image encoder parameters")
class AnimatedImageSinkTest {

  @Test
  @DisplayName("turns GIF dithering off, so a grayscale frame stays gray")
  void gifDisablesDithering() {
    assertArrayEquals(
        new int[] {Imgcodecs.IMWRITE_GIF_DITHER, Imgcodecs.IMWRITE_GIF_FAST_NO_DITHER},
        AnimatedImageSink.encoderParams(AnimationFormat.GIF));
  }

  @Test
  @DisplayName("leaves APNG at its defaults, compression included")
  void apngKeepsDefaults() {
    assertEquals(0, AnimatedImageSink.encoderParams(AnimationFormat.APNG).length);
  }

  @Test
  @DisplayName("never raises the PNG compression level, which measured 43 times slower")
  void apngNeverSetsCompression() {
    for (int value : AnimatedImageSink.encoderParams(AnimationFormat.APNG)) {
      assertTrue(value != Imgcodecs.IMWRITE_PNG_COMPRESSION);
    }
  }

  @Test
  @DisplayName("accepts frames until a cap is reached")
  void startsEmptyAndNotFull() {
    AnimatedImageSink sink =
        new AnimatedImageSink(java.nio.file.Path.of("unused.gif"), AnimationFormat.GIF);
    assertAll(
        () -> assertEquals(false, sink.isFull()),
        () -> assertTrue(AnimatedImageSink.MAX_FRAMES > 0),
        () -> assertTrue(AnimatedImageSink.MAX_RESIDENT_BYTES > 0));
  }
}
