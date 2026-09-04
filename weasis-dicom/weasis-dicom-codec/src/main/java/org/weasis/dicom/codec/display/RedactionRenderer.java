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

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Arrays;
import java.util.List;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.weasis.dicom.codec.Redaction.Mask;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageAnalyzer;

/**
 * Burns redaction regions into pixels: each closed shape is filled as drawn, not by its bounding
 * box, with the median color of the pixels just around it, so the region blends with its
 * surroundings. The work is confined to the region's neighborhood and works on any depth and
 * channel count the display or export pipeline produces.
 */
public final class RedactionRenderer {

  /** Width in pixels of the ring around a region whose median fills it. */
  static final int RING_WIDTH = 4;

  private RedactionRenderer() {}

  /** A copy of {@code source} with every region of {@code mask} burned; the source is untouched. */
  public static ImageCV burn(Mat source, Mask mask) {
    ImageCV result = new ImageCV();
    source.copyTo(result);
    if (mask != null) {
      mask.shapes().forEach(shape -> burnRegion(result, shape));
    }
    return result;
  }

  private static void burnRegion(Mat image, Shape shape) {
    Rectangle area = shape.getBounds();
    area.grow(RING_WIDTH, RING_WIDTH);
    area = area.intersection(new Rectangle(0, 0, image.cols(), image.rows()));
    if (area.isEmpty()) {
      return;
    }
    Mat roi = image.submat(new Rect(area.x, area.y, area.width, area.height));
    Mat inside = Mat.zeros(roi.size(), CvType.CV_8UC1);
    List<MatOfPoint> contours = ImageAnalyzer.transformShapeToContour(shape, true);
    try {
      Imgproc.fillPoly(
          inside, contours, new Scalar(255), Imgproc.LINE_8, 0, new Point(-area.x, -area.y));
      if (Core.countNonZero(inside) > 0) {
        roi.setTo(backgroundMedian(roi, inside), inside);
      }
    } finally {
      contours.forEach(Mat::release);
      inside.release();
      roi.release();
    }
  }

  /**
   * The median of the ring of pixels just outside the region, per channel. A region covering the
   * whole image has no ring; its own median still removes every detail.
   */
  static Scalar backgroundMedian(Mat roi, Mat inside) {
    Mat dilated = new Mat();
    Mat ring = new Mat();
    Mat kernel =
        Imgproc.getStructuringElement(
            Imgproc.MORPH_ELLIPSE, new Size(2.0 * RING_WIDTH + 1, 2.0 * RING_WIDTH + 1));
    try {
      Imgproc.dilate(inside, dilated, kernel);
      Core.subtract(dilated, inside, ring);
      return medianPerChannel(roi, Core.countNonZero(ring) > 0 ? ring : inside);
    } finally {
      dilated.release();
      ring.release();
      kernel.release();
    }
  }

  private static Scalar medianPerChannel(Mat roi, Mat sampleMask) {
    int depth = roi.depth();
    return depth == CvType.CV_8U || depth == CvType.CV_16U
        ? medianFromHistogram(roi, sampleMask)
        : medianFromSamples(roi, sampleMask);
  }

  // Integer levels: the masked histogram of a channel holds its sorted samples, with no copy
  private static Scalar medianFromHistogram(Mat roi, Mat sampleMask) {
    int levels = roi.depth() == CvType.CV_8U ? 1 << 8 : 1 << 16;
    double[] median = new double[Math.min(roi.channels(), 4)];
    Mat hist = new Mat();
    try {
      float[] counts = new float[levels];
      for (int c = 0; c < median.length; c++) {
        Imgproc.calcHist(
            List.of(roi),
            new MatOfInt(c),
            sampleMask,
            hist,
            new MatOfInt(levels),
            new MatOfFloat(0, levels));
        hist.get(0, 0, counts);
        median[c] = median(counts);
      }
    } finally {
      hist.release();
    }
    return new Scalar(median);
  }

  /** The median of the samples counted per level: the mean of the two middle ones when even. */
  static double median(float[] countPerLevel) {
    long total = 0;
    for (float count : countPerLevel) {
      total += (long) count;
    }
    if (total == 0) {
      return 0;
    }
    long upperRank = total / 2;
    long lowerRank = total % 2 == 1 ? upperRank : upperRank - 1;
    int lower = -1;
    long seen = 0;
    for (int level = 0; level < countPerLevel.length; level++) {
      seen += (long) countPerLevel[level];
      if (lower < 0 && seen > lowerRank) {
        lower = level;
      }
      if (seen > upperRank) {
        return (lower + level) / 2.0;
      }
    }
    return lower;
  }

  private static Scalar medianFromSamples(Mat roi, Mat sampleMask) {
    int channels = roi.channels();
    Mat values = new Mat();
    roi.convertTo(values, CvType.CV_64FC(channels));
    double[] data = new double[(int) values.total() * channels];
    values.get(0, 0, data);
    values.release();
    byte[] selected = new byte[(int) sampleMask.total()];
    sampleMask.get(0, 0, selected);

    int count = Core.countNonZero(sampleMask);
    double[][] samples = new double[channels][count];
    int n = 0;
    for (int i = 0; i < selected.length; i++) {
      if (selected[i] != 0) {
        for (int c = 0; c < channels; c++) {
          samples[c][n] = data[i * channels + c];
        }
        n++;
      }
    }
    double[] median = new double[Math.min(channels, 4)];
    for (int c = 0; c < median.length; c++) {
      median[c] = median(samples[c]);
    }
    return new Scalar(median);
  }

  /** The median of {@code values}, which are sorted in place. */
  static double median(double[] values) {
    if (values.length == 0) {
      return 0;
    }
    Arrays.sort(values);
    int middle = values.length / 2;
    return values.length % 2 == 1 ? values[middle] : (values[middle - 1] + values[middle]) / 2;
  }
}
