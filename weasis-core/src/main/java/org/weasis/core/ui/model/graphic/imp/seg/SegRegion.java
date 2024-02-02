/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic.imp.seg;

import java.awt.Color;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.seg.RegionAttributes;

public class SegRegion<E extends ImageElement> extends RegionAttributes {

  private SegMeasurableLayer<E> measurableLayer;

  public SegRegion(int id, String label, Color color) {
    super(id, label, color);
    restPixels();
  }

  public void addPixels(SegContour region) {
    this.numberOfPixels += region.getNumberOfPixels();
  }

  public void restPixels() {
    this.numberOfPixels = 0;
  }

  public SegMeasurableLayer<E> getMeasurableLayer() {
    return measurableLayer;
  }

  public void setMeasurableLayer(SegMeasurableLayer<E> measurableLayer) {
    this.measurableLayer = measurableLayer;
  }
}
