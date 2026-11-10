/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.weasis.dicom.codec.hp.TestSeries.ctSlice;
import static org.weasis.dicom.codec.hp.TestSeries.series;

import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;

/** The text of a derived value a command states, and what a wrong one is told. */
class SeriesFeaturesParseTest {

  private static SeriesFeatures features() {
    return new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE)
        .extract(series(ctSlice(1, 0), ctSlice(2, 2.5), ctSlice(3, 5.0)));
  }

  @Test
  void aConstantIsReadInAnyCaseAndAWrongOneListsTheNames() {
    SeriesFeatures f = features();
    IllegalArgumentException wrong =
        assertThrows(IllegalArgumentException.class, () -> f.parse(Key.ROLE, "nope"));
    assertAll(
        () -> assertEquals(SeriesFeatures.Role.LOCALIZER, f.parse(Key.ROLE, "localizer")),
        () -> assertEquals(SeriesFeatures.Orientation.AXIAL, f.parse(Key.ORIENTATION, " Axial ")),
        () -> assertTrue(wrong.getMessage().startsWith("role is one of "), wrong.getMessage()),
        () -> assertTrue(wrong.getMessage().contains("localizer"), wrong.getMessage()),
        () -> assertTrue(wrong.getMessage().endsWith("not 'nope'"), wrong.getMessage()),
        () ->
            assertTrue(
                !wrong.getMessage().contains("org.weasis"),
                "no class name: " + wrong.getMessage()));
  }
}
