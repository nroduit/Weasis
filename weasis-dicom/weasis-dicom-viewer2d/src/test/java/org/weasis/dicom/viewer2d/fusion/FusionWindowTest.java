/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.awt.Color;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;

class FusionWindowTest {

  private static final double DELTA = 1e-9;

  /** SUV factor turning 4000 BQML into 1 SUVbw. */
  private static final double SUV_FACTOR = 1.0 / 4000.0;

  /** A BQML PET whose SUV factor turns 4000 BQML into 1 SUVbw. */
  private static FusionWindow suvWindow(double min, double max) {
    return new FusionWindow(min, max, SUV_FACTOR, "SUVbw");
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> seriesWith(DicomImageElement middle) {
    MediaSeries<DicomImageElement> series = mock(MediaSeries.class);
    when(series.getMedia(MEDIA_POSITION.MIDDLE, null, null)).thenReturn(middle);
    return series;
  }

  @Test
  @DisplayName("Displayed values apply the SUV factor")
  void displayedValuesApplySuvFactor() {
    FusionWindow window = suvWindow(0.0, 20000.0);
    assertAll(
        () -> assertEquals(0.0, window.displayMin(), DELTA),
        () -> assertEquals(5.0, window.displayMax(), DELTA));
  }

  /** A SUV series whose values reach {@code suvMax} SUVbw. */
  private static FusionWindow windowForSuvMax(double suvMax) {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getTagValue(TagW.SuvFactor)).thenReturn(SUV_FACTOR);
    when(ref.getMaxValue(null)).thenReturn(suvMax / SUV_FACTOR);
    return FusionWindow.fromSlice(seriesWith(ref));
  }

  @Test
  @DisplayName("A SUV scale starts at 1 and ends on the next whole SUVbw above the data")
  void suvScaleFollowsTheData() {
    assertAll(
        () -> assertEquals(FusionWindow.SUV_MIN, windowForSuvMax(11.2).displayMin(), DELTA),
        () -> assertEquals(12.0, windowForSuvMax(11.2).displayMax(), DELTA),
        () -> assertEquals(10.0, windowForSuvMax(9.7).displayMax(), DELTA),
        // Bounds are held in modality-LUT values: 1 and 10 SUVbw are 4000 and 40000 BQML.
        () -> assertEquals(4_000.0, windowForSuvMax(9.7).min(), DELTA),
        () -> assertEquals(40_000.0, windowForSuvMax(9.7).max(), DELTA));
  }

  @Test
  @DisplayName("The SUV scale never gets narrower than 4 nor wider than 30")
  void suvScaleStaysUsable() {
    assertAll(
        () -> assertEquals(4.0, windowForSuvMax(1.3).displayMax(), DELTA),
        () -> assertEquals(30.0, windowForSuvMax(84.0).displayMax(), DELTA));
  }

  @Test
  @DisplayName("Without a SUV factor the scale is stretched from zero to the data maximum")
  void rawSeriesIsStretchedToTheData() {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getMaxValue(null)).thenReturn(7086.0);
    when(ref.getPixelValueUnit()).thenReturn("BQML");

