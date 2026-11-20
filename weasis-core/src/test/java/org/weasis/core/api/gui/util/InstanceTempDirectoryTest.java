/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayNameGeneration(ReplaceUnderscores.class)
class InstanceTempDirectoryTest {
  private static final String BASE = "weasis-user.source";

  @TempDir Path parent;

  @Test
  void a_running_instance_keeps_its_files_when_another_one_starts() throws IOException {
    Path first = InstanceTempDirectory.create(parent, BASE);
    Path image = Files.createDirectories(first.resolve("dicom")).resolve("image_1.dcm");
    Files.writeString(image, "dicom");

    // The lock is held by this JVM, as it would be by another running process
    Path second = InstanceTempDirectory.create(parent, BASE);

    assertNotEquals(first, second);
    assertTrue(Files.exists(image));
    assertEquals(BASE + "-" + ProcessHandle.current().pid(), first.getFileName().toString());
  }

  @Test
  void the_directory_of_an_exited_instance_is_deleted() throws IOException {
    Path exited = Files.createDirectories(parent.resolve(BASE + "-12345"));
    Files.writeString(exited.resolve(InstanceTempDirectory.LOCK_FILE), "");
    Files.writeString(Files.createDirectories(exited.resolve("dicom")).resolve("a.dcm"), "x");

    InstanceTempDirectory.deleteStale(parent, BASE);

    assertFalse(Files.exists(exited));
  }

  @Test
  void an_unlocked_directory_is_deleted_only_after_the_grace_delay() throws IOException {
    Path recent = Files.createDirectories(parent.resolve(BASE + "-222"));
    Path old = Files.createDirectories(parent.resolve(BASE + "-333-1"));
    Files.setLastModifiedTime(
        old,
        FileTime.from(Instant.now().minus(InstanceTempDirectory.UNLOCKED_GRACE).minusSeconds(60)));

    InstanceTempDirectory.deleteStale(parent, BASE);

    assertTrue(Files.exists(recent));
    assertFalse(Files.exists(old));
  }

  @Test
  void directories_of_other_sources_and_the_legacy_one_are_left_alone() throws IOException {
    Path legacy = Files.createDirectories(parent.resolve(BASE));
    Path otherSource = Files.createDirectories(parent.resolve(BASE + "-abc"));
    Path otherUser = Files.createDirectories(parent.resolve("weasis-other.source-444"));
    for (Path p : new Path[] {legacy, otherSource, otherUser}) {
      Files.setLastModifiedTime(p, FileTime.from(Instant.now().minusSeconds(3600)));
    }

    InstanceTempDirectory.deleteStale(parent, BASE);

    assertTrue(Files.exists(legacy));
    assertTrue(Files.exists(otherSource));
    assertTrue(Files.exists(otherUser));
  }
}
