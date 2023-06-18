/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.geometry;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.gui.util.SliderChangeListener;
import org.weasis.dicom.viewer3d.vr.View3d;

public abstract class ArcballMouseListener extends SliderChangeListener implements ActionState {

  private MouseActionAdapter window = null;
  private MouseActionAdapter level = null;

  public ArcballMouseListener() {
    super(ActionW.ROTATION, 0, 360, 0, true, 0.25);
  }

  @Override
  public String getValueToDisplay() {
    return getSliderValue() + " °";
  }

  private static View3d getView3d(InputEvent e) {
    if (e.getSource() instanceof View3d view3d) {
      return view3d;
    }
    return null;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    if (basicState.isActionEnabled()) {
      int buttonMask = getButtonMaskEx();
      int modifier = e.getModifiersEx();
      if (!e.isConsumed() && (modifier & buttonMask) != 0) {
        View3d view3d = getView3d(e);
        if (Objects.nonNull(view3d)) {
          view3d.getCamera().setAdjusting(true);
          if (window != null) {
            view3d.getJComponent().setCursor(ActionW.WINLEVEL.getCursor());
            window.mouseDragged(e);
            if (level != null) {
              level.mouseDragged(e);
            }
          } else {
            view3d.getCamera().rotate(e.getPoint());
          }
        }
      }
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    move(e);
  }

  @Override
  public void mousePressed(MouseEvent e) {
    if (basicState.isActionEnabled()) {
      int buttonMask = getButtonMaskEx();
      int modifier = e.getModifiersEx();
      View3d view3d = getView3d(e);
      if (view3d != null && !e.isConsumed() && (modifier & buttonMask) != 0) {
        view3d.getCamera().setAdjusting(true);
        int mask = InputEvent.CTRL_DOWN_MASK;
        if ((modifier & mask) == mask) {
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
        } else {
          releaseWinLevelAdapter();
          view3d.getCamera().init(e.getPoint());
        }
      }
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

      View3d view3d = getView3d(e);
      if (view3d != null) {
        view3d.getCamera().setAdjusting(false);
        if ((modifier & mask) == mask) {
          view3d.setCursor(null);
        }
        view3d.getCamera().init(e.getPoint());
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
      View3d view3d = getView3d(e);
      if (view3d != null) {
        view3d.setCursor(ActionW.WINLEVEL.getCursor());
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
