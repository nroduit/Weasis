/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** What the animation verbs read from their options: the rate and the file extension. */
class AnimationVerbsTest {

  @Test
  void theRateGivesTheFrameDurationWithinBounds() {
    assertAll(
        () -> assertEquals(100, AnimationVerbs.frameDurationMs(10)),
        () -> assertEquals(42, AnimationVerbs.frameDurationMs(24)),
        () -> assertEquals(1000, AnimationVerbs.frameDurationMs(1)),
        () -> assertThrows(IllegalArgumentException.class, () -> AnimationVerbs.frameDurationMs(0)),
        () ->
            assertThrows(
                IllegalArgumentException.class, () -> AnimationVerbs.frameDurationMs(500)));
  }

  @Test
  void theExtensionNamesTheSinkAndAnUnknownOneIsRefused() {
    assertAll(
        () -> assertEquals("apng", AnimationVerbs.extensionOf(Path.of("a/b.APNG"))), // NON-NLS
        () -> assertEquals("", AnimationVerbs.extensionOf(Path.of("noext"))), // NON-NLS
        () ->
            assertEquals(
                "apng", AnimationVerbs.sinkFor(Path.of("x.png"), null, "t").extension()), // NON-NLS
        () ->
            assertEquals(
                "gif", AnimationVerbs.sinkFor(Path.of("x.gif"), null, "t").extension()), // NON-NLS
        () ->
            assertEquals(
                "dcm", AnimationVerbs.sinkFor(Path.of("x.dcm"), null, "t").extension()), // NON-NLS
        () ->
            assertThrows(
                IllegalArgumentException.class,
                () -> AnimationVerbs.sinkFor(Path.of("x.txt"), null, "t"))); // NON-NLS
  }
}
