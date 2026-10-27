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
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;

/** A session keeps the specs of the series a plugin derived, so a restore builds them again. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class SessionDerivedTest {

  @Test
  void the_derived_specs_survive_the_json_round_trip_and_hold_no_identity() {
    JsonObject spec =
        Json.createObjectBuilder()
            .add("series", "1.2.3") // NON-NLS
            .add("mask", "1.2.4") // NON-NLS
            .add("factor", 1.0) // NON-NLS
            .build();
    Instant now = Instant.parse("2026-10-25T10:00:00Z");
    Session session =
        new Session(
            "s1", // NON-NLS
            null,
            false,
            now,
            now,
            List.of(new Session.Source(Session.LOCAL, List.of("1.2"))), // NON-NLS
            null,
            0,
            Map.of(),
            empty(),
            List.of(new Session.Derived("subtraction", spec))); // NON-NLS
    JsonObject json = session.toJson();
    Session back = Session.fromJson(json);
    assertAll(
        () -> assertEquals(1, back.derived().size()),
        () -> assertEquals("subtraction", back.derived().getFirst().kind()),
        () -> assertEquals(spec, back.derived().getFirst().spec()),
        () -> assertTrue(Session.forbiddenKeys(json).isEmpty()),
        () -> assertEquals(List.of(), Session.fromJson(withoutDerived(json)).derived()),
        () -> assertFalse(withoutDerived(json).containsKey("derived"))); // NON-NLS
  }

  @Test
  void a_session_of_acquired_series_only_has_no_derived_entry() {
    Instant now = Instant.now();
    Session session =
        new Session("s2", null, false, now, now, List.of(), null, 0, Map.of(), empty()); // NON-NLS
    assertAll(
        () -> assertTrue(session.derived().isEmpty()),
        () -> assertFalse(session.toJson().containsKey("derived"))); // NON-NLS
  }

  private static WorkspaceSnapshot empty() {
    return new WorkspaceSnapshot("1", List.of(), null); // NON-NLS
  }

  private static JsonObject withoutDerived(JsonObject json) {
    return Json.createObjectBuilder(json).remove("derived").build(); // NON-NLS
  }
}
