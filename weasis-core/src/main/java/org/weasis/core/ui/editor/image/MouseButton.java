/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.Locale;

/** A mouse button or the wheel, as the commands and the UI profiles name them. */
public enum MouseButton {
  LEFT(MouseActions.T_LEFT),
  MIDDLE(MouseActions.T_MIDDLE),
  RIGHT(MouseActions.T_RIGHT),
  WHEEL(MouseActions.T_WHEEL);

  private final String key;

  MouseButton(String key) {
    this.key = key;
  }

  /** The key of {@link MouseActions}. */
  public String key() {
    return key;
  }

  /** The button of a key or name, whatever case; null when unknown. */
  public static MouseButton of(String value) {
    if (value == null) {
      return null;
    }
    String text = value.trim().toLowerCase(Locale.ROOT);
    for (MouseButton button : values()) {
      if (button.key.equals(text)) {
        return button;
      }
    }
    return null;
  }

  @Override
  public String toString() {
    return key;
  }
}
