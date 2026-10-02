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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.LegacyConverters.Conversion;

@DisplayNameGeneration(ReplaceUnderscores.class)
class ConfigCommandsTest {

  private static final String LEGACY = "fakeNodes.xml"; // NON-NLS
  private static final String JSON = "fakeNodes.json"; // NON-NLS
  private static final String MISSING = "fakeMissing.xml"; // NON-NLS
  private static final String BROKEN = "fakeBroken.xml"; // NON-NLS

  @BeforeAll
  static void registerFakeConversions() {
    LegacyConverters.register(
        new Conversion(
            LEGACY,
            JSON,
            "nodes", // NON-NLS
            legacy ->
                Files.readAllLines(legacy).stream()
                    .filter(line -> !line.isBlank())
                    .map(line -> Json.createObjectBuilder().add("id", line.trim()).build())
                    .map(JsonObject.class::cast)
                    .toList()));
    LegacyConverters.register(
        new Conversion(MISSING, "fakeMissing.json", "entries", legacy -> List.of())); // NON-NLS
    LegacyConverters.register(
        new Conversion(
            BROKEN,
            "fakeBroken.json", // NON-NLS
            "entries", // NON-NLS
            legacy -> {
              throw new IOException("unreadable");
            }));
  }

  @Test
  void writes_the_json_of_every_legacy_file_of_the_source_and_skips_the_missing_ones(
      @TempDir Path source, @TempDir Path out) throws IOException {
    Files.writeString(source.resolve(LEGACY), "site.node.a\nsite.node.b\n"); // NON-NLS
    Files.writeString(source.resolve(BROKEN), "x"); // NON-NLS

    JsonObject body = ConfigCommands.export(source, out, false);

    ListDocument.Content content = ListDocument.read(out.resolve(JSON), "nodes"); // NON-NLS
    assertAll(
        () -> assertEquals(source.toString(), body.getString("source")),
        () -> assertEquals(out.toString(), body.getString("out")),
        () -> assertEquals(List.of(JSON), names(body, "written", "json")),
        () -> assertEquals(2, body.getJsonArray("written").getJsonObject(0).getInt("entries")),
        () ->
            assertTrue(
                body.getJsonArray("skipped")
                    .getValuesAs(JsonValue::toString)
                    .contains('"' + MISSING + '"')),
        () ->
            assertFalse(
                body.getJsonArray("skipped")
                    .getValuesAs(JsonValue::toString)
                    .contains('"' + LEGACY + '"')),
        () -> assertEquals(List.of(BROKEN), names(body, "failed", "legacy")),
        () ->
            assertEquals(
                "unreadable", body.getJsonArray("failed").getJsonObject(0).getString("error")),
        () -> assertEquals(ListDocument.SCHEMA_VERSION, content.schema()),
        () ->
            assertEquals(
                List.of("site.node.a", "site.node.b"),
                content.entries().stream().map(o -> o.getString("id")).toList()),
        () -> assertFalse(Files.exists(out.resolve("fakeBroken.json"))));
  }

  @Test
  void refuses_to_overwrite_an_existing_json_unless_forced(@TempDir Path source, @TempDir Path out)
      throws IOException {
    Files.writeString(source.resolve(LEGACY), "site.node.a\n"); // NON-NLS
    Path json = Files.writeString(out.resolve(JSON), "[]"); // NON-NLS

    JsonObject kept = ConfigCommands.export(source, out, false);
    String untouched = Files.readString(json);
    JsonObject forced = ConfigCommands.export(source, out, true);

    assertAll(
        () -> assertEquals("[]", untouched),
        () -> assertTrue(kept.getJsonArray("written").isEmpty()),
        () -> assertEquals(List.of(JSON), names(kept, "failed", "json")),
        () -> assertEquals(List.of(JSON), names(forced, "written", "json")),
        () -> assertEquals(1, ListDocument.read(json, "nodes").entries().size())); // NON-NLS
  }

  @Test
  void export_is_a_registered_verb() {
    assertTrue(ConfigCommands.functions.contains("export")); // NON-NLS
  }

  private static List<String> names(JsonObject body, String array, String field) {
    return body.getJsonArray(array).getValuesAs(JsonObject.class).stream()
        .map(o -> o.getString(field))
        .toList();
  }
}
