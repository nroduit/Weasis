/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import java.text.NumberFormat;
import java.util.Objects;
import org.opencv.core.CvType;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.QuantityKind;
import org.weasis.opencv.data.PlanarImage;

/**
 * Text of a measured value, for the labels on the image and for the measurement table. The number
 * of decimals follows what the value is and how finely the image can resolve it: a length is not
 * shown finer than a tenth of a pixel, a pixel value not finer than the step between two stored
 * values, a ratio always with two decimals. The value itself is never rounded: copies and exports
 * read it from the {@link MeasureItem}.
 */
public final class MeasureFormat {

  /** Decimals chosen per value instead of a fixed number. */
  public static final int AUTO = -1;

  public static final int MAX_DECIMALS = 4;

  private static final int SIGNIFICANT_DIGITS = 4;
  private static final int MOMENT_DIGITS = 3;
  private static final int RATIO_DECIMALS = 2;

  private final MeasurableLayer layer;
  private final double spatialStep;
  private final int decimals;
  private double valueStep = Double.NaN;

  /**
   * @param layer image being measured, {@code null} when unknown
   * @param displayUnit unit the lengths are expressed in
   * @param decimals {@link #AUTO}, or the number of decimals the user asked for
   */
  public MeasureFormat(MeasurableLayer layer, Unit displayUnit, int decimals) {
    this.layer = layer;
    this.decimals = decimals < 0 ? AUTO : Math.min(decimals, MAX_DECIMALS);
    MeasurementsAdapter adapter =
        layer == null || !layer.hasContent() ? null : layer.getMeasurementAdapter(displayUnit);
    this.spatialStep = adapter == null ? 1.0 : adapter.calibrationRatio();
  }

  /** The value of the item as text, without its unit. */
  public String format(MeasureItem item) {
    Object value = item.getValue();
    if (!(value instanceof Number number)) {
      return Objects.toString(value, "");
    }
    double v = number.doubleValue();
    if (Double.isNaN(v) || Double.isInfinite(v)) {
      return String.valueOf(v);
    }
    NumberFormat format = NumberFormat.getNumberInstance();
    // No grouping: "1,234" reads as one point two in half of the locales
    format.setGroupingUsed(false);
    int digits = decimalsOf(item.getMeasurement().getKind(), v, item.getUnit());
    format.setMinimumFractionDigits(digits);
    format.setMaximumFractionDigits(digits);
    return format.format(v);
  }

  int decimalsOf(QuantityKind kind, double value, String unit) {
    if (kind == QuantityKind.COUNT) {
      return 0;
    }
    if (decimals != AUTO) {
      return kind == QuantityKind.RATIO ? Math.max(decimals, RATIO_DECIMALS) : decimals;
    }
    return switch (kind) {
      case COUNT -> 0;
      case ANGLE -> 1;
      case RATIO -> RATIO_DECIMALS;
      case LENGTH, COORDINATE -> lengthDecimals();
      case AREA -> Math.abs(value) < 10 ? lengthDecimals() : Math.max(0, lengthDecimals() - 1);
      case PIXEL_VALUE -> pixelDecimals(value, unit, 0);
      case PIXEL_STATISTIC -> pixelDecimals(value, unit, 1);
      case MOMENT -> significantDecimals(value, MOMENT_DIGITS);
      case OTHER -> significantDecimals(value, SIGNIFICANT_DIGITS);
    };
  }

  /** One digit finer than the pixel, which is what placing a handle can resolve. */
  private int lengthDecimals() {
    int digits = stepDecimals(spatialStep);
    return Math.clamp(spatialStep < 10 ? Math.max(digits, 1) : digits, 0, MAX_DECIMALS);
  }

  /**
   * Limited by the step between two stored values when it is known (none for an integer image
   * without rescale, such as Hounsfield units), and by the significant digits in any case.
   */
  private int pixelDecimals(double value, String unit, int extra) {
    int significant = significantDecimals(value, SIGNIFICANT_DIGITS);
    boolean sameUnit = unit == null || layer == null || unit.equals(layer.getPixelValueUnit());
    double step = sameUnit ? valueStep() : 0.0;
    if (step <= 0) {
      return significant;
    }
    return Math.min(Math.clamp(stepDecimals(step) + (long) extra, 0, MAX_DECIMALS), significant);
  }

  /** Step in real values between two stored values of an integer image, 0 when unknown. */
  private double valueStep() {
    if (Double.isNaN(valueStep)) {
      valueStep = 0.0;
      PlanarImage image =
          layer == null || !layer.hasContent() ? null : layer.getSourceRenderedImage();
      if (image != null && CvType.depth(image.type()) < CvType.CV_32F) {
        double min = layer.getPixelMin();
        valueStep = Math.abs(layer.pixelToRealValue(min + 1) - layer.pixelToRealValue(min));
      }
    }
    return valueStep;
  }

  /** Decimals needed to show a step: 0.7 needs one, 0.05 two, 1 and above none. */
  static int stepDecimals(double step) {
    if (step <= 0 || Double.isNaN(step) || Double.isInfinite(step)) {
      return 1;
    }
    return Math.max(0, (int) Math.ceil(-Math.log10(step) - 1e-9));
  }

  /** Decimals that show the given number of significant digits; the integer part is kept. */
  static int significantDecimals(double value, int digits) {
    if (value == 0) {
      return 0;
    }
    int magnitude = (int) Math.floor(Math.log10(Math.abs(value)));
    return Math.clamp(digits - 1L - magnitude, 0, MAX_DECIMALS + 2);
  }
}
