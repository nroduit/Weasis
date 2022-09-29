/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.FocusListener;
import java.awt.event.KeyListener;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import javax.swing.Action;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.model.utils.bean.PanPoint;

public interface ViewCanvas<E extends ImageElement>
    extends Canvas,
        Image2DViewer<E>,
        PropertyChangeListener,
        FocusListener,
        ImageLayerChangeListener<E>,
        KeyListener {

  String ZOOM_TYPE_CMD = "zoom.type";
  int CENTER_POINTER = 1 << 1;
  int HIGHLIGHTED_POINTER = 1 << 2;

  void registerDefaultListeners();

  void copyActionWState(HashMap<String, Object> actionsInView);

  ImageViewerEventManager<E> getEventManager();

  void updateSynchState();

  PixelInfo getPixelInfo(Point p);

  Panner<E> getPanner();

  void setSeries(MediaSeries<E> series);

  void setSeries(MediaSeries<E> newSeries, E selectedMedia);

  void setFocused(Boolean focused);

  double getRealWorldViewScale();

  LayerAnnotation getInfoLayer();

  int getTileOffset();

  void setTileOffset(int tileOffset);

  /** Center the image into the view. */
  void center();

  /**
   * Set the offset from the center of the view. (0,0) will center the image into the view.
   *
   * @param modelOffsetX the X-offset
   * @param modelOffsetY the Y-offset
   */
  void setCenter(Double modelOffsetX, Double modelOffsetY);

  void moveOrigin(PanPoint point);

  Comparator<E> getCurrentSortComparator();

  void setActionsInView(String action, Object value);

  void setActionsInView(String action, Object value, Boolean repaint);

  void setSelected(Boolean selected);

  Font getFont();

  Font getLayerFont();

  void setDrawingsVisibility(Boolean visible);

  Object getLensActionValue(String action);

  void changeZoomInterpolation(Interpolation interpolation);

  OpManager getDisplayOpManager();

  void disableMouseAndKeyListener();

  void iniDefaultMouseListener();

  void iniDefaultKeyListener();

  int getPointerType();

  void setPointerType(int pointerType);

  void addPointerType(int i);

  void resetPointerType(int i);

  Point2D getHighlightedPosition();

  void drawPointer(Graphics2D g, Double x, Double y);

  List<Action> getExportActions();

  void enableMouseAndKeyListener(MouseActions mouseActions);

  void resetZoom();

  void resetPan();

  void reset();

  List<ViewButton> getViewButtons();

  void closeLens();

  void updateCanvas(boolean triggerViewModelChangeListeners);

  void updateGraphicSelectionListener(ImageViewerPlugin<E> viewerPlugin);

  boolean requiredTextAntialiasing();
}
