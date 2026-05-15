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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StructContourTest {

  @Test
  void position_z_is_initially_unset_and_mutable() {
    StructContour c = new StructContour("1", List.of());
    assertNull(c.getPositionZ());
    c.setPositionZ(12.5);
    assertEquals(12.5, c.getPositionZ());
  }

  @Test
  void points_round_trip_returns_the_same_array_reference() {
    StructContour c = new StructContour("2", List.of(), 0);
    assertNull(c.getPoints());
    double[] pts = {1, 2, 3, 4, 5, 6};
    c.setPoints(pts);
    assertSame(pts, c.getPoints());
    assertArrayEquals(new double[] {1, 2, 3, 4, 5, 6}, c.getPoints());
  }

  @Test
  void constructor_with_pixel_count_initialises_position_z_to_null() {
    StructContour c = new StructContour("seg-id", List.of(), 42);
    assertNotNull(c);
    assertNull(c.getPositionZ());
  }
}
