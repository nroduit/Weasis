/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

/**
 * Entry class of the Weasis launcher, a thin wrapper over {@link AppLauncher}. Each application has
 * its own entry class because the X11 toolkit derives {@code WM_CLASS} from the main class of the
 * thread that creates it, which is what groups the windows of an application in the taskbar ({@code
 * StartupWMClass} of the desktop file).
 */
public final class Weasis {

  private Weasis() {}

  public static void main(String[] argv) throws Exception {
    AppLauncher.main(argv);
  }
}
