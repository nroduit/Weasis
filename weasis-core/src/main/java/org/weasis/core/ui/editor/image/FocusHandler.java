/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.util.Optional;
import org.opencv.core.Point3;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.InfoValue;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.util.StringUtil;

/**
 * Handles focus and mouse interactions for image viewer canvas. Manages view selection, button
 * interactions, and pixel information display.
 *
 * @param <E> the type of image element
 */
public final class FocusHandler<E extends ImageElement> extends MouseActionAdapter {
  private static final long UPDATE_INTERVAL_MS = 50;

  private final ViewCanvas<E> viewCanvas;
  private long lastUpdateTime;

  private boolean buttonPressActive;

  /** The cursor before the pointer entered a value of the corner text, restored when it leaves. */
  private Cursor cursorBeforeValue;

  public FocusHandler(ViewCanvas<E> viewCanvas) {
    this.viewCanvas = viewCanvas;
  }

  @Override
  public void mousePressed(MouseEvent evt) {
    var pane = viewCanvas.getEventManager().getSelectedView2dContainer();
    if (pane == null) {
      return;
    }

    var selectedButton = findViewButtonAt(evt.getPoint());

    if (selectedButton.isPresent()) {
      // Consume immediately so action adapters that check isConsumed() (SliderChangeListener,
      // CrosshairListener, GraphicMouseHandler, …) skip the event before any side effect runs.
      buttonPressActive = true;
      evt.consume();
      selectViewIfNeeded(pane);
      viewCanvas.getJComponent().requestFocusInWindow();
      handleViewButtonClick(evt, selectedButton.get());
      return;
    }

    Optional<InfoValue> value = findInfoValueAt(evt.getPoint());
    if (value.isPresent() && evt.getButton() == MouseEvent.BUTTON1) {
      buttonPressActive = true;
      evt.consume();
      selectViewIfNeeded(pane);
      viewCanvas.getJComponent().requestFocusInWindow();
      // The editors open above the value: the values are at the bottom of the view
      Rectangle area = value.get().getBounds();
      value.get().getEditor().showPopup(evt.getComponent(), area.x, area.y);
      return;
    }

    buttonPressActive = false;

    if (evt.getClickCount() == 2) {
      pane.maximizedSelectedImagePane(viewCanvas, evt);
      return;
    }

    selectViewIfNeeded(pane);
    viewCanvas.getJComponent().requestFocusInWindow();
    updateCursorForMouseAction(evt);
  }

  @Override
  public void mouseDragged(MouseEvent evt) {
    if (buttonPressActive) {
      evt.consume();
    }
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    ImageViewerEventManager<E> eventManager = viewCanvas.getEventManager();
    if (eventManager == null) {
      return;
    }
    ImageViewerPlugin<E> container =
        WinUtil.getParentOfClass(viewCanvas.getJComponent(), ImageViewerPlugin.class);
    if (container == null) {
      return;
    }

    if (!container.equals(eventManager.getSelectedView2dContainer())) {
      eventManager.setSelectedView2dContainer(container);
    }
    if (!container.getSelectedViewCanvas().equals(viewCanvas)) {
      container.setSelectedImagePane(viewCanvas);
    }
  }

  @Override
  public void mouseMoved(MouseEvent evt) {
    showPixelInfos(evt);
    updateViewButtonsHoverState(evt.getPoint());
    if (updateInfoValuesHoverState(evt.getPoint())) {
      // Over a value: the graphic handler must not change the cursor
      evt.consume();
    }
  }

  @Override
  public void mouseExited(MouseEvent evt) {
    updateInfoValuesHoverState(null);
  }

