/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class JsonExtendsTest {

  private static JsonObject json(String text) {
    return JsonUtil.readObject(text);
  }

  @Test
  void an_entry_starts_from_its_base_and_keeps_its_own_fields() {
    JsonObject lung =
        json(
            """
            {"id": "weasis.ct.lung", "name": "Lung", "window": 1500, "level": -500,
             "key": "5", "locked": true, "when": {"minBitsStored": 9}}
            """);
    List<JsonObject> resolved =
        JsonExtends.resolve(
            List.of(
                json(
                    "{\"id\": \"site.ct.lung\", \"extends\": \"weasis.ct.lung\", \"window\": 1600}")),
            id -> id.equals("weasis.ct.lung") ? Optional.of(lung) : Optional.empty(),
            "preset");

    JsonObject entry = resolved.getFirst();
    assertAll(
        () -> assertEquals(1, resolved.size()),
        () -> assertEquals("site.ct.lung", entry.getString("id")),
        () -> assertEquals("Lung", entry.getString("name")),
        () -> assertEquals(1600, entry.getInt("window")),
        () -> assertEquals(-500, entry.getInt("level")),
        () -> assertEquals("5", entry.getString("key")),
        () -> assertEquals(9, entry.getJsonObject("when").getInt("minBitsStored")),
        () -> assertFalse(entry.containsKey("extends")),
        () -> assertFalse(entry.containsKey("locked"), "a lock is never inherited"));
  }

  @Test
  void a_base_of_the_same_document_is_resolved_first_whatever_its_position() {
    List<JsonObject> resolved =
        JsonExtends.resolve(
            List.of(
                json("{\"id\": \"c\", \"extends\": \"b\", \"c\": 3}"),
                json("{\"id\": \"b\", \"extends\": \"a\", \"b\": 2}"),
                json("{\"id\": \"a\", \"a\": 1}")),
            JsonExtends.NO_BASE,
            "item");

    assertAll(
        () -> assertEquals(3, resolved.size()),
        () -> assertEquals(1, resolved.getFirst().getInt("a")),
        () -> assertEquals(2, resolved.getFirst().getInt("b")),
        () -> assertEquals(3, resolved.getFirst().getInt("c")),
        () -> assertEquals("c", resolved.getFirst().getString("id")));
  }

  @Test
  void a_missing_base_a_cycle_or_a_deep_chain_skips_the_entry_only() {
    List<JsonObject> resolved =
        JsonExtends.resolve(
            List.of(
                json("{\"id\": \"orphan\", \"extends\": \"nowhere\"}"),
                json("{\"id\": \"x\", \"extends\": \"y\"}"),
                json("{\"id\": \"y\", \"extends\": \"x\"}"),
                json("{\"id\": \"plain\", \"v\": 1}")),
            JsonExtends.NO_BASE,
            "item");

    assertAll(
        () -> assertEquals(1, resolved.size()),
        () -> assertEquals("plain", resolved.getFirst().getString("id")));
  }

  @Test
  void a_field_of_the_entry_replaces_the_whole_field_of_the_base() {
    JsonObject base =
        json("{\"id\": \"a\", \"when\": {\"minBitsStored\": 9, \"requiresRescale\": true}}");
    List<JsonObject> resolved =
        JsonExtends.resolve(
            List.of(json("{\"id\": \"b\", \"extends\": \"a\", \"when\": {\"minBitsStored\": 12}}")),
            id -> Optional.of(base),
            "item");

    JsonObject when = resolved.getFirst().getJsonObject("when");
    assertAll(
        () -> assertEquals(12, when.getInt("minBitsStored")),
        () -> assertTrue(when.isEmpty() || !when.containsKey("requiresRescale")));
  }
}
