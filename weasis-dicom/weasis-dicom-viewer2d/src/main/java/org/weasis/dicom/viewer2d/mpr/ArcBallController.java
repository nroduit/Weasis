/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Vector2d;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;

public abstract class ArcBallController extends SliderChangeListener implements ActionState {
  private final MprController controller;
  private final Vector2d lastMousePosition;

  private MouseActionAdapter window = null;
  private MouseActionAdapter level = null;

  protected ArcBallController(MprController controller) {
    super(ActionW.ROTATION, 0, 360, 0, true, 0.25);
    this.controller = controller;
    this.lastMousePosition = new Vector2d();
  }

  @Override
  public String getValueToDisplay() {
    return getSliderValue() + " °";
  }

  private Vector3d mapToSphere(Vector2d point) {
    double x = point.x;
    double y = point.y;
    double z = 0.0;

    double lengthSquared = x * x + y * y;

    if (lengthSquared > 1.0) {
      double length = Math.sqrt(lengthSquared);
      x /= length;
      y /= length;
    } else {
      z = Math.sqrt(1.0 - lengthSquared);
    }

    return new Vector3d(x, y, z);
  }

  private static MprView getMprView(InputEvent e) {
    if (e.getSource() instanceof MprView view) {
      return view;
    }
    return null;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (basicState.isActionEnabled()) {
      int buttonMask = getButtonMaskEx();
      int modifier = e.getModifiersEx();
      //    if (!e.isConsumed() && (modifier & buttonMask) != 0) {
      MprView view = getMprView(e);
      if (Objects.nonNull(view)) {
        if (window != null) {
          view.getJComponent().setCursor(ActionW.WINLEVEL.getCursor());
          window.mouseDragged(e);
          if (level != null) {
            level.mouseDragged(e);
          }
        } else {
          int width = view.getWidth();
          int height = view.getHeight();

          Vector2d currentMousePosition = new Vector2d(e.getX(), e.getY());
          Vector2d lastPos =
              new Vector2d(lastMousePosition)
                  .sub(width / 2.0, height / 2.0)
                  .div(width / 2.0, height / 2.0);
          Vector2d currentPos =
              new Vector2d(currentMousePosition)
                  .sub(width / 2.0, height / 2.0)
                  .div(width / 2.0, height / 2.0);

          Vector3d lastVec = mapToSphere(lastPos);
          Vector3d currentVec = mapToSphere(currentPos);

          Quaterniond delta = new Quaterniond().rotationTo(lastVec, currentVec);
          controller.initRotation(delta);
          // controller.rotateXYZ(0, 0, Math.toRadians(1));
          lastMousePosition.set(currentMousePosition);
        }
      }
      //    }
    }
  }

  //  public Vector3d getPosition(Point2D p) {
  //    return new Vector3d(
  //        row.x * voxelSpacing.x * p.getX() + column.x * voxelSpacing.y * p.getY() + tlhc.x,
  //        row.y * voxelSpacing.x * p.getX() + column.y * voxelSpacing.y * p.getY() + tlhc.y,
  //        row.z * voxelSpacing.x * p.getX() + column.z * voxelSpacing.y * p.getY() + tlhc.z);
  //  }

  @Override
  public void mouseMoved(MouseEvent e) {
    move(e);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (basicState.isActionEnabled()) {
      int buttonMask = getButtonMaskEx();
      int modifier = e.getModifiersEx();
      MprView view3d = getMprView(e);
      //    if (view3d != null && !e.isConsumed() && (modifier & buttonMask) != 0) {
      int mask = InputEvent.CTRL_DOWN_MASK;
      if ((modifier & mask) == mask) {
        if (view3d != null) {
          view3d.setCursor(ActionW.WINLEVEL.getCursor());
          window = view3d.getAction(ActionW.WINDOW);
          if (window != null) {
            window.setButtonMaskEx(window.getButtonMaskEx() | buttonMask);
            window.setMoveOnX(true);
            window.mousePressed(e);
          }
          level = view3d.getAction(ActionW.LEVEL);
          if (level != null) {
            level.setButtonMaskEx(level.getButtonMaskEx() | buttonMask);
            level.setInverse(true);
            level.mousePressed(e);
          }
        }
      } else {
        releaseWinLevelAdapter();
        lastMousePosition.set(e.getX(), e.getY());
      }
      //    }
    }
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    releaseWinLevelAdapter();
    int modifier = e.getModifiersEx();
    if (basicState.isActionEnabled()
        && !e.isConsumed()
        && (e.getModifiers() & getButtonMask()) != 0) {
      int mask = InputEvent.CTRL_DOWN_MASK;

      MprView view = getMprView(e);
      if (view != null) {
        if ((modifier & mask) == mask) {
          view.setCursor(null);
        }
        lastMousePosition.set(e.getX(), e.getY());
      }
    }
  }

  @Override
  public void mouseEntered(MouseEvent e) {
    move(e);
  }

  @Override
  public void mouseExited(MouseEvent e) {
    move(e);
  }

  private void move(MouseEvent e) {
    int mask = InputEvent.CTRL_DOWN_MASK;
    if ((e.getModifiersEx() & mask) == mask) {
      MprView view = getMprView(e);
      if (view != null) {
        view.setCursor(ActionW.WINLEVEL.getCursor());
      }
    }
  }

  private void releaseWinLevelAdapter() {
    if (window != null) {
      window.setButtonMaskEx(0);
      window = null;
    }
    if (level != null) {
      level.setButtonMaskEx(0);
      level = null;
    }
  }
}
