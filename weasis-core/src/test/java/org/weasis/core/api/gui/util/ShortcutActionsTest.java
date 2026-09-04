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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ShortcutActionsTest {

  private final Map<String, KeyBinding> bindings = new HashMap<>();
  private final List<String> fired = new ArrayList<>();

  private ShortcutActions table() {
    return new ShortcutActions((id, e) -> bindings.getOrDefault(id, KeyBinding.NONE).matches(e));
  }

  private static KeyEvent pressed(int keyCode, int modifiers) {
    return new KeyEvent(
        new JPanel(), KeyEvent.KEY_PRESSED, 0L, modifiers, keyCode, KeyEvent.CHAR_UNDEFINED);
  }

  @Test
  void the_modifier_selects_the_action() {
    bindings.put("one", new KeyBinding(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK)); // NON-NLS
    bindings.put(
        "all", // NON-NLS
        new KeyBinding(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK | InputEvent.CTRL_DOWN_MASK));
    ShortcutActions actions =
        table().on("one", () -> fired.add("one")).on("all", () -> fired.add("all")); // NON-NLS

    assertAll(
        () -> assertTrue(actions.dispatch(pressed(KeyEvent.VK_X, InputEvent.ALT_DOWN_MASK))),
        () ->
            assertTrue(
                actions.dispatch(
                    pressed(KeyEvent.VK_X, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK))),
        () -> assertFalse(actions.dispatch(pressed(KeyEvent.VK_X, 0))),
        () -> assertFalse(actions.dispatch(pressed(KeyEvent.VK_X, InputEvent.SHIFT_DOWN_MASK))));
    assertEquals(List.of("one", "all"), fired); // NON-NLS
  }

  @Test
  void an_action_can_use_the_event_and_a_table_can_hold_plain_values() {
    bindings.put(
        "next", new KeyBinding(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK)); // NON-NLS
    bindings.put("prev", new KeyBinding(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK)); // NON-NLS
    ShortcutActions actions = table().on("next", KeyEvent::consume); // NON-NLS
    ShortcutTable<Integer> steps =
        new ShortcutTable<Integer>((id, e) -> bindings.get(id).matches(e))
            .on("next", 1) // NON-NLS
            .on("prev", -1); // NON-NLS

    KeyEvent next = pressed(KeyEvent.VK_PAGE_DOWN, InputEvent.CTRL_DOWN_MASK);
    assertAll(
        () -> assertEquals(1, steps.find(next).orElseThrow()),
        () ->
            assertEquals(
                -1,
                steps.find(pressed(KeyEvent.VK_PAGE_UP, InputEvent.CTRL_DOWN_MASK)).orElseThrow()),
        () -> assertTrue(steps.find(pressed(KeyEvent.VK_PAGE_UP, 0)).isEmpty()));
    assertTrue(actions.dispatch(next));
    assertTrue(next.isConsumed());
  }

  @Test
  void a_binding_changed_afterwards_applies_at_once() {
    bindings.put("print", new KeyBinding(KeyEvent.VK_P, 0)); // NON-NLS
    ShortcutActions actions = table().on("print", () -> fired.add("print")); // NON-NLS

    bindings.put("print", new KeyBinding(KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK)); // NON-NLS
    assertAll(
        () -> assertFalse(actions.dispatch(pressed(KeyEvent.VK_P, 0))),
        () -> assertTrue(actions.dispatch(pressed(KeyEvent.VK_P, InputEvent.SHIFT_DOWN_MASK))));
  }

  @Test
  void the_first_registered_id_wins_a_shared_binding_and_an_unset_one_never_fires() {
    bindings.put("first", new KeyBinding(KeyEvent.VK_I, 0)); // NON-NLS
    bindings.put("second", new KeyBinding(KeyEvent.VK_I, 0)); // NON-NLS
    bindings.put("unset", KeyBinding.NONE); // NON-NLS
    ShortcutActions actions =
        table()
            .on("unset", () -> fired.add("unset")) // NON-NLS
            .on("first", () -> fired.add("first")) // NON-NLS
            .on("second", () -> fired.add("second")); // NON-NLS

    assertTrue(actions.dispatch(pressed(KeyEvent.VK_I, 0)));
    assertFalse(actions.dispatch(pressed(0, 0)));
    assertEquals(List.of("first"), fired); // NON-NLS
  }
}
