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

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PolynomialSplineFunctionTest {

  private static PolynomialSplineFunction linearSpline() {
    // Two segments of an identity-like piecewise linear function: y = x on [0, 2]
    PolynomialFunction seg1 = new PolynomialFunction(new double[] {0, 1}); // y(x - 0) = x
    PolynomialFunction seg2 = new PolynomialFunction(new double[] {1, 1}); // y(x - 1) = 1 + (x - 1)
    return new PolynomialSplineFunction(
        new double[] {0, 1, 2}, new PolynomialFunction[] {seg1, seg2});
  }

  @Test
  void constructor_rejects_invalid_inputs() {
    PolynomialFunction p = new PolynomialFunction(new double[] {1, 1});
    assertThrows(
        IllegalArgumentException.class,
        () -> new PolynomialSplineFunction(null, new PolynomialFunction[] {p}));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PolynomialSplineFunction(new double[] {0, 1}, null));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PolynomialSplineFunction(new double[] {0}, new PolynomialFunction[] {p}));
    assertThrows(
        IllegalArgumentException.class,
        () -> new PolynomialSplineFunction(new double[] {0, 1, 2}, new PolynomialFunction[] {p}));
  }

  @Test
  void value_evaluates_correct_segment_within_range() {
    PolynomialSplineFunction spline = linearSpline();
    assertEquals(0.0, spline.value(0.0), 1e-12);
    assertEquals(0.5, spline.value(0.5), 1e-12);
    assertEquals(1.0, spline.value(1.0), 1e-12);
    assertEquals(1.7, spline.value(1.7), 1e-12);
    assertEquals(2.0, spline.value(2.0), 1e-12);
  }

  @Test
  void value_throws_when_outside_knot_range() {
    PolynomialSplineFunction spline = linearSpline();
    assertThrows(IllegalArgumentException.class, () -> spline.value(-0.1));
    assertThrows(IllegalArgumentException.class, () -> spline.value(2.1));
  }
}
