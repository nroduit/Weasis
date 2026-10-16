/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;

class SessionStoreTest {

  private static WorkspaceSnapshot snapshot(String seriesUid) {
    JsonObject state =
        Json.createObjectBuilder()
            .add("name", "viewer") // NON-NLS
            .add("series", Json.createArrayBuilder().add(seriesUid)) // NON-NLS
            .build();
    GroupNode group = new GroupNode(List.of(new ViewDescriptor("v1", "2d", state)), 0); // NON-NLS
    return new WorkspaceSnapshot(
        WorkspaceSnapshot.CURRENT_VERSION,
        List.of(new WindowSnapshot("screen0", null, true, group, null))); // NON-NLS
  }

  static Session session(String id, Instant at, boolean pinned) {
    return new Session(
        id,
        "reading " + id, // NON-NLS
        pinned,
        at,
        at,
        List.of(new Session.Source("dcm4chee", List.of("1.2.3." + id))), // NON-NLS
        "weasis.ct", // NON-NLS
        1,
        Map.of(1, "1.2.3." + id + ".1"), // NON-NLS
        snapshot("1.2.3." + id + ".1")); // NON-NLS
  }

  private static SessionStore store(Path dir, int max, int days) {
    LayeredDocument document = new LayeredDocument("hp.sessions.json", "sessions"); // NON-NLS
    document.configure(Optional.empty(), dir.resolve("hp.sessions.json")); // NON-NLS
    return new SessionStore(document, () -> max, () -> days);
  }

  @Test
  void aSessionSurvivesTheJsonRoundTripAndHoldsNoForbiddenKey() {
    Session session = session("s1", Instant.parse("2026-10-17T10:00:00Z"), true); // NON-NLS
    JsonObject json = session.toJson();
    Session back = Session.fromJson(json);
    assertAll(
        () -> assertEquals(session.id(), back.id()),
        () -> assertEquals(session.label(), back.label()),
        () -> assertTrue(back.pinned()),
        () -> assertEquals(session.sources(), back.sources()),
        () -> assertEquals(session.bindings(), back.bindings()),
        () -> assertEquals("weasis.ct", back.protocol()), // NON-NLS
        () -> assertEquals(1, back.workspace().windows().size()),
        () -> assertEquals(List.of(), Session.forbiddenKeys(json)),
        () -> assertEquals(java.util.Set.of("1.2.3.s1"), back.studies())); // NON-NLS
  }

  @Test
  void forbiddenKeysAreFoundAnywhereInTheTree() {
    JsonObject dirty =
        Json.createObjectBuilder()
            .add("id", "x") // NON-NLS
            .add(
                "workspace", // NON-NLS
                Json.createObjectBuilder()
                    .add("patientName", "DOE^JOHN") // NON-NLS
                    .add("nested", Json.createObjectBuilder().add("myPassword", "x"))) // NON-NLS
            .build();
    assertEquals(
        List.of("workspace.patientName", "workspace.nested.myPassword"), // NON-NLS
        Session.forbiddenKeys(dirty));
  }

  @Test
  void theRetentionDeletesTheOldUnpinnedBeyondTheNumberAndEverythingBeyondTheDays(@TempDir Path dir)
      throws IOException {
    Instant now = Instant.parse("2026-10-17T12:00:00Z");
    SessionStore unlimited = store(dir, 0, 0);
    unlimited.keep(session("old", now.minus(Duration.ofDays(40)), true)); // NON-NLS
    unlimited.keep(session("a", now.minus(Duration.ofDays(3)), false)); // NON-NLS
    unlimited.keep(session("b", now.minus(Duration.ofDays(2)), true)); // NON-NLS
    unlimited.keep(session("c", now.minus(Duration.ofDays(1)), false)); // NON-NLS
    assertEquals(4, unlimited.all().size(), "no retention without limits");
    SessionStore store = store(dir, 2, 30);
    int deleted = store.purge(now);
    List<String> left = store.all().stream().map(Session::id).toList();
    assertAll(
        () -> assertEquals(2, deleted, "the 40-day-old pinned one and the oldest unpinned"),
        () -> assertEquals(List.of("c", "b"), left, "newest first"), // NON-NLS
        () -> assertTrue(store.latestOf("1.2.3.b").isPresent()), // NON-NLS
        () -> assertTrue(store.find("a").isEmpty())); // NON-NLS
    store.touch(store.find("b").orElseThrow()); // NON-NLS
    assertTrue(store.find("b").orElseThrow().updated().isAfter(now)); // NON-NLS
    assertTrue(store.delete("b")); // NON-NLS
    assertEquals(List.of("c"), store.all().stream().map(Session::id).toList()); // NON-NLS
  }
}
