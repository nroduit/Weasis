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

import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.seg.Segment;

/** Traces the exposed pixel edges of a binary mask, preserving holes and single pixels. */
final class MaskContours {
  private MaskContours() {}

  static List<Segment> build(Mat mask) {
    Mat binary = new Mat();
    try {
      Core.compare(mask, Scalar.all(0), binary, Core.CMP_NE);
      byte[] pixels = new byte[Math.multiplyExact(mask.cols(), mask.rows())];
      binary.get(0, 0, pixels);
      return build(pixels, mask.cols(), mask.rows());
    } finally {
      binary.release();
    }
  }

  static List<Segment> build(byte[] pixels, int width, int height) {
    // Directions are clockwise: east, south, west, north. Foreground stays on the right,
    // giving holes the opposite winding for SegContour's WIND_NON_ZERO path.
    Map<Long, Integer> edges = new LinkedHashMap<>();
    long stride = (long) width + 1;
    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {
        int i = y * width + x;
        if (pixels[i] == 0) continue;
        long vertex = y * stride + x;
        if (y == 0 || pixels[i - width] == 0) add(edges, vertex, 0);
        if (x == width - 1 || pixels[i + 1] == 0) add(edges, vertex + 1, 1);
        if (y == height - 1 || pixels[i + width] == 0) add(edges, vertex + stride + 1, 2);
        if (x == 0 || pixels[i - 1] == 0) add(edges, vertex + stride, 3);
      }
    }

    List<Segment> contours = new ArrayList<>();
    long[] steps = {1, stride, -1, -stride};
    while (!edges.isEmpty()) {
      long start = edges.keySet().iterator().next();
      long vertex = start;
      int direction = Integer.numberOfTrailingZeros(edges.get(start));
      Segment contour = new Segment();
      contour.add(point(start, stride));
      do {
        int remaining = edges.get(vertex) & ~(1 << direction);
        if (remaining == 0) edges.remove(vertex);
        else edges.put(vertex, remaining);
        vertex += steps[direction];
        if (vertex == start) break;
        int outgoing = edges.get(vertex);
        // At a diagonal contact, turn right to keep the two foreground pixels separate.
        int next = (direction + 1) % 4;
        if ((outgoing & (1 << next)) == 0) {
          next = direction;
          if ((outgoing & (1 << next)) == 0) next = (direction + 3) % 4;
        }
        if (next != direction) contour.add(point(vertex, stride));
        direction = next;
      } while (true);
      contours.add(contour);
    }
    return contours;
  }

  private static void add(Map<Long, Integer> edges, long vertex, int direction) {
    edges.merge(vertex, 1 << direction, (a, b) -> a | b);
  }

  private static Point2D point(long vertex, long stride) {
    // DICOM and OpenCV pixel centres have integer coordinates. The viewer uses the same
    // affine transform for the source image and graphics, so boundaries lie half a pixel away.
    return new Point2D.Double(vertex % stride - 0.5, vertex / stride - 0.5);
  }
}
