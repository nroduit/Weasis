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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/** Options flow on one line when there is room and wrap, group by group, when there is not. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class WrapFlowLayoutTest {

  private static final int GAP = 10;
  private static final int VGAP = 4;
  private static final int HEIGHT = 20;

  private static JPanel box(int width) {
    JPanel box = new JPanel();
    box.setPreferredSize(new Dimension(width, HEIGHT));
    box.setMinimumSize(new Dimension(width, HEIGHT));
    return box;
  }

  /** "Line:" + button + spinner, a check box, "Unit:" + combo; border of 5. */
  private static JPanel options() {
    JPanel panel = new JPanel(new WrapFlowLayout(GAP, VGAP));
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    panel.add(box(30), WrapFlowLayout.KEEP_WITH_NEXT);
    panel.add(box(24), WrapFlowLayout.KEEP_WITH_NEXT);
    panel.add(box(40));
    panel.add(box(100));
    panel.add(box(30), WrapFlowLayout.KEEP_WITH_NEXT);
    panel.add(box(80));
    return panel;
  }

  private static int lineOf(JPanel panel, int index) {
    return (panel.getComponent(index).getY() - 5) / (HEIGHT + VGAP);
  }

  private static JPanel laidOutAt(int width) {
    JPanel panel = options();
    panel.setSize(width, 10);
    panel.setSize(width, panel.getPreferredSize().height);
    panel.doLayout();
    return panel;
  }

  @Test
  void without_a_width_everything_is_on_one_line() {
    int all = 30 + 24 + 40 + 100 + 30 + 80 + 5 * GAP;
    assertEquals(new Dimension(all + 10, HEIGHT + 10), options().getPreferredSize());
  }

  @Test
  void a_wide_container_keeps_one_line() {
    JPanel panel = laidOutAt(500);
    assertAll(
        () -> assertEquals(HEIGHT + 10, panel.getHeight()),
        () -> assertEquals(0, lineOf(panel, 5)),
        () -> assertEquals(5, panel.getComponent(0).getX()),
        () -> assertEquals(5 + 30 + GAP, panel.getComponent(1).getX()));
  }

  @Test
  void groups_wrap_as_a_whole_and_the_height_follows() {
    // 114 for the line group, 100 for the check box, 120 for the unit group
    JPanel panel = laidOutAt(240);
    assertAll(
        () -> assertEquals(2 * HEIGHT + VGAP + 10, panel.getHeight()),
        () -> assertEquals(0, lineOf(panel, 2)),
        () -> assertEquals(0, lineOf(panel, 3)),
        () -> assertEquals(1, lineOf(panel, 4), "the label goes down with its combo"),
        () -> assertEquals(1, lineOf(panel, 5)),
        () -> assertEquals(5, panel.getComponent(4).getX()));
  }

  @Test
  void one_group_per_line_in_a_narrow_container() {
    JPanel panel = laidOutAt(135);
    assertAll(
        () -> assertEquals(3 * HEIGHT + 2 * VGAP + 10, panel.getHeight()),
        () -> assertEquals(0, lineOf(panel, 0)),
        () -> assertEquals(1, lineOf(panel, 3)),
        () -> assertEquals(2, lineOf(panel, 5)));
  }

  @Test
  void a_group_wider_than_the_container_is_split_rather_than_cut() {
    JPanel panel = laidOutAt(100);
    assertAll(
        () -> assertEquals(0, lineOf(panel, 1)),
        () -> assertEquals(1, lineOf(panel, 2), "the spinner no longer fits beside the button"),
        () -> assertTrue(panel.getComponent(3).getWidth() <= 90, "never wider than the container"),
        () -> assertEquals(panel.getPreferredSize().height, panel.getHeight()));
  }

  @Test
  void any_width_is_accepted_and_the_height_is_not_stretched() {
    JPanel panel = laidOutAt(240);
    assertAll(
        () -> assertEquals(100 + 10, panel.getMinimumSize().width),
        () -> assertEquals(Integer.MAX_VALUE, panel.getMaximumSize().width),
        () -> assertEquals(panel.getPreferredSize().height, panel.getMaximumSize().height));
  }
}
