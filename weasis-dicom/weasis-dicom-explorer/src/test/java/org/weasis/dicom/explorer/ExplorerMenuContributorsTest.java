/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * The registry behind the series context menu: what a plugin registers shows up once, below a
 * separator, and a plugin that fails leaves the built-in menu intact.
 */
class ExplorerMenuContributorsTest {

  private final List<ExplorerMenuContributor> registered = new ArrayList<>();

  @AfterEach
  void unregisterAll() {
    registered.forEach(ExplorerMenuContributors::unregister);
  }

  private ExplorerMenuContributor register(ExplorerMenuContributor contributor) {
    ExplorerMenuContributors.register(contributor);
    registered.add(contributor);
    return contributor;
  }

  @Test
  void aContributorIsRegisteredOnce() {
    ExplorerMenuContributor c = register((menu, model, clicked, selection) -> {});
    ExplorerMenuContributors.register(c);
    ExplorerMenuContributors.register(null);

    assertEquals(1, ExplorerMenuContributors.all().stream().filter(c::equals).count());

    ExplorerMenuContributors.unregister(c);
    assertTrue(ExplorerMenuContributors.all().stream().noneMatch(c::equals));
  }

  @Test
  void contributedItemsGoBelowASeparator() {
    register((menu, model, clicked, selection) -> menu.add(new JMenuItem("AI tasks")));
    JPopupMenu menu = new JPopupMenu();
    menu.add(new JMenuItem("Open"));

    ExplorerMenuContributors.addMenuItems(menu, null, null, List.of());

    assertEquals(3, menu.getComponentCount());
    assertInstanceOf(JPopupMenu.Separator.class, menu.getComponent(1));
    assertEquals("AI tasks", ((JMenuItem) menu.getComponent(2)).getText());
  }

  @Test
  void noSeparatorWhenTheMenuWasEmpty() {
    register((menu, model, clicked, selection) -> menu.add(new JMenuItem("AI tasks")));
    JPopupMenu menu = new JPopupMenu();

    ExplorerMenuContributors.addMenuItems(menu, null, null, List.of());

    assertEquals(1, menu.getComponentCount());
    assertInstanceOf(JMenuItem.class, menu.getComponent(0));
  }

  @Test
  void noSeparatorWhenNothingWasAdded() {
    register((menu, model, clicked, selection) -> {});
    JPopupMenu menu = new JPopupMenu();
    menu.add(new JMenuItem("Open"));

    ExplorerMenuContributors.addMenuItems(menu, null, null, List.of());

    assertEquals(1, menu.getComponentCount());
  }

  @Test
  void aFailingContributorDoesNotBreakTheOthers() {
    register(
        (menu, model, clicked, selection) -> {
          throw new IllegalStateException("plugin not ready");
        });
    register((menu, model, clicked, selection) -> menu.add(new JMenuItem("Still here")));
    JPopupMenu menu = new JPopupMenu();
    menu.add(new JMenuItem("Open"));

    ExplorerMenuContributors.addMenuItems(menu, null, null, List.of());

    assertEquals(3, menu.getComponentCount());
    assertEquals("Still here", ((JMenuItem) menu.getComponent(2)).getText());
  }
}
