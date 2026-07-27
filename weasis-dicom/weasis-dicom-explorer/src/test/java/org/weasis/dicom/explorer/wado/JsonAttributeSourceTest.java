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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link JsonAttributeSource} — the adapter that exposes scalar JSON members as manifest
 * attributes. Only string/number/boolean members are attributes; nested objects, arrays and JSON
 * null are structural and must read as absent.
 */
class JsonAttributeSourceTest {

  private static final JsonObject OBJECT =
      Json.createObjectBuilder()
          .add("SeriesInstanceUID", "1.2.840.10008")
          .add("WadoCompressionRate", 75)
          .add("requireOnlySOPInstanceUID", true)
          .add("disabled", false)
          .addNull("nullValue")
          .add("nested", Json.createObjectBuilder().add("a", "b"))
          .add("array", Json.createArrayBuilder().add("x"))
          .build();

  private final JsonAttributeSource source = new JsonAttributeSource(OBJECT);

  @Test
  void readsScalarMembersAsAttributes() {
    assertAll(
        () -> assertEquals("1.2.840.10008", source.getAttribute("SeriesInstanceUID")),
        () -> assertEquals("75", source.getAttribute("WadoCompressionRate")),
        () -> assertEquals("true", source.getAttribute("requireOnlySOPInstanceUID")),
        () -> assertEquals("false", source.getAttribute("disabled")));
  }

  @Test
  void treatsMissingAndStructuralMembersAsAbsent() {
    assertAll(
        () -> assertNull(source.getAttribute("unknown")),
        () -> assertNull(source.getAttribute("nullValue")),
        () -> assertNull(source.getAttribute("nested")),
        () -> assertNull(source.getAttribute("array")));
  }
}
