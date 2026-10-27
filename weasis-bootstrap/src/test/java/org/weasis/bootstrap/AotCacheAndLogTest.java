/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AotCacheAndLogTest {

  @TempDir Path temp;

  @Test
  void classLinkingGate() {
    assertFalse(AotCache.useClassLinking(26, null));
    assertTrue(AotCache.useClassLinking(27, null));
    assertTrue(AotCache.useClassLinking(26, "true"));
    assertFalse(AotCache.useClassLinking(27, "false"));
  }

  @Test
  void flagsFollowTheCacheState() throws Exception {
    AotCache cache = AotCache.of(temp, "weasis", "", "5.0.0-abc");
    if (cache == null) {
      return; // macOS: no AOT relaunch
    }
    cache.prepare();
    assertTrue(cache.file().getFileName().toString().startsWith("weasis-5.0.0-abc-"));
    assertEquals("aot", cache.file().getParent().getFileName().toString());
    List<String> training = cache.flags(true);
    assertTrue(training.getFirst().startsWith("-XX:AOTCacheOutput="));
    assertTrue(cache.flags(false).isEmpty(), "the client never trains inline");
    Files.createFile(cache.file());
    assertEquals(List.of("-XX:AOTCache=" + cache.file()), cache.flags(true));
    Path stale = Files.createFile(cache.file().resolveSibling("weasis-4.9.0-old-1.aot"));
    cache.prepare();
    assertFalse(Files.exists(stale));
    cache.discard();
    assertFalse(cache.exists());
    assertTrue(cache.disabled());
    assertTrue(cache.flags(true).isEmpty());
  }

  @Test
  void decisionLineIsAppendedAndRotated() throws Exception {
    BootLog.write(temp, "set=embedded reason=test");
    Path log = temp.resolve("log").resolve("bootstrap.log");
    String content = Files.readString(log);
    assertTrue(content.contains("set=embedded reason=test"));
    Files.writeString(log, "x".repeat(1024 * 1024 + 1));
    BootLog.write(temp, "set=hub");
    assertTrue(Files.exists(temp.resolve("log").resolve("bootstrap.1.log")));
    assertTrue(Files.readString(log).contains("set=hub"));
  }
}
