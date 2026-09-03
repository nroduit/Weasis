/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;
import org.weasis.core.ui.model.utils.bean.QuantityKind;
import org.weasis.opencv.data.PlanarImage;

/** The decimals shown follow what a value is and what the image can resolve. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasureFormatTest {

  /** Expected text written with a point, in the decimal mark of the running locale. */
  private static String text(String value) {
    return value.replace('.', DecimalFormatSymbols.getInstance().getDecimalSeparator());
  }

  /** A layer whose pixels measure {@code pixelSize} display units, with integer or float data. */
  private static MeasurableLayer layer(double pixelSize, int cvType, double slope) {
    MeasurableLayer layer = mock(MeasurableLayer.class);
    when(layer.hasContent()).thenReturn(true);
    when(layer.getMeasurementAdapter(any()))
        .thenReturn(new MeasurementsAdapter(pixelSize, 0, 0, false, 0, "mm")); // NON-NLS
    when(layer.getPixelValueUnit()).thenReturn("HU"); // NON-NLS
    when(layer.pixelToRealValue(any()))
        .thenAnswer(inv -> ((Number) inv.getArgument(0)).doubleValue() * slope - 1024);
    PlanarImage image = mock(PlanarImage.class);
    when(image.type()).thenReturn(cvType);
    when(layer.getSourceRenderedImage()).thenReturn(image);
    return layer;
  }

  private static MeasureItem item(String key, double value, String unit) {
    return new MeasureItem(new Measurement(key, key, 1, true), value, unit);
  }

  private static MeasureFormat auto(MeasurableLayer layer) {
    return new MeasureFormat(layer, Unit.MILLIMETER, MeasureFormat.AUTO);
  }

  @Test
  void a_length_is_one_digit_finer_than_the_pixel() {
    assertAll(
        () ->
            assertEquals(
                text("12.3"),
                auto(layer(0.7, CvType.CV_16S, 1)).format(item("length", 12.3456, "mm"))),
        () ->
            assertEquals(
                text("12.35"),
                auto(layer(0.05, CvType.CV_16S, 1)).format(item("length", 12.3456, "mm"))),
        () ->
            assertEquals(
                text("1.235"),
                auto(layer(0.007, CvType.CV_16S, 1)).format(item("length", 1.23456, "cm"))),
        () ->
            assertEquals(
                text("12.0"), auto(layer(1.5, CvType.CV_16S, 1)).format(item("length", 12, "mm"))),
        () ->
            assertEquals(
                text("40.0"),
                auto(layer(0.7, CvType.CV_16S, 1)).format(item("center.x", 40, "mm"))));
  }

  @Test
  void an_area_drops_one_digit_unless_it_is_small() {
    MeasureFormat format = auto(layer(0.05, CvType.CV_16S, 1));
    assertAll(
        () -> assertEquals(text("1234.6"), format.format(item("area", 1234.5678, "mm2"))),
        () -> assertEquals(text("3.46"), format.format(item("area", 3.4567, "mm2"))));
  }

  @Test
  void angles_ratios_and_counts_have_their_own_fixed_rules() {
    MeasureFormat format = auto(layer(0.7, CvType.CV_16S, 1));
    assertAll(
        () -> assertEquals(text("47.3"), format.format(item("angle", 47.26, "deg"))),
        () -> assertEquals(text("90.0"), format.format(item("orientation", 90, "deg"))),
        () -> assertEquals(text("0.48"), format.format(item("ratio", 0.4849, null))),
        () -> assertEquals(text("0.50"), format.format(item("ratio", 0.5, null))),
        () -> assertEquals("1520", format.format(item("stats.pixels", 1520.0, "pix"))));
  }

  @Test
  void integer_pixel_values_have_no_decimal_and_their_statistics_one() {
    MeasureFormat format = auto(layer(0.7, CvType.CV_16S, 1));
    assertAll(
        () -> assertEquals("-20", format.format(item("stats.min", -20.0, "HU"))),
        () -> assertEquals(text("35.2"), format.format(item("stats.mean", 35.2468, "HU"))),
        () -> assertEquals(text("12.4"), format.format(item("stats.stdev", 12.3567, null))),
        () -> assertEquals("1035", format.format(item("stats.mean", 1035.2468, "HU"))),
        () -> assertEquals("48210", format.format(item("stats.sum", 48210.0, "HU"))));
  }

  @Test
  void rescaled_and_float_pixel_values_use_significant_digits() {
    MeasureFormat rescaled = auto(layer(0.7, CvType.CV_16S, 0.0023));
    MeasureFormat floating = auto(layer(0.7, CvType.CV_32F, 1));
    assertAll(
        () -> assertEquals(text("1.235"), rescaled.format(item("stats.max", 1.23456, "HU"))),
        () -> assertEquals(text("123.5"), rescaled.format(item("stats.max", 123.456, "HU"))),
        () -> assertEquals("12346", rescaled.format(item("stats.max", 12345.6, "HU"))),
        () -> assertEquals(text("0.003210"), floating.format(item("stats.mean", 0.00321, "HU"))),
        // A value in another unit than the pixels (SUV) is not bound to the stored step
        () ->
            assertEquals(
                text("2.346"),
                auto(layer(0.7, CvType.CV_16S, 1))
                    .format(item("stats.mean", 2.34567, "SUVbw, g/ml")))); // NON-NLS
  }

  @Test
  void moments_and_unknown_kinds_use_significant_digits() {
    MeasureFormat format = auto(null);
    assertAll(
        () -> assertEquals(text("0.0123"), format.format(item("stats.skew", 0.012345, null))),
        () -> assertEquals(text("3.21"), format.format(item("stats.kurtosis", 3.2109, null))),
        () -> assertEquals(text("0.01235"), format.format(item("myplugin.index", 0.012345, null))),
        () -> assertEquals("0", format.format(item("myplugin.index", 0.0, null))));
  }

  @Test
  void a_fixed_number_of_decimals_applies_to_everything_but_counts_and_keeps_ratios_readable() {
    MeasureFormat none = new MeasureFormat(layer(0.05, CvType.CV_16S, 1), Unit.MILLIMETER, 0);
    MeasureFormat three = new MeasureFormat(layer(0.7, CvType.CV_16S, 1), Unit.MILLIMETER, 3);
    assertAll(
        () -> assertEquals("12", none.format(item("length", 12.3456, "mm"))),
        () -> assertEquals(text("0.48"), none.format(item("ratio", 0.4849, null))),
        () -> assertEquals(text("12.346"), three.format(item("length", 12.3456, "mm"))),
        () -> assertEquals(text("0.485"), three.format(item("ratio", 0.4849, null))),
        () -> assertEquals("1520", three.format(item("stats.pixels", 1520.0, "pix"))));
  }

  @Test
  void thousands_are_never_grouped_and_text_values_pass_through() {
    MeasureFormat format = auto(layer(0.7, CvType.CV_16S, 1));
    assertAll(
        () -> assertEquals(text("12345.7"), format.format(item("length", 12345.67, "mm"))),
        () ->
            assertEquals(
                "abc", // NON-NLS
                format.format(
                    new MeasureItem(new Measurement("other", "other", 1, true), "abc", null))));
  }

  @Test
  void keys_tell_the_kind() {
    assertAll(
        () -> assertEquals(QuantityKind.LENGTH, QuantityKind.ofKey("length")),
        () -> assertEquals(QuantityKind.LENGTH, QuantityKind.ofKey("length.short")),
        () -> assertEquals(QuantityKind.LENGTH, QuantityKind.ofKey("length.dx")),
        () -> assertEquals(QuantityKind.LENGTH, QuantityKind.ofKey("ombb.width")),
        () -> assertEquals(QuantityKind.LENGTH, QuantityKind.ofKey("diameter")),
        () -> assertEquals(QuantityKind.COORDINATE, QuantityKind.ofKey("center.x")),
        () -> assertEquals(QuantityKind.AREA, QuantityKind.ofKey("area")),
        () -> assertEquals(QuantityKind.ANGLE, QuantityKind.ofKey("angle.reflex")),
        () -> assertEquals(QuantityKind.ANGLE, QuantityKind.ofKey("ombb.orientation")),
        () -> assertEquals(QuantityKind.ANGLE, QuantityKind.ofKey("azimuth")),
        () -> assertEquals(QuantityKind.RATIO, QuantityKind.ofKey("ratio")),
        () -> assertEquals(QuantityKind.COUNT, QuantityKind.ofKey("stats.pixels")),
        () -> assertEquals(QuantityKind.PIXEL_VALUE, QuantityKind.ofKey("pixel.1")),
        () -> assertEquals(QuantityKind.PIXEL_STATISTIC, QuantityKind.ofKey("stats.median")),
        () -> assertEquals(QuantityKind.MOMENT, QuantityKind.ofKey("stats.entropy")),
        () -> assertEquals(QuantityKind.OTHER, QuantityKind.ofKey("myplugin.index")));
  }

  @Test
  void every_built_in_measurement_has_a_kind() {
    List<Measurement> all = new ArrayList<>(List.of(ImageStatistics.ALL_MEASUREMENTS));
    // Its own hide rule: the shared registry would ask the UI core, absent from the tests
    GraphicRegistry registry = new GraphicRegistry(property -> false);
    for (ToolCategory category : List.of(ToolCategory.MEASURE, ToolCategory.ADVANCED)) {
      for (Graphic g : registry.prototypes(category)) {
        if (g.getMeasurementList() != null) {
          all.addAll(g.getMeasurementList());
        }
      }
    }
    assertAll(
        all.stream()
            .map(
                m ->
                    () ->
                        assertNotEquals(
                            QuantityKind.OTHER, m.getKind(), "no kind for key " + m.getKey())));
  }
}
