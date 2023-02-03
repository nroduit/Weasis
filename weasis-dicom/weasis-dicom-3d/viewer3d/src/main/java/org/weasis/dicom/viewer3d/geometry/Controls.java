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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import javax.swing.SwingUtilities;
import org.weasis.dicom.viewer3d.vr.View3d;

public class Controls implements MouseMotionListener, MouseWheelListener, MouseListener {
  private final View3d renderer;

  public Controls(View3d renderer) {
    this.renderer = renderer;
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    renderer.getCamera().setAdjusting(true);
    if (SwingUtilities.isLeftMouseButton(e)) {
      renderer.getCamera().rotate(e);

    } else if (SwingUtilities.isRightMouseButton(e)) {
      renderer.getCamera().translate(e);
    }
  }

  @Override
  public void mouseMoved(MouseEvent e) {}

  @Override
  public void mouseWheelMoved(MouseWheelEvent e) {
    float delta = (float) e.getPreciseWheelRotation();
    if (Math.abs(delta) < 1e-2f) {
      return;
    }
    renderer.getCamera().zoom(-delta);
  }

  @Override
  public void mouseClicked(MouseEvent e) {}

  @Override
  public void mousePressed(MouseEvent e) {
    renderer.getCamera().setAdjusting(true);
    renderer.getCamera().init(e);
  }

  @Override
  public void mouseReleased(MouseEvent e) {
    renderer.getCamera().setAdjusting(false);
    renderer.getCamera().init(e);
  }

  @Override
  public void mouseEntered(MouseEvent e) {}

  @Override
  public void mouseExited(MouseEvent e) {}
}
