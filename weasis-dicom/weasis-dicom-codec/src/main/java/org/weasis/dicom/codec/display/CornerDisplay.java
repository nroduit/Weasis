/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import org.weasis.dicom.codec.Messages;

public enum CornerDisplay {
  TOP_LEFT(Messages.getString("CornerDisplay.t_left")),

  TOP_RIGHT(Messages.getString("CornerDisplay.t_right")),

  BOTTOM_LEFT(Messages.getString("CornerDisplay.b_left")),

  BOTTOM_RIGHT(Messages.getString("CornerDisplay.b_right"));

  private final String name;

  CornerDisplay(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return name;
  }
}
