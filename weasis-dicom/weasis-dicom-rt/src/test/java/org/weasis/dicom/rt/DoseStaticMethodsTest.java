/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DoseStaticMethodsTest {

  @Test
  void calculate_relative_dose_returns_dose_as_percentage_of_plan_dose() {
    assertEquals(100.0, Dose.calculateRelativeDose(60.0, 60.0), 1e-12);
    assertEquals(50.0, Dose.calculateRelativeDose(30.0, 60.0), 1e-12);
    assertEquals(150.0, Dose.calculateRelativeDose(90.0, 60.0), 1e-12);
  }

  @Test
  void calculate_relative_dose_with_zero_plan_dose_yields_infinity() {
    assertTrue(Double.isInfinite(Dose.calculateRelativeDose(10.0, 0.0)));
  }

  @Test
  void interpolate_builds_a_piecewise_linear_spline_through_the_input_points() {
    PolynomialSplineFunction spline =
        Dose.interpolate(new double[] {0, 1, 3}, new double[] {0, 2, 6});
    assertEquals(0.0, spline.value(0.0), 1e-12);
    assertEquals(1.0, spline.value(0.5), 1e-12);
    assertEquals(2.0, spline.value(1.0), 1e-12);
    assertEquals(4.0, spline.value(2.0), 1e-12);
    assertEquals(6.0, spline.value(3.0), 1e-12);
  }

  @Test
  void interpolate_rejects_mismatched_or_too_short_arrays() {
    assertThrows(
        IllegalArgumentException.class,
        () -> Dose.interpolate(new double[] {0, 1}, new double[] {0, 1, 2}));
    assertThrows(
        IllegalArgumentException.class, () -> Dose.interpolate(new double[] {0}, new double[] {0}));
  }
}
