/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.Arrays;

/**
 * Piecewise polynomial function over a strictly increasing set of knots. Each segment {@code
 * [knots[i], knots[i+1]]} is evaluated by {@code functions[i]} translated by {@code -knots[i]}.
 */
public final class PolynomialSplineFunction {
  private final double[] knots;
  private final PolynomialFunction[] functions;
  private final int n;

  public PolynomialSplineFunction(double[] knots, PolynomialFunction[] functions) {
    if (knots == null
        || functions == null
        || knots.length < 2
        || knots.length - 1 != functions.length) {
      throw new IllegalArgumentException("invalid knots/functions length");
    }
    this.n = knots.length - 1;
    this.knots = Arrays.copyOf(knots, n + 1);
    this.functions = Arrays.copyOf(functions, n);
  }

  /** Evaluates the spline at {@code v}; throws if {@code v} is outside the knot range. */
  public double value(double v) {
    if (v < knots[0] || v > knots[n]) {
      throw new IllegalArgumentException("out of range: " + v);
    }
    int i = Arrays.binarySearch(knots, v);
    if (i < 0) {
      i = -i - 2;
    }
    if (i >= functions.length) {
      i--;
    }
    return functions[i].value(v - knots[i]);
  }
}