  @Override
  public void mouseReleased(MouseEvent evt) {
    viewCanvas
        .getJComponent()
        .setCursor(
            cursorBeforeValue == null
                ? DefaultView2d.DEFAULT_CURSOR
                : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    if (buttonPressActive) {
      // Consume even if the release point drifted off the button; clear the flag so a real
      // subsequent drag (Pan, Scroll, …) is not wrongly suppressed.
      evt.consume();
      buttonPressActive = false;
    } else {
      findViewButtonAt(evt.getPoint()).ifPresent(_ -> evt.consume());
    }
  }

  private Optional<InfoValue> findInfoValueAt(Point point) {
    // Only the values drawn by the last paint: a hidden layer may still draw some (a tag)
    LayerAnnotation<?> infoLayer = viewCanvas.getInfoLayer();
    if (infoLayer == null) {
      return Optional.empty();
    }
    return infoLayer.getInfoValues().stream().filter(v -> v.contains(point.x, point.y)).findFirst();
  }

  /**
   * Shows the hover background and the hand cursor on the value under the pointer.
   *
   * @param point the pointer, null when it left the view
   * @return whether the pointer is over a value
   */
  private boolean updateInfoValuesHoverState(Point point) {
    LayerAnnotation<?> infoLayer = viewCanvas.getInfoLayer();
    if (infoLayer == null) {
      return false;
    }
    var component = viewCanvas.getJComponent();
    boolean over = false;
    for (InfoValue value : infoLayer.getInfoValues()) {
      boolean hover = point != null && value.contains(point.x, point.y);
      over |= hover;
      if (hover != value.isHover()) {
        value.setHover(hover);
        component.repaint(value.getBounds());
      }
    }
    if (over && cursorBeforeValue == null) {
      cursorBeforeValue = component.getCursor();
      component.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    } else if (!over && cursorBeforeValue != null) {
      component.setCursor(cursorBeforeValue);
      cursorBeforeValue = null;
    }
    return over;
  }

  /** The tooltip of the value under the pointer, or null. */
  public String getInfoValueTooltip(Point point) {
    return findInfoValueAt(point).map(InfoValue::getTooltip).orElse(null);
  }

  private Optional<ViewButton> findViewButtonAt(Point point) {
    return viewCanvas.getViewButtons().stream()
        .filter(ViewButton::isVisible)
        .filter(button -> button.contains(point))
        .findFirst();
  }

  private void selectViewIfNeeded(ImageViewerPlugin<E> pane) {
    if (pane.isContainingView(viewCanvas) && pane.getSelectedViewCanvas() != viewCanvas) {
      // Register all EventManager actions immediately with this view. Waiting for focus gain
      // is not enough since other MouseListeners may trigger before the focus event occurs,
      // resulting in the view not yet being registered in the EventManager.
      pane.setSelectedImagePane(viewCanvas);
    }
  }

  private void handleViewButtonClick(MouseEvent evt, ViewButton button) {
    viewCanvas.getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);
    evt.consume(); // Consume event to not select the view
    button.showPopup(evt.getComponent(), evt.getX(), evt.getY());
    if (button instanceof SynchViewButton) {
      viewCanvas.getJComponent().repaint();
    }
  }

  private void updateCursorForMouseAction(MouseEvent evt) {
    var action = viewCanvas.getEventManager().getMouseAction(evt.getModifiersEx());
    var cursor = action.map(Feature::getCursor).orElse(DefaultView2d.DEFAULT_CURSOR);
    viewCanvas.getJComponent().setCursor(cursor);
  }

  private void updateViewButtonsHoverState(Point mousePoint) {
    viewCanvas.getViewButtons().stream()
        .filter(ViewButton::isVisible)
        .forEach(
            button -> {
              boolean hover = button.contains(mousePoint);
              if (hover != button.isHover()) {
                button.setHover(hover);
                viewCanvas.getJComponent().repaint();
              }
            });
  }

  private void showPixelInfos(MouseEvent mouseevent) {
    var infoLayer = viewCanvas.getInfoLayer();
    if (!isPixelInfoVisible(infoLayer)) {
      return;
    }

    if (!shouldUpdatePixelInfo()) {
      return;
    }

    var pixelInfo = computePixelInfo(mouseevent);
    if (pixelInfo == null) {
      return;
    }
    updatePixelInfoDisplay(pixelInfo);
  }

  private boolean isPixelInfoVisible(LayerAnnotation infoLayer) {
    return infoLayer != null
        && infoLayer.getVisible()
        && infoLayer.getDisplayPreferences(LayerItem.PIXEL)
        && !infoLayer.getDisplayPreferences(LayerItem.MIN_ANNOTATIONS);
  }

  private boolean shouldUpdatePixelInfo() {
    long currentTime = System.currentTimeMillis();
    if (currentTime - lastUpdateTime < UPDATE_INTERVAL_MS) {
      return false;
    }
    lastUpdateTime = currentTime;
    return true;
  }

  private PixelInfo computePixelInfo(MouseEvent mouseevent) {
    Point2D pModel = viewCanvas.getImageCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());
    var pixelInfo =
        viewCanvas.getPixelInfo(
            new Point((int) Math.floor(pModel.getX()), (int) Math.floor(pModel.getY())));
    if (pixelInfo != null) {
      Point3 point3d = viewCanvas.getVolumePoint3FromMouse(mouseevent.getX(), mouseevent.getY());
      pixelInfo.setPosition3d(point3d);
    }

    return pixelInfo;
  }

  private void updatePixelInfoDisplay(PixelInfo pixelInfo) {
    var infoLayer = viewCanvas.getInfoLayer();
    Rectangle oldBound = infoLayer.getPixelInfoBound();
    int textWidth = calculatePixelInfoTextWidth(pixelInfo);
    oldBound.width = Math.max(oldBound.width, textWidth + 4);

    infoLayer.setPixelInfo(pixelInfo);
    viewCanvas.getJComponent().repaint(oldBound);
  }

  private int calculatePixelInfoTextWidth(PixelInfo pixelInfo) {
    String text =
        Messages.getString("DefaultView2d.pix")
            + StringUtil.COLON_AND_SPACE
            + pixelInfo.getPixelValueText()
            + " - "
            + pixelInfo.getPixelPositionText();

    return viewCanvas
        .getJComponent()
        .getFontMetrics(viewCanvas.getJComponent().getFont())
        .stringWidth(text);
  }
}
