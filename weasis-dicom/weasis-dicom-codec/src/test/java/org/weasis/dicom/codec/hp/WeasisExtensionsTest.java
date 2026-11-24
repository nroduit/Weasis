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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
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
                    .add("2", Json.createObjectBuilder().add("screen", "viewer-2")))
            .build();
    WeasisExtensions extensions = WeasisExtensions.fromJson(json);
    JsonObject back = extensions.toJson();
    assertAll(
        () -> assertNull(extensions.displaySets().get(1).screen()),
        () -> assertEquals("viewer-2", extensions.displaySets().get(2).screen()),
        () ->
            assertEquals(
                "viewer-2",
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

  @Test
  void aDisplaySetFallsBackToAReformatOfAnotherImageSet() {
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
                                    .add("kind", "mpr")
                                    .add("imageSet", 1)
                                    .add("plane", "CORONAL")
                                    .add("fallback", true))))
            .build();
    WeasisExtensions extensions = WeasisExtensions.fromJson(json);
    WeasisExtensions.Derive derive = extensions.displaySets().get(3).derive();
    JsonObject back = extensions.toJson().getJsonObject("displaySets").getJsonObject("3");
    assertAll(
        () -> assertEquals("mpr", derive.kind()),
        () -> assertEquals(1, derive.imageSet()),
        () -> assertNull(derive.maskImageSet()),
        () -> assertEquals("CORONAL", derive.plane()),
        () -> assertTrue(derive.fallback()),
        () -> assertEquals("CORONAL", back.getJsonObject("derive").getString("plane")),
        () -> assertFalse(back.getJsonObject("derive").containsKey("maskImageSet")),
        () -> assertEquals(extensions, WeasisExtensions.fromJson(extensions.toJson())),
        () -> assertNull(new WeasisExtensions.Derive("subtraction", 1, null).plane()));
  }

  @Test
  void aRequiredImageSetAndAFusedDisplaySetSurviveTheJsonRoundTrip() {
    WeasisExtensions extensions =
        WeasisExtensions.fromJson(
            Json.createReader(
                    new StringReader(
                        """
                        {"imageSets": {"1": {"selectors": [], "required": true},
                                       "2": {"selectors": []}},
                         "displaySets": {"3": {"display": {"fusion":
                             {"overlay": 2, "colorMap": "weasis.turbo", "opacity": 0.5}}}}}
                        """))
                .readObject());
    WeasisExtensions back = WeasisExtensions.fromJson(extensions.toJson());
    WeasisExtensions.Fusion fusion = back.displaySets().get(3).display().fusion();
    assertAll(
        () -> assertTrue(back.imageSets().get(1).required()),
        () -> assertFalse(back.imageSets().get(2).required()),
        () ->
            assertFalse(
                extensions
                    .toJson()
                    .getJsonObject("imageSets")
                    .getJsonObject("2")
                    .containsKey("required"), // NON-NLS
                "written only when set"),
        () -> assertEquals(2, fusion.overlay()),
        () -> assertNull(fusion.base()),
        () -> assertEquals("weasis.turbo", fusion.colorMap()), // NON-NLS
        () -> assertEquals(0.5, fusion.opacity()));
  }

  @Test
  void aDisplaySetProjectsItsImagesThroughASlab() {
    JsonObject json =
        Json.createObjectBuilder()
            .add(
                "displaySets",
                Json.createObjectBuilder()
                    .add(
                        "4",
                        Json.createObjectBuilder()
                            .add(
                                "display",
                                Json.createObjectBuilder()
                                    .add(
                                        "projection",
                                        Json.createObjectBuilder()
                                            .add("mode", "MIP")
                                            .add("thickness", 30)))))
            .build();
    WeasisExtensions extensions = WeasisExtensions.fromJson(json);
    WeasisExtensions.Projection projection = extensions.displaySets().get(4).display().projection();
    JsonObject back = extensions.toJson();
    assertAll(
        () -> assertEquals("MIP", projection.mode()),
        () -> assertEquals(30.0, projection.thickness()),
        () ->
            assertEquals(
                "MIP",
                back.getJsonObject("displaySets")
                    .getJsonObject("4")
                    .getJsonObject("display")
                    .getJsonObject("projection")
                    .getString("mode")),
        () -> assertEquals(extensions, WeasisExtensions.fromJson(back)));
  }
}
