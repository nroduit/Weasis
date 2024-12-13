/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

/** The VolParams record represents volume parameters in a 3D space. */
public record VolParams(
    int width,
    int height,
    int depth,
    SliceOrientation orientation,
    double[] pixelSpacing,
    double[] imageOrientation,
    Object[] imagePosition) {}
