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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UriRelaunchTest {

  private static final String URI = "weasis://?%24dicom%3Aget+-l+%22%2Ftmp%2Fa.dcm%22"; // NON-NLS

  /**
   * A weasis:// URI relaunches this bundle by its path: by bundle id, LaunchServices picked the
   * registered Weasis, a 4.x sharing the id next to the 5.0 preview.
   */
  @Test
  void theBundleIsFoundFromTheLauncherOrTheApplicationDirectory() {
    Path bundle = Path.of("/Applications/Weasis 5 Preview.app"); // NON-NLS
    assertEquals(
        Optional.of(bundle),
        WeasisLauncher.appBundle(null, bundle + "/Contents/MacOS/Weasis")); // NON-NLS
    assertEquals(
        Optional.of(bundle), WeasisLauncher.appBundle("", bundle + "/Contents/app")); // NON-NLS
    assertTrue(WeasisLauncher.appBundle("/opt/weasis/bin/Weasis", null).isEmpty()); // NON-NLS
    assertTrue(WeasisLauncher.appBundle().isEmpty());

    assertArrayEquals(
        new String[] {"open", "-n", "-a", bundle.toString(), "--args", URI}, // NON-NLS
        WeasisLauncher.macOpenArgs(Optional.of(bundle), URI));
    assertArrayEquals(
        new String[] {"open", "-n", "-b", "org.weasis.launcher", "--args", URI}, // NON-NLS
        WeasisLauncher.macOpenArgs(Optional.empty(), URI));
  }
}
