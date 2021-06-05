/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import org.weasis.core.api.image.op.ByteLut;

public class DisplayByteLut extends ByteLut {
  private boolean invert;

  public DisplayByteLut(ByteLut lut) {
    super(lut.getName(), lut.getLutTable());
  }

  public DisplayByteLut(String name, byte[][] lutTable) {
    super(name, lutTable);
  }

  public boolean isInvert() {
    return invert;
  }

  public void setInvert(boolean invert) {
    this.invert = invert;
  }
}
