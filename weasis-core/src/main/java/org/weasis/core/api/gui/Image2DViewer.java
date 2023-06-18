/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.opencv.data.PlanarImage;

public interface Image2DViewer<E extends ImageElement> {

  MediaSeries<E> getSeries();

  int getFrameIndex();

  void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform);

  ViewModel getViewModel();

  ImageLayer<E> getImageLayer();

  MeasurableLayer getMeasurableLayer();

  AffineTransform getAffineTransform();

  E getImage();

  PlanarImage getSourceImage();

  Object getActionValue(String action);
}
