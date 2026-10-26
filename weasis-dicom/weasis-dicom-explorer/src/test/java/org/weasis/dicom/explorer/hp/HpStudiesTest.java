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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.hp.FeatureSelector;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.SeriesFeatures;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;

class HpStudiesTest {

  @Test
  void theExplorerInterestBecomesTheInterestFeatureTheSelectorsTest() {
    Interest interest =
        new Interest(
            Map.of(
                InterestSource.KEY_OBJECT, Set.of("1.2.3.1", "1.2.3.2"), // NON-NLS
                InterestSource.GRAPHICS, Set.of("1.2.3.2"))); // NON-NLS
    SeriesFeatures.Interest feature = HpStudies.toFeature(interest);
    assertAll(
        () -> assertEquals(2, feature.count(), "distinct images"),
        () -> assertEquals(Set.of("KO", "GRAPHICS"), feature.sources()),
        () -> assertEquals(SeriesFeatures.Interest.NONE, HpStudies.toFeature(Interest.NONE)),
        () -> assertEquals(SeriesFeatures.Interest.NONE, HpStudies.toFeature(null)));

    DicomSeries plain = ExplorerFixtures.ctSeries("1.2.3", "B30f"); // NON-NLS
    SeriesFeatures features = HpStudies.extractor().of(plain);
    assertAll(
        () -> assertEquals(SeriesFeatures.Interest.NONE, features.interest()),
        () -> assertTrue(Key.INTEREST.read(features) instanceof Boolean b && !b),
        () -> assertEquals(0, ((Number) Key.INTEREST_COUNT.read(features)).intValue()));
    assertTrue(FeatureSelector.match(Key.INTEREST, "TRUE") != null);
  }
}
