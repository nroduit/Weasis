/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.op.lut.PresentationStateLut;
import org.weasis.opencv.op.lut.WlPresentation;

class WindLevelParametersTest {

  private static final double EPS = 1.0e-12;

  // -- Builder ---------------------------------------------------------------

  @Test
  void builder_defaultsProduceValidInstance() {
    WindLevelParameters p = WindLevelParameters.builder().build();

    assertAll(
        () -> assertEquals(1.0, p.getWindow(), EPS),
        () -> assertEquals(0.0, p.getLevel(), EPS),
        () -> assertEquals(0.0, p.getLevelMin(), EPS),
        () -> assertEquals(1.0, p.getLevelMax(), EPS),
        () -> assertTrue(p.isPixelPadding(), "pixelPadding defaults to true"),
        () -> assertFalse(p.isInverseLut()),
        () -> assertFalse(p.isFillOutsideLutRange()),
        () -> assertFalse(p.isAllowWinLevelOnColorImage()),
        () -> assertEquals(LutShape.LINEAR, p.getLutShape()),
        () -> assertNull(p.getPresentationState()),
        () -> assertTrue(p.isValid()));
  }

  @Test
  void builder_fluentSettersOverrideEachField() {
    PresentationStateLut prState = mock(PresentationStateLut.class);

    WindLevelParameters p =
        WindLevelParameters.builder()
            .window(400.0)
            .level(40.0)
            .levelMin(-1000.0)
            .levelMax(3000.0)
            .pixelPadding(false)
            .inverseLut(true)
            .fillOutsideLutRange(true)
            .allowWinLevelOnColorImage(true)
            .lutShape(LutShape.SIGMOID)
            .presentationStateLut(prState)
            .build();

    assertAll(
        () -> assertEquals(400.0, p.getWindow(), EPS),
        () -> assertEquals(40.0, p.getLevel(), EPS),
        () -> assertEquals(-1000.0, p.getLevelMin(), EPS),
        () -> assertEquals(3000.0, p.getLevelMax(), EPS),
        () -> assertFalse(p.isPixelPadding()),
        () -> assertTrue(p.isInverseLut()),
        () -> assertTrue(p.isFillOutsideLutRange()),
        () -> assertTrue(p.isAllowWinLevelOnColorImage()),
        () -> assertEquals(LutShape.SIGMOID, p.getLutShape()),
        () -> assertEquals(prState, p.getPresentationState()));
  }

  @Test
  void builder_fromCopiesAllFields() {
    WindLevelParameters source =
        WindLevelParameters.builder()
            .window(400.0)
            .level(40.0)
            .levelMin(-1000)
            .levelMax(3000)
            .pixelPadding(false)
            .inverseLut(true)
            .lutShape(LutShape.SIGMOID)
            .build();

    WindLevelParameters copy = WindLevelParameters.builder().from(source).build();

    assertEquals(source, copy);
  }

  // -- Immutable mutators ---------------------------------------------------

  @Test
  void withWindowLevel_returnsNewInstanceWithUpdatedValues() {
    WindLevelParameters original =
        WindLevelParameters.builder().window(400).level(40).levelMin(-1000).levelMax(3000).build();

    WindLevelParameters modified = original.withWindowLevel(100.0, 50.0);

    assertAll(
        () -> assertNotSame(original, modified),
        () -> assertEquals(100.0, modified.getWindow(), EPS),
        () -> assertEquals(50.0, modified.getLevel(), EPS),
        // other fields preserved
        () -> assertEquals(-1000.0, modified.getLevelMin(), EPS),
        () -> assertEquals(3000.0, modified.getLevelMax(), EPS),
        // original untouched
        () -> assertEquals(400.0, original.getWindow(), EPS),
        () -> assertEquals(40.0, original.getLevel(), EPS));
  }

  @Test
  void withLutShape_returnsNewInstanceWithUpdatedShape() {
    WindLevelParameters original = WindLevelParameters.builder().lutShape(LutShape.LINEAR).build();

    WindLevelParameters modified = original.withLutShape(LutShape.SIGMOID);

    assertAll(
        () -> assertNotSame(original, modified),
        () -> assertEquals(LutShape.SIGMOID, modified.getLutShape()),
        () -> assertEquals(LutShape.LINEAR, original.getLutShape()));
  }

  // -- Range computations ---------------------------------------------------

  @Test
  void getWindowRange_centeredOnLevel() {
    WindLevelParameters p = WindLevelParameters.builder().window(400).level(40).build();

    assertArrayEquals(new double[] {-160.0, 240.0}, p.getWindowRange(), EPS);
  }

