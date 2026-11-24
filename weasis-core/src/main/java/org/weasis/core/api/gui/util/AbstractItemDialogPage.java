/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.weasis.core.api.gui.Insertable;

public abstract class AbstractItemDialogPage extends JPanel implements PageItem, Insertable {

  /**
   * Page property saying what its settings belong to: {@link #SCOPE_USER} (they follow the user
   * profile), {@link #SCOPE_MACHINE} (this workstation only) or {@link #SCOPE_SITE} (set by the
   * site). The preference dialog shows it under the page.
   */
  public static final String KEY_SCOPE = "scope"; // NON-NLS

  public static final String SCOPE_USER = "user"; // NON-NLS
  public static final String SCOPE_MACHINE = "machine"; // NON-NLS
  public static final String SCOPE_SITE = "site"; // NON-NLS

  private final String title;
  private final List<PageItem> subPageList = new ArrayList<>();
  private int pagePosition;

  private final Properties properties = new Properties();

  private static final int LIST_PREFERRED_HEIGHT = 300;
  private static final int LIST_MIN_WIDTH = 120;
  private static final int LIST_SCROLL_UNIT = 16;

  private boolean fillsHostHeight;

  protected AbstractItemDialogPage(String title) {
    this(title, 1000);
  }

  protected AbstractItemDialogPage(String title, int pagePosition) {
    this.title = title == null ? "item" : title; // NON-NLS
    this.pagePosition = pagePosition;
    setBorder(
        GuiUtils.getEmptyBorder(BLOCK_SEPARATOR, ITEM_SEPARATOR_LARGE, 0, ITEM_SEPARATOR_LARGE));
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
  }

  public void deselectPageAction() {}

  public void selectPageAction() {}

  @Override
  public String getTitle() {
    return title;
  }

  public void addSubPage(PageItem subPage) {
    subPageList.add(subPage);
  }

  public void addSubPage(PageItem subPage, ActionListener actionListener, JComponent menuPanel) {
    subPageList.add(subPage);
    if (actionListener != null && menuPanel != null) {
      JButton button = new JButton();
      button.putClientProperty("JButton.buttonType", "roundRect");
      button.setText(subPage.getTitle());
      button.addActionListener(actionListener);
      menuPanel.add(button);
    }
  }

  public void removeSubPage(PageItem subPage) {
    subPageList.remove(subPage);
  }

  @Override
  public List<PageItem> getSubPages() {
    return new ArrayList<>(subPageList);
  }

  public void sortSubPages() {
    subPageList.sort(
        (o1, o2) -> {
          int val1 =
              o1 instanceof AbstractItemDialogPage page
                  ? page.getComponentPosition()
                  : Integer.MAX_VALUE;
          int val2 =
              o2 instanceof AbstractItemDialogPage page
                  ? page.getComponentPosition()
                  : Integer.MAX_VALUE;
          return Integer.compare(val1, val2);
        });
  }

  public void resetAllSubPagesToDefaultValues() {
    for (PageItem subPage : subPageList) {
      subPage.resetToDefaultValues();
    }
  }

  public Properties getProperties() {
    return properties;
  }

  public String getProperty(String key) {
    return properties.getProperty(key);
  }

  public String getProperty(String key, String defaultValue) {
    return properties.getProperty(key, defaultValue);
  }

  @Override
  public String toString() {
    return title;
  }

  @Override
  public Type getPanelType() {
    return Insertable.Type.PREFERENCES;
  }

  @Override
  public String getComponentName() {
    return title;
  }

  @Override
  public boolean isComponentEnabled() {
    return isEnabled();
  }

  @Override
  public void setComponentEnabled(boolean enabled) {
    if (enabled != isComponentEnabled()) {
      setEnabled(enabled);
    }
  }

  @Override
  public int getComponentPosition() {
    return pagePosition;
  }

  @Override
  public void setComponentPosition(int position) {
    this.pagePosition = position;
  }

  public JComponent getMenuPanel() {
    return null;
  }

  /**
   * Lays the page out as a header that keeps its natural height above a list that takes the rest
   * of the height and scrolls on its own. In a short host the list scrolls while the header stays
   * in place; the host scrolls the whole page only below {@code minListHeight} (unscaled) for the
   * list. The page must hold nothing else.
   *
   * @return the scroll pane of the list
   */
  protected JScrollPane setHeaderAndList(JComponent header, JComponent list, int minListHeight) {
    int minHeight = GuiUtils.getScaleLength(minListHeight);
    // the list asks for a bounded height: its own scroll bar, not the page, absorbs a long list
    int maxPreferred = Math.max(minHeight, GuiUtils.getScaleLength(LIST_PREFERRED_HEIGHT));
    JScrollPane scroll =
        new JScrollPane(list) {
          @Override
          public Dimension getPreferredSize() {
            Dimension dim = super.getPreferredSize();
            dim.height = Math.min(dim.height, maxPreferred);
            return dim;
          }
        };
    scroll.setMinimumSize(new Dimension(GuiUtils.getScaleLength(LIST_MIN_WIDTH), minHeight));
    scroll.getVerticalScrollBar().setUnitIncrement(GuiUtils.getScaleLength(LIST_SCROLL_UNIT));
    scroll.getHorizontalScrollBar().setUnitIncrement(GuiUtils.getScaleLength(LIST_SCROLL_UNIT));
    setLayout(new BorderLayout(0, GuiUtils.getScaleLength(ITEM_SEPARATOR)));
    add(header, BorderLayout.NORTH);
    add(scroll, BorderLayout.CENTER);
    fillsHostHeight = true;
    return scroll;
  }

  /**
   * Whether the page fills the height of its host down to its minimum height, its list scrolling
   * on its own ({@link #setHeaderAndList}); otherwise the host gives it its preferred height.
   */
  public boolean fillsHostHeight() {
    return fillsHostHeight;
  }
}