    FusionWindow window = FusionWindow.fromSlice(seriesWith(ref));
    assertAll(
        () -> assertEquals(0.0, window.min(), DELTA),
        () -> assertEquals(7086.0, window.max(), DELTA),
        () -> assertFalse(window.isQuantitative()));
  }

  @Test
  @DisplayName("Only a SUVbw scale counts as quantitative")
  void onlySuvIsQuantitative() {
    assertAll(
        () -> assertTrue(suvWindow(0.0, 20000.0).isQuantitative()),
        // Raw counts vary with dose, weight and uptake time: no reader can act on them.
        () -> assertFalse(new FusionWindow(0.0, 7086.0, 1.0, "BQML").isQuantitative()),
        () -> assertFalse(new FusionWindow(0.0, 7086.0, 1.0, null).isQuantitative()));
  }

  @Test
  @DisplayName("A degenerate range is widened so the normalization slope stays finite")
  void degenerateRangeIsWidened() {
    FusionWindow window = new FusionWindow(7.0, 7.0, 1.0, "GML");
    assertTrue(window.max() > window.min());
  }

  @Test
  @DisplayName("A missing display factor falls back to raw values")
  void missingDisplayFactorFallsBackToRawValues() {
    FusionWindow window = new FusionWindow(0.0, 100.0, 0.0, null);
    assertAll(
        () -> assertEquals(1.0, window.displayFactor(), DELTA),
        () -> assertEquals(100.0, window.displayMax(), DELTA),
        () -> assertEquals("", window.displayUnit()));
  }

  private static ColorMap absoluteMap(String unit, double min, double max) {
    return ColorMap.builder("map")
        .domain(ColorMapDomain.absolute(unit, min, max))
        .stop(min, Color.BLACK)
        .stop(max, Color.WHITE)
        .build();
  }

  @Test
  @DisplayName("An SUVbw map declares its window through the SUV factor")
  void suvMapDeclaresTheWindow() {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getTagValue(TagW.SuvFactor)).thenReturn(SUV_FACTOR);
    when(ref.getMaxValue(null)).thenReturn(50_000.0);

    FusionWindow window = FusionWindow.fromSlice(seriesWith(ref), absoluteMap("SUVbw", 0, 10));
    assertAll(
        () -> assertEquals(0.0, window.min(), DELTA),
        () -> assertEquals(40_000.0, window.max(), DELTA),
        () -> assertEquals(10.0, window.displayMax(), DELTA),
        () -> assertTrue(window.isQuantitative()));
  }

  @Test
  @DisplayName("A map in the series' own unit maps directly; other units fall back to the data")
  void unitMapsDirectlyOrFallsBack() {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getMaxValue(null)).thenReturn(7086.0);
    when(ref.getPixelValueUnit()).thenReturn("BQML");
    MediaSeries<DicomImageElement> series = seriesWith(ref);

    FusionWindow direct = FusionWindow.fromSlice(series, absoluteMap("bqml", 100, 5000));
    FusionWindow fallback = FusionWindow.fromSlice(series, absoluteMap("SUVbw", 0, 10));
    FusionWindow relative =
        FusionWindow.fromSlice(
            series, ColorMap.builder("r").stop(0, Color.BLACK).stop(1, Color.WHITE).build());
    assertAll(
        () -> assertEquals(100.0, direct.min(), DELTA),
        () -> assertEquals(5000.0, direct.max(), DELTA),
        () -> assertEquals(0.0, fallback.min(), DELTA),
        () -> assertEquals(7086.0, fallback.max(), DELTA),
        () -> assertEquals(7086.0, relative.max(), DELTA),
        () -> assertNull(FusionWindow.declaredBy(absoluteMap("SUVbw", 0, 10), series, null)));
  }

  @Test
  @DisplayName("A percent-of-maximum map scales the data maximum")
  void percentMapScalesTheMaximum() {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getMaxValue(null)).thenReturn(8000.0);
    ColorMap percent =
        ColorMap.builder("p")
            .domain(ColorMapDomain.percent(ColorMapDomain.REFERENCE_MAX, 10, 90))
            .stop(10, Color.BLACK)
            .stop(90, Color.WHITE)
            .build();

    FusionWindow window = FusionWindow.fromSlice(seriesWith(ref), percent);
    assertAll(
        () -> assertEquals(800.0, window.min(), DELTA),
        () -> assertEquals(7200.0, window.max(), DELTA),
        () -> assertEquals(90.0, window.displayMax(), DELTA),
        () -> assertEquals("%", window.displayUnit()));
  }

  @Test
  @DisplayName("A percent-of-prescription map scales the reference stored on the series")
  void prescriptionMapScalesTheStoredReference() {
    DicomImageElement ref = mock(DicomImageElement.class);
    when(ref.getMaxValue(null)).thenReturn(9000.0);
    when(ref.getTagValue(TagW.PercentReference)).thenReturn(5000.0);
    ColorMap isodose =
        ColorMap.builder("iso")
            .domain(ColorMapDomain.percent(ColorMapDomain.REFERENCE_PRESCRIPTION, 0, 120))
            .stop(0, Color.BLACK)
            .stop(120, Color.WHITE)
            .build();

    FusionWindow window = FusionWindow.fromSlice(seriesWith(ref), isodose);
    DicomImageElement noReference = mock(DicomImageElement.class);
    when(noReference.getMaxValue(null)).thenReturn(9000.0);
    assertAll(
        () -> assertEquals(0.0, window.min(), DELTA),
        () -> assertEquals(6000.0, window.max(), DELTA),
        () -> assertEquals(120.0, window.displayMax(), DELTA),
        () -> assertEquals("%", window.displayUnit()),
        () -> assertNull(FusionWindow.declaredBy(isodose, seriesWith(noReference), null)));
  }
}
