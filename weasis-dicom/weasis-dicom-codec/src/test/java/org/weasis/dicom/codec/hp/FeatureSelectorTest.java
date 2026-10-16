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

import jakarta.json.Json;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.hp.FeatureSelector.Extreme;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.FeatureSelector.Usage;
import org.weasis.dicom.codec.hp.SeriesFeatures.Contrast;
import org.weasis.dicom.codec.hp.SeriesFeatures.CtKernel;
import org.weasis.dicom.codec.hp.SeriesFeatures.Orientation;
import org.weasis.dicom.codec.hp.SeriesFeatures.Role;
import org.weasis.dicom.codec.hp.SeriesFeatures.Temporal;

class FeatureSelectorTest {

  static final SeriesFeatures CT =
      new SeriesFeatures(
          "CT", // NON-NLS
          "1.2.840.10008.5.1.4.1.1.2", // NON-NLS
          Role.ORIGINAL,
          Orientation.AXIAL,
          new SeriesFeatures.Anatomy(
              "CHEST", "51185008", "Thoracic structure", Set.of("Thorax")), // NON-NLS
          "U", // NON-NLS
          null,
          Contrast.NONE,
          null,
          new CtKernel("B30f", CtKernel.Family.SOFT), // NON-NLS
          1.25,
          1.25,
          null,
          Temporal.STATIC,
          "1.2.3.4.5", // NON-NLS
          List.of("CHEST", "SOFT", "AXIAL"), // NON-NLS
          SeriesFeatures.Interest.NONE,
          120);

  @Test
  void keysReadScalarsCollectionsAndNumbersCaseInsensitively() {
    assertAll(
        () -> assertTrue(FeatureSelector.match(Key.MODALITY, "ct").accepts(CT)), // NON-NLS
        () ->
            assertTrue(FeatureSelector.match(Key.CT_KERNEL_FAMILY, "SOFT").accepts(CT)), // NON-NLS
        () -> assertTrue(FeatureSelector.match(Key.DESCRIPTION, "soft").accepts(CT)), // NON-NLS
        () -> assertTrue(FeatureSelector.match(Key.ANATOMY_GROUP, "thorax").accepts(CT)), // NON-NLS
        () -> assertTrue(FeatureSelector.match(Key.VOLUME, "true").accepts(CT)), // NON-NLS
        () -> assertFalse(FeatureSelector.match(Key.MR_WEIGHTING, "T1").accepts(CT)), // NON-NLS
        () ->
            assertFalse(
                FeatureSelector.match(Key.VIEW, "CC").accepts(CT), "absent feature"), // NON-NLS
        () ->
            assertEquals(
                Optional.of(1.25),
                FeatureSelector.prefer(Key.SLICE_THICKNESS, 1, Extreme.MIN).number(CT)),
        () -> assertEquals(Optional.of(Key.CT_KERNEL_FAMILY), Key.of("ctKernel.family")), // NON-NLS
        () -> assertTrue(Key.of("nope").isEmpty())); // NON-NLS
  }

  @Test
  void filtersExcludeOrRequireAndPreferencesAlwaysPass() {
    assertAll(
        () -> assertTrue(FeatureSelector.match(Key.ORIENTATION, "AXIAL").passes(CT)), // NON-NLS
        () -> assertFalse(FeatureSelector.match(Key.ORIENTATION, "CORONAL").passes(CT)), // NON-NLS
        () -> assertFalse(FeatureSelector.noMatch(Key.ROLE, "ORIGINAL").passes(CT)), // NON-NLS
        () -> assertTrue(FeatureSelector.noMatch(Key.ROLE, "LOCALIZER").passes(CT)), // NON-NLS
        () ->
            assertTrue(FeatureSelector.prefer(Key.ORIENTATION, 2, "CORONAL").passes(CT)), // NON-NLS
        () ->
            assertTrue(
                FeatureSelector.prefer(Key.ORIENTATION, 2, "CORONAL").isFilter()
                    == false)); // NON-NLS
  }

  @Test
  void aSelectorSurvivesJsonAndAnUnknownFeatureIsSkipped() {
    FeatureSelector prefer =
        FeatureSelector.prefer(Key.CT_KERNEL_FAMILY, 2, "soft", "SOFT"); // NON-NLS
    FeatureSelector thinnest = FeatureSelector.prefer(Key.SLICE_THICKNESS, 1.5, Extreme.MIN);
    FeatureSelector read = FeatureSelector.fromJson(prefer.toJson()).orElseThrow();
    FeatureSelector readThin = FeatureSelector.fromJson(thinnest.toJson()).orElseThrow();
    assertAll(
        () -> assertEquals(prefer, read),
        () -> assertEquals(Set.of("SOFT"), read.values()), // NON-NLS
        () -> assertEquals(Usage.PREFER, readThin.usage()),
        () -> assertEquals(Extreme.MIN, readThin.prefer()),
        () -> assertEquals(1.5, readThin.weight()),
        () -> assertEquals("prefer ctKernel.family in [SOFT] (x2.0)", prefer.describe()), // NON-NLS
        () -> assertEquals("prefer smallest sliceThickness (x1.5)", thinnest.describe()), // NON-NLS
        () ->
            assertEquals(
                "exclude role in [LOCALIZER]",
                FeatureSelector.noMatch(Key.ROLE, "LOCALIZER").describe()), // NON-NLS
        () ->
            assertTrue(
                FeatureSelector.fromJson(
                        Json.createObjectBuilder().add("feature", "nope").build()) // NON-NLS
                    .isEmpty()));
  }
}
