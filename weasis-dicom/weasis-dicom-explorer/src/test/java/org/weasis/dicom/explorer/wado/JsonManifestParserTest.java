/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link JsonManifestParser#children}, which drives JSON partial-level detection: a level is
 * treated as partial when this returns an empty list. The important edge is that a single child
 * serialized as a JSON object (not an array) still counts as present.
 */
class JsonManifestParserTest {

  private static JsonObject series(String uid) {
    return Json.createObjectBuilder().add("SeriesInstanceUID", uid).build();
  }

  @Test
  void singleChildObjectCountsAsPresent() {
    JsonObject study = Json.createObjectBuilder().add("Series", series("1.1")).build();
    List<JsonObject> children = JsonManifestParser.children(study, "Series");
    assertEquals(1, children.size());
    assertEquals("1.1", children.getFirst().getString("SeriesInstanceUID"));
  }

  @Test
  void childArrayReturnsEveryObject() {
    JsonObject study =
        Json.createObjectBuilder()
            .add(
                "Series",
                Json.createArrayBuilder().add(series("1.1")).add(series("1.2")).add(series("1.3")))
            .build();
    assertEquals(3, JsonManifestParser.children(study, "Series").size());
  }

  @Test
  void missingKeyIsEmpty() {
    JsonObject study = Json.createObjectBuilder().add("StudyInstanceUID", "1").build();
    assertTrue(JsonManifestParser.children(study, "Series").isEmpty());
  }

  @Test
  void nullAndScalarMembersAreEmpty() {
    JsonObject study =
        Json.createObjectBuilder().addNull("Series").add("StudyDescription", "text").build();
    assertTrue(JsonManifestParser.children(study, "Series").isEmpty());
    assertTrue(JsonManifestParser.children(study, "StudyDescription").isEmpty());
  }

  @Test
  void nonObjectArrayEntriesAreSkipped() {
    JsonObject study =
        Json.createObjectBuilder()
            .add("Series", Json.createArrayBuilder().add(series("1.1")).add("scalar").addNull())
            .build();
    assertEquals(1, JsonManifestParser.children(study, "Series").size());
  }
}
