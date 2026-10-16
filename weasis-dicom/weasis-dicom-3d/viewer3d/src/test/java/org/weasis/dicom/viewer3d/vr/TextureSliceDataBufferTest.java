/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.lang.foreign.ValueLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;

@DisplayNameGeneration(ReplaceUnderscores.class)
class TextureSliceDataBufferTest {

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(loadNativeLibrary(), "OpenCV native library unavailable, skipping");
  }

  // The library of the weasis-opencv module of this platform, once it has been built
  private static boolean loadNativeLibrary() {
    Path dir = Path.of(System.getProperty("user.dir")).toAbsolutePath();
    while (dir != null && !Files.isDirectory(dir.resolve("weasis-opencv"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      return false;
    }
    try (Stream<Path> found = Files.walk(dir.resolve("weasis-opencv"), 5)) {
      for (Path lib : found.filter(p -> p.endsWith("libopencv_java.so")).toList()) {
        try {
          System.load(lib.toString());
          return true;
        } catch (Throwable ignore) {
          // another architecture: try the next one
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }

  private static short[] pixels(Mat mat) {
    short[] data = new short[(int) mat.total()];
    mat.get(0, 0, data);
    return data;
  }

  @Test
  void continuous_slices_are_read_in_place() {
    Mat first = new Mat(3, 5, CvType.CV_16UC1);
    Mat second = new Mat(3, 5, CvType.CV_16UC1);
    Core.randu(first, 0, 65535);
    Core.randu(second, 0, 65535);

    TextureSliceDataBuffer buffer = TextureSliceDataBuffer.toImageData(List.of(first, second));

    assertEquals(CvType.CV_16UC1, buffer.cvType());
    assertTrue(buffer.copies().isEmpty());
    assertEquals(first.dataAddr(), buffer.slices().get(0).address());
    assertArrayEquals(pixels(second), buffer.slices().get(1).toArray(ValueLayout.JAVA_SHORT));
  }

  @Test
  void a_slice_that_is_not_continuous_is_copied_once() {
    Mat parent = new Mat(6, 8, CvType.CV_16UC1);
    Core.randu(parent, 0, 65535);
    Mat roi = parent.submat(new Rect(1, 1, 5, 3));

    TextureSliceDataBuffer buffer = TextureSliceDataBuffer.toImageData(List.of(roi));

    assertEquals(1, buffer.copies().size());
    assertArrayEquals(
        pixels(roi.clone()), buffer.slices().getFirst().toArray(ValueLayout.JAVA_SHORT));
    buffer.releaseMemory();
    assertTrue(buffer.copies().getFirst().empty());
  }

  @Test
  void an_unsupported_depth_is_refused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> TextureSliceDataBuffer.toImageData(List.of(new Mat(2, 2, CvType.CV_64FC1))));
  }
}
