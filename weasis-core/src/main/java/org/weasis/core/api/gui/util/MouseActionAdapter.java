/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

public abstract class MouseActionAdapter
    implements MouseListener, MouseWheelListener, MouseMotionListener {

  protected int buttonMaskEx = 0;
  protected int lastPosition = 0;
  private boolean inverse = false;
  private boolean moveOnX = false;
  private double mouseSensitivity = 1.0;
  protected double dragAccumulator = Double.MAX_VALUE;

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mousePressed(MouseEvent e) {}

  @Override
  public void mouseReleased(MouseEvent e) {}

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {}

  @Override
  public void mouseDragged(MouseEvent e) {}

  @Override
  public void mouseMoved(MouseEvent e) {}

  public double getMouseSensitivity() {
    return mouseSensitivity;
  }

  public void setMouseSensitivity(double mouseSensitivity) {
    this.mouseSensitivity = mouseSensitivity;
  }

  public int getButtonMaskEx() {
    return buttonMaskEx;
  }

  /**
   * True when the button pressed or released by the event is one this adapter is bound to. This is
   * the test to use in {@code mouseReleased}, where the extended modifiers no longer hold the
   * button.
   */
  public boolean isBoundButton(MouseEvent e) {
    int button = e.getButton();
    return button != MouseEvent.NOBUTTON
        && (buttonMaskEx & InputEvent.getMaskForButton(button)) != 0;
  }

  public void setButtonMaskEx(int buttonMask) {
    this.buttonMaskEx = buttonMask;
    // Zero is used to disable the mouse adapter
    if (buttonMask == 0) {
      // assign an invalid value to the accumulator.
      dragAccumulator = Double.MAX_VALUE;
    }
  }

  public boolean isMoveOnX() {
    return moveOnX;
  }

  public void setMoveOnX(boolean moveOnX) {
    this.moveOnX = moveOnX;
  }

  public boolean isInverse() {
    return inverse;
  }

  public void setInverse(boolean inverse) {
    this.inverse = inverse;
  }
}
