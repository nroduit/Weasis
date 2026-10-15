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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Color;
import java.awt.Shape;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;

class MaskContoursNativeTest {
  @BeforeAll
  static void loadOpenCV() throws IOException {
    Path modules = Path.of("..", "..", "weasis-opencv").toAbsolutePath().normalize();
    boolean loaded = false;
    if (Files.isDirectory(modules)) {
      try (var files = Files.walk(modules, 4)) {
        for (Path library :
            files
                .filter(
                    p -> p.getFileName().toString().equals(System.mapLibraryName("opencv_java")))
                .toList()) {
          try {
            System.load(library.toString());
            loaded = true;
            break;
          } catch (UnsatisfiedLinkError ignored) {
            // Try the next platform's build output.
          }
        }
      }
    }
    assumeTrue(loaded, "OpenCV native library unavailable");
  }

  @Test
  void pixelEdgesFollowNearestNeighbourImageZoom() {
    Mat mask = new Mat(3, 3, CvType.CV_16UC1);
    Mat transform = new Mat(2, 3, CvType.CV_64FC1);
    Mat zoomed = new Mat();
    try {
      // A hole, diagonal contacts and different non-zero values, including unsigned 16-bit.
      mask.put(0, 0, new short[] {1, 1, 0, 1, 0, 1, 0, 1, (short) 65535});
      SegContour contour = new SegContour("test", MaskContours.build(mask));
      contour.setAttributes(new SegRegion<>(1, "test", Color.RED));
      Shape shape = contour.getSegGraphic().getShape();
      transform.put(0, 0, new double[] {8, 0, 0, 0, 8, 0});
      Imgproc.warpAffine(mask, zoomed, transform, new Size(24, 24), Imgproc.INTER_NEAREST);
      short[] pixels = new short[24 * 24];
      zoomed.get(0, 0, pixels);
      for (int y = 0; y < 24; y++) {
        for (int x = 0; x < 24; x++) {
          // Exclude exact boundaries, where nearest-neighbour tie-breaking is arbitrary.
          if (x % 8 == 4 || y % 8 == 4) continue;
          assertEquals(
              pixels[y * 24 + x] != 0,
              shape.contains(x / 8.0, y / 8.0),
              "zoomed pixel " + x + "," + y);
        }
      }
    } finally {
      zoomed.release();
      transform.release();
      mask.release();
    }
  }
}
