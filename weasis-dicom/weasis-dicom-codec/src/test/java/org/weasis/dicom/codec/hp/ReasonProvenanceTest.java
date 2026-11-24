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
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.Orientation;
import org.weasis.dicom.codec.hp.SeriesFeatures.Role;
import org.weasis.dicom.codec.hp.SeriesFeatures.Temporal;

class ReasonProvenanceTest {

  private static SeriesFeatures features() {
    return new SeriesFeatures(
        "CT", // NON-NLS
        "1.2.840.10008.5.1.4.1.1.2",
        Role.ORIGINAL,
        Orientation.AXIAL,
        null,
        null,
        null,
        null,
        null,
        new CtKernel("B30f", CtKernel.Family.SOFT), // NON-NLS
        2.5,
        2.5,
        null,
        null,
        Temporal.STATIC,
        "1.2.3",
        List.of("THORAX"), // NON-NLS
        SeriesFeatures.Interest.NONE,
        267,
        Map.of(
            Key.ORIENTATION.id(),
            Provenance.header("(0020,0037)", "the plane of 7 sampled images"),
            Key.CT_KERNEL_FAMILY.id(),
            Provenance.table("hp-kernel-families.json", "kernel B30f"), // NON-NLS
            Key.ROLE.id(),
            Provenance.header("(0008,0008)", "ORIGINAL\\PRIMARY\\AXIAL")));
  }

  @Test
  void theFeatureOfARuleIsReadFromItsWording() {
    assertAll(
        () ->
            assertEquals(
                Optional.of(Key.ORIENTATION),
                ReasonProvenance.keyOf("match orientation in [AXIAL]")),
        () ->
            assertEquals(
                Optional.of(Key.ROLE),
                ReasonProvenance.keyOf("exclude role in [DOCUMENT, LOCALIZER]")),
        () ->
            assertEquals(
                Optional.of(Key.CT_KERNEL_FAMILY),
                ReasonProvenance.keyOf("prefer ctKernel.family in [SOFT] (x2.0)")),
        () ->
            assertEquals(
                Optional.of(Key.IMAGE_COUNT),
                ReasonProvenance.keyOf("prefer largest imageCount: 267.0")),
        () ->
            assertEquals(Optional.of(Key.ROLE), ReasonProvenance.keyOf("not match role in [MIP]")),
        () -> assertTrue(ReasonProvenance.keyOf("definition applies, specificity 2").isEmpty()),
        () -> assertTrue(ReasonProvenance.keyOf(null).isEmpty()));
  }

  @Test
  void aRuleIsAnnotatedWithTheKindOfItsSourceAndTheEvidenceCarriesTheValue() {
    SeriesFeatures features = features();
    assertAll(
        () ->
            assertEquals(
                "match orientation in [AXIAL]  [header]",
                ReasonProvenance.annotate("match orientation in [AXIAL]", features)),
        () ->
            assertEquals(
                "prefer ctKernel.family in [SOFT] (x2.0)  [table]",
                ReasonProvenance.annotate("prefer ctKernel.family in [SOFT] (x2.0)", features)),
        () ->
            assertEquals(
                "prefer largest imageCount",
                ReasonProvenance.annotate("prefer largest imageCount", features),
                "a raw feature has no provenance"),
        () -> assertEquals("x", ReasonProvenance.annotate("x", null)));

    JsonArray evidence =
        ReasonProvenance.evidence(
                List.of("match orientation in [AXIAL]", "prefer largest imageCount", "level user"),
                features)
            .build();
    JsonObject orientation = evidence.getJsonObject(0);
    JsonObject count = evidence.getJsonObject(1);
    JsonObject level = evidence.getJsonObject(2);
    assertAll(
        () -> assertEquals("orientation", orientation.getString("feature")),
        () -> assertEquals("AXIAL", orientation.getString("value")),
        () -> assertEquals("header", orientation.getJsonObject("provenance").getString("kind")),
        () ->
            assertEquals(
                "(0020,0037)", orientation.getJsonObject("provenance").getString("source")),
        () -> assertEquals(267.0, count.getJsonNumber("value").doubleValue()),
        () -> assertFalse(count.containsKey("provenance")),
        () -> assertEquals("level user", level.getString("rule")),
        () -> assertFalse(level.containsKey("feature")));
  }
}
