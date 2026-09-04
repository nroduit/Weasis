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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Random;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.core.api.image.util.ValueHistogram.Bins;
import org.weasis.opencv.data.ImageCV;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ValueHistogramTest {

  @Test
  void values_are_binned_over_the_range_and_outsiders_ignored() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library not available");
    var image = new ImageCV(3, 3, CvType.CV_16SC1);
    image.put(0, 0, new short[] {-1000, -1000, -500, -1, 0, 999, 1000, 1001, -1001});

    Bins bins = ValueHistogram.of(image, -1000, 1000, 4);

    // The upper bound belongs to the last bin
    assertArrayEquals(new double[] {2, 2, 1, 2}, bins.counts());
    image.release();
  }

  @Test
  void every_pixel_type_gives_the_counts_of_a_plain_loop() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library not available");
    int[] values = new int[61 * 47]; // odd size, so a row stride mistake cannot hide
    var random = new Random(11);
    for (int i = 0; i < values.length; i++) {
      values[i] = random.nextInt(256);
    }
    for (int type : new int[] {CvType.CV_8UC1, CvType.CV_16UC1, CvType.CV_16SC1, CvType.CV_32FC1}) {
      var image = new ImageCV(47, 61, CvType.CV_32SC1);
      image.put(0, 0, values);
      image.convertTo(image, type);
      // Edges that fall between and on the integer values, upper bound inside the data
      for (double[] range : new double[][] {{0, 256}, {10, 200}, {-5.5, 131.25}}) {
        int binCount = 37;
        double[] expected = new double[binCount];
        double scale = binCount / (range[1] - range[0]);
        for (int v : values) {
          if (v >= range[0] && v <= range[1]) {
            expected[Math.min(binCount - 1, (int) ((v - range[0]) * scale))]++;
          }
        }

        Bins bins = ValueHistogram.of(image, range[0], range[1], binCount);

        assertArrayEquals(expected, bins.counts(), CvType.typeToString(type) + " " + range[1]);
      }
      image.release();
    }
  }

  @Test
  void color_image_has_no_histogram() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library not available");
    var image = new ImageCV(2, 2, CvType.CV_8UC3);

    assertNull(ValueHistogram.of(image, 0, 255, 8));
    image.release();
  }

  @Test
  void rebinning_spreads_counts_over_the_overlapping_target_bins() {
    Bins full = new Bins(new double[] {4, 8, 0, 2}, 0, 4);

    Bins narrower = full.rebin(1, 3, 4);
    Bins wider = full.rebin(-4, 4, 2);
    Bins outside = full.rebin(10, 12, 2);

    assertAll(
        () -> assertArrayEquals(new double[] {4, 4, 0, 0}, narrower.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 14}, wider.counts(), 1e-9),
        () -> assertArrayEquals(new double[] {0, 0}, outside.counts(), 1e-9),
        () -> assertEquals(14, wider.total(), 1e-9, "no count lost inside the range"),
        () -> assertEquals(1.25, narrower.center(0), 1e-9));
  }
}
