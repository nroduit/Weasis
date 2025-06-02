/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.Set;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;

/** Interface for different implementations of lazy contour loading strategies. */
public interface LazyContourLoader {

  /**
   * Retrieve or generate contours lazily.
   *
   * @return a set of {@link SegContour} representing the contours.
   */
  Set<SegContour> getLazyContours();
}
