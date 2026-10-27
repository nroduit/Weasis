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

import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

/** The stack of a series read for its volume alone: no MPR series is generated from it. */
final class ReformatStack extends OriginalStack {

  ReformatStack(Plane plane, MediaSeries<DicomImageElement> series) {
    super(plane, series, null);
  }

  @Override
  public void generate(BuildContext context) {
    // The volume is resliced by the caller
  }
}
