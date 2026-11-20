/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class LocalImportTest {

  @Test
  void new_sources_come_first_without_duplicates_or_blanks() {
    assertEquals(
        List.of("/c", "/a", "/b"),
        LocalImport.mergeRecent(List.of("/c", "/a", " "), List.of("/a", "/b")));
  }

  @Test
  void the_history_keeps_the_most_recent_sources_only() {
    List<String> older = IntStream.range(0, 20).mapToObj(i -> "/old" + i).toList();

    List<String> merged = LocalImport.mergeRecent(List.of("/new"), older);

    assertEquals(LocalImport.MAX_RECENT, merged.size());
    assertEquals("/new", merged.getFirst());
  }

  @Test
  void the_history_is_seeded_with_the_single_path_of_earlier_versions() {
    Properties props = new Properties();
    props.setProperty(LocalImport.LAST_IMPORT_PATH, "/media/cdrom");

    assertEquals(List.of("/media/cdrom"), LocalImport.recentPaths(props));

    props.setProperty(LocalImport.RECENT_IMPORT_PATHS, "/a\n/b\n/a");
    assertEquals(List.of("/a", "/b"), LocalImport.recentPaths(props));
  }

  @Test
  void sources_without_dicom_leave_the_history() {
    assertEquals(
        List.of("/a", "/c"),
        LocalImport.withoutPaths(List.of("/a", "/b", "/c"), List.of("/b", "/other")));
  }
}
