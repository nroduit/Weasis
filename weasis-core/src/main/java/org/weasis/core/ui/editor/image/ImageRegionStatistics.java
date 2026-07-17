/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_ENTROPY;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_KURTOSIS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MAX;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MEDIAN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_MIN;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_PIXELS;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_SKEW;
import static org.weasis.core.ui.model.utils.ImageStatistics.IMAGE_STD;

import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.DataBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.HistogramData.Model;
import org.weasis.core.ui.model.graphic.GraphicArea;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.util.MathUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.ByteLut;

public class ImageRegionStatistics {
  private static final Logger LOGGER = LoggerFactory.getLogger(ImageRegionStatistics.class);

  private ImageRegionStatistics() {}

  public static List<Mat> prepareInputImages(GraphicArea graphic, MeasurableLayer layer) {
    if (layer != null && layer.hasContent()) {
      Shape shape = null;
      if (graphic != null) {
        if (!graphic.isShapeValid()) {
          return Collections.emptyList();
        }
        shape = graphic.getShape();
      }

      PlanarImage image = layer.getSourceRenderedImage();
      if (image == null) {
        return Collections.emptyList();
      }

      Shape roi = getShape(layer, shape);

      // Always apply pixel padding (deactivate in Display has no effect in statistics)
      Integer paddingValue = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingValue"));
      Integer paddingLimit = (Integer) layer.getSourceTagValue(TagW.get("PixelPaddingRangeLimit"));
      return ImageAnalyzer.getMaskImage(image.toMat(), roi, paddingValue, paddingLimit);
    }
    return Collections.emptyList();
  }

  private static Shape getShape(MeasurableLayer layer, Shape shape) {
    Shape roi = null;
    if (shape != null) {
      AffineTransform transform = layer.getShapeTransform();
      Point offset = layer.getOffset();
      if (offset != null) {
        if (transform == null) {
          transform = AffineTransform.getTranslateInstance(-offset.getX(), -offset.getY());
        } else {
          transform.translate(-offset.getX(), -offset.getY());
        }
      }
      if (transform != null) {
        // Rescale ROI, if needed
        roi = transform.createTransformedShape(shape);
      } else {
        roi = shape;
      }
    }
    return roi;
  }

  public static List<MeasureItem> getImageStatistics(MeasurableLayer layer) {
    return getImageStatistics(null, layer, true);
  }

  public static List<HistogramData> getHistogram(GraphicArea graphic, MeasurableLayer layer) {
    List<Mat> imgPr = prepareInputImages(graphic, layer);
    if (layer != null && layer.hasContent() && imgPr != null && imgPr.size() == 2) {
      return getHistogram(imgPr.get(0), imgPr.get(1), layer);
    }
    return Collections.emptyList();
  }

  public static List<HistogramData> getHistogram(Mat srcImg, Mat mask, MeasurableLayer layer) {
    if (srcImg == null || layer == null || !layer.hasContent()) {
      return Collections.emptyList();
    }

    double pixMin = layer.getPixelMin();
    double pixMax = layer.getPixelMax();

    int channels = srcImg.channels();
    Model colorModel = channels > 1 ? Model.RGB : Model.GRAY;
    int[] selChannels = new int[channels];
    for (int i = 0; i < selChannels.length; i++) {
      selChannels[i] = i;
    }

    List<HistogramData> data = new ArrayList<>();
    int datatype = ImageConversion.convertToDataType(srcImg.type());
    boolean intVal = datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT;
    try {
      int binCount = intVal ? (int) pixMax - (int) pixMin + 1 : 1024;
      List<Mat> histograms =
          HistogramData.computeHistogram(
              srcImg, mask, binCount, selChannels, colorModel, pixMin, pixMax);

      ByteLut[] lut = colorModel.getByteLut();
      DisplayByteLut[] luts = new DisplayByteLut[lut.length];

      for (int i = 0; i < lut.length; i++) {
        luts[i] = new DisplayByteLut(lut[i]);
        Mat h = histograms.get(i);
        float[] histValues = new float[h.rows()];
        h.get(0, 0, histValues);
        data.add(
            new HistogramData(histValues, luts[i], i, colorModel, null, pixMin, pixMax, layer));
      }
    } catch (Exception e) {
      LOGGER.error("Build histogram", e);
    }
    return data;
  }

