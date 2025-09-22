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
import java.util.Objects;
import java.util.Optional;
import org.opencv.core.Point3;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.util.StringUtil;

public class FocusHandler<E extends ImageElement> extends MouseActionAdapter {
  private final ViewCanvas<E> viewCanvas;

  public FocusHandler(ViewCanvas<E> viewCanvas) {
    this.viewCanvas = viewCanvas;
  }

  @Override
  public void mousePressed(MouseEvent evt) {
    ImageViewerPlugin<E> pane = viewCanvas.getEventManager().getSelectedView2dContainer();
    if (Objects.isNull(pane)) {
      return;
    }

    ViewButton selectedButton = null;
    // Do select the view when pressing on a view button
    for (ViewButton b : viewCanvas.getViewButtons()) {
      if (b.isVisible() && b.contains(evt.getPoint())) {
        selectedButton = b;
        break;
      }
    }

    if (evt.getClickCount() == 2 && selectedButton == null) {
      pane.maximizedSelectedImagePane(viewCanvas, evt);
      return;
    }

    if (pane.isContainingView(viewCanvas) && pane.getSelectedImagePane() != viewCanvas) {
      // register all actions of the EventManager with this view waiting the focus gained in some
      // cases is not
      // enough, because others mouseListeners are triggered before the focus event (that means
      // before
      // registering the view in the EventManager)
      pane.setSelectedImagePane(viewCanvas);
    }
    // request the focus even it is the same pane selected
    viewCanvas.getJComponent().requestFocusInWindow();

    // Do select the view when pressing on a view button
    if (selectedButton != null) {
      viewCanvas.getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);
      evt.consume();
      selectedButton.showPopup(evt.getComponent(), evt.getX(), evt.getY());
      return;
    }

    Optional<Feature<? extends ActionState>> action =
        viewCanvas.getEventManager().getMouseAction(evt.getModifiersEx());
    viewCanvas
        .getJComponent()
        .setCursor(action.isPresent() ? action.get().getCursor() : DefaultView2d.DEFAULT_CURSOR);
  }

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    ImageViewerEventManager<E> eventManager = viewCanvas.getEventManager();
    if (eventManager != null) {
      ImageViewerPlugin<E> container =
          WinUtil.getParentOfClass(viewCanvas.getJComponent(), ImageViewerPlugin.class);
      if (container != null) {
        if (!container.equals(eventManager.getSelectedView2dContainer())) {
          eventManager.setSelectedView2dContainer(container);
        }
        if (!container.getSelectedImagePane().equals(viewCanvas)) {
          container.setSelectedImagePane(viewCanvas);
        }
      }
    }
  }

  @Override
  public void mouseMoved(MouseEvent evt) {
    showPixelInfos(evt);
    for (ViewButton b : viewCanvas.getViewButtons()) {
      if (b.isVisible()) {
        boolean hover = b.contains(evt.getPoint());
        if (hover != b.isHover()) {
          b.setHover(hover);
          viewCanvas.getJComponent().repaint();
        }
      }
    }
  }

  @Override
  public void mouseReleased(MouseEvent evt) {
    viewCanvas.getJComponent().setCursor(DefaultView2d.DEFAULT_CURSOR);
    for (ViewButton b : viewCanvas.getViewButtons()) {
      if (b.isVisible() && b.contains(evt.getPoint())) {
        evt.consume();
        break;
      }
    }
  }

  protected void showPixelInfos(MouseEvent mouseevent) {
    LayerAnnotation infoLayer = viewCanvas.getInfoLayer();
    if (infoLayer != null) {
      Point2D pModel =
          viewCanvas.getImageCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());
      PixelInfo pixelInfo =
          viewCanvas.getPixelInfo(
              new Point((int) Math.floor(pModel.getX()), (int) Math.floor(pModel.getY())));
      if (pixelInfo == null) {
        return;
      }
      Rectangle oldBound = infoLayer.getPixelInfoBound();
      Point3 point3d =
          viewCanvas.getVolumeCoordinatesFromMouse(mouseevent.getX(), mouseevent.getY());
      pixelInfo.setPosition3d(point3d);
      oldBound.width =
          Math.max(
              oldBound.width,
              viewCanvas
                      .getJComponent()
                      .getGraphics()
                      .getFontMetrics()
                      .stringWidth(
                          Messages.getString("DefaultView2d.pix")
                              + StringUtil.COLON_AND_SPACE
                              + pixelInfo)
                  + 4);
      infoLayer.setPixelInfo(pixelInfo);
      viewCanvas.getJComponent().repaint(oldBound);
    }
  }
}
