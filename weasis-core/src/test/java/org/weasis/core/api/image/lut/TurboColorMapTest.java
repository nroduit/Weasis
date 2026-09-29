/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.lut;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonReader;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/** Turbo is bundled as stops; compiled back it must stay within one level of Google's table. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class TurboColorMapTest {

  @Test
  void turbo_stays_within_one_level_of_the_reference_table() throws Exception {
    ColorMap turbo = new ColorMapRegistry(null, null).findById("weasis.turbo").orElseThrow();
    JsonArray reference;
    try (InputStream in = getClass().getResourceAsStream("/turboReference.json");
        JsonReader reader = Json.createReader(in)) {
      reference = reader.readArray();
    }
    byte[][] bgr = ColorMapCompiler.toBgr(turbo, 256);
    assertEquals(256, reference.size());
    int worst = 0;
    for (int i = 0; i < 256; i++) {
      JsonArray rgb = reference.getJsonArray(i);
      worst = Math.max(worst, Math.abs((bgr[2][i] & 0xFF) - rgb.getInt(0)));
      worst = Math.max(worst, Math.abs((bgr[1][i] & 0xFF) - rgb.getInt(1)));
      worst = Math.max(worst, Math.abs((bgr[0][i] & 0xFF) - rgb.getInt(2)));
    }
    assertTrue(worst <= 1, "largest deviation " + worst);
    assertEquals("Scientific", turbo.category());
  }
}
