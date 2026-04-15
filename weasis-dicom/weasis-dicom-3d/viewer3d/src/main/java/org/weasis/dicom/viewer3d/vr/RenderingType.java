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

public enum RenderingType {
  COMPOSITE(Messages.getString("composite"), 0, 0),
  MIP_MAX(Messages.getString("mip.max"), 1, 3),
  MIP_MIN(Messages.getString("mip.min"), 1, 1),
  MIP_MEAN(Messages.getString("mip.mean"), 1, 2),
  ISO2(Messages.getString("iso.surface"), 2, 0);

  final int id;
  final int mipTypeId;
  final String title;

  RenderingType(String title, int id, int mipTypeId) {
    this.title = title;
    this.id = id;
    this.mipTypeId = mipTypeId;
  }

  public int getId() {
    return id;
  }

  public int getMipTypeId() {
    return mipTypeId;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public String toString() {
    return title;
  }
}
