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
import java.io.IOException;
import java.util.Optional;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.wsi.model.PixelSpacing;
import org.weasis.dicom.wsi.model.Slide;
import org.weasis.dicom.wsi.source.TileSource;
import org.weasis.opencv.data.PlanarImage;

/**
 * The measurement space of a slide: base level pixels, calibrated by the pixel spacing of the base
 * level. It has no source image, so the graphics measure lengths, areas and angles but no pixel
 * statistics, which a gigapixel matrix does not allow.
 */
public final class WsiMeasurableLayer implements MeasurableLayer {
  private static final Logger LOGGER = LoggerFactory.getLogger(WsiMeasurableLayer.class);

  private final Slide slide;
  private final Function<TagW, Object> tags;
  private final TileSource source;
  private final int focalPlane;
  private final int opticalPath;
  private final PlanarImage image;
  private final AffineTransform shapeTransform;

  /**
   * @param tags the attribute values shown with the measurements (those of the series)
   * @param source the tiles the statistics of a region are read from, null for none
   */
  public WsiMeasurableLayer(
      Slide slide,
      Function<TagW, Object> tags,
      TileSource source,
      int focalPlane,
      int opticalPath) {
    this(slide, tags, source, focalPlane, opticalPath, null, null);
  }

  private WsiMeasurableLayer(
      Slide slide,
      Function<TagW, Object> tags,
      TileSource source,
      int focalPlane,
      int opticalPath,
      PlanarImage image,
      AffineTransform shapeTransform) {
    this.slide = slide;
    this.tags = tags;
    this.source = source;
    this.focalPlane = focalPlane;
    this.opticalPath = opticalPath;
    this.image = image;
    this.shapeTransform = shapeTransform;
  }

  @Override
  public boolean hasContent() {
    return true;
  }

  @Override
  public Optional<Rectangle2D> getContentBounds() {
    return Optional.of(new Rectangle2D.Double(0, 0, slide.width(), slide.height()));
  }

  // The pixels of a region are read from the tiles at the finest level that keeps them few enough:
  // the statistics of a large region are those of a coarser level of the pyramid
  @Override
  public MeasurableLayer forRegion(Rectangle2D bounds) {
    if (source == null || bounds == null) {
      return this;
    }
    try {
      return SlidePixels.read(source, bounds, focalPlane, opticalPath)
          .<MeasurableLayer>map(
              r ->
                  new WsiMeasurableLayer(
                      slide, tags, null, focalPlane, opticalPath, r.image(), r.baseToImage()))
          .orElse(this);
    } catch (IOException | RuntimeException e) {
      LOGGER.warn("Cannot read the pixels of the region", e);
      return this;
    }
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
    return shapeTransform == null ? null : new AffineTransform(shapeTransform);
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

  /** The pixels of the region this layer was made for, null for the layer of the whole slide. */
  @Override
  public PlanarImage getSourceRenderedImage() {
    return image;
  }

  @Override
  public double pixelToRealValue(Number pixelValue) {
    return pixelValue.doubleValue();
  }

  // The samples of a slide are stored on 8 bits
  @Override
  public double getPixelMin() {
    return 0;
  }

  @Override
  public double getPixelMax() {
    return 255;
  }
}
