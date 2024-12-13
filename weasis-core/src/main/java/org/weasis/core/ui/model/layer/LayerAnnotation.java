/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.Point2D;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;

public interface LayerAnnotation extends Layer {
  enum Position {
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight
  }

  boolean getDisplayPreferences(LayerItem item);

  boolean setDisplayPreferencesValue(LayerItem displayItem, boolean selected);

  Rectangle getPreloadingProgressBound();

  Rectangle getPixelInfoBound();

  void setPixelInfo(PixelInfo pixelInfo);

  PixelInfo getPixelInfo();

  int getBorder();

  void setBorder(int border);

  void paint(Graphics2D g2d);

  LayerAnnotation getLayerCopy(ViewCanvas view2DPane, boolean useGlobalPreferences);

  boolean isShowBottomScale();

  void setShowBottomScale(Boolean showBottomScale);

  void resetToDefault();

  Point2D getPosition(Position position);

  void setPosition(Position position, double x, double y);
}
