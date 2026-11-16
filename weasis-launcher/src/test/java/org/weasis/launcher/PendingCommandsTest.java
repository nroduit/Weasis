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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.pref.ConfigData;

class PendingCommandsTest {

  /**
   * A macOS open-file or open-URI event of a cold start arrives before the plugin manager exists:
   * the commands are queued without building a command manager, whose extension lookup threw a
   * NullPointerException on the event thread and lost the event.
   */
  @Test
  void anEventBeforeTheFrameworkIsLoadedIsQueued() {
    AppLauncher launcher = new AppLauncher(new ConfigData(new String[0]));
    List<String> commands = List.of("dicom:get -l \"/tmp/a.dcm\""); // NON-NLS

    launcher.executeCommandsWhenLoaded(commands);

    assertEquals(commands, launcher.pendingCommands());
  }
}
