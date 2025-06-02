/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import java.util.LinkedHashSet;
import java.util.Set;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.dicom.codec.LazyContourLoader;

public class PlaneContourLoader implements LazyContourLoader {

  private final Set<SegContour> contours;

  public PlaneContourLoader() {
    this.contours = new LinkedHashSet<>();
  }

  public void addContour(SegContour contour) {
    if (contour != null) {
      contours.add(contour);
    }
  }

  public void addContours(Set<SegContour> contourList) {
    if (contourList != null && !contourList.isEmpty()) {
      contours.addAll(contourList);
    }
  }

  @Override
  public Set<SegContour> getLazyContours() {
    return contours;
  }
}
