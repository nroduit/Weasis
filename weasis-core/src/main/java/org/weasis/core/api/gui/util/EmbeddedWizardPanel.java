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

import java.awt.BorderLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

/**
 * Compact embedded host of {@link AbstractItemDialogPage} wizard pages: a page selector combo above
 * a scrollable page area, with a caller-supplied action bar at the bottom. The dialog counterpart
 * ({@link AbstractWizardDialog}) stays untouched — this host serves embedded contexts like the
 * bottom-left footer.
 */
public class EmbeddedWizardPanel extends JPanel {

  private final JComboBox<AbstractItemDialogPage> selector = new JComboBox<>();
  private final JScrollPane pageHost = new JScrollPane();
  private final List<AbstractItemDialogPage> pages;
  private AbstractItemDialogPage current;

  public EmbeddedWizardPanel(List<AbstractItemDialogPage> pages, JComponent actionBar) {
    super(new BorderLayout());
    this.pages = List.copyOf(pages);
    selector.setModel(
        new DefaultComboBoxModel<>(this.pages.toArray(AbstractItemDialogPage[]::new)));
    selector.setRenderer(
        new BasicComboBoxRenderer() {
          @Override
          public java.awt.Component getListCellRendererComponent(
              JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel label =
                (JLabel)
                    super.getListCellRendererComponent(
                        list, value, index, isSelected, cellHasFocus);
            if (value instanceof PageItem page) {
              label.setText(page.getTitle());
            }
            return label;
          }
        });
    selector.addActionListener(e -> showSelectedPage());

    JPanel top = new JPanel(new BorderLayout());
    top.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    top.add(selector, BorderLayout.CENTER);
    pageHost.setBorder(BorderFactory.createEmptyBorder());

    add(top, BorderLayout.NORTH);
    add(pageHost, BorderLayout.CENTER);
    if (actionBar != null) {
      add(actionBar, BorderLayout.SOUTH);
    }
    showSelectedPage();
  }

  private void showSelectedPage() {
    AbstractItemDialogPage page = (AbstractItemDialogPage) selector.getSelectedItem();
    if (page == current) {
      return;
    }
    if (current != null) {
      current.deselectPageAction();
    }
    current = page;
    if (page != null) {
      pageHost.setViewportView(page);
      page.selectPageAction();
    }
    revalidate();
    repaint();
  }

  public AbstractItemDialogPage getSelectedPage() {
    return current;
  }

  /** Selects the page with the given title when present. */
  public void selectPage(String title) {
    if (title != null) {
      pages.stream()
          .filter(page -> title.equals(page.getTitle()))
          .findFirst()
          .ifPresent(selector::setSelectedItem);
    }
  }

  /** Releases every page; call when the embedded host is discarded. */
  public void closeAllPages() {
    for (AbstractItemDialogPage page : pages) {
      page.closeAdditionalWindow();
    }
  }
}
