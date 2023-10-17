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

public class PolynomialSplineFunction {
  private final double[] knots;
  private final PolynomialFunction[] functions;
  private final int n;

  public PolynomialSplineFunction(double[] knots, PolynomialFunction[] functions) {
    if (knots == null
        || functions == null
        || knots.length < 2
        || knots.length - 1 != functions.length) {
      throw new IllegalArgumentException();
    }
    this.n = knots.length - 1;
    this.knots = new double[n + 1];
    System.arraycopy(knots, 0, this.knots, 0, n + 1);
    this.functions = new PolynomialFunction[n];
    System.arraycopy(functions, 0, this.functions, 0, n);
  }

  public double value(double v) {
    if (v < knots[0] || v > knots[n]) {
      throw new IllegalStateException("out of range");
    }
    int i = Arrays.binarySearch(knots, v);
    if (i < 0) {
      i = -i - 2;
    }
    // When v is the last knot value
    if (i >= functions.length) {
      i--;
    }
    return functions[i].value(v - knots[i]);
  }
}
