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
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewCanvas;

public interface LayerAnnotation extends Layer {

  String ANNOTATIONS = Messages.getString("AnnotationsLayer.anno");
  String MIN_ANNOTATIONS = Messages.getString("LayerAnnotation.min_anot");
  String ANONYM_ANNOTATIONS = Messages.getString("AnnotationsLayer.anonym");
  String SCALE = Messages.getString("AnnotationsLayer.scale");
  String LUT = Messages.getString("AnnotationsLayer.lut");
  String IMAGE_ORIENTATION = Messages.getString("AnnotationsLayer.or");
  String WINDOW_LEVEL = Messages.getString("AnnotationsLayer.wl");
  String ZOOM = Messages.getString("AnnotationsLayer.zoom");
  String ROTATION = Messages.getString("AnnotationsLayer.rot");
  String FRAME = Messages.getString("AnnotationsLayer.fr");
  String PIXEL = Messages.getString("AnnotationsLayer.pix");
  String PRELOADING_BAR = Messages.getString("AnnotationsLayer.preload_bar");
  String KEY_OBJECT = Messages.getString("AnnotationsLayer.ko");

  boolean getDisplayPreferences(String item);

  boolean setDisplayPreferencesValue(String displayItem, boolean selected);

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
}
