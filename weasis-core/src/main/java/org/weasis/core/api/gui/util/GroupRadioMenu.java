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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.event.ListDataEvent;
import org.weasis.core.api.image.op.ByteLut;

public class GroupRadioMenu<T> implements ActionListener, ComboBoxModelAdapter<T>, GroupPopup {

  protected final List<RadioMenuItem> itemList;
  protected final ButtonGroup group;
  protected ComboBoxModel<T> dataModel;

  public GroupRadioMenu() {
    this.itemList = new ArrayList<>();
    this.group = new ButtonGroup();
  }

  private void init() {
    for (RadioMenuItem item : itemList) {
      group.remove(item);
    }
    itemList.clear();
    Object selectedItem = dataModel.getSelectedItem();

    for (int i = 0; i < dataModel.getSize(); i++) {
      Object object = dataModel.getElementAt(i);
      RadioMenuItem radioMenuItem = new RadioMenuItem(object.toString(), object);
      Icon icon = null;
      if (object instanceof GUIEntry entry) {
        icon = entry.getIcon();
      } else if (object instanceof ByteLut lut) {
        icon = lut.getIcon(GuiUtils.getBigIconButtonSize(radioMenuItem).height);
      }
      radioMenuItem.setIcon(icon);
      GuiUtils.applySelectedIconEffect(radioMenuItem);
      radioMenuItem.setSelected(object == selectedItem);
      group.add(radioMenuItem);
      itemList.add(radioMenuItem);
      radioMenuItem.addActionListener(this);
    }
  }

  public List<RadioMenuItem> getRadioMenuItemListCopy() {
    return new ArrayList<>(itemList);
  }

  @Override
  public JPopupMenu createJPopupMenu() {
    JPopupMenu popupMouseButtons = new JPopupMenu();
    for (RadioMenuItem radioMenuItem : itemList) {
      popupMouseButtons.add(radioMenuItem);
    }
    return popupMouseButtons;
  }

  @Override
  public JMenu createMenu(String title) {
    JMenu menu = new JMenu(title);
    for (RadioMenuItem radioMenuItem : itemList) {
      menu.add(radioMenuItem);
    }
    return menu;
  }

  public JMenu createMenu(String title, Icon icon) {
    JMenu menu = new JMenu(title);
    if (icon != null) {
      menu.setIcon(icon);
      GuiUtils.applySelectedIconEffect(menu);
    }
    for (RadioMenuItem radioMenuItem : itemList) {
      menu.add(radioMenuItem);
    }
    return menu;
  }

  @Override
  public void contentsChanged(ListDataEvent e) {
    setSelected(dataModel.getSelectedItem());
  }

  @Override
  public void intervalAdded(ListDataEvent e) {
    // Do nothing

  }

  @Override
  public void intervalRemoved(ListDataEvent e) {
    // Do nothing

  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() instanceof RadioMenuItem item && item.isSelected()) {
      dataModel.setSelectedItem(item.getUserObject());
    }
  }

  public void setSelected(Object selected) {
    if (selected == null) {
      group.clearSelection();
      dataModel.setSelectedItem(null);
    } else {
      for (RadioMenuItem item : itemList) {
        if (item.getUserObject() == selected) {
          item.setSelected(true); // Do not trigger actionPerformed
          dataModel.setSelectedItem(item.getUserObject());
          return;
        }
      }
    }
  }

  public int getSelectedIndex() {
    Object sObject = dataModel.getSelectedItem();

    for (int i = 0; i < dataModel.getSize(); i++) {
      if (Objects.equals(dataModel.getElementAt(i), sObject)) {
        return i;
      }
    }
    return -1;
  }

  @SuppressWarnings("unchecked")
  public T getSelectedItem() {
    return (T) dataModel.getSelectedItem();
  }

  public ComboBoxModel<T> getModel() {
    return dataModel;
  }

  @Override
  public void setModel(ComboBoxModel<T> dataModel) {
    if (this.dataModel != null) {
      this.dataModel.removeListDataListener(this);
    }
    if (dataModel != null) {
      dataModel.removeListDataListener(this);
    }
    this.dataModel = Optional.ofNullable(dataModel).orElseGet(DefaultComboBoxModel::new);
    init();
    this.dataModel.addListDataListener(this);
  }

  @Override
  public void setEnabled(boolean enabled) {
    for (RadioMenuItem radioMenuItem : itemList) {
      radioMenuItem.setEnabled(enabled);
    }
  }
}
