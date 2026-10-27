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

import org.pf4j.Extension;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.editor.VolumePrefetcher;
import org.weasis.dicom.codec.DicomImageElement;

/** Hands a prefetch to the volume service. */
@Extension
public class MprVolumePrefetch implements VolumePrefetcher {

  @Override
  @SuppressWarnings("unchecked")
  public void prefetch(MediaSeries<? extends MediaElement> series) {
    if (series != null
        && series.getMedia(MediaSeries.MEDIA_POSITION.FIRST, null, null)
            instanceof DicomImageElement) {
      VolumeService.getInstance().prefetch((MediaSeries<DicomImageElement>) series);
    }
  }
}
