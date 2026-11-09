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

import org.junit.jupiter.api.Test;

class PrereleaseTest {

  @Test
  void theVersionSuffixSaysWhetherTheBuildIsAPreview() {
    Prerelease beta = Prerelease.of("5.0.0-beta.1");
    Prerelease rc = Prerelease.of("5.0.0-rc2");
    Prerelease snapshot = Prerelease.of("5.0.0-SNAPSHOT");
    Prerelease release = Prerelease.of("5.0.0");
    Prerelease patch = Prerelease.of("5.0.1");
    assertAll(
        () -> assertTrue(beta.isPreview()),
        () -> assertEquals("beta", beta.stage()),
        () -> assertEquals(1, beta.number()),
        () -> assertEquals("beta 1", beta.label()),
        () -> assertEquals("beta.1", beta.suffix()),
        () -> assertEquals("5.0.0", beta.baseVersion()),
        () -> assertEquals("rc 2", rc.label()),
        () -> assertTrue(snapshot.isSnapshot()),
        () -> assertEquals("snapshot", snapshot.label()),
        () -> assertFalse(release.isPreview()),
        () -> assertEquals("", release.label()),
        () -> assertEquals("", release.suffix()),
        () -> assertFalse(patch.isPreview()),
        () -> assertFalse(Prerelease.of(null).isPreview()),
        () -> assertEquals("0.0.0", Prerelease.of(" ").version()));
  }
}
