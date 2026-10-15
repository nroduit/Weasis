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

import java.awt.Rectangle;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.weasis.opencv.seg.Segment;

/**
 * Traces the exposed pixel edges of a binary mask, preserving holes and single pixels.
 *
 * <p>Pixel centres have integer coordinates, as in DICOM and in the OpenCV rendering of the image,
 * so the edges of pixel {@code (x, y)} lie at {@code x ± 0.5} and {@code y ± 0.5}. Outer boundaries
 * run clockwise on screen and holes counter-clockwise, as separate outlines: filled with the
 * non-zero winding rule of {@code SegContour}, they cover exactly the mask pixels. Diagonally
 * touching pixels get separate outlines.
 *
 * <p>Only the bounding box of the mask is visited and the boundary is kept in primitive arrays of
 * that size, so the cost does not depend on the frame size.
 */
final class MaskContours {

  /** Mask membership of the pixel at {@code (x, y)}. */
  @FunctionalInterface
  interface PixelTest {
    boolean inside(int x, int y);
  }

  // Directions: 0 = east, 1 = south, 2 = west, 3 = north (a +1 step turns clockwise on screen)
  private static final int[] DX = {1, 0, -1, 0};
  private static final int[] DY = {0, 1, 0, -1};

  private MaskContours() {}

  /** Traces the non-zero pixels of a single-channel mask. */
  static List<Segment> build(Mat mask) {
    int width = mask.cols();
    int height = mask.rows();
    byte[] pixels = new byte[Math.multiplyExact(width, height)];
    if (mask.depth() == CvType.CV_8U || mask.depth() == CvType.CV_8S) {
      mask.get(0, 0, pixels);
    } else {
      Mat binary = new Mat();
      try {
        Core.compare(mask, Scalar.all(0), binary, Core.CMP_NE);
        binary.get(0, 0, pixels);
      } finally {
        binary.release();
      }
    }
    return build(pixels, width, height);
  }

  /** Traces the non-zero pixels of a row-major mask. */
  static List<Segment> build(byte[] pixels, int width, int height) {
    Rectangle bounds = nonZeroBounds(pixels, width, height);
    if (bounds == null) {
      return List.of();
    }
    return build((x, y) -> pixels[y * width + x] != 0, bounds);
  }

  /**
   * Traces the pixels accepted by {@code test}. Only the pixels within {@code bounds} are tested;
   * the pixels outside are background.
   */
  static List<Segment> build(PixelTest test, Rectangle bounds) {
    if (bounds == null || bounds.isEmpty()) {
      return List.of();
    }
    return traceLoops(buildEdges(test, bounds), bounds);
  }

  private static Rectangle nonZeroBounds(byte[] pixels, int width, int height) {
    int minX = width;
    int minY = height;
    int maxX = -1;
    int maxY = -1;
    for (int y = 0; y < height; y++) {
      int row = y * width;
      for (int x = 0; x < width; x++) {
        if (pixels[row + x] != 0) {
          minX = Math.min(minX, x);
          maxX = Math.max(maxX, x);
          minY = Math.min(minY, y);
          maxY = y;
        }
      }
    }
    return maxX < 0 ? null : new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
  }

  /**
   * Returns, for each pixel corner of {@code bounds}, the bit set of the boundary edges leaving it.
   * Every edge keeps the foreground on its right-hand side.
   */
  private static byte[] buildEdges(PixelTest test, Rectangle bounds) {
    int bw = bounds.width;
    int bh = bounds.height;
    // Membership with a one-pixel background frame, so neighbours need no bound check
    int mw = bw + 2;
    boolean[] in = new boolean[mw * (bh + 2)];
    for (int y = 0; y < bh; y++) {
      int row = (y + 1) * mw + 1;
      for (int x = 0; x < bw; x++) {
        in[row + x] = test.inside(bounds.x + x, bounds.y + y);
      }
    }

    int cw = bw + 1;
    byte[] outEdges = new byte[cw * (bh + 1)];
    for (int y = 0; y < bh; y++) {
      int row = (y + 1) * mw + 1;
      for (int x = 0; x < bw; x++) {
        int m = row + x;
        if (!in[m]) {
          continue;
        }
        int corner = y * cw + x;
        if (!in[m - mw]) {
          outEdges[corner] |= 1; // top edge, eastwards
        }
        if (!in[m + 1]) {
          outEdges[corner + 1] |= 2; // right edge, southwards
        }
        if (!in[m + mw]) {
          outEdges[corner + cw + 1] |= 4; // bottom edge, westwards
        }
        if (!in[m - 1]) {
          outEdges[corner + cw] |= 8; // left edge, northwards
        }
      }
    }
    return outEdges;
  }

  private static List<Segment> traceLoops(byte[] outEdges, Rectangle bounds) {
    int cw = bounds.width + 1;
    List<Segment> contours = new ArrayList<>();
    int[] xy = new int[64];
    for (int start = 0; start < outEdges.length; start++) {
      while (outEdges[start] != 0) {
        int startDir = Integer.numberOfTrailingZeros(outEdges[start]);
        int n = 0;
        xy[n++] = start % cw;
        xy[n++] = start / cw;

        int x = xy[0];
        int y = xy[1];
        int dir = startDir;
        while (true) {
          outEdges[y * cw + x] &= (byte) ~(1 << dir);
          x += DX[dir];
          y += DY[dir];
          int corner = y * cw + x;
          // The first edge is still pending at the start corner: it closes the loop
          boolean atStart = corner == start;
          int edges = atStart ? outEdges[corner] | (1 << startDir) : outEdges[corner];
          int next = nextDirection(edges, dir);
          if (atStart && next == startDir) {
            if (dir == startDir) {
              // The start corner lies inside a straight run: not a vertex
              System.arraycopy(xy, 2, xy, 0, n - 2);
              n -= 2;
            }
            break;
          }
          if (next != dir) {
            if (n + 2 > xy.length) {
              xy = Arrays.copyOf(xy, xy.length * 2);
            }
            xy[n++] = x;
            xy[n++] = y;
          }
          dir = next;
        }
        contours.add(toSegment(xy, n, bounds));
      }
    }
    return contours;
  }

  // Prefers the right turn, which keeps diagonally touching pixels in separate outlines
  private static int nextDirection(int edges, int arrival) {
    int right = (arrival + 1) & 3;
    if ((edges & (1 << right)) != 0) {
      return right;
    }
    if ((edges & (1 << arrival)) != 0) {
      return arrival;
    }
    return (arrival + 3) & 3;
  }

  private static Segment toSegment(int[] xy, int n, Rectangle bounds) {
    // Corner (i, j) of the bounding box is the top-left corner of pixel (x + i, y + j), whose
    // centre has integer coordinates
    double x0 = bounds.x - 0.5;
    double y0 = bounds.y - 0.5;
    Segment segment = new Segment();
    segment.ensureCapacity(n / 2);
    for (int i = 0; i < n; i += 2) {
      segment.add(new Point2D.Double(x0 + xy[i], y0 + xy[i + 1]));
    }
    return segment;
  }
}
