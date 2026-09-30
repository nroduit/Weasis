/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.utils.bean;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit;

/** What a caller of the command surface reads: stable keys, English names and UCUM units. */
class MeasurementVocabularyTest {

  private static Measurement of(String key) {
    return new Measurement(key, "libellé", 1, true); // NON-NLS
  }

  @Test
  void aNameDoesNotFollowTheLanguageOfTheInterface() {
    assertAll(
        () -> assertEquals("Length", MeasurementVocabulary.nameOf(of("length"))),
        () -> assertEquals("Standard deviation", MeasurementVocabulary.nameOf(of("stats.stdev"))),
        () -> assertEquals("Cardiothoracic ratio", MeasurementVocabulary.nameOf(of("ctr.ratio"))),
        // a measurement built at runtime, such as one channel of a colour image, keeps its name
        () -> assertEquals("libellé", MeasurementVocabulary.nameOf(of("pixel.0"))));
  }

  @Test
  void aUnitIsTheUcumCodeOfWhatTheKindHolds() {
    assertAll(
        () -> assertEquals("mm", unit("length", Unit.MILLIMETER, "HU")),
        () -> assertEquals("mm2", unit("area", Unit.MILLIMETER, "HU")),
        () -> assertEquals("cm", unit("center.x", Unit.CENTIMETER, "HU")),
        () -> assertEquals("{pixel}", unit("perimeter", Unit.PIXEL, null)),
        () -> assertEquals("{pixel2}", unit("area", Unit.PIXEL, null)),
        () -> assertEquals("deg", unit("angle", Unit.MILLIMETER, "HU")),
        () -> assertEquals("{pixel}", unit("stats.pixels", Unit.MILLIMETER, "HU")),
        () -> assertEquals("[hnsf'U]", unit("stats.min", Unit.MILLIMETER, "HU")),
        () -> assertEquals("[hnsf'U]", unit("stats.mean", Unit.MILLIMETER, "HU")),
        () -> assertNull(unit("ratio", Unit.MILLIMETER, "HU")),
        () -> assertNull(unit("stats.skew", Unit.MILLIMETER, "HU")));
  }

  @Test
  void anImageValueUnitThatIsNotStandardStaysAnAnnotation() {
    assertAll(
        () -> assertEquals("g/mL", MeasurementVocabulary.pixelValue("SUVbw, g/ml")),
        () -> assertEquals("{US}", MeasurementVocabulary.pixelValue("US")),
        () -> assertEquals("{counts}", MeasurementVocabulary.pixelValue(" counts ")),
        () -> assertNull(MeasurementVocabulary.pixelValue("  ")),
        () -> assertNull(MeasurementVocabulary.pixelValue(null)));
  }

  private static String unit(String key, Unit spatial, String pixelValueUnit) {
    return MeasurementVocabulary.unitOf(of(key), spatial, pixelValueUnit);
  }
}
