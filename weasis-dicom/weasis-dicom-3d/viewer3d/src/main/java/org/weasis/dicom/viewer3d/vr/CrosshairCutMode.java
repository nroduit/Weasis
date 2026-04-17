/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import org.weasis.dicom.viewer3d.Messages;

public enum CrosshairCutMode {
  NONE(Messages.getString("cut.none"), 0),
  RIGHT(Messages.getString("cut.right"), 1),
  LEFT(Messages.getString("cut.left"), 2),
  FRONT(Messages.getString("cut.front"), 3),
  BACK(Messages.getString("cut.back"), 4),
  UP(Messages.getString("cut.up"), 5),
  DOWN(Messages.getString("cut.down"), 6),
  RIGHT_UP(Messages.getString("cut.up.right"), 7),
  LEFT_UP(Messages.getString("cut.up.left"), 8),
  RIGHT_DOWN(Messages.getString("cut.down.right"), 9),
  LEFT_DOWN(Messages.getString("cut.down.left"), 10),
  UP_RIGHT_FRONT(Messages.getString("cut.up.right.front"), 11),
  UP_LEFT_FRONT(Messages.getString("cut.up.left.front"), 12),
  DOWN_RIGHT_FRONT(Messages.getString("cut.down.right.front"), 13),
  DOWN_LEFT_FRONT(Messages.getString("cut.down.left.front"), 14),
  UP_RIGHT_BACK(Messages.getString("cut.up.right.back"), 15),
  UP_LEFT_BACK(Messages.getString("cut.up.left.back"), 16),
  DOWN_RIGHT_BACK(Messages.getString("cut.down.right.back"), 17),
  DOWN_LEFT_BACK(Messages.getString("cut.down.left.back"), 18);

  private final String title;
  private final int id;

  CrosshairCutMode(String title, int id) {
    this.title = title;
    this.id = id;
  }

  public int getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public String toString() {
    return title;
  }
}
