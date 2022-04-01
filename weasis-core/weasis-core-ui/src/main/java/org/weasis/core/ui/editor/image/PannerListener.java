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

import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.BasicActionState;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.ui.model.utils.bean.PanPoint;

public abstract class PannerListener extends MouseActionAdapter
    implements ActionState, KeyListener {

  private final BasicActionState basicState;
  private final boolean triggerAction = true;
  protected Point pickPoint;

  private Point2D point;

  protected PannerListener(ActionW action, Point2D point) {
    this.basicState = new BasicActionState(action);
    this.point = point == null ? new Point2D.Double() : point;
  }

  @Override
  public void enableAction(boolean enabled) {
    basicState.enableAction(enabled);
  }

  @Override
  public boolean isActionEnabled() {
    return basicState.isActionEnabled();
  }

  @Override
  public boolean registerActionState(Object c) {
    return basicState.registerActionState(c);
  }

  @Override
  public void unregisterActionState(Object c) {
    basicState.unregisterActionState(c);
  }

  public Point2D getPoint() {
    return (Point2D) point.clone();
  }

  public void setPoint(Point2D point) {
    if (point != null) {
      this.point = point;
      pointChanged(point);
      AuditLog.LOGGER.info("action:{} val:{},{}", getActionW().cmd(), point.getX(), point.getY());
    }
  }

  public boolean isTriggerAction() {
    return triggerAction;
  }

  @Override
  public ActionW getActionW() {
    return basicState.getActionW();
  }

  public String getValueToDisplay() {
    return "x:" + point.getX() + ", y:" + point.getY(); // NON-NLS
  }

  public abstract void pointChanged(Point2D point);

  @Override
  public String toString() {
    return basicState.getActionW().getTitle();
  }

  protected ViewCanvas<? extends ImageElement> getViewCanvas(InputEvent e) {
    Object source = e.getSource();
    if (source instanceof ViewCanvas) {
      return (ViewCanvas<?>) source;
    }
    return null;
  }

  @Override
  public void mousePressed(MouseEvent e) {
    int buttonMask = getButtonMaskEx();
    if ((e.getModifiersEx() & buttonMask) != 0) {
      ViewCanvas<?> panner = getViewCanvas(e);
      if (panner != null) {
        pickPoint = e.getPoint();
        if (panner.getViewModel() != null) {
          double scale = panner.getViewModel().getViewScale();
          setPoint(
              new PanPoint(
                  PanPoint.State.DRAGSTART,
                  -(pickPoint.getX() / scale),
                  -(pickPoint.getY() / scale)));
        }
      }
    }
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    int buttonMask = getButtonMaskEx();
    if (!e.isConsumed() && (e.getModifiersEx() & buttonMask) != 0) {
      ViewCanvas<?> panner = getViewCanvas(e);
      if (panner != null && pickPoint != null && panner.getViewModel() != null) {
        double scale = panner.getViewModel().getViewScale();
        setPoint(
            new PanPoint(
                PanPoint.State.DRAGGING,
                -((e.getX() - pickPoint.getX()) / scale),
                -((e.getY() - pickPoint.getY()) / scale)));
        panner.addPointerType(ViewCanvas.CENTER_POINTER);
      }
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    int buttonMask = getButtonMask();
    if (!e.isConsumed() && (e.getModifiers() & buttonMask) != 0) {
      ViewCanvas<?> panner = getViewCanvas(e);
      if (panner != null) {
        panner.resetPointerType(ViewCanvas.CENTER_POINTER);
        panner.getJComponent().repaint();
      }
    }
  }

  public void reset() {
    pickPoint = null;
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.getKeyCode() == KeyEvent.VK_LEFT) {
      setPoint(new PanPoint(PanPoint.State.MOVE, 5, 0));
    } else if (e.getKeyCode() == KeyEvent.VK_UP) {
      setPoint(new PanPoint(PanPoint.State.MOVE, 0, 5));
    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
      setPoint(new PanPoint(PanPoint.State.MOVE, -5, 0));
    } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
      setPoint(new PanPoint(PanPoint.State.MOVE, 0, -5));
    }
  }

  @Override
  public void keyReleased(KeyEvent e) {}

  @Override
  public void keyTyped(KeyEvent e) {}
}
