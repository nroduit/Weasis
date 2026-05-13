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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PolynomialFunctionTest {

  @Test
  void constructor_rejects_null_or_empty_coefficients() {
    assertThrows(NullPointerException.class, () -> new PolynomialFunction(null));
    assertThrows(IllegalArgumentException.class, () -> new PolynomialFunction(new double[0]));
  }

  @Test
  void value_evaluates_polynomial_with_horner_scheme() {
    // p(x) = 1 + 2x + 3x^2
    PolynomialFunction p = new PolynomialFunction(new double[] {1, 2, 3});
    assertEquals(1.0, p.value(0.0), 1e-12);
    assertEquals(6.0, p.value(1.0), 1e-12);
    assertEquals(17.0, p.value(2.0), 1e-12);
  }

  @Test
  void constant_polynomial_returns_constant_value() {
    PolynomialFunction p = new PolynomialFunction(new double[] {7.5});
    assertEquals(7.5, p.value(0.0));
    assertEquals(7.5, p.value(1234.5));
  }

  @Test
  void trailing_zero_coefficients_are_dropped_for_equality() {
    PolynomialFunction a = new PolynomialFunction(new double[] {1, 2});
    PolynomialFunction b = new PolynomialFunction(new double[] {1, 2, 0, 0});
    assertEquals(a, b);
    assertEquals(a.hashCode(), b.hashCode());
  }

  @Test
  void equals_returns_false_for_different_coefficients_and_other_types() {
    assertNotEquals(
        new PolynomialFunction(new double[] {1, 2}), new PolynomialFunction(new double[] {1, 3}));
    assertNotEquals(new PolynomialFunction(new double[] {1}), "1");
  }

  @Test
  void all_zero_coefficients_keep_at_least_one_element() {
    PolynomialFunction p = new PolynomialFunction(new double[] {0, 0, 0});
    assertEquals(0.0, p.value(42.0));
  }
}
