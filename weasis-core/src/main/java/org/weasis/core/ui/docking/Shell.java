/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import java.util.Objects;
import org.weasis.core.ui.docking.SidePanel.Role;

/**
 * The fixed slots around the workspace tree: the explorer side, the tools side and the central
 * strip. Sessions and UI profiles address it through {@link ShellState}, commands through {@code
 * workspace:panel}.
 */
public record Shell(SidePanel explorer, SidePanel tools, MiniStrip strip) {

  public Shell {
    Objects.requireNonNull(explorer);
    Objects.requireNonNull(tools);
    Objects.requireNonNull(strip);
  }

  public SidePanel side(Role role) {
    return role == Role.EXPLORER ? explorer : tools;
  }

  private static volatile Shell current;

  /** The shell of the application window, or null when it runs headless. */
  public static Shell current() {
    return current;
  }

  /** Called once by the UI core when it builds the shell; null when it runs headless. */
  public static void register(Shell shell) {
    current = shell;
  }
}
