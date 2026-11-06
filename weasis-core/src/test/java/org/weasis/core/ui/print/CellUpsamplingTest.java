/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.geom.Rectangle2D;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpNode.Param;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.cv.OpenCvTestLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

/**
 * The cell image is rendered by the display pipeline at the device resolution: a small image in a
 * large cell is interpolated up, not drawn pixel by pixel and left to the printer.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class CellUpsamplingTest {

  @BeforeAll
  static void loadNatives() {
    assumeTrue(OpenCvTestLoader.tryLoad(), "OpenCV native library not available");
  }

  /** A horizontal ramp of ten gray levels per column. */
  private static ImageCV ramp() {
    ImageCV image = new ImageCV(20, 25, CvType.CV_8UC1);
    byte[] row = new byte[25];
    for (int x = 0; x < 25; x++) {
      row[x] = (byte) (x * 10);
    }
    for (int y = 0; y < 20; y++) {
      image.put(y, 0, row);
    }
    return image;
  }

  private static PlanarImage render(double scale, Interpolation interpolation) throws Exception {
    AffineTransformOp op = new AffineTransformOp();
    op.setParam(Param.INPUT_IMG, ramp());
    op.setParam(AffineTransformOp.P_AFFINE_MATRIX, List.of(scale, 0.0, 0.0, 0.0, scale, 0.0));
    op.setParam(
        AffineTransformOp.P_DST_BOUNDS, new Rectangle2D.Double(0, 0, 25 * scale, 20 * scale));
    op.setParam(AffineTransformOp.P_INTERPOLATION, interpolation);
    op.process();
    return (PlanarImage) op.getParam(Param.OUTPUT_IMG);
  }

  /** The longest run of equal pixels along a row, away from the replicated borders. */
  private static int longestPlateau(PlanarImage image, int y) {
    byte[] row = new byte[image.width()];
    image.get(y, 0, row);
    int margin = 12;
    int longest = 1;
    int run = 1;
    for (int x = margin + 1; x < row.length - margin; x++) {
      run = row[x] == row[x - 1] ? run + 1 : 1;
      longest = Math.max(longest, run);
    }
    return longest;
  }

  @Test
  void a_tenfold_magnification_interpolates_between_the_source_pixels() throws Exception {
    PlanarImage cubic = render(10, Interpolation.BICUBIC);
    PlanarImage nearest = render(10, Interpolation.NEAREST_NEIGHBOUR);
    assertAll(
        () -> assertEquals(250, cubic.width()),
        () -> assertEquals(200, cubic.height()),
        // Nearest neighbour repeats every source pixel ten times: the staircase of #509
        () -> assertTrue(longestPlateau(nearest, 10) >= 10),
        // Bicubic changes value at every pixel along a ramp of ten levels per source pixel
        () -> assertTrue(longestPlateau(cubic, 10) <= 4, "plateau " + longestPlateau(cubic, 10)));
  }

  @Test
  void a_reduction_keeps_the_ramp_monotonic() throws Exception {
    PlanarImage half = render(0.5, Interpolation.BILINEAR);
    byte[] row = new byte[half.width()];
    half.get(5, 0, row);
    for (int x = 1; x < row.length; x++) {
      assertTrue((row[x] & 0xFF) >= (row[x - 1] & 0xFF), "not monotonic at " + x);
    }
    assertEquals(13, half.width());
  }
}
