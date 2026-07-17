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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Builds a rectified {@link Volume} from a fusion overlay series (e.g. PET) so it can be resliced
 * on arbitrary display planes by {@link FusionVolumeResampler}.
 *
 * <p>This is an expensive operation (it allocates the full 3D volume) and must be called off the
 * EDT.
 */
public final class FusionVolumeBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(FusionVolumeBuilder.class);

  private FusionVolumeBuilder() {}

  /**
   * Builds the rectified volume for the given series, or returns {@code null} if it cannot be built
   * (no images, non-rectifiable geometry, allocation failure).
   */
  public static Volume<?, ?> build(MediaSeries<DicomImageElement> series) {
    if (series == null) {
      return null;
    }
    DicomImageElement middle = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (middle == null) {
      return null;
    }
    FusionStack stack = new FusionStack(planeOf(middle), series, null);
    if (stack.getWidth() == 0 || stack.getHeight() == 0) {
      return null;
    }
    try {
      return Volume.createVolume(stack, null, false);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot build fusion overlay volume", e);
      return null;
    }
  }

  private static Plane planeOf(DicomImageElement image) {
    Plan orientation = ImageOrientation.getPlan(image);
    if (Plan.CORONAL.equals(orientation)) {
      return Plane.CORONAL;
    }
    if (Plan.SAGITTAL.equals(orientation)) {
      return Plane.SAGITTAL;
    }
    return Plane.AXIAL;
  }
}
