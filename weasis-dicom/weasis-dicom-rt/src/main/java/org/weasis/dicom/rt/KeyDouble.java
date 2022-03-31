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

/** @author Tomas Skripcak */
public class KeyDouble implements Comparable<KeyDouble> {
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
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    return MathUtil.isEqual(key, ((KeyDouble) obj).key);
  }

  @Override
  public int compareTo(KeyDouble v) {
    return Double.compare(value, v.getValue());
  }
}
