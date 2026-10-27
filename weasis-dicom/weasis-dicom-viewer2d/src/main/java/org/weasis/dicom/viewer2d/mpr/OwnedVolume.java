/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/**
 * The rectified volume of a series for a computation, released with it unless a viewer already
 * holds the same volume, in which case the viewer keeps it.
 */
public final class OwnedVolume implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(OwnedVolume.class);

  private final Volume<?, ?> volume;
  private final boolean owned;

  private OwnedVolume(Volume<?, ?> volume, boolean owned) {
    this.volume = volume;
    this.owned = owned;
  }

  /**
   * The rectified volume of the series, or null when it cannot be built (no geometry, a basic
   * volume only, allocation failure).
   */
  public static OwnedVolume of(MediaSeries<DicomImageElement> series) {
    DicomImageElement middle =
        series == null ? null : series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (middle == null) {
      return null;
    }
    try {
      ReformatStack stack = new ReformatStack(Plane.getAcquisitionPlane(middle), series);
      if (stack.getWidth() == 0 || stack.getHeight() == 0) {
        return null;
      }
      boolean shared = Volume.getSharedVolume(stack) != null;
      Volume<?, ?> volume = Volume.createVolume(stack, null, false);
      return volume == null || volume.isBasic() ? null : new OwnedVolume(volume, !shared);
    } catch (RuntimeException e) {
      LOGGER.error("Cannot build the volume of the series", e);
      return null;
    }
  }

  public Volume<?, ?> volume() {
    return volume;
  }

  @Override
  public void close() {
    if (owned) {
      volume.removeData();
    }
  }
}