  @Test
  void getWindowRange_zeroWindowCollapsesToLevel() {
    // Allowed by the builder (isValid() catches window<=0; this is purely the math contract).
    WindLevelParameters p = WindLevelParameters.builder().window(0).level(123.45).build();

    double[] range = p.getWindowRange();
    assertAll(() -> assertEquals(123.45, range[0], EPS), () -> assertEquals(123.45, range[1], EPS));
  }

  @Test
  void getLevelRange_returnsConfiguredBounds() {
    WindLevelParameters p = WindLevelParameters.builder().levelMin(-1024).levelMax(3071).build();

    assertArrayEquals(new double[] {-1024.0, 3071.0}, p.getLevelRange(), EPS);
  }

  // -- isValid() ------------------------------------------------------------

  @Test
  void isValid_falseForZeroWindow() {
    assertFalse(WindLevelParameters.builder().window(0).build().isValid());
  }

  @Test
  void isValid_falseForNegativeWindow() {
    assertFalse(WindLevelParameters.builder().window(-1).build().isValid());
  }

  @Test
  void isValid_falseForNaNWindow() {
    assertFalse(WindLevelParameters.builder().window(Double.NaN).build().isValid());
  }

  @Test
  void isValid_falseForInfiniteWindow() {
    assertFalse(WindLevelParameters.builder().window(Double.POSITIVE_INFINITY).build().isValid());
  }

  @Test
  void isValid_falseForNaNLevel() {
    assertFalse(WindLevelParameters.builder().level(Double.NaN).build().isValid());
  }

  @Test
  void isValid_falseWhenLevelMinExceedsLevelMax() {
    assertFalse(WindLevelParameters.builder().levelMin(10).levelMax(-10).build().isValid());
  }

  @Test
  void isValid_falseForNullLutShape() {
    assertFalse(WindLevelParameters.builder().lutShape(null).build().isValid());
  }

  @Test
  void isValid_trueForRealisticCtParameters() {
    // Typical CT lung window.
    WindLevelParameters p =
        WindLevelParameters.builder()
            .window(1500)
            .level(-600)
            .levelMin(-1024)
            .levelMax(3071)
            .lutShape(LutShape.LINEAR)
            .build();

    assertTrue(p.isValid());
  }

  // -- equals / hashCode / toString -----------------------------------------

  @Test
  void equals_identicalBuildersProduceEqualInstances() {
    WindLevelParameters a = WindLevelParameters.builder().window(400).level(40).build();
    WindLevelParameters b = WindLevelParameters.builder().window(400).level(40).build();

    assertAll(() -> assertEquals(a, b), () -> assertEquals(a.hashCode(), b.hashCode()));
  }

  @Test
  void equals_differentWindowIsNotEqual() {
    WindLevelParameters a = WindLevelParameters.builder().window(400).build();
    WindLevelParameters b = WindLevelParameters.builder().window(401).build();

    assertNotEquals(a, b);
  }

  @Test
  void equals_differentLutShapeIsNotEqual() {
    WindLevelParameters a = WindLevelParameters.builder().lutShape(LutShape.LINEAR).build();
    WindLevelParameters b = WindLevelParameters.builder().lutShape(LutShape.SIGMOID).build();

    assertNotEquals(a, b);
  }

  @Test
  void equals_differentTypeIsNotEqual() {
    assertNotEquals(WindLevelParameters.builder().build(), "not a params");
  }

  @Test
  void toString_includesAllFields() {
    String s = WindLevelParameters.builder().window(400).level(40).build().toString();

    assertAll(
        () -> assertTrue(s.contains("window=400")),
        () -> assertTrue(s.contains("level=40")),
        () -> assertTrue(s.contains("lutShape")));
  }

  // -- Constructor with ImageElement ---------------------------------------

  @Test
  void constructor_nullImageElementThrowsNpe() {
    assertThrows(NullPointerException.class, () -> new WindLevelParameters(null, null));
  }

