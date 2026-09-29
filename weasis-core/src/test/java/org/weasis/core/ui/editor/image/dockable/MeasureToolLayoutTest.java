/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.dockable;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.CollapsiblePanel;
import org.weasis.core.api.gui.util.WrapGridLayout;

/** Every section of the tool spans its width, whatever the alignment of the component. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasureToolLayoutTest {

  private static final int WIDTH = 300;

  @Test
  void centred_and_left_aligned_sections_all_fill_the_width() {
    JPanel tool = new JPanel(MeasureTool.sectionLayout());
    JPanel centred = new JPanel(new BorderLayout());
    centred.add(new JLabel("palette")); // NON-NLS
    IntensityProfilePanel profile = new IntensityProfilePanel();
    CollapsiblePanel section = new CollapsiblePanel("Profile", profile, true); // NON-NLS
    JPanel hidden = new JPanel();
    hidden.setPreferredSize(new Dimension(50, 80));
    hidden.setVisible(false);
    centred.setPreferredSize(new Dimension(150, 40));
    tool.add(centred);
    tool.add(hidden);
    tool.add(section, MeasureTool.FOLLOW_WIDTH);
    int preferredWidth = tool.getPreferredSize().width;
    tool.setSize(WIDTH, 600);
    layoutTree(tool);

    assertAll(
        () -> assertEquals(150, preferredWidth, "the chart section must not widen the tool"),
        () -> assertEquals(0, centred.getX()),
        () -> assertEquals(WIDTH, centred.getWidth()),
        () -> assertEquals(0, section.getX()),
        () -> assertEquals(WIDTH, section.getWidth()),
        () -> assertEquals(centred.getHeight(), section.getY(), "a hidden section takes no room"),
        () -> assertTrue(profile.getWidth() > WIDTH - 40, "chart width " + profile.getWidth()));
  }

  @Test
  void the_palette_wraps_to_the_width_of_the_tool_and_pushes_the_next_section_down() {
    JPanel tool = new JPanel(MeasureTool.sectionLayout());
    JPanel block = new JPanel();
    block.setLayout(new BoxLayout(block, BoxLayout.Y_AXIS));
    JPanel palette = new JPanel(new WrapGridLayout(5));
    for (int i = 0; i < 10; i++) {
      JPanel button = new JPanel();
      button.setPreferredSize(new Dimension(40, 30));
      palette.add(button);
    }
    JLabel below = new JLabel("options"); // NON-NLS
    block.add(palette);
    block.add(below);
    tool.add(block);

    // Wide: the ten buttons fit on two rows of five, then on one row
    assertEquals(2 * 30, heightOfPaletteAt(tool, palette, 5 * 40));
    assertEquals(30, heightOfPaletteAt(tool, palette, 500));
    // Narrow: three per row, four rows, and what follows is below them
    assertEquals(4 * 30, heightOfPaletteAt(tool, palette, 3 * 40 + 10));
    assertTrue(below.getY() >= palette.getY() + palette.getHeight());
  }

  @Test
  void the_profile_combo_fills_its_row_and_drops_under_the_label_when_too_narrow() {
    JComboBox<String> combo = new JComboBox<>(new String[] {"Cross-sectional (CT, MR, PET)"});
    JLabel label = new JLabel("Profile:"); // NON-NLS
    JPanel row = MeasureTool.profileRow(label, combo);
    int natural = combo.getPreferredSize().width;
    int labelWidth = label.getPreferredSize().width;

    layoutRow(row, 1000);
    int wideCombo = combo.getWidth();
    boolean wideSameLine = combo.getY() < label.getY() + label.getHeight();
    int wideEnd = combo.getX() + combo.getWidth();

    layoutRow(row, labelWidth + 130);
    int besideCombo = combo.getWidth();
    boolean besideSameLine = combo.getY() < label.getY() + label.getHeight();

    layoutRow(row, labelWidth + 60);
    int droppedWidth = combo.getWidth();
    int droppedX = combo.getX();
    boolean dropped = combo.getY() >= label.getY() + label.getHeight();

    assertAll(
        () -> assertTrue(wideSameLine),
        () -> assertTrue(wideCombo >= natural, "fills a wide row: " + wideCombo),
        () -> assertEquals(1000 - 5, wideEnd, "up to the right inset"),
        () -> assertTrue(besideSameLine, "stays beside the label while its minimum fits"),
        () -> assertTrue(besideCombo >= 90 && besideCombo < natural, "shortened: " + besideCombo),
        () -> assertTrue(dropped, "drops under the label"),
        () -> assertEquals(label.getX(), droppedX),
        () -> assertEquals(labelWidth + 60 - 10, droppedWidth, "and takes the whole width"),
        () -> assertEquals(row.getPreferredSize().height, row.getHeight()));
  }

  private static void layoutRow(JPanel row, int width) {
    row.setSize(width, 10);
    row.setSize(width, row.getPreferredSize().height);
    row.doLayout();
  }

  /** Two passes, as on screen: the first gives the width, the second the height it needs. */
  private static int heightOfPaletteAt(JPanel tool, JPanel palette, int width) {
    for (int pass = 0; pass < 2; pass++) {
      tool.setSize(width, tool.getPreferredSize().height);
      invalidateTree(tool);
      layoutTree(tool);
    }
    return palette.getHeight();
  }

  private static void invalidateTree(Component component) {
    component.invalidate();
    if (component instanceof java.awt.Container container) {
      for (Component child : container.getComponents()) {
        invalidateTree(child);
      }
    }
  }

  private static void layoutTree(Component component) {
    component.doLayout();
    if (component instanceof java.awt.Container container) {
      for (Component child : container.getComponents()) {
        layoutTree(child);
      }
    }
  }
}
