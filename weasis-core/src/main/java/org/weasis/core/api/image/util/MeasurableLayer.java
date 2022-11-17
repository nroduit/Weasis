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
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

/**
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 */
public interface MeasurableLayer {

  boolean hasContent();

  MeasurementsAdapter getMeasurementAdapter(Unit displayUnit);

  // Only for statistics:
  AffineTransform getShapeTransform();

  Object getSourceTagValue(TagW tagW);

  String getPixelValueUnit();

  Point getOffset();

  void setOffset(Point p);

  /**
   * Returns the source image for display. All preprocessing operations has been applied to this
   * image.
   *
   * @return the source image for display
   */
  PlanarImage getSourceRenderedImage();

  double pixelToRealValue(Number pixelValue);

  double getPixelMin();

  double getPixelMax();
}
