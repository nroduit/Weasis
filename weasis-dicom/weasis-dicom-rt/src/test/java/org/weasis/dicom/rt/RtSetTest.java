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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RtSetTest {

  @Test
  void plane_thickness_is_zero_for_fewer_than_two_planes() {
    assertEquals(0.0, RtSet.calculatePlaneThickness(Set.of()));
    assertEquals(0.0, RtSet.calculatePlaneThickness(Set.of(new KeyDouble(1.0))));
  }

  @Test
  void plane_thickness_is_smallest_gap_between_consecutive_z_positions() {
    Set<KeyDouble> planes = new LinkedHashSet<>();
    planes.add(new KeyDouble(0.0));
    planes.add(new KeyDouble(2.5));
    planes.add(new KeyDouble(5.0));
    planes.add(new KeyDouble(7.0)); // smallest gap = 2.0
    assertEquals(2.0, RtSet.calculatePlaneThickness(planes), 1e-12);
  }

  @Test
  void plane_thickness_handles_unsorted_input() {
    Set<KeyDouble> planes = new LinkedHashSet<>();
    planes.add(new KeyDouble(10.0));
    planes.add(new KeyDouble(0.0));
    planes.add(new KeyDouble(2.0));
    assertEquals(2.0, RtSet.calculatePlaneThickness(planes), 1e-12);
  }

  @Test
  void plane_thickness_is_zero_when_all_planes_share_the_same_z() {
    Set<KeyDouble> planes = new LinkedHashSet<>();
    planes.add(new KeyDouble(3.0));
    planes.add(new KeyDouble(3.0));
    // Set deduplicates equal KeyDoubles -> falls back to fewer than two -> 0.0
    assertEquals(0.0, RtSet.calculatePlaneThickness(planes));
  }
}
