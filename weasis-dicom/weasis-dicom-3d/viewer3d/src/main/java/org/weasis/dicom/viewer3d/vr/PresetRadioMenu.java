/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.dicom.codec.display.Modality;

public class PresetRadioMenu extends GroupRadioMenu<Preset> {

  protected final EnumMap<Modality, List<RadioMenuItem>> menuGroup = new EnumMap<>(Modality.class);

  @Override
  protected void init() {
    for (RadioMenuItem item : itemList) {
      group.remove(item);
    }
    itemList.clear();
    Object selectedItem = dataModel.getSelectedItem();

    Map<Modality, List<Preset>> modalityMap = new EnumMap<>(Modality.class);
    for (int i = 0; i < dataModel.getSize(); i++) {
      Preset preset = dataModel.getElementAt(i);
      Modality modality = preset.getModality();
      modalityMap.putIfAbsent(modality, new ArrayList<>());
      modalityMap.get(modality).add(preset);
    }

    modalityMap.entrySet().stream()
        .sorted(Comparator.comparing(o -> o.getKey().name()))
        .forEach(
            e -> {
              Modality modality = e.getKey();
              List<RadioMenuItem> items = new ArrayList<>();
              menuGroup.put(modality, items);
              List<Preset> presets = e.getValue();
              for (Preset preset : presets) {
                RadioMenuItem radioMenuItem = new RadioMenuItem(preset.toString(), preset);
                Icon icon = preset.getLUTIcon(GuiUtils.getBigIconButtonSize(radioMenuItem).height);

                radioMenuItem.setIcon(icon);
                GuiUtils.applySelectedIconEffect(radioMenuItem);
                radioMenuItem.setSelected(preset == selectedItem);
                group.add(radioMenuItem);
                itemList.add(radioMenuItem);
                items.add(radioMenuItem);
                radioMenuItem.addActionListener(this);
              }
            });
  }

  public Map<Modality, List<RadioMenuItem>> getMenuGroup() {
    return new EnumMap<>(menuGroup);
  }

  public JPopupMenu createJPopupMenu(Modality curModality) {
    JPopupMenu popupMouseButtons = new JPopupMenu();
    addSubMenu(
        popupMouseButtons, getMenuGroup(), curModality == null ? Modality.DEFAULT : curModality);
    return popupMouseButtons;
  }

  public JMenu createMenu(String title, Icon icon, Modality curModality) {
    JMenu menu = new JMenu(title);
    if (icon != null) {
      menu.setIcon(icon);
      GuiUtils.applySelectedIconEffect(menu);
    }

    addSubMenu(menu, getMenuGroup(), curModality == null ? Modality.DEFAULT : curModality);
    return menu;
  }

  private static void addSubMenu(
      JComponent component, Map<Modality, List<RadioMenuItem>> map, Modality curModality) {
    addRootMenu(component, map, curModality);
    if (curModality != Modality.DEFAULT) {
      component.add(new JSeparator());
      addRootMenu(component, map, Modality.DEFAULT);
    }

    component.add(new JSeparator());
    for (Entry<Modality, List<RadioMenuItem>> entry : map.entrySet()) {
      Modality modality = entry.getKey();
      JMenu modMenu = new JMenu(modality.toString());
      component.add(modMenu);
      for (RadioMenuItem menuItem : entry.getValue()) {
        modMenu.add(menuItem);
      }
    }
  }

  private static void addRootMenu(
      JComponent component, Map<Modality, List<RadioMenuItem>> map, Modality curModality) {
    List<RadioMenuItem> items = map.remove(curModality);
    if (items != null) {
      for (RadioMenuItem menuItem : items) {
        component.add(menuItem);
      }
    }
  }
}
