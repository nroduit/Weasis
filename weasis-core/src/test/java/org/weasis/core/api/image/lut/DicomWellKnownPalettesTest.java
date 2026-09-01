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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapCompiler;

/**
 * The eight well-known palettes of PS3.6 Annex B must compile back to the tables the standard
 * lists. The four table-defined palettes are exact to the entry. The four segmented palettes are
 * two-point ramps whose entries the standard leaves to the decoder's rounding (weasis-dicom-tools
 * floors each step), so they are checked within one level of that expansion.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class DicomWellKnownPalettesTest {

  private static final List<String> LABELS =
      List.of(
          "HOT_IRON", "PET", "HOT_METAL_BLUE", "PET_20_STEP", "SPRING", "SUMMER", "FALL", "WINTER");

  @Test
  void every_well_known_palette_matches_the_standard_table() throws Exception {
    ColorMapRegistry registry = new ColorMapRegistry(null, null);
    JsonObject reference;
    try (InputStream in = getClass().getResourceAsStream("/dicomPalettesReference.json")) {
      reference = JsonUtil.readObject(in.readAllBytes());
    }
    for (int p = 0; p < LABELS.size(); p++) {
      String label = LABELS.get(p);
      String uid = "1.2.840.10008.9." + (p + 1);
      ColorMap map = registry.findByDicomUid(uid).orElseThrow(() -> new AssertionError(label));
      JsonArray table = reference.getJsonArray(label);
      byte[][] bgr = ColorMapCompiler.toBgr(map, table.size());
      byte[][] expected = new byte[3][table.size()];
      for (int i = 0; i < table.size(); i++) {
        JsonArray rgb = table.getJsonArray(i);
        expected[2][i] = (byte) rgb.getInt(0);
        expected[1][i] = (byte) rgb.getInt(1);
        expected[0][i] = (byte) rgb.getInt(2);
      }
      int tolerance = p < 4 ? 0 : 1;
      assertAll(
          () -> assertEquals(label, map.metadata().get(ColorMap.META_DICOM_LABEL)),
          () -> assertEquals("DICOM", map.category()),
          () -> assertTrue(map.tags().contains("well-known")),
          () -> assertWithin(expected, bgr, tolerance, label));
    }
  }

  private static void assertWithin(
      byte[][] expected, byte[][] actual, int tolerance, String label) {
    for (int band = 0; band < 3; band++) {
      for (int i = 0; i < expected[band].length; i++) {
        int delta = Math.abs((expected[band][i] & 0xFF) - (actual[band][i] & 0xFF));
        assertTrue(
            delta <= tolerance, label + " band " + band + " entry " + i + " differs by " + delta);
      }
    }
  }
}