  public static List<MeasureItem> getImageStatistics(
      GraphicArea graphic, MeasurableLayer layer, boolean releaseEvent) {
    if (layer != null && layer.hasContent()) {
      List<MeasureItem> measVal = new ArrayList<>();
      if (releaseEvent && isOneComputed()) {
        List<Mat> imgPr = prepareInputImages(graphic, layer);
        if (imgPr.size() == 2) {
          Mat srcImg = imgPr.get(0);
          Mat mask = imgPr.get(1);
          RoiPixelStats direct = srcImg.channels() == 1 ? computeRoiPixelStats(srcImg, mask) : null;
          List<HistogramData> hists = getHistogram(srcImg, mask, layer);
          for (int i = 0; i < hists.size(); i++) {
            HistogramData data = hists.get(i);
            Integer bandIndex = hists.size() == 1 ? null : data.getBandIndex();
            measVal.addAll(
                getStatistics(data, bandIndex, i == 0, bandIndex == null ? direct : null));
          }
        }
        for (MeasurableLayer secondary : layer.getSecondaryLayers()) {
          measVal.addAll(getSuvStatistics(graphic, secondary));
        }
      }
      return measVal;
    }

    return Collections.emptyList();
  }

  /** Direct ROI statistics in stored-pixel units (min, max, mean, standard deviation, count). */
  private record RoiPixelStats(double min, double max, double mean, double stdDev, double count) {}

  /**
   * Measures min/max/mean/std/count on the masked ROI pixels via {@link ImageAnalyzer#meanStdDev};
   * values are in stored-pixel units. Returns {@code null} when the ROI selects no pixel or the
   * measurement fails.
   */
  private static RoiPixelStats computeRoiPixelStats(Mat srcImg, Mat mask) {
    try {
      double[][] s = ImageAnalyzer.meanStdDev(srcImg, mask, null, null);
      if (s.length < 5 || s[0].length == 0 || s[4][0] <= 0.0) {
        return null;
      }
      return new RoiPixelStats(s[0][0], s[1][0], s[2][0], s[3][0], s[4][0]);
    } catch (Exception e) {
      LOGGER.error("Compute ROI pixel statistics", e);
      return null;
    }
  }

  /**
   * Computes SUV min/max/mean for {@code petLayer} (a PET overlay sampled on the base layer's pixel
   * grid) within the ROI, labeled with a "PT" extension so they read alongside the base statistics.
   * Returns an empty list when the layer carries no SUV factor or the ROI covers no PET voxel.
   */
  private static List<MeasureItem> getSuvStatistics(GraphicArea graphic, MeasurableLayer petLayer) {
    if (petLayer == null || !petLayer.hasContent()) {
      return Collections.emptyList();
    }
    Double suv = (Double) petLayer.getSourceTagValue(TagW.SuvFactor);
    if (suv == null
        || !(IMAGE_MIN.getComputed() || IMAGE_MAX.getComputed() || IMAGE_MEAN.getComputed())) {
      return Collections.emptyList();
    }
    List<Mat> imgPr = prepareInputImages(graphic, petLayer);
    if (imgPr.size() != 2) {
      return Collections.emptyList();
    }
    Mat mask = imgPr.get(1); // null when the whole image is measured (no ROI shape)
    // The source may be any depth (native PET is CV_16S, the resampled volume CV_32F): read it as
    // float so a single code path handles both. The volume path uses NaN to mark voxels outside it.
    Mat src = new Mat();
    imgPr.get(0).convertTo(src, CvType.CV_32F);
    int rows = src.rows();
    int cols = src.cols();
    float[] values = new float[cols];
    byte[] selected = mask == null ? null : new byte[cols];
    double min = Double.MAX_VALUE;
    double max = -Double.MAX_VALUE;
    double sum = 0.0;
    long count = 0;
    for (int r = 0; r < rows; r++) {
      src.get(r, 0, values);
      if (mask != null) {
        mask.get(r, 0, selected);
      }
      for (int c = 0; c < cols; c++) {
        if ((selected == null || selected[c] != 0) && !Float.isNaN(values[c])) {
          double v = values[c];
          min = Math.min(min, v);
          max = Math.max(max, v);
          sum += v;
          count++;
        }
      }
    }
    src.release();
    if (count == 0) {
      return Collections.emptyList();
    }
    String unit = "SUVbw, g/ml"; // NON-NLS
    String label = petLayer.getStatLabel();
    String ext = " " + (label == null ? "PT" : label); // NON-NLS
    List<MeasureItem> measList = new ArrayList<>(3);
    addSuvMeasure(measList, IMAGE_MIN, ext, petLayer.pixelToRealValue(min) * suv, unit);
    addSuvMeasure(measList, IMAGE_MAX, ext, petLayer.pixelToRealValue(max) * suv, unit);
    addSuvMeasure(measList, IMAGE_MEAN, ext, petLayer.pixelToRealValue(sum / count) * suv, unit);
    return measList;
  }

