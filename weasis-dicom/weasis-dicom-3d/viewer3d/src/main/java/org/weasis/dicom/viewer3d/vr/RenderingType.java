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
import org.weasis.dicom.viewer3d.vr.View3d.ViewType;

public enum RenderingType {
  COMPOSITE(Messages.getString("composite"), 0, ViewType.VOLUME3D),
  MIP(Messages.getString("mip"), 1, ViewType.VOLUME3D),
  ISO2(Messages.getString("iso.surface"), 2, ViewType.VOLUME3D),
  SLICE(Messages.getString("slice"), 3, ViewType.SLICE);
  //  SLICE_AXIAL("MPR Axial", 4, ViewType.AXIAL),
  //  SLICE_CORONAL("MPR Coronal", 5, ViewType.CORONAL),
  //  SLICE_SAGITTAL("MPR Sagittal", 6, ViewType.SAGITTAL);

  final int id;
  final String title;
  final ViewType viewType;

  RenderingType(String title, int id, ViewType viewType) {
    this.title = title;
    this.id = id;
    this.viewType = viewType;
  }

  public int getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public ViewType getViewType() {
    return viewType;
  }

  @Override
  public String toString() {
    return title;
  }
}
