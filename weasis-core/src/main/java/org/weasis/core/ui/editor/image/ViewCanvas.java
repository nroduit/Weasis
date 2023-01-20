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
import java.awt.event.InputEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeListener;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPopupMenu;
import org.weasis.core.api.gui.Image2DViewer;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.ui.model.graphic.Graphic;
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

  default MouseActionAdapter getAction(Feature<?> action) {
    Optional<?> a = getEventManager().getAction(action);
    if (a.isPresent() && a.get() instanceof MouseActionAdapter actionAdapter) {
      return actionAdapter;
    }
    return null;
  }

  default void addModifierMask(String action, int mask) {
    MouseActionAdapter adapter = getMouseAdapter(action);
    if (adapter != null) {
      adapter.setButtonMaskEx(adapter.getButtonMaskEx() | mask);
      if (ActionW.WINLEVEL.cmd().equals(action)) {
        MouseActionAdapter win = getMouseAdapter(ActionW.WINDOW.cmd());
        if (win != null) {
          win.setButtonMaskEx(win.getButtonMaskEx() | mask);
        }
      }
    }
  }

  default void resetMouseAdapter() {
    for (ActionState adapter : getEventManager().getAllActionValues()) {
      if (adapter instanceof MouseActionAdapter mouseActionAdapter) {
        mouseActionAdapter.setButtonMaskEx(0);
      }
    }
  }

  default void addMouseAdapter(String actionName, int buttonMask) {
    MouseActionAdapter adapter = getMouseAdapter(actionName);
    if (adapter == null) {
      return;
    }
    JComponent c = getJComponent();
    adapter.setButtonMaskEx(adapter.getButtonMaskEx() | buttonMask);
    if (adapter instanceof GraphicMouseHandler) {
      c.addKeyListener(getDrawingsKeyListeners());
    } else if (adapter instanceof PannerListener pannerListener) {
      pannerListener.reset();
      c.addKeyListener(pannerListener);
    }

    if (actionName.equals(ActionW.WINLEVEL.cmd())) {
      // For window/level action set window action on x-axis
      MouseActionAdapter win = getAction(ActionW.WINDOW);
      if (win != null) {
        win.setButtonMaskEx(win.getButtonMaskEx() | buttonMask);
        win.setMoveOnX(true);
        c.addMouseListener(win);
        c.addMouseMotionListener(win);
      }
      // set level action with inverse progression (moving the cursor down will decrease the values)
      adapter.setInverse(
          getEventManager().getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    } else if (actionName.equals(ActionW.WINDOW.cmd())) {
      adapter.setMoveOnX(false);
    } else if (actionName.equals(ActionW.LEVEL.cmd())) {
      adapter.setInverse(
          getEventManager().getOptions().getBooleanProperty(WindowOp.P_INVERSE_LEVEL, true));
    }
    c.addMouseListener(adapter);
    c.addMouseMotionListener(adapter);
  }

  default void enableMouseAndKeyListener(MouseActions actions) {
    disableMouseAndKeyListener();
    iniDefaultMouseListener();
    iniDefaultKeyListener();
    // Set the buttonMask to 0 of all the actions
    resetMouseAdapter();

    getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);

    addMouseAdapter(actions.getLeft(), InputEvent.BUTTON1_DOWN_MASK); // left mouse button
    if (actions.getMiddle().equals(actions.getLeft())) {
      // If mouse action is already registered, only add the modifier mask
      addModifierMask(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK);
    } else {
      addMouseAdapter(actions.getMiddle(), InputEvent.BUTTON2_DOWN_MASK); // middle mouse button
    }
    if (actions.getRight().equals(actions.getLeft())
        || actions.getRight().equals(actions.getMiddle())) {
      // If mouse action is already registered, only add the modifier mask
      addModifierMask(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK);
    } else {
      addMouseAdapter(actions.getRight(), InputEvent.BUTTON3_DOWN_MASK); // right mouse button
    }
    getJComponent().addMouseWheelListener(getMouseAdapter(actions.getWheel()));
  }

  MouseActionAdapter getMouseAdapter(String command);

  default boolean isDrawActionActive() {
    ViewerPlugin<?> container = WinUtil.getParentOfClass(getJComponent(), ViewerPlugin.class);
    if (container != null) {
      final ViewerToolBar<?> toolBar = container.getViewerToolBar();
      if (toolBar != null) {
        return toolBar.isCommandActive(ActionW.MEASURE.cmd())
            || toolBar.isCommandActive(ActionW.DRAW.cmd());
      }
    }
    return false;
  }

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

  void resetZoom();

  void resetPan();

  void reset();

  List<ViewButton> getViewButtons();

  void closeLens();

  void updateCanvas(boolean triggerViewModelChangeListeners);

  void updateGraphicSelectionListener(ImageViewerPlugin<E> viewerPlugin);

  boolean requiredTextAntialiasing();

  JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected);

  JPopupMenu buildContextMenu(MouseEvent evt);

  boolean hasValidContent();
}
