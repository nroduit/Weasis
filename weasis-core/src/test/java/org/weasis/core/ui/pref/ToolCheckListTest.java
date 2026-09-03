/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The checked tools, in the order of the list, are the palette. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class ToolCheckListTest {

  private static ToolCheckList list() {
    ToolCheckList list = new ToolCheckList("Tools"); // NON-NLS
    list.setItems(
        List.of(
            new ToolCheckList.Item("a", "A", null, true), // NON-NLS
            new ToolCheckList.Item("b", "B", null, false), // NON-NLS
            new ToolCheckList.Item("c", "C", null, true))); // NON-NLS
    return list;
  }

  @Test
  void toggling_changes_the_palette() {
    ToolCheckList list = list();
    list.toggle(1);
    list.toggle(0);
    list.toggle(7);
    assertEquals(List.of("b", "c"), list.checkedKeys());
  }

  @Test
  void the_selected_row_moves_and_stays_selected() {
    ToolCheckList list = list();
    list.select(2);
    list.move(-1);
    list.move(-1);
    list.move(-1);
    assertAll(
        () -> assertEquals(List.of("c", "a", "b"), list.keys()),
        () -> assertEquals(0, list.selectedIndex()),
        () -> assertEquals(List.of("c", "a"), list.checkedKeys()));
  }

  @Test
  void a_row_dropped_elsewhere_takes_that_place() {
    ToolCheckList list = list();
    list.moveRow(0, 2);
    list.moveRow(5, 0);
    assertEquals(List.of("b", "c", "a"), list.keys());
  }

  @Test
  void a_tool_can_be_checked_by_key() {
    ToolCheckList list = list();
    list.setChecked("b", true); // NON-NLS
    list.setChecked("a", false); // NON-NLS
    assertEquals(List.of("b", "c"), list.checkedKeys());
  }
}
