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
import java.util.HashMap;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.Icon;
import javax.swing.JToggleButton;
import javax.swing.event.ListDataEvent;

public class JToggleButtonGroup<T> implements ActionListener, ComboBoxModelAdapter<T> {

  protected final List<JToggleButton> itemList;
  protected final HashMap<JToggleButton, Object> map = new HashMap<>();
  protected ComboBoxModel<T> dataModel;

  public JToggleButtonGroup() {
    this.itemList = new ArrayList<>();
  }

  private void init() {
    itemList.clear();
    Object selectedItem = dataModel.getSelectedItem();
    ButtonGroup group = new ButtonGroup();
    for (int i = 0; i < dataModel.getSize(); i++) {
      Object object = dataModel.getElementAt(i);
      Icon icon = null;
      if (object instanceof GUIEntry entry) {
        icon = entry.getIcon();
      }
      JToggleButton b = new JToggleButton(icon);
      GuiUtils.applySelectedIconEffect(b);
      b.setToolTipText(object.toString());
      map.put(b, object);
      b.setSelected(object == selectedItem);
      group.add(b);
      b.addActionListener(this);
      itemList.add(b);
    }
  }

  @Override
  public void setModel(ComboBoxModel<T> dataModel) {
    boolean changeListener = dataModel != null && dataModel != this.dataModel;
    if (this.dataModel != null) {
      this.dataModel.removeListDataListener(this);
    }
    this.dataModel = dataModel == null ? new DefaultComboBoxModel<>() : dataModel;
    init();
    if (changeListener) {
      this.dataModel.addListDataListener(this);
    }
  }

  public JToggleButton[] getJToggleButtonList() {
    return itemList.toArray(new JToggleButton[0]);
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
    if (e.getSource() instanceof JToggleButton item && item.isSelected()) {
      dataModel.setSelectedItem(map.get(item));
    }
  }

  public void setSelected(Object selected) {
    if (selected != null) {
      for (JToggleButton item : itemList) {
        Object itemObj = map.get(item);
        if (itemObj == selected) {
          item.setSelected(true); // Do not trigger actionPerformed
          dataModel.setSelectedItem(itemObj);
          return;
        }
      }
    }
  }

  public int getSelectedIndex() {
    Object sObject = dataModel.getSelectedItem();
    int i;
    int c;
    Object obj;

    for (i = 0, c = dataModel.getSize(); i < c; i++) {
      obj = dataModel.getElementAt(i);
      if (obj != null && obj.equals(sObject)) {
        return i;
      }
    }
    return -1;
  }

  public Object getSelectedItem() {
    return dataModel.getSelectedItem();
  }

  @Override
  public void setEnabled(boolean enabled) {
    for (JToggleButton jToggleButton : itemList) {
      jToggleButton.setEnabled(enabled);
    }
  }
}
