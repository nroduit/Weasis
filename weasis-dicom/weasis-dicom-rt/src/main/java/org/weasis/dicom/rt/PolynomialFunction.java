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

public class PolynomialFunction {

  private final double[] coefs;

  public PolynomialFunction(double[] c) {
    int length = Objects.requireNonNull(c).length;
    if (length == 0) {
      throw new IllegalStateException("empty data");
    }
    while ((length > 1) && (c[length - 1] == 0)) {
      --length;
    }
    this.coefs = new double[length];
    System.arraycopy(c, 0, this.coefs, 0, length);
  }

  public double value(double x) {
    int length = Objects.requireNonNull(coefs).length;
    if (length == 0) {
      throw new IllegalStateException("empty data");
    }
    double result = coefs[length - 1];
    for (int j = length - 2; j >= 0; j--) {
      result = x * result + coefs[j];
    }
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PolynomialFunction that = (PolynomialFunction) o;
    return Arrays.equals(coefs, that.coefs);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(coefs);
  }
}
