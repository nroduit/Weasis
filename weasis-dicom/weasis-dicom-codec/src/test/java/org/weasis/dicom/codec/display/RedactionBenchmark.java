/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.dicom.codec.Redaction.Mask;

/**
 * Cost of burning a redaction mask, paid on every pass of the display chain. Not part of the normal
 * test run (the class name does not match the Surefire pattern); run with {@code -Dtest=
 * RedactionBenchmark -Dbench.run=true -Dbench.opencv=<path of libopencv_java>}.
 */
class RedactionBenchmark {

  @Test
  void baseline() {
    assumeTrue(Boolean.getBoolean("bench.run"), "bench.run is not set");
    System.load(System.getProperty("bench.opencv"));

    measure("US 1024 × 768 BGR, header strip 1024 × 60", 1024, 768, CvType.CV_8UC3, 0, 0, 1024, 60);
    measure(
        "CR 3000 × 3000 gray, region 1000 × 1000",
        3000,
        3000,
        CvType.CV_8UC1,
        500,
        500,
        1000,
        1000);
    measure("2000 × 2000 gray, whole image", 2000, 2000, CvType.CV_8UC1, 0, 0, 2000, 2000);
  }

  private static void measure(
      String label, int width, int height, int type, int x, int y, int w, int h) {
    Mat image = new Mat(height, width, type);
    Core.randu(image, 0, 255);
    var mask = new Mask(List.of(new Rectangle(x, y, w, h)));
    for (int i = 0; i < 3; i++) {
      RedactionRenderer.burn(image, mask).release();
    }
    double[] times = new double[10];
    for (int i = 0; i < times.length; i++) {
      long start = System.nanoTime();
      RedactionRenderer.burn(image, mask).release();
      times[i] = (System.nanoTime() - start) / 1e6;
    }
    Arrays.sort(times);
    System.out.println(
        String.format(Locale.ROOT, "| %s | %.2f | %.2f |", label, times[5], times[9]));
    image.release();
  }
}
