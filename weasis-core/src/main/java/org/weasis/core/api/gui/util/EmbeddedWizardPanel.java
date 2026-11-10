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

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;

/**
 * Compact embedded host of {@link AbstractItemDialogPage} pages: a scrollable page area with a
 * caller-supplied action bar under it. With several pages, a row of toggle buttons above the page
 * picks the one shown; a single page is shown directly. It serves the tools of the shell slots,
 * where the wizard dialog ({@link AbstractWizardDialog}) has no place.
 */
public class EmbeddedWizardPanel extends JPanel {

  private final JScrollPane pageHost = new JScrollPane();
  private final List<AbstractItemDialogPage> pages;
  private final Map<AbstractItemDialogPage, JToggleButton> selectors = new LinkedHashMap<>();
  private AbstractItemDialogPage current;

  public EmbeddedWizardPanel(List<AbstractItemDialogPage> pages, JComponent actionBar) {
    super(new BorderLayout());
    this.pages = List.copyOf(pages);
    pageHost.setBorder(BorderFactory.createEmptyBorder());
    if (this.pages.size() > 1) {
      add(createSelector(), BorderLayout.NORTH);
    }
    add(pageHost, BorderLayout.CENTER);
    if (actionBar != null) {
      add(actionBar, BorderLayout.SOUTH);
    }
    showPage(this.pages.isEmpty() ? null : this.pages.getFirst());
  }

  private JPanel createSelector() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEADING, 2, 0));
    bar.setBorder(BorderFactory.createEmptyBorder(5, 5, 0, 5));
    ButtonGroup group = new ButtonGroup();
    for (AbstractItemDialogPage page : pages) {
      JToggleButton button = new JToggleButton(page.getTitle());
      button.putClientProperty(
          FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
      button.setFocusable(false);
      button.addActionListener(_ -> showPage(page));
      group.add(button);
      selectors.put(page, button);
      bar.add(button);
    }
    return bar;
  }

  private void showPage(AbstractItemDialogPage page) {
    if (page == current) {
      return;
    }
    if (current != null) {
      current.deselectPageAction();
    }
    current = page;
    if (page != null) {
      JToggleButton button = selectors.get(page);
      if (button != null) {
        button.setSelected(true);
      }
      pageHost.setViewportView(page);
      page.selectPageAction();
    }
    revalidate();
    repaint();
  }

  public AbstractItemDialogPage getSelectedPage() {
    return current;
  }

  /** The pages in order. */
  public List<AbstractItemDialogPage> getPages() {
    return pages;
  }

  /** Selects the page with the given title when present. */
  public void selectPage(String title) {
    if (title != null) {
      pages.stream()
          .filter(page -> title.equals(page.getTitle()))
          .findFirst()
          .ifPresent(this::showPage);
    }
  }

  /** Releases every page; call when the embedded host is discarded. */
  public void closeAllPages() {
    for (AbstractItemDialogPage page : pages) {
      page.closeAdditionalWindow();
    }
  }
}
