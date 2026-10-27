/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

class WeasisExtensionsTest {

  @Test
  void aDisplaySetNamesTheScreenItOpensOn() {
    JsonObject json =
        Json.createObjectBuilder()
            .add(
                "displaySets",
                Json.createObjectBuilder()
                    .add("1", Json.createObjectBuilder().add("viewer", "VIEWER_2D"))
                    .add("2", Json.createObjectBuilder().add("screen", "diagnostic-2")))
            .build();
    WeasisExtensions extensions = WeasisExtensions.fromJson(json);
    JsonObject back = extensions.toJson();
    assertAll(
        () -> assertNull(extensions.displaySets().get(1).screen()),
        () -> assertEquals("diagnostic-2", extensions.displaySets().get(2).screen()),
        () ->
            assertEquals(
                "diagnostic-2",
                back.getJsonObject("displaySets").getJsonObject("2").getString("screen")),
        () -> assertEquals(extensions, WeasisExtensions.fromJson(back)));
  }

  @Test
  void aDisplaySetDerivesItsSeriesFromAnotherImageSet() {
    JsonObject json =
        Json.createObjectBuilder()
            .add(
                "displaySets",
                Json.createObjectBuilder()
                    .add(
                        "3",
                        Json.createObjectBuilder()
                            .add(
                                "derive",
                                Json.createObjectBuilder()
                                    .add("kind", "subtraction")
                                    .add("maskImageSet", 1)
                                    .add("factor", 0.8))))
            .build();
    WeasisExtensions extensions = WeasisExtensions.fromJson(json);
    WeasisExtensions.Derive derive = extensions.displaySets().get(3).derive();
    JsonObject back = extensions.toJson();
    assertAll(
        () -> assertEquals("subtraction", derive.kind()),
        () -> assertEquals(1, derive.maskImageSet()),
        () -> assertEquals(0.8, derive.factor()),
        () ->
            assertEquals(
                1,
                back.getJsonObject("displaySets")
                    .getJsonObject("3")
                    .getJsonObject("derive")
                    .getInt("maskImageSet")),
        () -> assertEquals(extensions, WeasisExtensions.fromJson(back)));
  }
}
