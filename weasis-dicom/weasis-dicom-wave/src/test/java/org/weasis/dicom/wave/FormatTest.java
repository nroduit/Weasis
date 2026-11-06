/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

@DisplayNameGeneration(ReplaceUnderscores.class)
class FormatTest {

  private static final List<Lead> CABRERA_LIMB =
      List.of(Lead.AVL, Lead.I, Lead.AVR, Lead.II, Lead.AVF, Lead.III);

  /** The leads of one column of a row-major layout. */
  private static List<Lead> column(Format format, boolean cabrera, int column) {
    List<Lead> leads = new ArrayList<>();
    Lead[] all = format.getLeads(cabrera);
    for (int i = column; i < all.length; i += format.getXlayoutSize()) {
      leads.add(all[i]);
    }
    return leads;
  }

  @Test
  void cabrera_sequences_hold_the_same_leads_as_the_standard_ones() {
    for (Format format : Format.values()) {
      assertEquals(format.getLeads().length, format.getLeads(true).length, format.name());
      assertEquals(
          new HashSet<>(List.of(format.getLeads())),
          new HashSet<>(List.of(format.getLeads(true))),
          format.name());
    }
  }

  @Test
  void cabrera_limb_leads_are_in_anatomical_order() {
    // 12 x 1: the six limb leads first, in the Cabrera sequence, then V1 to V6
    assertEquals(CABRERA_LIMB, List.of(Format.DEFAULT.getLeads(true)).subList(0, 6));
    assertEquals(
        List.of(Lead.V1, Lead.V2, Lead.V3, Lead.V4, Lead.V5, Lead.V6),
        List.of(Format.DEFAULT.getLeads(true)).subList(6, 12));

    // 6 x 2: the limb leads down the first column, the chest leads down the second
    assertEquals(CABRERA_LIMB, column(Format.TWO, true, 0));
    assertEquals(column(Format.TWO, false, 1), column(Format.TWO, true, 1));

    // 3 x 4: the first two columns read the Cabrera sequence down then across
    for (Format format : List.of(Format.FOUR, Format.FOUR_RHYTHM)) {
      List<Lead> limb = new ArrayList<>(column(format, true, 0).subList(0, 3));
      limb.addAll(column(format, true, 1).subList(0, 3));
      assertEquals(CABRERA_LIMB, limb, format.name());
      assertEquals(column(format, false, 2), column(format, true, 2), format.name());
      assertEquals(column(format, false, 3), column(format, true, 3), format.name());
    }
    assertEquals(Lead.RHYTHM, Format.FOUR_RHYTHM.getLeads(true)[12]);
  }
}
