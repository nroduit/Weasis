/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.view;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.Optional;
import java.util.function.Function;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.wsi.model.PixelSpacing;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.opencv.data.PlanarImage;

/**
 * The measurement space of a slide: base level pixels, calibrated by the pixel spacing of the base
 * level. It has no source image, so the graphics measure lengths, areas and angles but no pixel
 * statistics, which a gigapixel matrix does not allow.
 */
public final class WsiMeasurableLayer implements MeasurableLayer {

  private final Slide slide;
  private final Function<TagW, Object> tags;

  /**
   * @param tags the attribute values shown with the measurements (those of the series)
   */
  public WsiMeasurableLayer(Slide slide, Function<TagW, Object> tags) {
    this.slide = slide;
    this.tags = tags;
  }

  @Override
  public boolean hasContent() {
    return true;
  }

  @Override
  public Optional<Rectangle2D> getContentBounds() {
    return Optional.of(new Rectangle2D.Double(0, 0, slide.width(), slide.height()));
  }

  @Override
  public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
    Optional<PixelSpacing> spacing = slide.base().pixelSpacing();
    if (displayUnit == null || Unit.PIXEL.equals(displayUnit) || spacing.isEmpty()) {
      return new MeasurementsAdapter(1.0, 0, 0, false, 0, Unit.PIXEL.getAbbreviation());
    }
    // Square pixels are the rule for slides; the column spacing calibrates both axes
    double ratio =
        spacing.get().column()
            * displayUnit.getConversionRatio(Unit.MILLIMETER.getFactorToMeters());
    return new MeasurementsAdapter(ratio, 0, 0, false, 0, displayUnit.getAbbreviation());
  }

  @Override
  public AffineTransform getShapeTransform() {
    return null;
  }

  @Override
  public Object getSourceTagValue(TagW tagW) {
    return tags.apply(tagW);
  }

  @Override
  public String getPixelValueUnit() {
    return null;
  }

  @Override
  public Point getOffset() {
    return null;
  }

  @Override
  public void setOffset(Point offset) {
    // A slide is never cropped
  }

  @Override
  public PlanarImage getSourceRenderedImage() {
    return null;
  }

  @Override
  public double pixelToRealValue(Number pixelValue) {
    return pixelValue.doubleValue();
  }

  @Override
  public double getPixelMin() {
    return 0;
  }

  @Override
  public double getPixelMax() {
    return 0;
  }
}
