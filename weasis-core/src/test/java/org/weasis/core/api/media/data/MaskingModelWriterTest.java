/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.media.data.MaskingModel.TagRule;

@DisplayName("MaskingModel writer")
class MaskingModelWriterTest {

  @TempDir Path dir;

  private static MaskingModel document() {
    Map<TagCategory, AnonymizationAction> actions = new EnumMap<>(TagCategory.class);
    actions.put(TagCategory.DIRECT_ID, AnonymizationAction.PSEUDONYMIZE);
    actions.put(TagCategory.DATE, AnonymizationAction.SHIFT);
    actions.put(TagCategory.FREE_TEXT, AnonymizationAction.REMOVE);
    MaskingProfile profile =
        new MaskingProfile(
            "user-demo",
            "Demo",
            Map.of("fr", "Démonstration"),
            actions,
            Map.of("StudyDescription", AnonymizationAction.KEEP),
            true,
            true);
    return new MaskingModel(
        List.of(
            new TagRule("OperatorsName", TagCategory.DIRECT_ID),
            new TagRule("00291010@ACME", TagCategory.FREE_TEXT)),
        List.of(profile),
        "user-demo",
        "ai-request",
        false);
  }

  @Test
  @DisplayName("a document read back is the one written")
  void roundTrip() throws IOException {
    Path file = dir.resolve("masking.json");
    document().write(file);
    MaskingModel read = MaskingModel.read(file);
    MaskingProfile profile = read.profiles().getFirst();

    assertAll(
        () -> assertEquals(document().tags(), read.tags()),
        () -> assertEquals(document().profiles(), read.profiles()),
        () -> assertEquals("user-demo", read.sessionProfile()),
        () -> assertEquals("ai-request", read.aiProfile()),
        () -> assertFalse(read.locked()),
        () -> assertEquals("Démonstration", profile.labels().get("fr")),
        () -> assertEquals(AnonymizationAction.SHIFT, profile.actionFor(TagCategory.DATE)),
        () ->
            assertEquals(
                AnonymizationAction.KEEP,
                profile.actionFor(TagW.get("StudyDescription")),
                "a tag action overrides its category"));
  }

  @Test
  @DisplayName("a category left out is kept, and the same document always writes the same bytes")
  void compactAndStable() throws IOException {
    Path first = dir.resolve("first.json");
    Path second = dir.resolve("second.json");
    document().write(first);
    document().write(second);
    String json = Files.readString(first);

    assertAll(
        () -> assertEquals(json, Files.readString(second)),
        () -> assertFalse(json.contains("DEVICE"), "an unset category is not written"),
        () -> assertTrue(json.contains("\"schema\""), "the schema version is written"),
        () ->
            assertEquals(
                AnonymizationAction.KEEP,
                MaskingModel.read(first).profiles().getFirst().actionFor(TagCategory.DEVICE)));
  }

  @Test
  @DisplayName("the locked flag survives a round trip")
  void lockedFlag() throws IOException {
    Path file = dir.resolve("locked.json");
    new MaskingModel(List.of(), List.of(), null, null, true).write(file);
    assertTrue(MaskingModel.read(file).locked());
  }
}
