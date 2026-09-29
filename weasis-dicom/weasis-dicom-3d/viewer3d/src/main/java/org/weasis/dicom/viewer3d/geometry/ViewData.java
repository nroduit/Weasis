/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public record ViewData(String title, Vector3dc position, Quaterniondc rotation, double zoom)
    implements View {

  /** Copies the vectors so the preset cannot change under a caller that keeps mutating them. */
  public ViewData {
    position = new Vector3d(position);
    rotation = new Quaterniond(rotation);
  }
}
