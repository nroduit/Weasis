/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Collection;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;

public interface SpecialElementRegion {
  boolean isVisible();

  void setVisible(boolean visible);

  float getOpacity();

  void setOpacity(float opacity);

  boolean containsSopInstanceUIDReference(DicomImageElement img);

  Collection<SegContour> getContours(DicomImageElement img);
}
