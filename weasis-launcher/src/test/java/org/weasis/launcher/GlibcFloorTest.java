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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The floor comes from the configuration, the version from the running libc, nothing is loaded. */
class GlibcFloorTest {

  @Test
  void versionsCompareNumerically() {
    assertAll(
        () -> assertTrue(GlibcFloor.compare("2.39", "2.27") > 0),
        () -> assertTrue(GlibcFloor.compare("2.9", "2.27") < 0, "2.9 is older than 2.27"),
        () -> assertEquals(0, GlibcFloor.compare("2.27", "2.27")),
        () -> assertTrue(GlibcFloor.compare("3.0", "2.99") > 0),
        () -> assertEquals(Optional.of("2.39"), GlibcFloor.parse("glibc 2.39")),
        () -> assertEquals(Optional.of("2.17"), GlibcFloor.parse("2.17.1")),
        () -> assertEquals(Optional.empty(), GlibcFloor.parse("musl")),
        () -> assertEquals(Optional.empty(), GlibcFloor.parse(null)));
  }

  @Test
  void theCheckAppliesToLinuxWithAFloorOnly() {
    assertAll(
        () -> assertTrue(GlibcFloor.check("2.27", false, () -> Optional.of("2.17")).isEmpty()),
        () -> assertTrue(GlibcFloor.check(null, true, () -> Optional.of("2.17")).isEmpty()),
        () -> assertTrue(GlibcFloor.check(" ", true, () -> Optional.of("2.17")).isEmpty()),
        () -> assertTrue(GlibcFloor.check("none", true, () -> Optional.of("2.17")).isEmpty()),
        () ->
            assertTrue(
                GlibcFloor.check("2.27", true, () -> Optional.of("2.27"))
                    .orElseThrow()
                    .supported()),
        () ->
            assertFalse(
                GlibcFloor.check("2.28", true, () -> Optional.of("2.27"))
                    .orElseThrow()
                    .supported()),
        () ->
            assertFalse(
                GlibcFloor.check("2.27", true, Optional::empty).orElseThrow().supported(),
                "a Linux without glibc is unsupported"));
  }

  @Test
  void theMessageNamesTheApplicationTheFloorAndTheSystem() {
    GlibcFloor.Check older = new GlibcFloor.Check("2.28", "2.27");
    GlibcFloor.Check none = new GlibcFloor.Check("2.28", null);
    assertAll(
        () -> assertTrue(older.message("Weasis").contains("Weasis"), older.message("Weasis")),
        () -> assertTrue(older.message("Weasis").contains("2.28")),
        () -> assertTrue(older.message("Weasis").contains("2.27")),
        () -> assertTrue(none.message("Weasis").contains("2.28")),
        () -> assertFalse(none.message("Weasis").contains("null")));
  }

  @Test
  @EnabledOnOs(OS.LINUX)
  void theRunningGlibcIsReadWithoutLoadingAnything() {
    Optional<String> ffm = GlibcFloor.glibcVersionFfm();
    Optional<String> getconf = GlibcFloor.glibcVersionGetconf();
    Optional<String> version = GlibcFloor.glibcVersion();
    assertAll(
        () -> assertTrue(version.isPresent(), "this Linux has a glibc"),
        () -> assertTrue(version.get().matches("\\d+\\.\\d+"), version.get()),
        () -> assertTrue(ffm.isEmpty() || ffm.equals(version)),
        () -> assertTrue(getconf.isEmpty() || getconf.equals(version)),
        () -> assertTrue(GlibcFloor.check("2.0").orElseThrow().supported()),
        () -> assertFalse(GlibcFloor.check("99.0").orElseThrow().supported()));
  }
}
