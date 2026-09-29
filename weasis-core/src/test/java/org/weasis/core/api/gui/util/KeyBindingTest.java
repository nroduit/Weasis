/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ShortcutManager.ShortcutContext;
import org.weasis.core.api.gui.util.ShortcutManager.ShortcutEntry;

@DisplayNameGeneration(ReplaceUnderscores.class)
class KeyBindingTest {

  // Values of the deprecated InputEvent.SHIFT_MASK, CTRL_MASK, META_MASK, ALT_MASK and
  // BUTTON1_MASK, as older versions stored them in the preferences
  private static final int OLD_SHIFT = 1;
  private static final int OLD_CTRL = 2;
  private static final int OLD_META = 4;
  private static final int OLD_ALT = 8;
  private static final int OLD_BUTTON1 = 16;

  private static KeyEvent pressed(int keyCode, int extendedModifiers) {
    return new KeyEvent(
        new JPanel(),
        KeyEvent.KEY_PRESSED,
        0L,
        extendedModifiers,
        keyCode,
        KeyEvent.CHAR_UNDEFINED);
  }

  private static MouseEvent released(int button, int extendedModifiers) {
    return new MouseEvent(
        new JPanel(),
        MouseEvent.MOUSE_RELEASED,
        0L,
        extendedModifiers,
        10,
        10,
        10,
        10,
        1,
        false,
        button);
  }

  @Test
  void modifiers_of_the_old_event_model_are_converted() {
    assertAll(
        () -> assertEquals(InputEvent.SHIFT_DOWN_MASK, KeyBinding.toExtended(OLD_SHIFT)),
        () -> assertEquals(InputEvent.CTRL_DOWN_MASK, KeyBinding.toExtended(OLD_CTRL)),
        () -> assertEquals(InputEvent.META_DOWN_MASK, KeyBinding.toExtended(OLD_META)),
        () -> assertEquals(InputEvent.ALT_DOWN_MASK, KeyBinding.toExtended(OLD_ALT)),
        () ->
            assertEquals(
                InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
                KeyBinding.toExtended(OLD_CTRL | OLD_ALT)),
        () -> assertEquals(0, KeyBinding.toExtended(OLD_BUTTON1)),
        () -> assertEquals(0, KeyBinding.toExtended(InputEvent.BUTTON1_DOWN_MASK)),
        () ->
            assertEquals(
                InputEvent.CTRL_DOWN_MASK,
                KeyBinding.toExtended(
                    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK)
                        .getModifiers()),
                "a KeyStroke reports both models at once"));
  }

  @Test
  void each_modifier_matches_only_when_it_is_the_one_held() {
    int[] masks = {
      InputEvent.SHIFT_DOWN_MASK,
      InputEvent.CTRL_DOWN_MASK,
      InputEvent.ALT_DOWN_MASK,
      InputEvent.META_DOWN_MASK,
      InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK,
      InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK
    };
    for (int bound : masks) {
      KeyBinding binding = new KeyBinding(KeyEvent.VK_X, bound);
      for (int held : masks) {
        assertEquals(
            bound == held,
            binding.matches(pressed(KeyEvent.VK_X, held)),
            () -> binding.text() + " against modifiers " + held);
      }
      assertFalse(binding.matches(pressed(KeyEvent.VK_X, 0)));
      assertFalse(binding.matches(pressed(KeyEvent.VK_Y, bound)));
    }
  }

  @Test
  void a_plain_key_does_not_fire_with_a_modifier_held() {
    KeyBinding plain = new KeyBinding(KeyEvent.VK_C, 0);
    assertAll(
        () -> assertTrue(plain.matches(pressed(KeyEvent.VK_C, 0))),
        () -> assertFalse(plain.matches(pressed(KeyEvent.VK_C, InputEvent.ALT_DOWN_MASK))),
        () -> assertFalse(plain.matches(pressed(KeyEvent.VK_C, InputEvent.ALT_GRAPH_DOWN_MASK))),
        () ->
            assertTrue(
                plain.matches(pressed(KeyEvent.VK_C, InputEvent.BUTTON1_DOWN_MASK)),
                "a mouse button held during the key press is not a keyboard modifier"));
  }

  @Test
  void an_unassigned_binding_matches_nothing() {
    assertAll(
        () -> assertFalse(KeyBinding.NONE.matches(pressed(0, 0))),
        () -> assertNull(KeyBinding.NONE.toKeyStroke()),
        () -> assertEquals("", KeyBinding.NONE.text()));
  }

  @Test
  void the_text_lists_the_modifiers_before_the_key() {
    assertEquals(
        "Ctrl+Alt+Shift+" + KeyEvent.getKeyText(KeyEvent.VK_Z),
        new KeyBinding(
                KeyEvent.VK_Z,
                InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK)
            .text());
  }

  @Test
  void an_entry_stored_by_an_older_version_is_the_same_shortcut() {
    ShortcutEntry entry =
        new ShortcutEntry(
            "test.entry", // NON-NLS
            null,
            null,
            ShortcutContext.VIEW_CANVAS,
            KeyEvent.VK_Z,
            InputEvent.CTRL_DOWN_MASK);
    entry.setModifier(OLD_CTRL);
    assertAll(
        () -> assertFalse(entry.isModified()),
        () -> assertEquals(InputEvent.CTRL_DOWN_MASK, entry.getModifier()),
        () ->
            assertTrue(
                entry.getBinding().matches(pressed(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK))));

    entry.setModifier(OLD_ALT | OLD_SHIFT);
    assertAll(
        () -> assertTrue(entry.isModified()),
        () ->
            assertTrue(
                entry
                    .getBinding()
                    .matches(
                        pressed(
                            KeyEvent.VK_Z,
                            InputEvent.ALT_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))));
  }

  @Test
  void a_release_is_attributed_to_the_released_button_whatever_the_modifiers() {
    MouseActionAdapter left = new MouseActionAdapter() {};
    left.setButtonMaskEx(InputEvent.BUTTON1_DOWN_MASK);
    MouseActionAdapter middle = new MouseActionAdapter() {};
    middle.setButtonMaskEx(InputEvent.BUTTON2_DOWN_MASK);
    MouseActionAdapter disabled = new MouseActionAdapter() {};

    MouseEvent altLeft = released(MouseEvent.BUTTON1, InputEvent.ALT_DOWN_MASK);
    assertAll(
        () -> assertTrue(left.isBoundButton(released(MouseEvent.BUTTON1, 0))),
        () -> assertTrue(left.isBoundButton(altLeft)),
        () ->
            assertTrue(
                left.isBoundButton(
                    released(
                        MouseEvent.BUTTON1,
                        InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))),
        // ALT_MASK and BUTTON2_MASK were the same bit: Alt + left release used to reach the
        // adapter of the middle button
        () -> assertFalse(middle.isBoundButton(altLeft)),
        () -> assertTrue(middle.isBoundButton(released(MouseEvent.BUTTON2, 0))),
        () -> assertFalse(disabled.isBoundButton(released(MouseEvent.BUTTON1, 0))),
        () -> assertFalse(left.isBoundButton(released(MouseEvent.NOBUTTON, 0))));
  }
}
