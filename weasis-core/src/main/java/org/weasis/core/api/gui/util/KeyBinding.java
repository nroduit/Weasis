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

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.KeyStroke;

/**
 * A key and the keyboard modifiers held with it, always expressed with the extended {@code
 * *_DOWN_MASK} modifiers of {@link InputEvent}.
 *
 * <p>The modifiers passed in are normalized: a value written with the masks of the first AWT event
 * model (as stored by older versions or passed by older plugins) is converted, and the mouse button
 * bits are dropped.
 *
 * @param keyCode a {@code KeyEvent.VK_*} code, {@code 0} when no key is assigned
 * @param modifiers the extended keyboard modifiers
 */
public record KeyBinding(int keyCode, int modifiers) {

  /** Every keyboard modifier that takes part in a binding. */
  public static final int KEYBOARD_MASK =
      InputEvent.SHIFT_DOWN_MASK
          | InputEvent.CTRL_DOWN_MASK
          | InputEvent.META_DOWN_MASK
          | InputEvent.ALT_DOWN_MASK
          | InputEvent.ALT_GRAPH_DOWN_MASK;

  /** Modifier of the menu shortcuts of the platform: Ctrl on Windows and Linux, Cmd on macOS. */
  public static final int MENU_SHORTCUT_MASK =
      SystemInfo.isMacOS ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK;

  public static final KeyBinding NONE = new KeyBinding(0, 0);

  // Values of the deprecated InputEvent.SHIFT_MASK, CTRL_MASK, META_MASK, ALT_MASK and
  // ALT_GRAPH_MASK. They all sit below the first extended modifier (1 << 6).
  private static final int LEGACY_SHIFT = 1;
  private static final int LEGACY_CTRL = 1 << 1;
  private static final int LEGACY_META = 1 << 2;
  private static final int LEGACY_ALT = 1 << 3;
  private static final int LEGACY_ALT_GRAPH = 1 << 5;

  public KeyBinding {
    modifiers = toExtended(modifiers);
  }

  public static KeyBinding of(KeyEvent e) {
    return new KeyBinding(e.getKeyCode(), e.getModifiersEx());
  }

  /** Keyboard modifiers held during the event, without the mouse button bits. */
  public static int modifiersOf(InputEvent e) {
    return e.getModifiersEx() & KEYBOARD_MASK;
  }

  /** Converts the modifiers to the extended keyboard masks, whichever model they are written in. */
  public static int toExtended(int modifiers) {
    int extended = modifiers & KEYBOARD_MASK;
    if ((modifiers & LEGACY_SHIFT) != 0) {
      extended |= InputEvent.SHIFT_DOWN_MASK;
    }
    if ((modifiers & LEGACY_CTRL) != 0) {
      extended |= InputEvent.CTRL_DOWN_MASK;
    }
    if ((modifiers & LEGACY_META) != 0) {
      extended |= InputEvent.META_DOWN_MASK;
    }
    if ((modifiers & LEGACY_ALT) != 0) {
      extended |= InputEvent.ALT_DOWN_MASK;
    }
    if ((modifiers & LEGACY_ALT_GRAPH) != 0) {
      extended |= InputEvent.ALT_GRAPH_DOWN_MASK;
    }
    return extended;
  }

  public boolean isAssigned() {
    return keyCode != 0;
  }

  /** True when the key matches and exactly the same keyboard modifiers are held. */
  public boolean matches(KeyEvent e) {
    return matches(e.getKeyCode(), e.getModifiersEx());
  }

  /** Same as {@link #matches(KeyEvent)}; the modifiers may be written in either model. */
  public boolean matches(int keyCode, int modifiers) {
    return isAssigned() && this.keyCode == keyCode && this.modifiers == toExtended(modifiers);
  }

  /**
   * The keystroke to use as a menu accelerator or in an input map, {@code null} when unassigned.
   */
  public KeyStroke toKeyStroke() {
    return isAssigned() ? KeyStroke.getKeyStroke(keyCode, modifiers) : null;
  }

  /** Readable form such as "Ctrl+Shift+Z", empty when unassigned. */
  public String text() {
    if (!isAssigned()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    if ((modifiers & InputEvent.CTRL_DOWN_MASK) != 0) {
      sb.append("Ctrl+"); // NON-NLS
    }
    if ((modifiers & InputEvent.ALT_DOWN_MASK) != 0) {
      sb.append("Alt+"); // NON-NLS
    }
    if ((modifiers & InputEvent.ALT_GRAPH_DOWN_MASK) != 0) {
      sb.append("AltGr+"); // NON-NLS
    }
    if ((modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
      sb.append("Shift+"); // NON-NLS
    }
    if ((modifiers & InputEvent.META_DOWN_MASK) != 0) {
      sb.append("Meta+"); // NON-NLS
    }
    return sb.append(KeyEvent.getKeyText(keyCode)).toString();
  }
}
