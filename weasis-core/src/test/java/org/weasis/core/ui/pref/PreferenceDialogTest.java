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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.ui.pref.PreferenceDialog.PageNode;

@DisplayNameGeneration(ReplaceUnderscores.class)
class PreferenceDialogTest {

  private static final class Page extends AbstractItemDialogPage {
    Page(String title) {
      super(title, 10);
      add(new JLabel("<html>Display <b>profile</b></html>")); // NON-NLS
      JCheckBox box = new JCheckBox("Convert color images"); // NON-NLS
      box.setToolTipText("ICC conversion"); // NON-NLS
      add(box);
    }

    @Override
    public void closeAdditionalWindow() {
      // nothing to save
    }

    @Override
    public void resetToDefaultValues() {
      // nothing to reset
    }
  }

  @Test
  void a_page_is_built_once_and_only_when_asked() {
    AtomicInteger built = new AtomicInteger();
    PageNode node =
        new PageNode(
            "Monitors", // NON-NLS
            108,
            () -> {
              built.incrementAndGet();
              return new Page("Monitors"); // NON-NLS
            });
    assertAll(
        () -> assertFalse(node.isBuilt()),
        () -> assertEquals(0, built.get(), "describing a page does not build it"));

    AbstractItemDialogPage page = node.page();

    assertAll(
        () -> assertTrue(node.isBuilt()),
        () -> assertSame(page, node.page()),
        () -> assertEquals(1, built.get()));
  }

  @Test
  void a_page_that_cannot_be_built_is_reported_once() {
    AtomicInteger tries = new AtomicInteger();
    PageNode node =
        new PageNode(
            "Broken", // NON-NLS
            1,
            () -> {
              tries.incrementAndGet();
              throw new IllegalStateException("no data"); // NON-NLS
            });
    assertNull(node.page());
    assertNull(node.page());
    assertEquals(1, tries.get());
  }

  @Test
  void the_path_names_the_parents() {
    PageNode general = new PageNode("General", 100, () -> new Page("General")); // NON-NLS
    PageNode monitors = new PageNode("Monitors", 108, () -> new Page("Monitors")); // NON-NLS
    general.add(monitors);
    assertAll(
        () -> assertEquals("General", general.path()), // NON-NLS
        () -> assertTrue(monitors.path().startsWith("General")), // NON-NLS
        () -> assertTrue(monitors.path().endsWith("Monitors"))); // NON-NLS
  }

  @Test
  void a_search_looks_into_the_title_then_into_the_texts_of_a_built_page() {
    PageNode node = new PageNode("Monitors", 108, () -> new Page("Monitors")); // NON-NLS
    assertAll(
        () -> assertTrue(PreferenceDialog.matches(node.index(), "monit")), // NON-NLS
        () -> assertFalse(PreferenceDialog.matches(node.index(), "profile"))); // NON-NLS

    node.page();

    assertAll(
        () -> assertTrue(PreferenceDialog.matches(node.index(), "profile"), "label, without tags"),
        () -> assertTrue(PreferenceDialog.matches(node.index(), "convert color")), // NON-NLS
        () -> assertTrue(PreferenceDialog.matches(node.index(), "icc"), "tooltip"), // NON-NLS
        () -> assertTrue(PreferenceDialog.matches(node.index(), "color monitors"), "every word"),
        () -> assertFalse(PreferenceDialog.matches(node.index(), "color printer"))); // NON-NLS
  }
}
