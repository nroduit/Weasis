/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.seg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.stream.Stream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

class SegBinaryMaskWorkaroundTest {

  private static final int WIDTH = 13;
  private static final int HEIGHT = 5;

  @Test
  void unpackPreservesNonSquareDisconnectedMasksAcrossFrames() {
    // The two frames deliberately contain non-rectangular islands at unrelated positions. Their
    // total size is not byte-aligned, which catches both a per-row stride and a per-frame offset.
    boolean[][] first = new boolean[HEIGHT][WIDTH];
    first[0][0] = first[0][1] = first[1][0] = first[2][0] = true;
    first[1][7] = first[2][7] = first[2][8] = true;
    first[4][12] = true;
    boolean[][] second = new boolean[HEIGHT][WIDTH];
    second[0][12] = second[1][11] = second[1][12] = second[2][10] = true;
    second[3][2] = second[4][2] = second[4][3] = true;

    byte[] packed = pack(first, second);
    assertArrayEquals(
        flatten(first), SegBinaryMaskWorkaround.unpackFrame(packed, 0, WIDTH, HEIGHT));
    assertArrayEquals(
        flatten(second), SegBinaryMaskWorkaround.unpackFrame(packed, 1, WIDTH, HEIGHT));
  }

  @Test
  void unpackRejectsTruncatedFrame() {
    assertNull(SegBinaryMaskWorkaround.unpackFrame(new byte[1], 0, WIDTH, HEIGHT));
  }

  @Test
  void reDecodeProducesTheSameRasterWhenNativeOpenCvIsAvailable() {
    assumeTrue(loadNativeOpenCV(), "OpenCV native library unavailable");
    boolean[][] mask = new boolean[HEIGHT][WIDTH];
    mask[0][0] = mask[2][8] = mask[4][12] = true;
    Attributes dicom = new Attributes();
    dicom.setInt(Tag.BitsAllocated, VR.US, 1);
    dicom.setInt(Tag.Columns, VR.US, WIDTH);
    dicom.setInt(Tag.Rows, VR.US, HEIGHT);
    dicom.setBytes(Tag.PixelData, VR.OB, pack(mask));
    assertRows(dicom, 0, mask);
  }

  @Test
  void normaliseTransposesOnlyAnExactlySwappedRaster() {
    assumeTrue(loadNativeOpenCV(), "OpenCV native library unavailable");
    ImageCV swapped = new ImageCV(WIDTH, HEIGHT, CvType.CV_8UC1);
    PlanarImage normalized = null;
    try {
      byte[] data = new byte[WIDTH * HEIGHT];
      // Input is WIDTH rows by HEIGHT columns. After the corrective transpose, the foreground
      // must be at (x=9, y=3) in the declared WIDTH×HEIGHT grid.
      data[9 * HEIGHT + 3] = (byte) 255;
      swapped.toMat().put(0, 0, data);
      normalized = SegMaskOrientation.normalize(swapped, WIDTH, HEIGHT, "test-seg");
      assertNotNull(normalized);
      assertEquals(WIDTH, normalized.width());
      assertEquals(HEIGHT, normalized.height());
      byte[] corrected = new byte[WIDTH * HEIGHT];
      normalized.toMat().get(0, 0, corrected);
      assertEquals(255, corrected[3 * WIDTH + 9] & 0xFF);
    } finally {
      if (normalized != null && normalized != swapped) {
        ImageConversion.releasePlanarImage(normalized);
      }
      swapped.release();
    }
  }

  @Test
  void normaliseLeavesAnAlreadyDeclaredRasterUntouched() {
    assumeTrue(loadNativeOpenCV(), "OpenCV native library unavailable");
    ImageCV declared = new ImageCV(HEIGHT, WIDTH, CvType.CV_8UC1);
    try {
      assertSame(declared, SegMaskOrientation.normalize(declared, WIDTH, HEIGHT, "test-seg"));
    } finally {
      declared.release();
    }
  }

  private static void assertRows(Attributes dicom, int frame, boolean[][] expected) {
    PlanarImage decoded = SegBinaryMaskWorkaround.reDecodeFrame(dicom, frame, WIDTH, HEIGHT);
    try {
      assertNotNull(decoded);
      assertEquals(WIDTH, decoded.width());
      assertEquals(HEIGHT, decoded.height());
      byte[] actual = new byte[WIDTH * HEIGHT];
      decoded.toMat().get(0, 0, actual);
      assertArrayEquals(flatten(expected), actual);
    } finally {
      if (decoded != null) {
        ImageConversion.releasePlanarImage(decoded);
      }
    }
  }

  private static byte[] pack(boolean[][]... frames) {
    int bits = Arrays.stream(frames).mapToInt(frame -> frame.length * frame[0].length).sum();
    byte[] packed = new byte[(bits + 7) / 8];
    int bit = 0;
    for (boolean[][] frame : frames) {
      for (boolean[] row : frame) {
        for (boolean pixel : row) {
          if (pixel) {
            packed[bit >> 3] |= (byte) (1 << (bit & 7));
          }
          bit++;
        }
      }
    }
    return packed;
  }

  private static byte[] flatten(boolean[][] mask) {
    byte[] result = new byte[WIDTH * HEIGHT];
    int i = 0;
    for (boolean[] row : mask) {
      for (boolean pixel : row) {
        result[i++] = pixel ? (byte) 255 : 0;
      }
    }
    return result;
  }

  private static boolean loadNativeOpenCV() {
    String os = System.getProperty("os.name", "").toLowerCase();
    String libFile =
        os.contains("win")
            ? "opencv_java.dll"
            : os.contains("mac") ? "libopencv_java.dylib" : "libopencv_java.so";
    Path modules = Paths.get(System.getProperty("user.dir")).getParent().resolve("weasis-opencv");
    if (!Files.isDirectory(modules)) {
      return false;
    }
    try (Stream<Path> dirs = Files.list(modules)) {
      for (Path dir : dirs.filter(Files::isDirectory).toList()) {
        Path library = dir.resolve("target").resolve("classes").resolve(libFile);
        if (!Files.isRegularFile(library)) {
          continue;
        }
        try {
          System.load(library.toAbsolutePath().toString());
          return true;
        } catch (Throwable ignore) {
          // Try the next architecture-specific native module.
        }
      }
    } catch (IOException ignore) {
      // Native tests are not available until the matching OpenCV module is built.
    }
    return false;
  }
}
