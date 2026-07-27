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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.media.data.AttributeSource;

/**
 * Tests the series-level bulk-retrieve opt-in decision in {@link
 * ManifestModelBuilder#isBulkSeriesRetrieve}. The opt-in only applies to {@code DICOM_WEB}
 * manifests and is driven by the {@code seriesRetrieve} arcQuery attribute.
 *
 * <p>The {@code weasis.dicom.web.series.bulk} system-property fallback is intentionally not
 * exercised here: mutating a process-global property would break under the parallel test runner.
 */
class ManifestModelBuilderTest {

  /** Backs an {@link AttributeSource} with a fixed map so a single attribute can be set. */
  private static AttributeSource source(String seriesRetrieve) {
    Map<String, String> attributes =
        seriesRetrieve == null ? Map.of() : Map.of("seriesRetrieve", seriesRetrieve);
    return attributes::get;
  }

  @Test
  void notWadoRsIsNeverBulk() {
    // The DICOM_WEB gate wins even when the attribute explicitly opts in.
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source("true"), false));
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source(null), false));
  }

  @Test
  void wadoRsWithAttributeTrueIsBulk() {
    assertTrue(ManifestModelBuilder.isBulkSeriesRetrieve(source("true"), true));
    assertTrue(ManifestModelBuilder.isBulkSeriesRetrieve(source("TRUE"), true));
    assertTrue(ManifestModelBuilder.isBulkSeriesRetrieve(source("True"), true));
  }

  @Test
  void wadoRsWithAttributeFalseOrGarbageIsNotBulk() {
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source("false"), true));
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source("0"), true));
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source("yes"), true));
  }

  @Test
  void wadoRsWithoutAttributeDefaultsToNotBulk() {
    // No attribute and (by default) no system property set.
    assertFalse(ManifestModelBuilder.isBulkSeriesRetrieve(source(null), true));
  }
}
