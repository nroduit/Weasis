/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class MouseEventDoubleTest {

  private static MouseEvent press(int extendedModifiers) {
    return new MouseEvent(
        new JPanel(),
        MouseEvent.MOUSE_PRESSED,
        0L,
        extendedModifiers,
        10,
        10,
        10,
        10,
        1,
        false,
        MouseEvent.BUTTON1);
  }

  @Test
  void a_copy_keeps_alt_held_with_the_left_button() {
    // ALT_MASK and BUTTON2_MASK are the same bit in the deprecated modifier set: copying through
    // getModifiers() used to turn this press into a middle-button one, and every Alt gesture of
    // the graphic editing silently did nothing
    MouseEventDouble copy =
        new MouseEventDouble(press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.ALT_DOWN_MASK));

    assertAll(
        () -> assertTrue(copy.isAltDown(), "Alt must survive the copy"),
        () ->
            assertTrue(
                (copy.getModifiersEx() & InputEvent.BUTTON1_DOWN_MASK) != 0,
                "the left button must survive the copy"),
        () ->
            assertFalse(
                (copy.getModifiersEx() & InputEvent.BUTTON2_DOWN_MASK) != 0,
                "Alt must not be read as the middle button"));
  }

  @Test
  void a_copy_keeps_the_other_modifiers_and_adds_none() {
    MouseEventDouble shift =
        new MouseEventDouble(press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK));
    MouseEventDouble plain = new MouseEventDouble(press(InputEvent.BUTTON1_DOWN_MASK));

    assertAll(
        () -> assertTrue(shift.isShiftDown()),
        () -> assertFalse(shift.isAltDown()),
        () -> assertFalse(plain.isShiftDown()),
        () -> assertFalse(plain.isAltDown()),
        () -> assertFalse(plain.isControlDown()));
  }

  @Test
  void a_copy_at_another_position_keeps_the_modifiers_too() {
    MouseEventDouble copy =
        new MouseEventDouble(
            press(InputEvent.BUTTON1_DOWN_MASK | InputEvent.ALT_DOWN_MASK), 40, 50);

    assertAll(
        () -> assertTrue(copy.isAltDown()),
        () -> assertTrue(copy.getX() == 40 && copy.getY() == 50));
  }
}
