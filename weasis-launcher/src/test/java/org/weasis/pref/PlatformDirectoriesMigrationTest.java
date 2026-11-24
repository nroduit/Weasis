/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.pref;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformDirectoriesMigrationTest {

  @Test
  void movesPreferencesDataAndLogAndDropsCache(@TempDir Path root) throws IOException {
    Path legacy = root.resolve(".weasis");
    write(legacy.resolve("preferences/user/default/weasis.properties"), "k=v");
    write(legacy.resolve("data/weasis-core/proxy.properties"), "proxy");
    write(legacy.resolve("log/default.log"), "log");
    write(legacy.resolve("cache/bundle0/data"), "regenerable");

    Path config = root.resolve("config/weasis");
    Path data = root.resolve("data/weasis");
    Path state = root.resolve("state/weasis");

    PlatformDirectories.migrateLegacyLayout(legacy, config, data, state);

    assertEquals(
        "k=v", Files.readString(config.resolve("preferences/user/default/weasis.properties")));
    assertEquals("proxy", Files.readString(data.resolve("data/weasis-core/proxy.properties")));
    assertEquals("log", Files.readString(state.resolve("log/default.log")));
    assertFalse(Files.exists(legacy.resolve("cache")), "regenerable cache should be deleted");
    assertTrue(Files.exists(legacy.resolve(".xdg-migrated")), "marker should be written");
  }

  @Test
  void leavesLegacyResourcesInPlace(@TempDir Path root) throws IOException {
    Path legacy = root.resolve(".weasis");
    write(legacy.resolve("data/8EE82632/resources/svg/icon.svg"), "<svg/>");
    write(legacy.resolve("data/8EE82632/other.properties"), "moved");
    Path data = root.resolve("data/weasis");

    PlatformDirectories.migrateLegacyLayout(
        legacy, root.resolve("config/weasis"), data, root.resolve("state/weasis"));

    assertEquals(
        "<svg/>", Files.readString(legacy.resolve("data/8EE82632/resources/svg/icon.svg")));
    assertFalse(Files.exists(data.resolve("data/8EE82632/resources")));
    assertEquals("moved", Files.readString(data.resolve("data/8EE82632/other.properties")));
    assertFalse(Files.exists(legacy.resolve("data/8EE82632/other.properties")));
  }

  @Test
  void isIdempotentAndDoesNotOverwriteNewerFiles(@TempDir Path root) throws IOException {
    Path legacy = root.resolve(".weasis");
    write(legacy.resolve("preferences/p.properties"), "old");
    Path config = root.resolve("config/weasis");
    write(config.resolve("preferences/p.properties"), "new");
    Path data = root.resolve("data/weasis");
    Path state = root.resolve("state/weasis");

    PlatformDirectories.migrateLegacyLayout(legacy, config, data, state);
    // Existing destination must be preserved.
    assertEquals("new", Files.readString(config.resolve("preferences/p.properties")));

    // A second run is a no-op because the marker is present.
    write(legacy.resolve("preferences/q.properties"), "added-after-migration");
    PlatformDirectories.migrateLegacyLayout(legacy, config, data, state);
    assertFalse(Files.exists(config.resolve("preferences/q.properties")));
  }

  @Test
  void noopWhenLegacyEqualsTarget(@TempDir Path root) throws IOException {
    Path legacy = root.resolve(".weasis");
    write(legacy.resolve("preferences/p.properties"), "v");
    PlatformDirectories.migrateLegacyLayout(legacy, legacy, legacy, legacy);
    assertFalse(Files.exists(legacy.resolve(".xdg-migrated")));
  }

  private static void write(Path file, String content) throws IOException {
    Files.createDirectories(file.getParent());
    Files.writeString(file, content);
  }
}
