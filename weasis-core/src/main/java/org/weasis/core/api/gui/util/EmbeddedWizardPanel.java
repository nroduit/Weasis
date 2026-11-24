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
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

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
      pageHost.setViewportView(new PageView(page));
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

  /**
   * The width that shows every page whole: the widest page with the vertical scroll bar the page
   * area shows when the slot is short, or the selector row or the action bar when wider. A page
   * with wrapping rows ({@link WrapFlowLayout}) asks for the width that keeps each unfolded row on
   * one line.
   */
  public int getWidthToFit() {
    int page = 0;
    for (AbstractItemDialogPage p : pages) {
      page = Math.max(page, Math.max(p.getPreferredSize().width, WrapFlowLayout.widestLine(p)));
    }
    Insets host = pageHost.getInsets();
    page += host.left + host.right + pageHost.getVerticalScrollBar().getPreferredSize().width;
    int width = page;
    for (Component c : getComponents()) {
      if (c != pageHost) {
        width = Math.max(width, c.getPreferredSize().width);
      }
    }
    Insets insets = getInsets();
    return width + insets.left + insets.right;
  }

  /** Releases every page; call when the embedded host is discarded. */
  public void closeAllPages() {
    for (AbstractItemDialogPage page : pages) {
      page.closeAdditionalWindow();
    }
  }

  /**
   * Holds a page in the scroll pane: the page follows the width of the slot down to its minimum
   * width, then scrolls, and fills the height of the slot when it is shorter. A page with its own
   * scrolling list ({@link AbstractItemDialogPage#fillsHostHeight()}) follows the height down to
   * its minimum height, so its list scrolls and its header stays in place.
   */
  private static final class PageView extends JPanel implements Scrollable {
    private static final int SCROLL_UNIT = 16;

    private final boolean fillsHeight;

    PageView(AbstractItemDialogPage page) {
      super(new BorderLayout());
      this.fillsHeight = page.fillsHostHeight();
      add(page, BorderLayout.CENTER);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
      return GuiUtils.getScaleLength(SCROLL_UNIT);
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
      int extent = orientation == SwingConstants.VERTICAL ? visibleRect.height : visibleRect.width;
      return Math.max(GuiUtils.getScaleLength(SCROLL_UNIT), extent - SCROLL_UNIT);
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
      return getParent() instanceof JViewport viewport
          && viewport.getWidth() >= getMinimumSize().width;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
      if (getParent() instanceof JViewport viewport) {
        Dimension needed = fillsHeight ? getMinimumSize() : getPreferredSize();
        return viewport.getHeight() >= needed.height;
      }
      return false;
    }
  }
}
