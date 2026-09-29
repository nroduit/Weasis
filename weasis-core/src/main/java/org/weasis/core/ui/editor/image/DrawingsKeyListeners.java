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
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.weasis.core.api.gui.util.ShortcutActions;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.api.gui.util.ShortcutTable;
import org.weasis.core.ui.model.utils.bean.GraphicClipboard;

public class DrawingsKeyListeners implements KeyListener {
  private final Canvas canvas;
  private final ShortcutActions shortcuts = buildShortcuts();
  private final ShortcutTable<Predicate<GraphicMouseHandler<?>>> draftKeys =
      new ShortcutTable<Predicate<GraphicMouseHandler<?>>>()
          .on(ShortcutManager.ID_DRAW_CANCEL_DRAWING, GraphicMouseHandler::cancelDrawing)
          .on(ShortcutManager.ID_DRAW_REMOVE_LAST_VERTEX, GraphicMouseHandler::removeLastVertex);

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
    return draftKeys.find(e).map(action -> action.test(handler)).orElse(false);
  }

  @Override
  public void keyPressed(KeyEvent e) {
    if (e.isConsumed() || handleDrawingKeys(e)) {
      return;
    }
    shortcuts.dispatch(e);
  }

  /** Selection keys, then cut, copy, paste and duplicate. */
  private ShortcutActions buildShortcuts() {
    return new ShortcutActions()
        .on(
            ShortcutManager.ID_DRAW_DELETE,
            () -> canvas.getGraphicManager().deleteSelectedGraphics(canvas, true))
        .on(
            ShortcutManager.ID_DRAW_DESELECT_ALL,
            () -> canvas.getGraphicManager().setSelectedGraphic(null))
        .on(
            ShortcutManager.ID_DRAW_SELECT_ALL,
            () -> canvas.getGraphicManager().setSelectedAllGraphics())
        .on(ShortcutManager.ID_DRAW_COPY, () -> onView(GraphicEditActions::copy))
        .on(ShortcutManager.ID_DRAW_CUT, () -> onView(GraphicEditActions::cut))
        .on(
            ShortcutManager.ID_DRAW_PASTE_IN_PLACE,
            () ->
                onView(v -> GraphicEditActions.paste(v, GraphicClipboard.PasteMode.IN_PLACE, null)))
        // A paste lands where the cursor last was on this view
        .on(
            ShortcutManager.ID_DRAW_PASTE,
            () ->
                onView(
                    v ->
                        GraphicEditActions.paste(
                            v, GraphicClipboard.defaultPasteMode(), v.getCursorImagePoint())))
        .on(ShortcutManager.ID_DRAW_DUPLICATE, () -> onView(GraphicEditActions::duplicate));
  }

  /** The clipboard actions only exist on a view canvas. */
  private void onView(Consumer<ViewCanvas<?>> action) {
    if (canvas instanceof ViewCanvas<?> view) {
      action.accept(view);
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
