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

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Objects;
import org.weasis.core.ui.model.GraphicModel;

public class DrawingsKeyListeners implements KeyListener {
  private final Canvas canvas;

  public DrawingsKeyListeners(Canvas canvas) {
    this.canvas = Objects.requireNonNull(canvas);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    GraphicModel graphicManager = canvas.getGraphicManager();
    if (e.getKeyCode() == KeyEvent.VK_DELETE) {
      graphicManager.deleteSelectedGraphics(canvas, true);
    } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D) {
      graphicManager.setSelectedGraphic(null);
    } else if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
      graphicManager.setSelectedAllGraphics();
    }
    // FIXME arrows is already used with pan!
    // else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
    // layerModel.moveSelectedGraphics(-1, 0);
    // }
    // else if (e.getKeyCode() == KeyEvent.VK_UP) {
    // layerModel.moveSelectedGraphics(0, -1);
    // }
    // else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
    // layerModel.moveSelectedGraphics(1, 0);
    // }
    // else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
    // layerModel.moveSelectedGraphics(0, 1);
    // }
  }

  @Override
  public void keyReleased(KeyEvent e) {
    // Do Nothing
  }

  @Override
  public void keyTyped(KeyEvent e) {
    // DO nothing
  }
}