  @Test
  void constructor_nullParamsUsesAllImageDefaults() {
    ImageElement img = mock(ImageElement.class);
    when(img.getDefaultWindow(any(WlPresentation.class))).thenReturn(400.0);
    when(img.getDefaultLevel(any(WlPresentation.class))).thenReturn(40.0);
    when(img.getDefaultShape(any(WlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(img.getMinValue(any(WlPresentation.class))).thenReturn(-1024.0);
    when(img.getMaxValue(any(WlPresentation.class))).thenReturn(3071.0);

    WindLevelParameters p = new WindLevelParameters(img, null);

    assertAll(
        () -> assertEquals(400.0, p.getWindow(), EPS),
        () -> assertEquals(40.0, p.getLevel(), EPS),
        () -> assertEquals(LutShape.LINEAR, p.getLutShape()),
        () -> assertTrue(p.isPixelPadding(), "pixelPadding defaults to true when absent"),
        () -> assertFalse(p.isInverseLut(), "inverseLut defaults to false"));
  }

  @Test
  void constructor_paramsOverrideImageDefaults() {
    ImageElement img = mock(ImageElement.class);
    when(img.getDefaultWindow(any(WlPresentation.class))).thenReturn(400.0);
    when(img.getDefaultLevel(any(WlPresentation.class))).thenReturn(40.0);
    when(img.getDefaultShape(any(WlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(img.getMinValue(any(WlPresentation.class))).thenReturn(-1024.0);
    when(img.getMaxValue(any(WlPresentation.class))).thenReturn(3071.0);

    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.WINDOW.cmd(), 100.0);
    params.put(ActionW.LEVEL.cmd(), 50.0);
    params.put(ActionW.LUT_SHAPE.cmd(), LutShape.SIGMOID);

    WindLevelParameters p = new WindLevelParameters(img, params);

    assertAll(
        () -> assertEquals(100.0, p.getWindow(), EPS, "window override"),
        () -> assertEquals(50.0, p.getLevel(), EPS, "level override"),
        () -> assertEquals(LutShape.SIGMOID, p.getLutShape(), "lutShape override"));
  }

  @Test
  void constructor_explicitLevelRangeExpandsToCoverImageRange() {
    // Provided range is narrower than image's pixel range — must expand to include both.
    ImageElement img = mock(ImageElement.class);
    when(img.getDefaultWindow(any(WlPresentation.class))).thenReturn(400.0);
    when(img.getDefaultLevel(any(WlPresentation.class))).thenReturn(40.0);
    when(img.getDefaultShape(any(WlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(img.getMinValue(any(WlPresentation.class))).thenReturn(-1024.0);
    when(img.getMaxValue(any(WlPresentation.class))).thenReturn(3071.0);
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.LEVEL_MIN.cmd(), 0.0); // narrower than image's -1024
    params.put(ActionW.LEVEL_MAX.cmd(), 100.0); // narrower than image's 3071

    WindLevelParameters p = new WindLevelParameters(img, params);

    assertAll(
        () -> assertEquals(-1024.0, p.getLevelMin(), EPS, "min expands to imageMin"),
        () -> assertEquals(3071.0, p.getLevelMax(), EPS, "max expands to imageMax"));
  }

  @Test
  void constructor_paramOverridesPixelPaddingDefault() {
    ImageElement img = mock(ImageElement.class);
    when(img.getDefaultWindow(any(WlPresentation.class))).thenReturn(400.0);
    when(img.getDefaultLevel(any(WlPresentation.class))).thenReturn(40.0);
    when(img.getDefaultShape(any(WlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(img.getMinValue(any(WlPresentation.class))).thenReturn(0.0);
    when(img.getMaxValue(any(WlPresentation.class))).thenReturn(255.0);
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.FALSE);

    WindLevelParameters p = new WindLevelParameters(img, params);

    assertFalse(p.isPixelPadding());
  }

  @Test
  void constructor_paramsWithWrongTypeAreIgnored() {
    // A clinician-set window of "400" as a String must not be silently used; the extractor
    // checks the type and falls back to the image default.
    ImageElement img = mock(ImageElement.class);
    when(img.getDefaultWindow(any(WlPresentation.class))).thenReturn(400.0);
    when(img.getDefaultLevel(any(WlPresentation.class))).thenReturn(40.0);
    when(img.getDefaultShape(any(WlPresentation.class))).thenReturn(LutShape.LINEAR);
    when(img.getMinValue(any(WlPresentation.class))).thenReturn(-1024.0);
    when(img.getMaxValue(any(WlPresentation.class))).thenReturn(3071.0);
    Map<String, Object> params = new HashMap<>();
    params.put(ActionW.WINDOW.cmd(), "100.0"); // wrong type
    params.put(ActionW.LEVEL.cmd(), 999); // wrong type (Integer not Double)

    WindLevelParameters p = new WindLevelParameters(img, params);

    assertAll(
        () -> assertEquals(400.0, p.getWindow(), EPS, "String ignored, image default used"),
        () -> assertEquals(40.0, p.getLevel(), EPS, "Integer ignored, image default used"));
  }
}
