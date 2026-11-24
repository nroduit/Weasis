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

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;

/**
 * A page with a header and a long list, in a short slot: the list scrolls on its own and the header
 * stays in place; the whole page scrolls only when the slot cannot hold the header and the minimum
 * height of the list.
 */
@DisplayNameGeneration(ReplaceUnderscores.class)
class EmbeddedWizardPanelTest {

  private static final int HEADER_HEIGHT = 80;
  private static final int LIST_HEIGHT = 2000;
  private static final int MIN_LIST_HEIGHT = 100;

  private static final class ListPage extends AbstractItemDialogPage {
    final JPanel header = fixed(HEADER_HEIGHT);
    final JScrollPane listScroll;

    ListPage() {
      super("List");
      listScroll = setHeaderAndList(header, fixed(LIST_HEIGHT), MIN_LIST_HEIGHT);
    }

    @Override
    public void resetToDefaultValues() {}

    @Override
    public void closeAdditionalWindow() {}
  }

  private static JPanel fixed(int height) {
    JPanel panel = new JPanel();
    panel.setPreferredSize(new Dimension(200, height));
    panel.setMinimumSize(new Dimension(50, height));
    return panel;
  }

  /** Lays the tree out from the top, as a validation of a shown window does. */
  private static void layOut(Component component) {
    if (component instanceof Container container) {
      container.doLayout();
      for (Component child : container.getComponents()) {
        layOut(child);
      }
    }
  }

  private static JScrollPane pageHost(int height, ListPage page) {
    EmbeddedWizardPanel wizard = new EmbeddedWizardPanel(List.of(page), null);
    wizard.setSize(300, height);
    layOut(wizard);
    // a second pass, once the viewport knows its size
    layOut(wizard);
    return (JScrollPane) wizard.getComponent(0);
  }

  @Test
  void in_a_short_slot_the_list_scrolls_and_the_header_stays() {
    ListPage page = new ListPage();
    // shorter than the preferred height of the page, taller than its minimum height
    int slot = HEADER_HEIGHT + 2 * MIN_LIST_HEIGHT;
    assertTrue(slot < page.getPreferredSize().height && slot > page.getMinimumSize().height);
    JScrollPane host = pageHost(slot, page);
    int viewport = host.getViewport().getHeight();

    assertAll(
        () -> assertEquals(viewport, page.getHeight(), "the page fills the slot, not more"),
        () -> assertEquals(HEADER_HEIGHT, page.header.getHeight(), "the header keeps its height"),
        () ->
            assertTrue(
                page.listScroll.getViewport().getHeight() < LIST_HEIGHT,
                "the list shows a part of itself and scrolls"),
        () ->
            assertTrue(
                page.listScroll.getY() + page.listScroll.getHeight() <= page.getHeight(),
                "the list ends within the slot"));
  }

  @Test
  void the_width_to_fit_keeps_a_wrapping_row_on_one_line_after_it_wrapped() {
    ListPage page = new ListPage();
    page.header.setLayout(new WrapFlowLayout(10, 4));
    for (int i = 0; i < 4; i++) {
      page.header.add(fixed(20)).setPreferredSize(new Dimension(150, 20));
    }
    EmbeddedWizardPanel wizard = new EmbeddedWizardPanel(List.of(page), null);
    // a narrow side: the header wraps to one box per line
    wizard.setSize(200, 600);
    layOut(wizard);
    layOut(wizard);

    int line = 4 * 150 + 3 * 10;
    assertTrue(
        wizard.getWidthToFit() >= line,
        "asks for the one-line width of the header, not only for its widest box");
  }

  @Test
  void below_the_minimum_of_the_list_the_whole_page_scrolls() {
    ListPage page = new ListPage();
    JScrollPane host = pageHost(HEADER_HEIGHT + MIN_LIST_HEIGHT / 2, page);

    assertTrue(
        page.getHeight() > host.getViewport().getHeight(),
        "the page keeps its minimum height and the slot scrolls it");
  }
}
