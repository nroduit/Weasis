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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.weasis.dicom.codec.hp.TestSeries.ctSlice;
import static org.weasis.dicom.codec.hp.TestSeries.series;

import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;

/**
 * The rule of the provenance specification: no derived value without provenance. Every derived
 * feature the extractor fills carries where it came from; the raw attributes (modality, UIDs,
 * counts, description tokens) carry none.
 */
class DerivedValueProvenanceTest {

  static final List<Key> DERIVED =
      List.of(
          Key.ROLE,
          Key.ORIENTATION,
          Key.ANATOMY,
          Key.LATERALITY,
          Key.VIEW,
          Key.CONTRAST,
          Key.CONTRAST_PHASE,
          Key.MR_WEIGHTING,
          Key.CT_KERNEL,
          Key.CT_KERNEL_FAMILY,
          Key.SLICE_THICKNESS,
          Key.SPACING,
          Key.PET_ATTENUATION_CORRECTED,
          Key.PET_SUV_CAPABLE,
          Key.TEMPORAL);

  @Test
  void everyDerivedFeatureOfAContrastCtStackSaysWhereItCameFrom() {
    Attributes first = ctSlice(1, 0);
    first.setString(Tag.SeriesDescription, VR.LO, "Thorax C+ arterial 5mm soft"); // NON-NLS
    first.setString(Tag.ContrastBolusAgent, VR.LO, "OMNIPAQUE 350"); // NON-NLS
    first.setString(Tag.BodyPartExamined, VR.CS, "CHEST"); // NON-NLS
    first.setString(Tag.Laterality, VR.CS, "L"); // NON-NLS
    SeriesFeatures f =
        new SeriesFeatureExtractor(new DerivationTables(), InterestProvider.NONE)
            .extract(series(first, ctSlice(2, 2.5), ctSlice(3, 5.0), ctSlice(4, 7.5)));
    assertAll(
        DERIVED.stream()
            .filter(key -> key.read(f) != null)
            .map(
                key ->
                    (Executable)
                        () ->
                            assertNotNull(
                                f.provenanceOf(key.id()),
                                key.id() + " = " + key.read(f) + " has no provenance"))
            .toList());
  }
}
