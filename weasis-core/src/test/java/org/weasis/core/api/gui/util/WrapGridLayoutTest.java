/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** The number of columns follows the width; the height follows the number of rows. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class WrapGridLayoutTest {

  private static final int CELL = 40;
  private static final int CELL_HEIGHT = 30;

  /** Seven cells of 40 x 30 in a panel with a border of 5. */
  private static JPanel palette(int preferredColumns) {
    JPanel panel = new JPanel(new WrapGridLayout(preferredColumns));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    for (int i = 0; i < 7; i++) {
      JPanel cell = new JPanel();
      cell.setPreferredSize(new Dimension(CELL - (i % 2) * 6, CELL_HEIGHT));
      panel.add(cell);
    }
    return panel;
  }

  private static int preferredHeightAt(JPanel panel, int width) {
    panel.setSize(width, 10);
    return panel.getPreferredSize().height;
  }

  @Test
  void before_it_has_a_width_the_panel_asks_for_the_preferred_columns() {
    assertEquals(new Dimension(5 * CELL + 10, 2 * CELL_HEIGHT + 10), palette(5).getPreferredSize());
  }

  @Test
  void the_height_grows_as_the_width_allows_fewer_columns() {
    JPanel panel = palette(5);
    assertAll(
        () -> assertEquals(CELL_HEIGHT + 10, preferredHeightAt(panel, 7 * CELL + 10)),
        () -> assertEquals(CELL_HEIGHT + 10, preferredHeightAt(panel, 1000)),
        () -> assertEquals(2 * CELL_HEIGHT + 10, preferredHeightAt(panel, 4 * CELL + 10)),
        () -> assertEquals(2 * CELL_HEIGHT + 10, preferredHeightAt(panel, 5 * CELL + 9)),
        () -> assertEquals(4 * CELL_HEIGHT + 10, preferredHeightAt(panel, 2 * CELL + 10)),
        () -> assertEquals(7 * CELL_HEIGHT + 10, preferredHeightAt(panel, 20)));
  }

  @Test
  void an_imposed_width_is_never_refused() {
    JPanel panel = palette(5);
    panel.setSize(3 * CELL + 10, 10);
    assertAll(
        () -> assertEquals(CELL + 10, panel.getPreferredSize().width),
        () -> assertEquals(CELL + 10, panel.getMinimumSize().width),
        () -> assertEquals(Integer.MAX_VALUE, panel.getMaximumSize().width),
        () -> assertEquals(panel.getPreferredSize().height, panel.getMaximumSize().height));
  }

  @Test
  void cells_are_equal_and_wrap_from_left_to_right() {
    JPanel panel = palette(5);
    panel.setSize(3 * CELL + 10, 200);
    panel.doLayout();
    assertAll(
        () ->
            assertEquals(new Rectangle(5, 5, CELL, CELL_HEIGHT), panel.getComponent(0).getBounds()),
        () ->
            assertEquals(
                new Rectangle(5 + 2 * CELL, 5, CELL, CELL_HEIGHT),
                panel.getComponent(2).getBounds()),
        () ->
            assertEquals(
                new Rectangle(5, 5 + CELL_HEIGHT, CELL, CELL_HEIGHT),
                panel.getComponent(3).getBounds()),
        () ->
            assertEquals(
                new Rectangle(5, 5 + 2 * CELL_HEIGHT, CELL, CELL_HEIGHT),
                panel.getComponent(6).getBounds()));
  }

  @Test
  void hidden_components_take_no_cell() {
    JPanel panel = palette(5);
    panel.getComponent(1).setVisible(false);
    panel.setSize(3 * CELL + 10, 200);
    panel.doLayout();
    assertAll(
        () -> assertEquals(2 * CELL_HEIGHT + 10, panel.getPreferredSize().height),
        () -> assertEquals(5 + CELL, panel.getComponent(2).getX()));
  }
}