  private static void addSuvMeasure(
      List<MeasureItem> measList, Measurement measure, String ext, double value, String unit) {
    if (measure.getComputed()) {
      measList.add(new MeasureItem(measure, ext, value, unit));
    }
  }

  private static boolean isOneComputed() {
    return IMAGE_PIXELS.getComputed()
        || IMAGE_MIN.getComputed()
        || IMAGE_MAX.getComputed()
        || IMAGE_MEDIAN.getComputed()
        || IMAGE_MEAN.getComputed()
        || IMAGE_STD.getComputed()
        || IMAGE_SKEW.getComputed()
        || IMAGE_KURTOSIS.getComputed()
        || IMAGE_ENTROPY.getComputed();
  }

  private static void addMeasure(
      List<MeasureItem> measVal,
      Measurement measure,
      Integer channelIndex,
      Double val,
      String unit) {
    if (measure.getComputed()) {
      if (channelIndex == null) {
        measVal.add(new MeasureItem(measure, val, unit));
      } else {
        measVal.add(new MeasureItem(measure, " " + (channelIndex + 1), val, unit));
      }
    }
  }

  public static List<MeasureItem> getStatistics(
      HistogramData data, Integer channelIndex, boolean imagePixels) {
    return getStatistics(data, channelIndex, imagePixels, null);
  }

