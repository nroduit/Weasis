/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.SeriesFeatures.Interest;

/**
 * Tells the images of interest of a series. The codec cannot see the key objects, presentation
 * states or drawings that make an image interesting (the explorer holds them), so the owner of that
 * knowledge plugs itself here.
 */
@FunctionalInterface
public interface InterestProvider {

  /** No image of interest, whatever the series. */
  InterestProvider NONE = series -> Interest.NONE;

  Interest interestOf(MediaSeries<DicomImageElement> series);
}
