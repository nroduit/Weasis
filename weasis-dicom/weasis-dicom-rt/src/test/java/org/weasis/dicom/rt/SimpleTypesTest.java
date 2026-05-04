/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class SimpleTypesTest {

  @Test
  void data_source_has_only_provided_and_calculated_constants() {
    assertEquals(2, DataSource.values().length);
    assertEquals(DataSource.PROVIDED, DataSource.valueOf("PROVIDED"));
    assertEquals(DataSource.CALCULATED, DataSource.valueOf("CALCULATED"));
  }

  @Test
  void dose_lut_record_exposes_x_and_y_arrays() {
    double[] x = {0, 1, 2};
    double[] y = {10, 20};
    DoseLut lut = new DoseLut(x, y);
    assertArrayEquals(x, lut.x());
    assertArrayEquals(y, lut.y());
  }

  @Test
  void dose_lut_record_equality_and_hash_use_array_identity() {
    double[] x = {0, 1};
    double[] y = {0, 1};
    DoseLut a = new DoseLut(x, y);
    DoseLut b = new DoseLut(x, y);
    assertEquals(a, b);
    // Records with array components compare by reference identity of those components.
    assertNotEquals(new DoseLut(new double[] {0, 1}, new double[] {0, 1}), a);
  }

  @Test
  void largest_contour_record_exposes_index_and_area() {
    LargestContour lc = new LargestContour(3, 12.5);
    assertEquals(3, lc.index());
    assertEquals(12.5, lc.area());
  }
}
