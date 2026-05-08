/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import org.weasis.core.util.MathUtil;

/**
 * Wraps a {@code double} value with a stable rounded key (2 decimals) so it can be safely used as a
 * {@link java.util.Map} key while remaining ordered by its full-precision value.
 *
 * @author Tomas Skripcak
 */
public final class KeyDouble implements Comparable<KeyDouble> {
  private final double value;
  private final double key;

  public KeyDouble(double value) {
    this.value = value;
    this.key = MathUtil.round(value, 2);
  }

  public double getValue() {
    return value;
  }

  public double getKey() {
    return key;
  }

  @Override
  public int hashCode() {
    return Double.hashCode(key);
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof KeyDouble other && MathUtil.isEqual(key, other.key);
  }

  @Override
  public int compareTo(KeyDouble v) {
    return Double.compare(value, v.value);
  }

  @Override
  public String toString() {
    return Double.toString(value);
  }
}
