/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.BorderLayout;
import java.text.DecimalFormatSymbols;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.MeasureItem;
import org.weasis.core.ui.model.utils.bean.Measurement;

/** A table of measured values shows rounded text and copies the numbers it holds. */
@DisplayNameGeneration(ReplaceUnderscores.class)
class MeasureTablesTest {

  private static final List<MeasureItem> ITEMS =
      List.of(
          new MeasureItem(new Measurement("ratio", "Ratio", 1, true), 0.4849271, null), // NON-NLS
          new MeasureItem(
              new Measurement("stats.pixels", "Pixels", 2, true), 1520.0, "pix"), // NON-NLS
          new MeasureItem(
              new Measurement("ctr.mrd", "Midline to right heart border", 3, true), // NON-NLS
              61.25,
              "mm")); // NON-NLS

  private static JTable table() {
    JTable table =
        new JTable(
            new SimpleTableModel(
                new String[] {"Parameter", "Value"}, MeasureTool.getLabels(ITEMS))); // NON-NLS
    table
        .getColumnModel()
        .getColumn(1)
        .setCellRenderer(
            MeasureTables.valueRenderer(ITEMS, new MeasureFormat(null, null, MeasureFormat.AUTO)));
    return table;
  }

  private static JLabel cell(JTable table, int row, int column) {
    return (JLabel) table.prepareRenderer(table.getCellRenderer(row, column), row, column);
  }

  @Test
  void values_are_shown_rounded_and_right_aligned() {
    JTable table = table();
    char mark = DecimalFormatSymbols.getInstance().getDecimalSeparator();
    assertAll(
        () -> assertEquals("0" + mark + "48", cell(table, 0, 1).getText()),
        () -> assertEquals("1520", cell(table, 1, 1).getText()),
        () -> assertEquals(SwingConstants.RIGHT, cell(table, 0, 1).getHorizontalAlignment()));
  }

  @Test
  void in_a_narrow_container_the_values_stay_whole_and_the_names_give_way() {
    JTable table = table();
    JPanel container = new JPanel(new BorderLayout());
    MeasureTables.fit(table, container);
    int valueWidth = MeasureTables.contentWidth(table, 1);
    int wanted = container.getPreferredSize().width;
    int narrow = valueWidth + 45;

    layoutAt(container, table, narrow);

    assertAll(
        () -> assertTrue(wanted > narrow, "the test must squeeze the table: " + wanted),
        () -> assertEquals(narrow, table.getWidth()),
        () -> assertEquals(valueWidth, table.getColumnModel().getColumn(1).getWidth()),
        () -> assertEquals(narrow - valueWidth, table.getColumnModel().getColumn(0).getWidth()),
        () -> assertEquals("Ratio", cell(table, 0, 0).getToolTipText())); // NON-NLS
  }

  @Test
  void in_a_wide_container_the_columns_stop_at_their_longest_text() {
    JTable table = table();
    JPanel container = new JPanel(new BorderLayout());
    MeasureTables.fit(table, container);
    int nameWidth = MeasureTables.contentWidth(table, 0);
    int valueWidth = MeasureTables.contentWidth(table, 1);

    layoutAt(container, table, 3 * (nameWidth + valueWidth));

    assertAll(
        () -> assertEquals(nameWidth + valueWidth, table.getWidth()),
        () -> assertEquals(0, table.getX(), "flush left"),
        () -> assertEquals(nameWidth, table.getColumnModel().getColumn(0).getWidth()),
        () -> assertEquals(valueWidth, table.getColumnModel().getColumn(1).getWidth()),
        () -> assertEquals(table.getWidth(), table.getTableHeader().getWidth()));
  }

  private static void layoutAt(JPanel container, JTable table, int width) {
    container.setSize(width, 200);
    container.doLayout();
    table.getParent().doLayout();
    table.doLayout();
    table.getTableHeader().doLayout();
  }

  @Test
  void the_copied_text_keeps_the_full_values() {
    assertEquals(
        "Parameter\tValue\nRatio\t0.4849271\nPixels [pix]\t1520.0\n" // NON-NLS
            + "Midline to right heart border [mm]\t61.25", // NON-NLS
        MeasureTables.asText(table()));
  }
}
