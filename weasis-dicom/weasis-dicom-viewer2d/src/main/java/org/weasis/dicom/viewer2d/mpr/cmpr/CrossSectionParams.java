/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Parameters controlling a {@link CurvedMprBuilder#openCrossSectionSeries} run.
 *
 * @param widthMm slab extent along the in-plane perpendicular (mm)
 * @param heightMm slab extent along Z (mm)
 * @param spacingMm distance between consecutive cuts along the curve (mm)
 */
public record CrossSectionParams(double widthMm, double heightMm, double spacingMm) {

  /** Default parameters: 40mm wide perpendicular slab, full volume Z height, 1mm between cuts. */
  public static CrossSectionParams defaults(Volume<?, ?> volume) {
    return new CrossSectionParams(40.0, volume.getSizeZ() * volume.getPixelRatio().z, 1.0);
  }
}
