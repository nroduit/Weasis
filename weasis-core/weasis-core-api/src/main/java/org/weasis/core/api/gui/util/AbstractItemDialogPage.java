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

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.weasis.core.api.gui.Insertable;

public abstract class AbstractItemDialogPage extends JPanel implements PageItem, Insertable {

  private final String title;
  private final List<PageItem> subPageList = new ArrayList<>();
  private int pagePosition;

  private final Properties properties = new Properties();

  protected AbstractItemDialogPage(String title) {
    this(title, 1000);
  }

  protected AbstractItemDialogPage(String title, int pagePosition) {
    this.title = title == null ? "item" : title; // NON-NLS
    this.pagePosition = pagePosition;
    setBorder(
        GuiUtils.getEmptyBorder(BLOCK_SEPARATOR, ITEM_SEPARATOR_LARGE, 0, ITEM_SEPARATOR_LARGE));
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
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
  public Type getType() {
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
}
