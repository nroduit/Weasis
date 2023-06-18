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

import java.util.Date;
import java.util.HashMap;
import java.util.Objects;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class StructureSet extends HashMap<Integer, StructureLayer> {

  private final String label;
  private final Date date;

  public StructureSet(String label, Date date) {
    this.label = Objects.requireNonNull(label);
    this.date = date;
  }

  public String getLabel() {
    return this.label;
  }

  public Date getDate() {
    return this.date;
  }

  @Override
  public String toString() {
    return this.label;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    StructureSet that = (StructureSet) o;
    return Objects.equals(label, that.label) && Objects.equals(date, that.date);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), label, date);
  }
}
