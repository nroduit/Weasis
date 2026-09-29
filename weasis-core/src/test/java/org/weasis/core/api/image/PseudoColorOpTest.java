/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PseudoColorOpTest {

  private static final ColorMap RAMP =
      ColorMap.builder("Ramp").stop(0, Color.BLACK).stop(1, Color.RED).build();

  @BeforeAll
  static void loadOpenCV() {
    assumeTrue(tryLoadOpenCV(), "OpenCV native library unavailable, skipping");
  }

  @Test
  void eight_bit_source_uses_the_byte_table() throws Exception {
    ImageCV src = new ImageCV(1, 3, CvType.CV_8UC1);
    src.put(0, 0, new byte[] {0, (byte) 128, (byte) 255});

    byte[] pixels = run(src, ColorMapCompiler.toByteLut(RAMP), false, 8);

    assertArrayEquals(
        new byte[] {0, 0, 0, 0, 0, (byte) 128, 0, 0, (byte) 255}, pixels, "BGR, red ramps");
  }

  @Test
  void sixteen_bit_source_is_indexed_over_the_declared_bits() throws Exception {
    ImageCV src = new ImageCV(1, 3, CvType.CV_16UC1);
    src.put(0, 0, new short[] {0, 2048, 4095});

    byte[] pixels = run(src, ColorMapCompiler.toByteLut(RAMP), false, 12);
    byte[] inverted = run(src, ColorMapCompiler.toByteLut(RAMP), true, 12);

    assertAll(
        () -> assertArrayEquals(new byte[] {0, 0, 0, 0, 0, (byte) 128, 0, 0, (byte) 255}, pixels),
        () ->
            assertArrayEquals(
                new byte[] {0, 0, (byte) 255, 0, 0, 127, 0, 0, 0}, inverted, "reversed"));
  }

  @Test
  void sixteen_bit_source_with_a_plain_table_is_upsampled() throws Exception {
    ImageCV src = new ImageCV(1, 2, CvType.CV_16UC1);
    src.put(0, 0, new short[] {0, 4095});

    byte[] pixels = run(src, ColorLut.RED.getByteLut(), false, 12);

    assertArrayEquals(new byte[] {0, 0, 0, 0, 0, (byte) 255}, pixels);
  }

  @Test
  void sixteen_bit_source_with_the_identity_lut_becomes_8_bit_gray() throws Exception {
    ImageCV src = new ImageCV(1, 3, CvType.CV_16UC1);
    src.put(0, 0, new short[] {0, 2048, 4095});

    PlanarImage out = process(src, ColorLut.IMAGE.getByteLut(), false, 12);
    PlanarImage inverted = process(src, ColorLut.IMAGE.getByteLut(), true, 12);
    byte[] gray = new byte[3];
    out.toMat().get(0, 0, gray);
    byte[] neg = new byte[3];
    inverted.toMat().get(0, 0, neg);

    assertAll(
        () -> assertEquals(CvType.CV_8UC1, out.type()),
        () -> assertArrayEquals(new byte[] {0, (byte) 128, (byte) 255}, gray),
        () -> assertArrayEquals(new byte[] {(byte) 255, 127, 0}, neg));
  }

  @Test
  void no_lut_returns_the_source() throws Exception {
    ImageCV src = new ImageCV(1, 1, CvType.CV_16UC1);
    assertSame(src, process(src, null, false, 12));
  }

  @Test
  void color_source_is_not_mapped_again() throws Exception {
    ImageCV src = new ImageCV(1, 1, CvType.CV_8UC3);
    src.put(0, 0, new byte[] {10, 20, 30});

    PlanarImage inverted = process(src, ColorMapCompiler.toByteLut(RAMP), true, 8);
    byte[] pixels = new byte[3];
    inverted.toMat().get(0, 0, pixels);

    assertAll(
        () -> assertSame(src, process(src, ColorMapCompiler.toByteLut(RAMP), false, 8)),
        () -> assertArrayEquals(new byte[] {(byte) 245, (byte) 235, (byte) 225}, pixels));
  }

  private static byte[] run(ImageCV src, ByteLut lut, boolean invert, int bits) throws Exception {
    PlanarImage out = process(src, lut, invert, bits);
    assertEquals(CvType.CV_8UC3, out.type());
    byte[] pixels = new byte[src.width() * 3];
    out.toMat().get(0, 0, pixels);
    return pixels;
  }

  private static PlanarImage process(ImageCV src, ByteLut lut, boolean invert, int bits)
      throws Exception {
    PseudoColorOp op = new PseudoColorOp();
    op.setParam(Param.INPUT_IMG, src);
    op.setParam(PseudoColorOp.P_LUT, lut);
    op.setParam(PseudoColorOp.P_LUT_INVERSE, invert);
    op.setParam(PseudoColorOp.P_INPUT_BITS, bits);
    op.process();
    return (PlanarImage) op.getParam(Param.OUTPUT_IMG);
  }

  /**
   * Loads {@code opencv_java} from a sibling {@code weasis-opencv-core-*} module's build output.
   */
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
