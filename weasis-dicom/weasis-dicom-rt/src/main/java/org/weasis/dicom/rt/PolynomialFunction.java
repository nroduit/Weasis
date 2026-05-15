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
import java.util.Objects;

/**
 * Immutable polynomial function evaluated with Horner's scheme. Trailing zero coefficients are
 * dropped so that two equivalent polynomials compare equal.
 */
public final class PolynomialFunction {

  private final double[] coefs;

  public PolynomialFunction(double[] c) {
    int length = Objects.requireNonNull(c).length;
    if (length == 0) {
      throw new IllegalArgumentException("empty coefficients");
    }
    while (length > 1 && c[length - 1] == 0) {
      length--;
    }
    this.coefs = Arrays.copyOf(c, length);
  }

  /** Evaluates the polynomial at {@code x} using Horner's scheme. */
  public double value(double x) {
    double result = coefs[coefs.length - 1];
    for (int j = coefs.length - 2; j >= 0; j--) {
      result = x * result + coefs[j];
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof PolynomialFunction other && Arrays.equals(coefs, other.coefs);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(coefs);
  }
}
