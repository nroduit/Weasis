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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AotCacheTest {

  @TempDir Path tempDir;

  @AfterEach
  void clearProperties() {
    System.clearProperty("jpackage.app-version");
    System.clearProperty(AotCache.STAGE_PROP);
    System.clearProperty(AotCache.ENABLE_PROP);
    System.clearProperty(AotCache.CLASSLINKING_PROP);
  }

  @Test
  void classLinkingEnabledFromJdk27() {
    assertFalse(AotCache.useClassLinking(25, null));
    assertFalse(AotCache.useClassLinking(26, null), "JDK 26: crashes at use (JDK-8373392)");
    assertTrue(AotCache.useClassLinking(27, null), "JDK 27: fix landed");
    assertTrue(AotCache.useClassLinking(28, null));
  }

  @Test
  void classLinkingPropertyOverridesVersionGate() {
    assertTrue(AotCache.useClassLinking(26, "true"), "force on even on JDK 26");
    assertFalse(AotCache.useClassLinking(27, "false"), "force off even on JDK 27");
  }

  @Test
  void buildToolOptionsTrainsWhenCacheMissing() {
    System.setProperty(AotCache.CLASSLINKING_PROP, "false"); // deterministic regardless of test JVM
    Path cache = tempDir.resolve("weasis-5.0.0-abc.aot");
    String options = AotCache.buildToolOptions(cache);
    assertTrue(options.contains("-XX:AOTCacheOutput="));
    assertTrue(options.contains("-XX:-AOTClassLinking"));
    assertFalse(options.contains("-XX:AOTCache=" + cache));
    assertTrue(options.contains("-D" + AotCache.STAGE_PROP + "=2"));
  }

  @Test
  void buildToolOptionsUsesExistingCache() throws Exception {
    Path cache = Files.createFile(tempDir.resolve("weasis-5.0.0-abc.aot"));
    String options = AotCache.buildToolOptions(cache);
    assertTrue(options.contains("-XX:AOTCache="));
    assertFalse(options.contains("-XX:AOTCacheOutput="));
    assertFalse(options.contains("AOTClassLinking"));
  }

  @Test
  void pruneOtherCachesKeepsOnlyCurrentFile() throws Exception {
    Path keep = Files.createFile(tempDir.resolve("weasis-5.0.0-abc.aot"));
    Path stale1 = Files.createFile(tempDir.resolve("weasis-4.9.0-abc.aot"));
    Path stale2 = Files.createFile(tempDir.resolve("weasis-5.0.0-old.aot"));
    Path staleMarker = Files.createFile(tempDir.resolve("weasis-4.9.0-abc.disabled"));
    Path other = Files.createFile(tempDir.resolve("unrelated.txt"));

    AotCache.pruneOtherCaches(tempDir, keep);

    assertTrue(Files.exists(keep));
    assertTrue(Files.exists(other));
    assertFalse(Files.exists(stale1));
    assertFalse(Files.exists(stale2));
    assertFalse(Files.exists(staleMarker), "stale .disabled markers are swept too");
  }

  @Test
  void disabledMarkerSharesTheCacheStamp() {
    Path cache = tempDir.resolve("weasis-5.0.0-abc.aot");
    assertEquals(tempDir.resolve("weasis-5.0.0-abc.disabled"), AotCache.disabledMarker(cache));
  }

  @Test
  void notEligibleOutsidePackagedApp() {
    System.clearProperty("jpackage.app-version");
    assertFalse(AotCache.isEligible());
  }

  @Test
  void eligibilityMatrixForPackagedApp() {
    System.setProperty("jpackage.app-version", "5.0.0");
    boolean macOS = System.getProperty("os.name", "").toLowerCase().startsWith("mac");
    assertEquals(!macOS, AotCache.isEligible());

    System.setProperty(AotCache.STAGE_PROP, "2");
    assertFalse(AotCache.isEligible(), "stage-2 child must not relaunch");
    System.clearProperty(AotCache.STAGE_PROP);

    System.setProperty(AotCache.ENABLE_PROP, "false");
    assertFalse(AotCache.isEligible(), "opt-out must disable the relaunch");
  }

  @Test
  void cacheFileIsStampedWithVersion() {
    System.setProperty("jpackage.app-version", "5.0.0");
    Path cache = AotCache.cacheFile();
    String name = cache.getFileName().toString();
    assertTrue(name.startsWith("weasis-5.0.0-"));
    assertTrue(name.endsWith(".aot"));
    assertEquals("aot", cache.getParent().getFileName().toString());
    assertEquals(AotCache.cacheFile(), cache, "naming must be deterministic");
  }

  @Test
  void relaunchIsNoOpWhenNotEligible() {
    System.clearProperty("jpackage.app-version");
    assertFalse(AotCache.relaunch(List.of("arg").toArray(new String[0])));
  }

  @Test
  void writeAotOptionAppendsFlagToCfg() throws Exception {
    Path cfg =
        Files.writeString(tempDir.resolve("Weasis.cfg"), "[JavaOptions]\njava-options=-Xmx1g\n");
    Path cache = tempDir.resolve("weasis-5.0.0-abc.aot");

    assertTrue(AotCache.writeAotOption(cfg, cache));

    List<String> lines = Files.readAllLines(cfg);
    assertTrue(lines.contains("java-options=-Xmx1g"), "existing options are kept");
    assertEquals(1, lines.stream().filter(l -> l.startsWith("java-options=-XX:AOTCache=")).count());
    assertTrue(lines.contains("java-options=-XX:AOTCache=" + cache));
  }

  @Test
  void writeAotOptionReplacesStaleFlagAndIsIdempotent() throws Exception {
    Path cache = tempDir.resolve("weasis-5.0.0-new.aot");
    Path cfg =
        Files.writeString(
            tempDir.resolve("Weasis.cfg"),
            "[JavaOptions]\njava-options=-XX:AOTCache=/old/weasis-4.9.0-stale.aot\n");

    assertTrue(AotCache.writeAotOption(cfg, cache));
    assertTrue(AotCache.writeAotOption(cfg, cache)); // idempotent

    List<String> lines = Files.readAllLines(cfg);
    assertEquals(1, lines.stream().filter(l -> l.startsWith("java-options=-XX:AOTCache=")).count());
    assertTrue(lines.contains("java-options=-XX:AOTCache=" + cache));
    assertFalse(lines.stream().anyMatch(l -> l.contains("stale")));
  }
}
