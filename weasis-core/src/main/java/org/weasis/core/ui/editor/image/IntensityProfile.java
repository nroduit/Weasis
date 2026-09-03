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
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.opencv.data.PlanarImage;

/** Pixel values along an open path, one sample per image pixel of path length. */
public final class IntensityProfile {

  private IntensityProfile() {}

  /** An open measurement whose handle points are its path: a line or a polyline. */
  public static boolean isOpenPath(Graphic graphic) {
    return (graphic instanceof LineGraphic || graphic instanceof PolylineGraphic)
        && !(graphic instanceof GraphicArea)
        && graphic.getLayerType() == LayerType.MEASURE
        && Boolean.TRUE.equals(graphic.isGraphicComplete())
        && graphic.getPts().size() > 1;
  }

  /**
   * The samples of a path.
   *
   * @param values real value of each sampled pixel, {@code NaN} outside the image
   * @param positions centre of each sampled pixel, in the coordinates of the graphics
   */
  public record Samples(double[] values, List<Point2D> positions) {

    static final Samples EMPTY = new Samples(new double[0], List.of());

    public int size() {
      return values.length;
    }

    /** Length of the path from its start to a sample, in the coordinates of the graphics. */
    public double distanceTo(int index) {
      double distance = 0;
      for (int i = 1; i <= Math.min(index, positions.size() - 1); i++) {
        distance += positions.get(i - 1).distance(positions.get(i));
      }
      return distance;
    }
  }

  /**
   * Real values sampled every pixel from the first to the last point through every vertex; {@code
   * NaN} outside the image, empty when the layer has no content.
   */
  public static double[] sample(List<Point2D> path, MeasurableLayer layer) {
    return samples(path, layer).values();
  }

  /** Values and positions sampled every pixel through every vertex of the path. */
  public static Samples samples(List<Point2D> path, MeasurableLayer layer) {
    if (layer == null || !layer.hasContent() || path == null || path.size() < 2) {
      return Samples.EMPTY;
    }
    PlanarImage image = layer.getSourceRenderedImage();
    if (image == null) {
      return Samples.EMPTY;
    }
    List<Double> values = new ArrayList<>();
    List<Point2D> positions = new ArrayList<>();
    for (int s = 0; s < path.size() - 1; s++) {
      Point2D a = toImage(layer, path.get(s));
      Point2D b = toImage(layer, path.get(s + 1));
      if (a == null || b == null) {
        continue;
      }
      boolean last = s == path.size() - 2;
      int count = Math.max(2, (int) Math.ceil(a.distance(b)) + 1);
      for (int i = 0; i < (last ? count : count - 1); i++) {
        double t = i / (double) (count - 1);
        int col = (int) Math.round(a.getX() + (b.getX() - a.getX()) * t);
        int row = (int) Math.round(a.getY() + (b.getY() - a.getY()) * t);
        values.add(valueAt(image, layer, row, col));
        positions.add(fromImage(layer, col + 0.5, row + 0.5));
      }
    }
    return new Samples(
        values.stream().mapToDouble(Double::doubleValue).toArray(), List.copyOf(positions));
  }

  /** Inverse of {@link #toImage}: a position of the source image as a position of the graphics. */
  private static Point2D fromImage(MeasurableLayer layer, double x, double y) {
    Point2D result = new Point2D.Double(x, y);
    Point offset = layer.getOffset();
    if (offset != null) {
      result.setLocation(x + offset.getX(), y + offset.getY());
    }
    AffineTransform transform = layer.getShapeTransform();
    if (transform != null) {
      try {
        transform.inverseTransform(result, result);
      } catch (NoninvertibleTransformException e) {
        // Keep the source coordinates
      }
    }
    return result;
  }

  private static Point2D toImage(MeasurableLayer layer, Point2D p) {
    if (p == null) {
      return null;
    }
    Point2D result = new Point2D.Double(p.getX(), p.getY());
    AffineTransform transform = layer.getShapeTransform();
    if (transform != null) {
      transform.transform(result, result);
    }
    Point offset = layer.getOffset();
    if (offset != null) {
      result.setLocation(result.getX() - offset.getX(), result.getY() - offset.getY());
    }
    return result;
  }

  private static double valueAt(PlanarImage image, MeasurableLayer layer, int row, int col) {
    if (row < 0 || col < 0 || row >= image.height() || col >= image.width()) {
      return Double.NaN;
    }
    double[] channels = image.get(row, col);
    if (channels == null || channels.length == 0) {
      return Double.NaN;
    }
    double sum = 0;
    for (double c : channels) {
      sum += c;
    }
    return layer.pixelToRealValue(sum / channels.length);
  }
}
