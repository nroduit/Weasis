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
import org.weasis.dicom.codec.DicomImageElement;

/**
 * The rectified volume of a series for a computation, held under a lease of the volume service for
 * the time of the computation: shared with the viewers that show it and kept for the next consumer
 * once released.
 */
public final class OwnedVolume implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(OwnedVolume.class);

  private final VolumeService.Lease lease;
  private final Volume<?, ?> volume;

  private OwnedVolume(VolumeService.Lease lease, Volume<?, ?> volume) {
    this.lease = lease;
    this.volume = volume;
  }

  /**
   * The rectified volume of the series, or null when it cannot be built (no geometry, a basic
   * volume only, allocation failure).
   */
  public static OwnedVolume of(MediaSeries<DicomImageElement> series) {
    if (series == null) {
      return null;
    }
    VolumeService.Lease lease = null;
    try {
      lease = VolumeService.getInstance().acquire(series, true, null);
      Volume<?, ?> volume;
      try (VolumeProgress.Shown shown = VolumeProgress.show(lease)) {
        volume = lease.volume();
      }
      if (volume.isBasic()) {
        lease.close();
        return null;
      }
      return new OwnedVolume(lease, volume);
    } catch (RuntimeException e) {
      if (lease != null) {
        lease.close();
      }
      LOGGER.error("Cannot build the volume of the series", e);
      return null;
    }
  }

  public Volume<?, ?> volume() {
    return volume;
  }

  @Override
  public void close() {
    lease.close();
  }
}
