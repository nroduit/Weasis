/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.List;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** The toolbar row fits the width of its window: what does not fit goes to the overflow. */
class ToolBarContainerFitTest {

  private static WtoolBar bar(String name, int position, int icons) {
    WtoolBar bar = new WtoolBar(name, position);
    for (int i = 0; i < icons; i++) {
      JButton button = new JButton();
      button.setName(name + i);
      Dimension size = new Dimension(30, 30);
      button.setPreferredSize(size);
      bar.add(button);
    }
    return bar;
  }

  private static void layout(ToolBarContainer row, int width) throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          row.setSize(width, 40);
          row.doLayout();
        });
  }

  private static List<Component> icons(WtoolBar bar) {
    return Arrays.stream(bar.getComponents()).filter(JButton.class::isInstance).toList();
  }

  /** The width the row keeps for its chevron, at its end. */
  private static int menus(ToolBarContainer row) {
    Component[] all = row.getComponents();
    return all[all.length - 1].getPreferredSize().width;
  }

  /** Shown in the row: visible and in a visible toolbar. */
  private static boolean shown(Component icon) {
    return icon.isVisible() && icon.getParent().isVisible();
  }

  @Test
  void theTrailingIconsLeaveANarrowRowAndComeBackWhenItWidens() throws Exception {
    ToolBarContainer row = new ToolBarContainer();
    WtoolBar first = bar("first", 10, 4);
    WtoolBar second = bar("second", 20, 4);
    JButton hiddenByItsOwner = (JButton) icons(first).get(1);
    hiddenByItsOwner.setVisible(false);
    SwingUtilities.invokeAndWait(() -> row.registerToolBar(List.of(first, second)));

    layout(row, 2000);
    assertAll(
        () -> assertTrue(icons(second).stream().allMatch(Component::isVisible)),
        () -> assertFalse(hiddenByItsOwner.isVisible(), "left as its owner set it"));

    // room for the first toolbar and part of the second only
    int firstWidth = first.getPreferredSize().width + menus(row);
    layout(row, firstWidth + 60);
    assertAll(
        () -> assertTrue(first.isVisible()),
        () -> assertFalse(shown(icons(second).getLast()), "the last icon is clipped"),
        () -> assertFalse(hiddenByItsOwner.isVisible()));

    layout(row, 2000);
    assertAll(
        () -> assertTrue(second.isVisible()),
        () -> assertTrue(icons(second).stream().allMatch(Component::isVisible), "all back"),
        () -> assertFalse(hiddenByItsOwner.isVisible(), "never shown by the fitting"));
  }

  @Test
  void aToolbarWithNoRoomForAnIconLeavesTheRowWhole() throws Exception {
    ToolBarContainer row = new ToolBarContainer();
    WtoolBar first = bar("first", 10, 3);
    WtoolBar second = bar("second", 20, 3);
    SwingUtilities.invokeAndWait(() -> row.registerToolBar(List.of(first, second)));
    layout(row, first.getPreferredSize().width + menus(row) + 10);
    assertAll(
        () -> assertTrue(first.isVisible()),
        () -> assertFalse(second.isVisible(), "no icon fits: the whole toolbar is clipped"));
  }

  @Test
  void theChevronShowsInAWideRowWhenAToolbarIsLeftOut() throws Exception {
    ToolBarContainer row = new ToolBarContainer();
    WtoolBar shown = bar("shown", 10, 2);
    WtoolBar off = bar("off", 20, 2);
    off.setComponentEnabled(false);
    SwingUtilities.invokeAndWait(() -> row.registerToolBar(List.of(shown, off)));
    layout(row, 2000);
    Component[] all = row.getComponents();
    assertTrue(all[all.length - 1].isVisible(), "the chevron offers the toolbar left out");

    SwingUtilities.invokeAndWait(() -> row.registerToolBar(List.of(shown)));
    layout(row, 2000);
    Component[] now = row.getComponents();
    assertFalse(now[now.length - 1].isVisible(), "nothing left out, everything fits");
  }
}
