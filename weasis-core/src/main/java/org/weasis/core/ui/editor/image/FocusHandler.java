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
    // Do select the view when pressing on a view button
    if (evt.getClickCount() == 2 && selectedButton.isEmpty()) {
      pane.maximizedSelectedImagePane(viewCanvas, evt);
      return;
    }

    selectViewIfNeeded(pane);
    viewCanvas.getJComponent().requestFocusInWindow();

    if (selectedButton.isPresent()) {
      handleViewButtonClick(evt, selectedButton.get());
      return;
    }

    updateCursorForMouseAction(evt);
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
    if (!container.getSelectedImagePane().equals(viewCanvas)) {
      container.setSelectedImagePane(viewCanvas);
    }
  }

  @Override
  public void mouseMoved(MouseEvent evt) {
    showPixelInfos(evt);
    updateViewButtonsHoverState(evt.getPoint());
  }

  @Override
  public void mouseReleased(MouseEvent evt) {
    viewCanvas.getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);
    findViewButtonAt(evt.getPoint()).ifPresent(_ -> evt.consume());
  }

  private Optional<ViewButton> findViewButtonAt(Point point) {
    return viewCanvas.getViewButtons().stream()
        .filter(ViewButton::isVisible)
        .filter(button -> button.contains(point))
        .findFirst();
  }

  private void selectViewIfNeeded(ImageViewerPlugin<E> pane) {
    if (pane.isContainingView(viewCanvas) && pane.getSelectedImagePane() != viewCanvas) {
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
      Point3 point3d =
          viewCanvas.getVolumeCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());
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
