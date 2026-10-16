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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.dicom.codec.hp.DerivationTables.MrSample;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel.Family;
import org.weasis.dicom.codec.hp.SeriesFeatures.MrWeighting;

@DisplayNameGeneration(ReplaceUnderscores.class)
class DerivationTablesTest {

  @Test
  void the_built_in_tables_know_the_main_vendors() {
    DerivationTables tables = new DerivationTables();
    assertAll(
        () -> assertEquals(Family.SOFT, tables.kernelFamily("SIEMENS", "B31f")), // NON-NLS
        () ->
            assertEquals(
                Family.LUNG, tables.kernelFamily("Siemens Healthineers", "Bl64d")), // NON-NLS
        () ->
            assertEquals(Family.BONE, tables.kernelFamily("GE MEDICAL SYSTEMS", "BONE")), // NON-NLS
        () -> assertEquals(Family.SOFT, tables.kernelFamily("Philips", "B")), // NON-NLS
        () -> assertEquals(Family.LUNG, tables.kernelFamily("TOSHIBA", "FC51")), // NON-NLS
        () ->
            assertEquals(
                Family.SOFT,
                tables.kernelFamily(null, "STANDARD"),
                "pattern, no vendor"), // NON-NLS
        () -> assertEquals(Family.UNKNOWN, tables.kernelFamily("SIEMENS", null)), // NON-NLS
        () -> assertEquals(Family.UNKNOWN, tables.kernelFamily("ACME", "Z9")), // NON-NLS
        () -> assertEquals(3, tables.kernelFamilies().entries().size()),
        () -> assertTrue(tables.mrWeightingRules().entries().size() > 10));
  }

  @Test
  void rules_are_tried_by_priority_and_every_condition_must_hold() {
    DerivationTables tables = new DerivationTables();
    MrSample adcByType =
        new MrSample(
            List.of("EP"),
            List.of(),
            List.of("DERIVED", "ADC"),
            List.of("T2"),
            5000.0,
            90.0,
            null,
            null); // NON-NLS
    MrSample flairNeedsIr =
        new MrSample(
            List.of("SE"), List.of(), List.of(), List.of(), 9000.0, 100.0, 2500.0, null); // NON-NLS
    MrSample flair =
        new MrSample(
            List.of("IR", "SE"),
            List.of(),
            List.of(),
            List.of(),
            9000.0,
            100.0,
            2500.0,
            null); // NON-NLS
    MrSample nothing = new MrSample(null, null, null, null, null, null, null, null);
    assertAll(
        () ->
            assertEquals(
                MrWeighting.ADC, tables.mrWeighting(adcByType), "image type beats the T2 token"),
        () ->
            assertEquals(
                MrWeighting.T2, tables.mrWeighting(flairNeedsIr), "no IR, so the timing rule"),
        () -> assertEquals(MrWeighting.FLAIR, tables.mrWeighting(flair)),
        () -> assertEquals(MrWeighting.UNKNOWN, tables.mrWeighting(nothing)));
  }

  @Test
  void a_site_entry_overrides_a_family_and_a_user_rule_is_added(@TempDir Path dir)
      throws IOException {
    Path config = dir.resolve("config"); // NON-NLS
    Files.createDirectories(config);
    Files.writeString(
        config.resolve(DerivationTables.KERNELS),
        """
        {"schema": 1, "families": [
          {"id": "BONE", "vendors": {"ACME": ["Z9"]}, "patterns": ["^HARD"]}
        ]}
        """);
    Path userRules = dir.resolve(DerivationTables.MR_WEIGHTING);
    Files.writeString(
        userRules,
        """
        {"schema": 1, "rules": [
          {"id": "site-perfusion", "weighting": "PERFUSION", "priority": 200, "when": {"tokens": ["BOLUS"]}}
        ]}
        """);
    DerivationTables tables = new DerivationTables();
    tables.configure(config, null, userRules);

    assertAll(
        () -> assertEquals(Family.BONE, tables.kernelFamily("ACME", "Z9")), // NON-NLS
        () ->
            assertEquals(
                Family.UNKNOWN,
                tables.kernelFamily("GE", "BONE"),
                "the site entry replaced the built-in list"), // NON-NLS
        () ->
            assertEquals(
                Family.SOFT,
                tables.kernelFamily("SIEMENS", "B30f"),
                "other families untouched"), // NON-NLS
        () ->
            assertEquals(
                Origin.SITE,
                tables.kernelFamilies().find("BONE").orElseThrow().origin()), // NON-NLS
        () ->
            assertEquals(
                MrWeighting.PERFUSION,
                tables.mrWeighting(
                    new MrSample(
                        List.of("SE"),
                        List.of(),
                        List.of(),
                        List.of("BOLUS"),
                        500.0,
                        15.0,
                        null,
                        null))), // NON-NLS
        () ->
            assertEquals(
                Origin.USER,
                tables
                    .mrWeightingRules()
                    .find("site-perfusion")
                    .orElseThrow()
                    .origin())); // NON-NLS
  }
}
