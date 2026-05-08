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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class IsoDoseRegionTest {

  @Test
  void absolute_dose_is_a_percentage_of_the_planned_dose() {
    IsoDoseRegion region = new IsoDoseRegion(50, Color.RED, "Half", 6000);
    assertEquals(50, region.getLevel());
    assertEquals(3000.0, region.getAbsoluteDose(), 1e-9);
  }

  @Test
  void label_includes_level_dose_and_optional_name() {
    IsoDoseRegion named = new IsoDoseRegion(95, Color.BLUE, "Target", 6000);
    assertTrue(named.getLabel().startsWith("95 % / 5700"));
    assertTrue(named.getLabel().endsWith("[Target]"));
  }

  @Test
  void label_omits_brackets_when_name_is_blank() {
    IsoDoseRegion noName = new IsoDoseRegion(80, Color.GREEN, "", 6000);
    assertTrue(noName.getLabel().startsWith("80 % / 4800"));
    assertFalse(noName.getLabel().contains("["));
  }

  @Test
  void thickness_is_initially_zero_and_mutable() {
    IsoDoseRegion region = new IsoDoseRegion(70, Color.MAGENTA, "x", 4200);
    assertEquals(0.0, region.getThickness());
    region.setThickness(2.5);
    assertEquals(2.5, region.getThickness());
  }

  @Test
  void interior_opacity_is_pre_set_to_indicate_a_translucent_overlay() {
    IsoDoseRegion region = new IsoDoseRegion(30, Color.CYAN, "low", 6000);
    assertEquals(0.2f, region.getInteriorOpacity(), 1e-6f);
  }
}
