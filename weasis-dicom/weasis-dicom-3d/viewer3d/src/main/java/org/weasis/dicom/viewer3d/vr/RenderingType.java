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

public enum RenderingType {
  COMPOSITE("Composite", 0),
  MIP("MIP", 2),
  ISO2("Iso surface", 3),
  ALPHA("Alpha-blending", 1),
  SLICE("Slice", 4),
  SLICE_ORTHO("Orthogonal slices", 5);

  final int id;
  final String title;

  RenderingType(String title, int id) {
    this.title = title;
    this.id = id;
  }

  public int getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String toString() {
    return title;
  }
}
