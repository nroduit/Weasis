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

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
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
  void a_right_to_left_container_flows_from_the_right() {
    JPanel panel = options();
    panel.applyComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
    panel.setSize(500, 10);
    panel.setSize(500, panel.getPreferredSize().height);
    panel.doLayout();
    assertAll(
        () -> assertEquals(500 - 5 - 30, panel.getComponent(0).getX()),
        () -> assertEquals(500 - 5 - 30 - GAP - 24, panel.getComponent(1).getX()),
        () -> assertEquals(0, lineOf(panel, 5)));
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

  /** "Archive:" + a combo that fills, its minimum 60, preferred 100 and maximum {@code max}. */
  private static JPanel fillingRow(int width, int max) {
    JPanel panel = new JPanel(new WrapFlowLayout(GAP, VGAP));
    panel.add(box(30), WrapFlowLayout.KEEP_WITH_NEXT);
    JPanel combo = box(100);
    combo.setMinimumSize(new Dimension(60, HEIGHT));
    combo.setMaximumSize(new Dimension(max, HEIGHT));
    panel.add(combo, WrapFlowLayout.FILL);
    panel.setSize(width, 10);
    panel.setSize(width, panel.getPreferredSize().height);
    panel.doLayout();
    return panel;
  }

  @Test
  void a_filling_component_takes_the_rest_of_its_line() {
    JPanel panel = fillingRow(300, Integer.MAX_VALUE);
    assertEquals(300 - 30 - GAP, panel.getComponent(1).getWidth());
  }

  @Test
  void a_filling_component_stops_at_its_maximum_width_in_a_wide_container() {
    JPanel panel = fillingRow(1000, 150);
    assertAll(
        () -> assertEquals(150, panel.getComponent(1).getWidth()),
        () -> assertEquals(30 + GAP, panel.getComponent(1).getX(), "still beside its label"));
  }

  @Test
  void a_filling_component_shrinks_to_its_minimum_in_a_narrow_container() {
    JPanel panel = fillingRow(30 + GAP + 70, 150);
    assertAll(
        () -> assertEquals(0, lineOf(panel, 1)),
        () -> assertEquals(70, panel.getComponent(1).getWidth()));
  }

  @Test
  void the_widest_line_keeps_every_shown_row_on_one_line_with_the_insets_around_it() {
    JPanel root = new JPanel(new BorderLayout());
    root.setBorder(BorderFactory.createEmptyBorder(0, 7, 0, 3));
    JPanel shown = new JPanel(new BorderLayout());
    shown.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 6));
    shown.add(options(), BorderLayout.CENTER);
    root.add(shown, BorderLayout.NORTH);
    JPanel folded = new JPanel(new WrapFlowLayout(GAP, VGAP));
    folded.add(box(2000));
    folded.setVisible(false);
    root.add(folded, BorderLayout.SOUTH);
    // laid out narrow, so the row wraps and its preferred width is only its widest component
    root.setSize(150, 400);
    root.doLayout();
    shown.doLayout();

    int line = 30 + 24 + 40 + 100 + 30 + 80 + 5 * GAP + 10;
    assertAll(
        () -> assertEquals(7 + 3 + 4 + 6 + line, WrapFlowLayout.widestLine(root)),
        () -> assertEquals(0, WrapFlowLayout.widestLine(box(50)), "no wrapping row"));
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