  private static List<MeasureItem> getStatistics(
      HistogramData data, Integer channelIndex, boolean imagePixels, RoiPixelStats direct) {
    MeasurableLayer layer = data.getLayer();
    if (layer != null && layer.hasContent()) {
      float[] bins = data.getHistValues();
      double offset = data.getPixMin();
      List<MeasureItem> measList = new ArrayList<>();
      double sum = 0;
      double min = Float.MAX_VALUE;
      double max = -Float.MAX_VALUE;
      double mean = 0.0;

      // A single bin means a uniform ROI: keep the factor at 0 so every level maps to the bin
      // value instead of dividing by zero (which would yield an infinite median).
      double binFactor = bins.length > 1 ? (data.getPixMax() - offset) / (bins.length - 1) : 0.0;

      for (int k = 0; k < bins.length; k++) {
        boolean valid = MathUtil.isDifferentFromZero(bins[k]) && bins[k] > 0.0f;
        float val = bins[k];
        double level = layer.pixelToRealValue(k * binFactor + offset);
        if (valid && level < min) {
          min = level;
        }
        if (valid && level > max) {
          max = level;
        }
        sum += val;
        mean += val * level;
      }

      mean /= sum;

      double m2 = 0.0;
      double skew = 0.0;
      double kurtosis = 0.0;
      double entropy = 0.0;
      double log2 = Math.log(2.0);
      for (int k = 0; k < bins.length; k++) {
        double val = bins[k];
        double level = layer.pixelToRealValue(k * binFactor + offset) - mean;
        m2 += val * Math.pow(level, 2);
        skew += val * Math.pow(level, 3);
        kurtosis += val * Math.pow(level, 4);
        double h = val / sum;
        if (MathUtil.isDifferentFromZero(h)) {
          entropy -= h * (Math.log(h) / log2);
        }
      }

      double variance = m2 / (sum - 1); // variance
      double stdev = Math.sqrt(variance);

      if (bins.length > 3 && variance > MathUtil.DOUBLE_EPSILON) {
        skew =
            (sum * skew)
                / ((sum - 1) // NOSONAR the condition above should exclude the division by 0
                    * (sum - 2)
                    * stdev
                    * variance);
        kurtosis =
            (sum * (sum + 1) * kurtosis - 3 * m2 * m2 * (sum - 1))
                / ((sum - 1) * (sum - 2) * (sum - 3) * variance * variance);
      } else {
        skew = 0.0;
        kurtosis = 0.0;
      }
      // Prefer the values measured directly on the ROI pixels (real-value units) when available;
      // otherwise fall back to the histogram-derived estimates.
      double pixelCount = sum;
      double dispMin = min;
      double dispMax = max;
      double dispMean = mean;
      double dispStd = stdev;
      if (direct != null) {
        pixelCount = direct.count();
        double realA = layer.pixelToRealValue(direct.min());
        double realB = layer.pixelToRealValue(direct.max());
        dispMin = Math.min(realA, realB);
        dispMax = Math.max(realA, realB);
        dispMean = layer.pixelToRealValue(direct.mean());
        // Scale the stored-value standard deviation into real-value units (modality LUT slope).
        double scale =
            direct.max() > direct.min()
                ? Math.abs(realB - realA) / (direct.max() - direct.min())
                : 1.0;
        dispStd = direct.stdDev() * scale;
      }

      String unit = layer.getPixelValueUnit();
      if (imagePixels) {
        addMeasure(measList, IMAGE_PIXELS, channelIndex, pixelCount, Unit.PIXEL.getAbbreviation());
      }
      addMeasure(measList, IMAGE_MIN, channelIndex, dispMin, unit);
      addMeasure(measList, IMAGE_MAX, channelIndex, dispMax, unit);
      addMeasure(
          measList,
          IMAGE_MEDIAN,
          channelIndex,
          layer.pixelToRealValue(medianBin(bins, sum / 2.0) * binFactor + offset),
          unit);
      addMeasure(measList, IMAGE_MEAN, channelIndex, dispMean, unit);
      addMeasure(measList, IMAGE_STD, channelIndex, dispStd, null);
      addMeasure(measList, IMAGE_SKEW, channelIndex, skew, null);
      addMeasure(measList, IMAGE_KURTOSIS, channelIndex, kurtosis, null);
      addMeasure(measList, IMAGE_ENTROPY, channelIndex, entropy, null);

      Double suv = (Double) layer.getSourceTagValue(TagW.SuvFactor);
      if (channelIndex == null && Objects.nonNull(suv)) {
        unit = "SUVbw, g/ml"; // NON-NLS
        addMeasure(measList, IMAGE_MIN, null, dispMin * suv, unit);
        addMeasure(measList, IMAGE_MAX, null, dispMax * suv, unit);
        addMeasure(measList, IMAGE_MEAN, null, dispMean * suv, unit);
      }

      return measList;
    }

    return Collections.emptyList();
  }

  public static double medianBin(final float[] bin, double halfEntries) {
    if (bin == null || bin.length < 1) {
      return 0.0;
    } else {
      double sumBin = 0.0;
      double sum;
      for (int i = 0; i < bin.length; i++) {
        sum = sumBin + bin[i];
        // Check if bin crosses halfTotal point
        if (sum >= halfEntries) {
          // Scale linearly across the bin
          double dif = halfEntries - sumBin;
          double frac = 0.0;
          if (bin[i] > 0) {
            frac = (dif) / bin[i];
          }
          return (i + frac);
        }
        sumBin = sum;
      }
    }
    return 0.0;
  }
}
