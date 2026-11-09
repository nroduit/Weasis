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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandOrigin;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.GroupNode;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.WindowSnapshot;

class CheckpointServiceTest {

  private static JsonObject state(String series, int image) {
    return Json.createObjectBuilder()
        .add("series", Json.createArrayBuilder().add(series)) // NON-NLS
        .add("image", image) // NON-NLS
        .build();
  }

  private static WorkspaceSnapshot workspace(ViewDescriptor... views) {
    return new WorkspaceSnapshot(
        WorkspaceSnapshot.CURRENT_VERSION,
        List.of(new WindowSnapshot(null, null, true, new GroupNode(List.of(views), 0), null)));
  }

  private static Checkpoint checkpoint(
      String id,
      WorkspaceSnapshot workspace,
      Map<String, String> graphics,
      Set<String> studies,
      String selected) {
    return new Checkpoint(
        id,
        1,
        Instant.parse("2026-11-07T10:00:00Z"),
        "before " + id,
        CommandOrigin.REMOTE,
        workspace,
        graphics,
        studies,
        selected,
        null,
        0,
        Map.of(),
        List.of(),
        id.startsWith("auto"));
  }

  @Test
  void theDiffNamesTheViewsGraphicsStudiesAndSelectionThatChanged() {
    ViewDescriptor a = new ViewDescriptor("a", "2d", state("s1", 1)); // NON-NLS
    ViewDescriptor b = new ViewDescriptor("b", "2d", state("s2", 1)); // NON-NLS
    Checkpoint before =
        checkpoint(
            "one",
            workspace(a, b),
            Map.of("sop1#1", "<xml a/>"), // NON-NLS
            Set.of("study1", "study2"), // NON-NLS
            "a");
    Checkpoint after =
        checkpoint(
            "two",
            workspace(
                new ViewDescriptor("a", "2d", state("s1", 7)), // NON-NLS
                new ViewDescriptor("c", "mpr", state("s3", 1))), // NON-NLS
            Map.of("sop1#1", "<xml a2/>", "sop9#1", "<xml new/>"), // NON-NLS
            Set.of("study1", "study3"), // NON-NLS
            "c");

    CheckpointService.Diff diff = CheckpointService.diff(before, after);

    assertAll(
        () -> assertEquals(List.of("c"), diff.viewsAdded()),
        () -> assertEquals(List.of("b"), diff.viewsRemoved()),
        () -> assertEquals(List.of("a"), diff.viewsChanged()),
        () -> assertEquals(Set.of("sop1#1", "sop9#1"), Set.copyOf(diff.graphicsChanged())),
        () -> assertEquals(List.of("study3"), diff.studiesLoaded()),
        () -> assertEquals(List.of("study2"), diff.studiesClosed()),
        () -> assertTrue(diff.selectionChanged()),
        () -> assertFalse(diff.isEmpty()),
        () -> assertTrue(CheckpointService.diff(before, before).isEmpty()),
        () -> assertTrue(diff.toJson().build().getBoolean("selectionChanged")));
  }

  @Test
  void theStackIsBoundedAndKeepsTheSameDisplayOnce() {
    CheckpointService service = new CheckpointService(() -> 2, EventBus::new);
    ViewDescriptor a = new ViewDescriptor("a", "2d", state("s1", 1)); // NON-NLS
    Checkpoint first = checkpoint("auto-first", workspace(a), Map.of(), Set.of("study1"), "a");
    Checkpoint same = checkpoint("auto-same", workspace(a), Map.of(), Set.of("study1"), "a");
    Checkpoint named = checkpoint("named", workspace(a), Map.of(), Set.of("study1"), "a");
    Checkpoint again = checkpoint("auto-again", workspace(a), Map.of(), Set.of("study1"), "a");
    Checkpoint other = checkpoint("other", workspace(a), Map.of(), Set.of("study2"), "a");
    Checkpoint third = checkpoint("third", workspace(), Map.of(), Set.of(), null);

    service.push(first);
    service.push(same);
    assertAll(
        "an automatic boundary on the same display is kept once, under the newest label",
        () -> assertEquals(1, service.list().size()),
        () -> assertEquals("auto-same", service.latest().orElseThrow().id()),
        () -> assertTrue(first.sameDisplayAs(same)),
        () -> assertFalse(first.sameDisplayAs(other)));

    service.push(named);
    service.push(again);
    assertAll(
        "a named checkpoint is never replaced by an automatic one",
        () -> assertEquals(2, service.list().size()),
        () -> assertEquals("auto-again", service.list().get(0).id()),
        () -> assertEquals("named", service.list().get(1).id()));
    service.clear();
    service.push(first);

    service.push(other);
    service.push(third);
    assertAll(
        "the oldest leaves when the bound is reached",
        () -> assertEquals(2, service.list().size()),
        () -> assertEquals("third", service.list().get(0).id()),
        () -> assertEquals("other", service.list().get(1).id()),
        () -> assertTrue(service.find("auto-first").isEmpty()),
        () -> assertTrue(service.find("other").isPresent()));
  }

  @Test
  void theSummaryAndTheEventsAreNamedInTheStateNamespace() {
    ViewDescriptor a = new ViewDescriptor("a", "2d", state("s1", 1)); // NON-NLS
    Checkpoint checkpoint =
        checkpoint("cp", workspace(a), Map.of("k", "<x/>"), Set.of("study1"), "a"); // NON-NLS
    JsonObject summary = checkpoint.summary().build();
    List<String> names = CheckpointService.eventTypes().stream().map(EventType::name).toList();
    assertAll(
        () -> assertEquals("cp", summary.getString("checkpoint")),
        () -> assertEquals(1, summary.getInt("views")),
        () -> assertEquals(1, summary.getInt("graphics")),
        () -> assertEquals(1, summary.getInt("studies")),
        () -> assertEquals("remote", summary.getString("origin")),
        () -> assertEquals(Set.of("s1"), checkpoint.series()),
        () ->
            assertEquals(
                List.of("state:checkpoint.taken", "state:rollback.done"), names), // NON-NLS
        () -> assertTrue(Checkpoint.NOT_CAPTURED.size() >= 3));
  }
}
