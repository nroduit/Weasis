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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.WindowOp;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ImageElementWideRenderingTest {

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(tryLoadOpenCV(), "OpenCV native library unavailable, skipping");
  }

  @Test
  void wide_output_maps_the_window_onto_the_index_range() {
    ImageCV src = new ImageCV(1, 4, CvType.CV_16SC1);
    src.put(0, 0, new short[] {-1000, -500, 0, 500});

    PlanarImage out =
        ImageElement.getDefaultRenderedImage(mock(ImageElement.class), src, 1000, 0, 12);
    short[] index = new short[4];
    out.toMat().get(0, 0, index);

    assertEquals(CvType.CV_16UC1, out.type());
    assertArrayEquals(new short[] {0, 0, 2048, 4095}, index);
  }

  @Test
  void color_sources_stay_on_the_byte_path() {
    ImageCV src = new ImageCV(1, 2, CvType.CV_8UC3);
    src.put(0, 0, new byte[] {0, 0, 0, (byte) 255, (byte) 255, (byte) 255});
    ImageElement element = mock(ImageElement.class, CALLS_REAL_METHODS);
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.WINDOW.cmd(), 255.0);
    params.put(ActionW.LEVEL.cmd(), 127.5);
    params.put(WindowOp.P_OUTPUT_BITS, 12);
    params.put(WindowOp.P_OUTPUT_RANGE, new WindowOp.OutputRange(-1000, 2000));

    PlanarImage out = element.getRenderedImage(src, params);

    assertEquals(CvType.CV_8UC3, out.type(), "no 16-bit index image for a color source");
  }

  @Test
  void a_fixed_range_anchors_the_byte_output_too() {
    ImageCV src = new ImageCV(1, 3, CvType.CV_16SC1);
    src.put(0, 0, new short[] {-1000, 500, 2000});
    ImageElement element = mock(ImageElement.class, CALLS_REAL_METHODS);
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.WINDOW.cmd(), 400.0);
    params.put(ActionW.LEVEL.cmd(), 40.0);
    params.put(WindowOp.P_OUTPUT_BITS, 8);
    params.put(WindowOp.P_OUTPUT_RANGE, new WindowOp.OutputRange(-1000, 2000));

    PlanarImage out = element.getRenderedImage(src, params);
    byte[] pixels = new byte[3];
    out.toMat().get(0, 0, pixels);

    assertAll(
        () -> assertEquals(CvType.CV_8UC1, out.type()),
        () -> assertEquals(0, pixels[0] & 0xFF),
        () -> assertTrue(Math.abs((pixels[1] & 0xFF) - 128) <= 1, "mid-range, not the window"),
        () -> assertEquals(255, pixels[2] & 0xFF));
  }

  @Test
  void bits_are_clamped_to_the_16_bit_range() {
    ImageCV src = new ImageCV(1, 2, CvType.CV_16UC1);
    src.put(0, 0, new short[] {0, 100});

    PlanarImage out =
        ImageElement.getDefaultRenderedImage(mock(ImageElement.class), src, 100, 50, 40);
    short[] index = new short[2];
    out.toMat().get(0, 0, index);

    assertArrayEquals(new short[] {0, (short) 65535}, index);
  }

  private static boolean tryLoadOpenCV() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String libFile =
        os.contains("win")
            ? "opencv_java.dll"
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so";
    Path opencvModules =
        Path.of(System.getProperty("user.dir")).getParent().resolve("weasis-opencv");
    if (!Files.isDirectory(opencvModules)) {
      return false;
    }
    try (Stream<Path> dirs = Files.list(opencvModules)) {
      List<Path> candidates =
          dirs.filter(Files::isDirectory)
              .map(d -> d.resolve("target").resolve("classes").resolve(libFile))
              .filter(Files::isRegularFile)
              .toList();
      for (Path lib : candidates) {
        try {
          System.load(lib.toAbsolutePath().toString());
          return true;
        } catch (Throwable ignore) {
          // wrong architecture or incompatible binary: try the next candidate
        }
      }
    } catch (IOException e) {
      return false;
    }
    return false;
  }
}
