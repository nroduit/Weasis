/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.PriorityQueue;
import org.opencv.core.CvType;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.opencv.data.PlanarImage;

/**
 * Live wire on a window of the image: from an anchor, the cheapest path to a point of the window
 * runs along the edges the user sees. The image is first normalized to the displayed window /
 * level, then every step is weighted by gradient magnitude, Laplacian and gradient direction
 * (Mortensen and Barrett, <i>Interactive Segmentation with Intelligent Scissors</i>, 1998), with
 * the weights and kernels of the cornerstone3D {@code LivewireScissors} (MIT). The search is lazy:
 * it runs until the requested point is reached and resumes from there for the next one.
 */
public final class EdgeSnapper {

  /** Side of the square window, in source pixels, on which paths are computed. */
  static final int WINDOW = 512;

  /** Distance, in source pixels, within which the cursor is pulled onto a stronger edge. */
  static final int SNAP_RADIUS = 2;

  private static final float GRADIENT_WEIGHT = 0.43f;
  private static final float LAPLACE_WEIGHT = 0.43f;
  private static final float DIRECTION_WEIGHT = 0.11f;
  private static final float LAPLACE_THRESHOLD = 0.33f;
  private static final float SQRT1_2 = (float) Math.sqrt(0.5);
  private static final double TWO_THIRD_PI = 2 / (3 * Math.PI);
  private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
  private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};

  private final MeasurableLayer layer;
  private final PlanarImage image;
  private final double lower;
  private final double upper;
  private Rectangle window;
  private Features features;
  private Search search;
  private Point2D anchor;

  /** Edges are weighted as seen through the pixel range of the whole image. */
  public EdgeSnapper(MeasurableLayer layer) {
    this(
        layer,
        layer == null ? 0 : layer.pixelToRealValue(layer.getPixelMin()),
        layer == null ? 0 : layer.pixelToRealValue(layer.getPixelMax()));
  }

  /**
   * @param lower real value displayed as black
   * @param upper real value displayed as white
   */
  public EdgeSnapper(MeasurableLayer layer, double lower, double upper) {
    this.layer = layer;
    this.image = layer == null || !layer.hasContent() ? null : layer.getSourceRenderedImage();
    this.lower = Math.min(lower, upper);
    this.upper = Math.max(lower, upper);
  }

  /** Edges are weighted as seen through the window / level of the view. */
  public static EdgeSnapper forView(ViewCanvas<?> view) {
    MeasurableLayer layer = view.getMeasurableLayer();
    if (view.getActionValue(ActionW.WINDOW.cmd()) instanceof Number width
        && view.getActionValue(ActionW.LEVEL.cmd()) instanceof Number level
        && width.doubleValue() > 0) {
      double half = width.doubleValue() / 2;
      return new EdgeSnapper(layer, level.doubleValue() - half, level.doubleValue() + half);
    }
    return new EdgeSnapper(layer);
  }

  public boolean isReady() {
    return image != null && image.width() > 2 && image.height() > 2;
  }

  public Point2D getAnchor() {
    return anchor;
  }

  /** Starts a search from this point (image coordinates) over the window around it. */
  public void anchorAt(Point2D imagePoint) {
    anchor = imagePoint;
    Point source = toSourcePixel(imagePoint);
    if (window == null || !core(window).contains(source)) {
      window = windowAround(source, image.width(), image.height());
      float[] gray = normalize(readGray(image, window), layer, lower, upper);
      features = Features.of(gray, window.width, window.height);
    }
    search = new Search(features, source.x - window.x, source.y - window.y);
  }

  /**
   * Path from the anchor along the edges to the cursor, which is pulled onto the strongest edge
   * within {@link #SNAP_RADIUS}; a straight segment when the cursor is outside the window.
   */
  public List<Point2D> pathTo(Point2D cursor) {
    List<Point2D> path = new ArrayList<>();
    Point target = toSourcePixel(cursor);
    if (anchor == null || search == null || !window.contains(target)) {
      path.add(anchor);
      path.add(cursor);
      return path;
    }
    int end = features.snap(target.x - window.x, target.y - window.y, SNAP_RADIUS);
    int[] previous = search.reach(end);
    List<Point> back = new ArrayList<>();
    for (int index = end; index >= 0; index = previous[index]) {
      back.add(new Point(index % window.width + window.x, index / window.width + window.y));
    }
    path.add(anchor);
    for (int i = back.size() - 2; i >= 0; i--) {
      path.add(fromSourcePixel(back.get(i)));
    }
    return path;
  }

  /** Part of the window far enough from its sides to keep it for a new anchor. */
  private Rectangle core(Rectangle r) {
    int margin = WINDOW / 4;
    int left = r.x == 0 ? 0 : margin;
    int top = r.y == 0 ? 0 : margin;
    int right = r.x + r.width == image.width() ? 0 : margin;
    int bottom = r.y + r.height == image.height() ? 0 : margin;
    return new Rectangle(r.x + left, r.y + top, r.width - left - right, r.height - top - bottom);
  }

  static Rectangle windowAround(Point center, int width, int height) {
    int half = WINDOW / 2;
    int x = Math.clamp(center.x - half, 0, Math.max(0, width - WINDOW));
    int y = Math.clamp(center.y - half, 0, Math.max(0, height - WINDOW));
    return new Rectangle(x, y, Math.min(WINDOW, width), Math.min(WINDOW, height));
  }

  /** Real values mapped to [0, 1] through the displayed range, clamped as on screen. */
  static float[] normalize(float[] gray, MeasurableLayer layer, double lower, double upper) {
    double range = upper - lower;
    float[] normalized = new float[gray.length];
    if (range <= 0) {
      return normalized;
    }
    for (int i = 0; i < gray.length; i++) {
      double real = layer.pixelToRealValue(gray[i]);
      normalized[i] = (float) Math.clamp((real - lower) / range, 0, 1);
    }
    return normalized;
  }

  /** Edge terms of the window; each is a cost in [0, 1], low on an edge. */
  record Features(int w, int h, float[] gradient, float[] laplace, float[] gx, float[] gy) {

    static Features of(float[] gray, int w, int h) {
      float[] gx = new float[w * h];
      float[] gy = new float[w * h];
      float[] gradient = new float[w * h];
      float max = 0;
      for (int y = 0; y < h; y++) {
        for (int x = 0; x < w; x++) {
          int i = y * w + x;
          // Forward differences; the last column and row repeat the previous one
          int ix = x + 1 == w ? i - 1 : i;
          int iy = y + 1 == h ? i - w : i;
          gx[i] = gray[ix + 1] - gray[ix];
          gy[i] = gray[iy] - gray[iy + w];
          gradient[i] = (float) Math.hypot(gx[i], gy[i]);
          max = Math.max(max, gradient[i]);
        }
      }
      for (int i = 0; i < gradient.length; i++) {
        gradient[i] = max <= 0 ? 1 : 1 - gradient[i] / max;
      }
      return new Features(w, h, gradient, laplace(gray, w, h), gx, gy);
    }

    /** 5 x 5 Laplacian of Gaussian, thresholded to drop the clutter: 0 on an edge, 1 elsewhere. */
    private static float[] laplace(float[] g, int w, int h) {
      float[] laplace = new float[w * h];
      Arrays.fill(laplace, 1);
      for (int y = 2; y < h - 2; y++) {
        for (int x = 2; x < w - 2; x++) {
          int i = y * w + x;
          float value =
              g[i - 2 * w]
                  + g[i - w - 1]
                  + 2 * g[i - w]
                  + g[i - w + 1]
                  + g[i - 2]
                  + 2 * g[i - 1]
                  - 16 * g[i]
                  + 2 * g[i + 1]
                  + g[i + 2]
                  + g[i + w - 1]
                  + 2 * g[i + w]
                  + g[i + w + 1]
                  + g[i + 2 * w];
          laplace[i] = value > LAPLACE_THRESHOLD ? 0 : 1;
        }
      }
      return laplace;
    }

    /** Cost of the step from pixel {@code a} to its neighbor {@code b}. */
    float stepCost(int a, int b) {
      boolean straight = a % w == b % w || a / w == b / w;
      float magnitude = straight ? gradient[b] * SQRT1_2 : gradient[b];
      return GRADIENT_WEIGHT * magnitude
          + LAPLACE_WEIGHT * laplace[b]
          + DIRECTION_WEIGHT * direction(a, b, straight);
    }

    /** Low when the step runs along the edge at both pixels, i.e. across their gradients. */
    private float direction(int a, int b, boolean straight) {
      int dx = b % w - a % w;
      int dy = b / w - a / w;
      double lengthA = Math.hypot(gx[a], gy[a]);
      double lengthB = Math.hypot(gx[b], gy[b]);
      double dp = lengthA <= 0 ? 0 : (gy[a] * dx - gx[a] * dy) / lengthA;
      double dq = lengthB <= 0 ? 0 : (gy[b] * dx - gx[b] * dy) / lengthB;
      if (dp < 0) {
        dp = -dp;
        dq = -dq;
      }
      if (!straight) {
        dp *= SQRT1_2;
        dq *= SQRT1_2;
      }
      return (float)
          (TWO_THIRD_PI * (Math.acos(Math.min(dp, 1)) + Math.acos(Math.clamp(dq, -1, 1))));
    }

    /** Index of the strongest edge within the radius, the nearer the better. */
    int snap(int x, int y, int radius) {
      int best = y * w + x;
      float bestCost = 0.8f * gradient[best];
      for (int ny = Math.max(0, y - radius); ny <= Math.min(h - 1, y + radius); ny++) {
        for (int nx = Math.max(0, x - radius); nx <= Math.min(w - 1, x + radius); nx++) {
          float distance = (Math.abs(nx - x) + Math.abs(ny - y)) / (2f * radius);
          float cost = 0.8f * gradient[ny * w + nx] + 0.2f * distance;
          if (cost < bestCost) {
            bestCost = cost;
            best = ny * w + nx;
          }
        }
      }
      return best;
    }
  }

  /** Dijkstra from a seed, advanced only as far as the requested pixels. */
  static final class Search {

    private record Node(float cost, int index) {}

    private final Features features;
    private final float[] cost;
    private final int[] previous;
    private final boolean[] done;
    private final PriorityQueue<Node> queue =
        new PriorityQueue<>((a, b) -> Float.compare(a.cost, b.cost));

    Search(Features features, int seedX, int seedY) {
      this.features = features;
      int n = features.w() * features.h();
      cost = new float[n];
      Arrays.fill(cost, Float.MAX_VALUE);
      previous = new int[n];
      Arrays.fill(previous, -1);
      done = new boolean[n];
      int seed =
          Math.clamp(seedY, 0, features.h() - 1) * features.w()
              + Math.clamp(seedX, 0, features.w() - 1);
      cost[seed] = 0;
      queue.add(new Node(0, seed));
    }

    /** Predecessor of every pixel reached so far, the target included; {@code -1} at the seed. */
    int[] reach(int target) {
      int w = features.w();
      int h = features.h();
      while (!done[target] && !queue.isEmpty()) {
        int i = queue.poll().index();
        if (done[i]) {
          continue;
        }
        done[i] = true;
        int x = i % w;
        int y = i / w;
        for (int k = 0; k < 8; k++) {
          int nx = x + DX[k];
          int ny = y + DY[k];
          if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
            continue;
          }
          int j = ny * w + nx;
          float d = cost[i] + features.stepCost(i, j);
          if (!done[j] && d < cost[j]) {
            cost[j] = d;
            previous[j] = i;
            queue.add(new Node(d, j));
          }
        }
      }
      return previous;
    }
  }

  /** Mean of the channels of every pixel of the window, row by row. */
  static float[] readGray(PlanarImage image, Rectangle window) {
    int channels = Math.max(1, image.channels());
    float[] gray = new float[window.width * window.height];
    int depth = CvType.depth(image.type());
    for (int row = 0; row < window.height; row++) {
      double[] line = readRow(image, depth, window.y + row, window.x, window.width * channels);
      for (int col = 0; col < window.width; col++) {
        double sum = 0;
        for (int c = 0; c < channels; c++) {
          sum += line[col * channels + c];
        }
        gray[row * window.width + col] = (float) (sum / channels);
      }
    }
    return gray;
  }

  private static double[] readRow(PlanarImage image, int depth, int row, int col, int count) {
    double[] values = new double[count];
    switch (depth) {
      case CvType.CV_8U, CvType.CV_8S -> {
        byte[] data = new byte[count];
        image.get(row, col, data);
        for (int i = 0; i < count; i++) {
          values[i] = depth == CvType.CV_8U ? data[i] & 0xff : data[i];
        }
      }
      case CvType.CV_16U, CvType.CV_16S -> {
        short[] data = new short[count];
        image.get(row, col, data);
        for (int i = 0; i < count; i++) {
          values[i] = depth == CvType.CV_16U ? data[i] & 0xffff : data[i];
        }
      }
      case CvType.CV_32S -> {
        int[] data = new int[count];
        image.get(row, col, data);
        for (int i = 0; i < count; i++) {
          values[i] = data[i];
        }
      }
      case CvType.CV_32F -> {
        float[] data = new float[count];
        image.get(row, col, data);
        for (int i = 0; i < count; i++) {
          values[i] = data[i];
        }
      }
      default -> image.get(row, col, values);
    }
    return values;
  }

  private Point toSourcePixel(Point2D p) {
    Point2D s = new Point2D.Double(p.getX(), p.getY());
    AffineTransform transform = layer.getShapeTransform();
    if (transform != null) {
      transform.transform(s, s);
    }
    Point offset = layer.getOffset();
    if (offset != null) {
      s.setLocation(s.getX() - offset.getX(), s.getY() - offset.getY());
    }
    return new Point(
        Math.clamp((int) Math.round(s.getX()), 0, image.width() - 1),
        Math.clamp((int) Math.round(s.getY()), 0, image.height() - 1));
  }

  private Point2D fromSourcePixel(Point p) {
    Point2D s = new Point2D.Double(p.x, p.y);
    Point offset = layer.getOffset();
    if (offset != null) {
      s.setLocation(s.getX() + offset.getX(), s.getY() + offset.getY());
    }
    AffineTransform transform = layer.getShapeTransform();
    if (transform != null) {
      try {
        transform.inverseTransform(s, s);
      } catch (NoninvertibleTransformException e) {
        // Keep the source coordinates
      }
    }
    return s;
  }
}
