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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class StructRegionTreeTest {

  @Test
  void has_dvh_returns_false_for_empty_list() {
    assertFalse(StructRegionTree.hasDvh(List.of()));
  }

  @Test
  void has_dvh_returns_false_when_no_region_carries_a_dvh() {
    StructRegion r1 = new StructRegion(1, "A", Color.RED);
    StructRegion r2 = new StructRegion(2, "B", Color.RED);
    assertFalse(StructRegionTree.hasDvh(List.of(r1, r2)));
  }

  @Test
  void has_dvh_returns_true_when_at_least_one_region_carries_a_dvh() {
    StructRegion r1 = new StructRegion(1, "A", Color.RED);
    StructRegion r2 = new StructRegion(2, "B", Color.RED);
    r2.setDvh(new Dvh());
    assertTrue(StructRegionTree.hasDvh(List.of(r1, r2)));
  }
}
