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
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard;

public class DrawingsKeyListeners implements KeyListener {
  private final Canvas canvas;

  public DrawingsKeyListeners(Canvas canvas) {
    this.canvas = Objects.requireNonNull(canvas);
  }

  /**
   * The keys a drawing in progress owns: they are evaluated before the common display shortcuts, so
   * that Escape cancels the draft instead of resetting the display. Returns true when the event was
   * handled here.
   */
  public boolean handleDrawingKeys(KeyEvent e) {
    GraphicMouseHandler<?> handler = canvas.getGraphicMouseHandler();
    if (handler == null || !handler.isDrawing()) {
      return false;
    }
    ShortcutManager sm = ShortcutManager.getInstance();
    if (sm.matches(ShortcutManager.ID_DRAW_CANCEL_DRAWING, e)) {
      return handler.cancelDrawing();
    }
    if (sm.matches(ShortcutManager.ID_DRAW_REMOVE_LAST_VERTEX, e)) {
      return handler.removeLastVertex();
    }
    return false;
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.isConsumed() || handleDrawingKeys(e)) {
      return;
    }
    ShortcutManager sm = ShortcutManager.getInstance();
    GraphicModel graphicManager = canvas.getGraphicManager();
    if (sm.matches(ShortcutManager.ID_DRAW_DELETE, e)) {
      graphicManager.deleteSelectedGraphics(canvas, true);
    } else if (sm.matches(ShortcutManager.ID_DRAW_DESELECT_ALL, e)) {
      graphicManager.setSelectedGraphic(null);
    } else if (sm.matches(ShortcutManager.ID_DRAW_SELECT_ALL, e)) {
      graphicManager.setSelectedAllGraphics();
    } else {
      handleClipboardKeys(sm, e);
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

  /** Cut, copy, paste and duplicate; a paste lands where the cursor last was on this view. */
  private void handleClipboardKeys(ShortcutManager sm, KeyEvent e) {
    if (!(canvas instanceof ViewCanvas<?> view)) {
      return;
    }
    if (sm.matches(ShortcutManager.ID_DRAW_COPY, e)) {
      GraphicEditActions.copy(view);
    } else if (sm.matches(ShortcutManager.ID_DRAW_CUT, e)) {
      GraphicEditActions.cut(view);
    } else if (sm.matches(ShortcutManager.ID_DRAW_PASTE_IN_PLACE, e)) {
      GraphicEditActions.paste(view, GraphicClipboard.PasteMode.IN_PLACE, null);
    } else if (sm.matches(ShortcutManager.ID_DRAW_PASTE, e)) {
      GraphicEditActions.paste(
          view, GraphicClipboard.defaultPasteMode(), view.getCursorImagePoint());
    } else if (sm.matches(ShortcutManager.ID_DRAW_DUPLICATE, e)) {
      GraphicEditActions.duplicate(view);
    }
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
