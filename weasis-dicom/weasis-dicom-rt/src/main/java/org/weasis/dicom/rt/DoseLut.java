/*
 * Copyright (c) 2009-2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.Arrays;

/** Pair of axis lookup tables (x and y) used to map dose / image grid coordinates. */
public record DoseLut(double[] x, double[] y) {

  @Override
  public boolean equals(Object o) {
    return o instanceof DoseLut other && Arrays.equals(x, other.x) && Arrays.equals(y, other.y);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(x) + Arrays.hashCode(y);
  }

  @Override
  public String toString() {
    return "DoseLut[x=" + Arrays.toString(x) + ", y=" + Arrays.toString(y) + "]";
  }
}
