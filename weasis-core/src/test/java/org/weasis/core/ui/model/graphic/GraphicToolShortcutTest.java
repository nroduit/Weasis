/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.graphic;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.awt.event.KeyEvent;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ShortcutManager;
import org.weasis.core.ui.model.graphic.imp.BuiltinGraphicTools;

/** Every palette tool has an entry in the shortcut preferences. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class GraphicToolShortcutTest {

  private static final GraphicRegistry REGISTRY = GraphicRegistry.getInstance();
  private static final ShortcutManager SHORTCUTS = ShortcutManager.getInstance();

  private static int keyOf(String toolKey) {
    return REGISTRY.prototype(toolKey).orElseThrow().getKeyCode();
  }

  @Test
  void the_ids_of_the_former_shortcuts_are_kept() {
    assertAll(
        () ->
            assertEquals(
                ShortcutManager.ID_GRAPHIC_LINE,
                ShortcutManager.graphicToolId(BuiltinGraphicTools.LINE)),
        () ->
            assertEquals(
                ShortcutManager.ID_GRAPHIC_POLYGON,
                ShortcutManager.graphicToolId(BuiltinGraphicTools.POLYLINE)),
        () ->
            assertEquals(
                ShortcutManager.ID_GRAPHIC_ANNOTATION,
                ShortcutManager.graphicToolId(BuiltinGraphicTools.TEXT)),
        () ->
            assertEquals(
                "graphic.weasis.ellipse", // NON-NLS
                ShortcutManager.graphicToolId(BuiltinGraphicTools.ELLIPSE)));
  }

  @Test
  void palette_tools_are_listed_with_their_default_key_and_advanced_tools_are_not() {
    assertAll(
        () -> assertEquals(KeyEvent.VK_D, keyOf(BuiltinGraphicTools.LINE)),
        () -> assertEquals(KeyEvent.VK_Y, keyOf(BuiltinGraphicTools.POLYLINE)),
        () -> assertEquals(KeyEvent.VK_E, keyOf(BuiltinGraphicTools.ELLIPSE)),
        () -> assertEquals(KeyEvent.VK_O, keyOf(BuiltinGraphicTools.CIRCLE)),
        () -> assertEquals(KeyEvent.VK_X, keyOf(BuiltinGraphicTools.BIDIRECTIONAL)),
        () -> assertEquals(KeyEvent.VK_B, keyOf(BuiltinGraphicTools.TEXT)),
        () -> assertEquals(0, keyOf(BuiltinGraphicTools.DRAW_LINE)),
        () -> assertEquals(0, keyOf(BuiltinGraphicTools.RECTANGLE)),
        () -> assertEquals(0, keyOf(BuiltinGraphicTools.POLYGON)),
        () ->
            assertNotNull(
                SHORTCUTS.getEntry(ShortcutManager.graphicToolId(BuiltinGraphicTools.RECTANGLE))),
        () ->
            assertNull(
                SHORTCUTS.getEntry(ShortcutManager.graphicToolId(BuiltinGraphicTools.POLYGON))),
        () ->
            assertNull(
                SHORTCUTS.getEntry(ShortcutManager.graphicToolId(BuiltinGraphicTools.SELECT))));
  }

  @Test
  void a_tool_registered_twice_keeps_its_entry() {
    String id = ShortcutManager.graphicToolId(BuiltinGraphicTools.CIRCLE);
    ShortcutManager.ShortcutEntry entry = SHORTCUTS.getEntry(id);
    REGISTRY.registerShortcuts();
    assertEquals(entry, SHORTCUTS.getEntry(id));
  }
}
