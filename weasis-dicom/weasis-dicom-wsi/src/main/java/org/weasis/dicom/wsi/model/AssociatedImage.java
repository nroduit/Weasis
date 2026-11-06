/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

/** A thumbnail, overview or label image of a slide, outside the pyramid. */
public record AssociatedImage(WsiImageType type, String sopInstanceUID, int width, int height) {

  static AssociatedImage of(WsiImageType type, Attributes dcm) {
    int columns = dcm.getInt(Tag.Columns, 0);
    int rows = dcm.getInt(Tag.Rows, 0);
    return new AssociatedImage(
        type,
        dcm.getString(Tag.SOPInstanceUID),
        dcm.getInt(Tag.TotalPixelMatrixColumns, columns),
        dcm.getInt(Tag.TotalPixelMatrixRows, rows));
  }
}
