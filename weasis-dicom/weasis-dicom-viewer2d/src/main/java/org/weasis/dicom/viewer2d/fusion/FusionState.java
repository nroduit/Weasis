/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.op.lut.ByteLut;

/**
 * Immutable snapshot of a view's active fusion configuration, used to seed a newly opened MPR with
 * the same overlay as the 2D view it was launched from. The MPR panes get their own {@link
 * FusionOp}, so they can be customized independently afterwards.
 *
 * @param series the overlay (e.g. PET/NM) series fused on top of the base image
 * @param lut the overlay color LUT
 * @param baseOpacity the base (CT/MR) opacity in {@code [0, 1]}
 * @param overlayOpacity the overlay opacity in {@code [0, 1]}
 * @param volume the rectified overlay volume already built for the source view, or {@code null} to
 *     have the target rebuild it
 */
public record FusionState(
    MediaSeries<DicomImageElement> series,
    ByteLut lut,
    double baseOpacity,
    double overlayOpacity,
    Volume<?, ?> volume) {}
