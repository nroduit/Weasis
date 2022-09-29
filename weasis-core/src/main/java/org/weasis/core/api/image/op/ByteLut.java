/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.op;

import javax.swing.Icon;
import org.weasis.core.api.image.op.ByteLutCollection.Lut;

public class ByteLut {

  private final String name;
  private final byte[][] lutTable;

  public ByteLut(String name, byte[][] lutTable) {
    this.name = name;
    this.lutTable = lutTable;
  }

  @Override
  public String toString() {
    return name;
  }

  public String getName() {
    return name;
  }

  public byte[][] getLutTable() {
    return lutTable;
  }

  public Icon getIcon(int height) {
    return ByteLutCollection.getLUTIcon(
        lutTable == null ? Lut.GRAY.getByteLut().getLutTable() : lutTable, height);
  }
}
