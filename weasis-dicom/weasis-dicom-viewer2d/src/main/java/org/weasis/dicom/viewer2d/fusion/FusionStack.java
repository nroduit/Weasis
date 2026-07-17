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

import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.BuildContext;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.OriginalStack;

/**
 * Minimal {@link OriginalStack} used only to build a rectified {@link
 * org.weasis.dicom.viewer2d.mpr.Volume} for a fusion overlay series (e.g. PET). It reuses the MPR
 * volume construction without producing any derived MPR series, so {@link #generate} is a no-op.
 */
final class FusionStack extends OriginalStack {

  FusionStack(
      Plane plane, MediaSeries<DicomImageElement> series, Filter<DicomImageElement> filter) {
    super(plane, series, filter);
  }

  @Override
  public void generate(BuildContext context) {
    // Overlay volume only: no derived MPR series are generated.
  }
}
