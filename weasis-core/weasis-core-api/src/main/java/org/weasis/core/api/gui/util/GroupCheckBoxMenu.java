/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.gui.util;

import java.awt.Component;
import java.util.ArrayList;
import java.util.List;

import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import org.weasis.core.api.util.FontTools;

public class GroupCheckBoxMenu implements GroupPopup {

    protected final List<CheckBoxModel> itemList;
    private boolean startBySelectAll = false;

    public GroupCheckBoxMenu() {
        this.itemList = new ArrayList<>();
    }

    public List<CheckBoxModel> getModelList() {
        return new ArrayList<>(itemList);
    }

    @Override
    public JPopupMenu createJPopupMenu() {
        final JPopupMenu popupMouseButtons = new JScrollPopupMenu();

        if (startBySelectAll) {
            boolean all = false;
            for (int i = 1; i < itemList.size(); i++) {
                if (itemList.get(i).isSelected()) {
                    all = true;
                    break;
                }
            }
            CheckBoxModel item = itemList.get(0);
            item.setSelected(all);
            JCheckBox box = new JCheckBox(item.getObject().toString(), null, item.isSelected());
            box.setFont(FontTools.getFont12Bold());
            box.addActionListener(e -> {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox btn = (JCheckBox) e.getSource();
                    selectAll(popupMouseButtons, btn.isSelected());
                }
            });
            popupMouseButtons.add(box);
        }

        for (int i = 1; i < itemList.size(); i++) {
            Icon icon = null;
            CheckBoxModel item = itemList.get(i);
            if (item.getObject() instanceof GUIEntry) {
                icon = ((GUIEntry) item.getObject()).getIcon();
            }
            JCheckBox box = new JCheckBox(item.getObject().toString(), icon, item.isSelected());
            box.addActionListener(e -> {
                if (e.getSource() instanceof JCheckBox) {
                    JCheckBox btn = (JCheckBox) e.getSource();
                    item.setSelected(btn.isSelected());
                }
            });
            popupMouseButtons.add(box);
        }
        return popupMouseButtons;
    }

    private void selectAll(final JComponent parent, boolean selected) {
        for (int i = 0; i < parent.getComponentCount(); i++) {
            Component c = parent.getComponent(i);
            if (c instanceof AbstractButton) {
                ((AbstractButton) c).setSelected(selected);
            }
        }

        itemList.forEach(m -> m.setSelected(selected));
    }

    public void selectAll() {
        itemList.forEach(m -> m.setSelected(true));
    }

    @Override
    public JMenu createMenu(String title) {
        JMenu menu = new JMenu(title);
        for (CheckBoxModel item : itemList) {
            Icon icon = null;
            if (item.getObject() instanceof GUIEntry) {
                icon = ((GUIEntry) item.getObject()).getIcon();
            }

            JCheckBoxMenuItem box = new JCheckBoxMenuItem(item.getObject().toString(), icon, item.isSelected());
            box.addActionListener(e -> {
                if (e.getSource() instanceof JCheckBoxMenuItem) {
                    JCheckBoxMenuItem btn = (JCheckBoxMenuItem) e.getSource();
                    if (startBySelectAll && itemList.get(0).getObject().toString().equals(btn.getText())) {
                        selectAll(menu, btn.isSelected());
                    } else {
                        item.setSelected(btn.isSelected());
                    }
                }
            });
            menu.add(box);
        }
        return menu;
    }

    public void setModel(List<?> model, boolean initValue, boolean startBySelectAll) {
        this.startBySelectAll = startBySelectAll;
        itemList.clear();

        for (Object object : model) {
            CheckBoxModel m = new CheckBoxModel(object, initValue);
            itemList.add(m);
        }
    }
}
