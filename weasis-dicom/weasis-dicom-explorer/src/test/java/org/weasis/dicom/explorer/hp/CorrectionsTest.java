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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.explorer.hp.Corrections.Correction;

class CorrectionsTest {

  private static Correction correction(
      String id, String protocol, int cell, FeatureSelector... rule) {
    return new Correction(
        id,
        protocol,
        1,
        cell,
        cell,
        "1.2.3.4", // NON-NLS
        "1.2.3.5", // NON-NLS
        Json.createObjectBuilder().add("kernel", "SOFT").build(), // NON-NLS
        Json.createObjectBuilder().add("kernel", "SHARP").build(), // NON-NLS
        List.of(rule));
  }

  @Test
  void theStatisticsCountPerProtocolCellAndRuleWithoutAnyIdentifier() {
    FeatureSelector sharp = FeatureSelector.match(Key.CT_KERNEL, "SHARP"); // NON-NLS
    FeatureSelector thin = FeatureSelector.prefer(Key.SLICE_THICKNESS, 2, "1"); // NON-NLS
    List<Correction> log =
        List.of(
            correction("2026-10-24T10:00:00Z", "weasis.ct", 1, sharp),
            correction("2026-10-24T11:00:00Z", "weasis.ct", 1, sharp),
            correction("2026-10-24T12:00:00Z", "weasis.ct", 2, sharp, thin),
            correction("2026-10-24T09:00:00Z", "weasis.mr", 3));
    JsonObject stats = Corrections.statistics(log);
    JsonObject ct = stats.getJsonArray("protocols").getJsonObject(0);
    String text = stats.toString();
    assertAll(
        () -> assertEquals(4, stats.getInt("corrections")),
        () -> assertEquals(2, stats.getJsonArray("protocols").size()),
        () -> assertEquals("weasis.ct", ct.getString("protocol")),
        () -> assertEquals(3, ct.getInt("corrections")),
        () -> assertEquals(2, ct.getJsonObject("cells").getInt("1")),
        () -> assertEquals(1, ct.getJsonObject("cells").getInt("2")),
        () -> assertEquals(2, ct.getJsonObject("rules").getInt("ctKernel=SHARP")),
        () ->
            assertEquals(
                1, ct.getJsonObject("rules").getInt("ctKernel=SHARP & sliceThickness=1 (PREFER)")),
        () -> assertEquals("2026-10-24T12:00:00Z", ct.getString("last")),
        () -> assertFalse(text.contains("1.2.3."), "no series uid"),
        () -> assertFalse(text.contains("SOFT"), "no feature value of a study"));
  }
}
