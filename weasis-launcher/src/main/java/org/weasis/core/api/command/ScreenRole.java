/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.util.Locale;

/**
 * A screen of the workstation named by its role, never by its device: {@code control} is the
 * worklist screen that holds the main window, {@code viewer-1} and {@code viewer-2} the viewer
 * screens, {@code color} and {@code portrait} the screens with that capability. A session or a
 * protocol names roles; the workstation maps them to its monitors.
 */
@Definition(value = "the role a screen plays in the window model")
public enum ScreenRole implements SemanticType {
  CONTROL,
  VIEWER_1,
  VIEWER_2,
  COLOR,
  PORTRAIT;

  private static final String EXPECTED =
      "a screen role: control, viewer-1, viewer-2, color or portrait"; // NON-NLS

  /** The vocabulary key of the role: {@code viewer-1}. */
  public String key() {
    return SemanticType.valueOf(this);
  }

  public static ScreenRole parse(String text) {
    String value = SemanticType.handle(text, EXPECTED).trim().toLowerCase(Locale.ROOT);
    for (ScreenRole role : values()) {
      if (role.key().equals(value)) {
        return role;
      }
    }
    throw new IllegalArgumentException(EXPECTED);
  }

  @Override
  public String toString() {
    return key();
  }
}
