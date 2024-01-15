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

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.util.Objects;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

public class SegMeasurableLayer<E extends ImageElement> implements MeasurableLayer {

  private final double thickness;
  private Point offset;
  private final E sourceImage;

  public SegMeasurableLayer(E sourceImage) {
    this(sourceImage, 1.0);
  }

  public SegMeasurableLayer(E sourceImage, double thickness) {
    this.sourceImage = Objects.requireNonNull(sourceImage);
    this.thickness = thickness;
  }

  @Override
  public boolean hasContent() {
    return true;
  }

  @Override
  public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
    return sourceImage.getMeasurementAdapter(displayUnit, offset);
  }

  @Override
  public AffineTransform getShapeTransform() {
    return null;
  }

  @Override
  public Object getSourceTagValue(TagW tagW) {
    return sourceImage.getTagValue(tagW);
  }

  @Override
  public String getPixelValueUnit() {
    return sourceImage.getPixelValueUnit();
  }

  @Override
  public Point getOffset() {
    return offset;
  }

  @Override
  public void setOffset(Point offset) {
    this.offset = offset;
  }

  @Override
  public PlanarImage getSourceRenderedImage() {
    return sourceImage.getImage(null);
  }

  @Override
  public double pixelToRealValue(Number pixelValue) {
    Number val = sourceImage.pixelToRealValue(pixelValue, null);
    if (val != null) {
      return val.doubleValue();
    }
    return 0;
  }

  @Override
  public double getPixelMin() {
    return sourceImage.getPixelMin();
  }

  @Override
  public double getPixelMax() {
    return sourceImage.getPixelMax();
  }

  public E getSourceImage() {
    return sourceImage;
  }

  public double getThickness() {
    return thickness;
  }
}
