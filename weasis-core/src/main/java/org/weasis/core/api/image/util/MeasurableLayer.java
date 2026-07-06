/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Optional;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

/**
 * Interface for layers supporting measurements and spatial operations. Provides pixel-to-real-world
 * conversions, coordinate transformations, and metadata access.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @author Nicolas Roduit
 */
public interface MeasurableLayer {

  /**
   * Checks if the layer has measurable content.
   *
   * @return true if content is available for measurement
   */
  boolean hasContent();

  /**
   * Returns the bounds of the measurable content.
   *
   * @return bounds rectangle, empty if no content
   */
  default Optional<Rectangle2D> getContentBounds() {
    return Optional.ofNullable(getSourceRenderedImage())
        .map(image -> new Rectangle2D.Double(0, 0, image.width(), image.height()));
  }

  /**
   * Returns the measurement adapter for unit conversions and transformations.
   *
   * @param displayUnit unit for measurements
   * @return measurement adapter, or null if unavailable
   */
  MeasurementsAdapter getMeasurementAdapter(Unit displayUnit);

  /**
   * Returns the measurement adapter with default pixel units.
   *
   * @return measurement adapter with pixel units
   */
  default MeasurementsAdapter getMeasurementAdapter() {
    return getMeasurementAdapter(Unit.PIXEL);
  }

  /**
   * Returns the transform applied to shapes for statistics calculations.
   *
   * @return shape transform for image coordinate conversion, or null if none
   */
  AffineTransform getShapeTransform();

  /**
   * Returns the value associated with the specified tag.
   *
   * @param tagW tag to retrieve
   * @return tag value, or null if not found
   */
  Object getSourceTagValue(TagW tagW);

  /**
   * Returns the unit of pixel values.
   *
   * @return pixel value unit as string
   */
  String getPixelValueUnit();

  /**
   * Returns the coordinate offset.
   *
   * @return offset point, or null if none applied
   */
  Point getOffset();

  /**
   * Sets the coordinate offset.
   *
   * @param offset offset point to apply
   */
  void setOffset(Point offset);

  /**
   * Returns the source image with all preprocessing operations applied.
   *
   * @return rendered image, or null if unavailable
   */
  PlanarImage getSourceRenderedImage();

  /**
   * Converts a pixel value to its real-world representation.
   *
   * @param pixelValue pixel value to convert
   * @return real-world value
   */
  double pixelToRealValue(Number pixelValue);

  /**
   * Returns the minimum pixel value in the layer.
   *
   * @return minimum pixel value
   */
  double getPixelMin();

  /**
   * Returns the maximum pixel value in the layer.
   *
   * @return maximum pixel value
   */
  double getPixelMax();

  /**
   * Returns additional layers whose region statistics should be reported alongside this layer's
   * (e.g. a fused PET overlay contributing SUV values to a CT measurement). The shapes are sampled
   * on the same pixel grid as this layer.
   *
   * @return secondary measurable layers, empty by default
   */
  default List<MeasurableLayer> getSecondaryLayers() {
    return List.of();
  }

  /**
   * Returns the label suffix used to tag statistics contributed by this layer when reported on
   * another layer's measurement (e.g. {@code "PET"}), or {@code null} for none.
   *
   * @return the statistics label suffix, or null
   */
  default String getStatLabel() {
    return null;
  }
}
