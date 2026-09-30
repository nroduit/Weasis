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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What the user chose to see is the interface's business; a command reads every value. */
class MeasurementComputedTest {

  private static Measurement hidden() {
    Measurement measurement = new Measurement("stats.skew", "Skewness", 5, false); // NON-NLS
    measurement.setComputed(false);
    return measurement;
  }

  @Test
  void aMeasurementTheUserHidTellsTheInterfaceSoAndTheCommandsNot() {
    Measurement measurement = hidden();
    assertAll(
        () -> assertFalse(measurement.getComputed(), "outside the scope, the preference holds"),
        () ->
            assertTrue(
                Measurement.computingAll(measurement::getComputed),
                "inside the scope, every measurement is computed"),
        () -> assertFalse(measurement.getComputed(), "and the preference holds again after it"));
  }

  @Test
  void theScopeChangesNothingForAMeasurementTheUserKept() {
    Measurement measurement = new Measurement("length", "Length", 1, true); // NON-NLS
    assertAll(
        () -> assertTrue(measurement.getComputed()),
        () -> assertTrue(Measurement.computingAll(measurement::getComputed)));
  }
}
