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

import java.awt.Component;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.ui.editor.image.lut.ColorMapEditorDialog;
import org.weasis.core.ui.editor.image.lut.ColorMapRadioMenu;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.opencv.op.lut.colormap.ColorMap;

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

  /** Marks the presets tagged for the cinematic modes next to the rendering-cost dots. */
  private static final String CINEMATIC_BADGE = "✦";

  // The cost badge depends on the loaded volume, so the labels are refreshed each time a menu is
  // built.
  private void refreshCostBadges() {
    View3d view3d = EventManager.getInstance().getSelectedViewPane() instanceof View3d v ? v : null;
    for (RadioMenuItem item : itemList) {
      if (!(item.getUserObject() instanceof Preset preset)) {
        continue;
      }
      ColorMap map = preset.toColorMap();
      PresetCost cost = view3d == null ? null : VolumePresetHost.cost(view3d, map);
      boolean cinematic = Preset.isCinematic(map);
      StringBuilder text = new StringBuilder(preset.toString());
      List<String> tips = new ArrayList<>();
      if (cost != null) {
        text.append("  ").append(cost.badge());
        tips.add(
            Messages.getString("preset.cost")
                + StringUtil.COLON_AND_SPACE
                + MessageFormat.format(
                    Messages.getString("preset.cost.visible"),
                    Math.round(100.0 * cost.visibleFraction())));
      }
      if (cinematic) {
        text.append(cost != null ? " " : "  ").append(CINEMATIC_BADGE);
        tips.add(Messages.getString("preset.cinematic"));
      }
      item.setText(text.toString());
      item.setToolTipText(tips.isEmpty() ? null : String.join("<br>", tips)); // NON-NLS
    }
  }

  public JPopupMenu createJPopupMenu(Modality curModality) {
    refreshCostBadges();
    JPopupMenu popupMouseButtons = new JPopupMenu();
    addSubMenu(
        popupMouseButtons, getMenuGroup(), curModality == null ? Modality.DEFAULT : curModality);
    return popupMouseButtons;
  }

  public JMenu createMenu(String title, Icon icon, Modality curModality) {
    refreshCostBadges();
    JMenu menu = new JMenu(title);
    if (icon != null) {
      menu.setIcon(icon);
      GuiUtils.applySelectedIconEffect(menu);
    }

    addSubMenu(menu, getMenuGroup(), curModality == null ? Modality.DEFAULT : curModality);
    return menu;
  }

  // Root: the favorites, the presets of the current modality, then the modality-free ones.
  private void addSubMenu(
      JComponent component, Map<Modality, List<RadioMenuItem>> map, Modality curModality) {
    addRootItems(
        component,
        org.weasis.core.Messages.getString("ColorMapEditor.favorites"),
        takeFavorites(map));
    if (curModality != Modality.DEFAULT) {
      addRootItems(component, curModality.toString(), map.remove(curModality));
    }
    addRootItems(
        component,
        org.weasis.core.Messages.getString("ColorMapEditor.general"),
        map.remove(Modality.DEFAULT));

    for (Entry<Modality, List<RadioMenuItem>> entry : map.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      separate(component);
      JMenu modMenu = new JMenu(entry.getKey().toString());
      component.add(modMenu);
      addGroup(modMenu, entry.getValue());
    }

    separate(component);
    if (dataModel.getSelectedItem() instanceof Preset preset) {
      JMenuItem favorite = ColorMapRadioMenu.createFavoriteItem(preset.toColorMap());
      if (favorite != null) {
        component.add(favorite);
      }
    }
    JMenuItem editLutItem =
        new JMenuItem(org.weasis.core.Messages.getString("ColorMapEditor.edit") + Suffix.THREE_PTS);
    editLutItem.addActionListener(
        e -> {
          if (EventManager.getInstance().getSelectedViewPane() instanceof View3d view3d) {
            ColorMapEditorDialog.open(view3d, new VolumePresetHost(view3d));
          }
        });
    component.add(editLutItem);
  }

  // Favorites leave their modality group for the root; the groups are replaced, never mutated.
  private static List<RadioMenuItem> takeFavorites(Map<Modality, List<RadioMenuItem>> map) {
    ColorMapRegistry registry = ColorMapRegistry.getInstance();
    List<RadioMenuItem> favorites = new ArrayList<>();
    for (Entry<Modality, List<RadioMenuItem>> entry : map.entrySet()) {
      List<RadioMenuItem> rest = new ArrayList<>();
      for (RadioMenuItem item : entry.getValue()) {
        boolean favorite =
            item.getUserObject() instanceof Preset preset
                && registry.isFavorite(preset.toColorMap());
        (favorite ? favorites : rest).add(item);
      }
      entry.setValue(rest);
    }
    return favorites;
  }

  private static void addRootItems(JComponent component, String title, List<RadioMenuItem> items) {
    if (items == null || items.isEmpty()) {
      return;
    }
    separate(component);
    component.add(GuiUtils.createMenuSectionLabel(title));
    addGroup(component, items);
  }

  // Presets without a category come first, the others in one submenu per category.
  private static void addGroup(JComponent component, List<RadioMenuItem> items) {
    Map<String, List<RadioMenuItem>> categories = new TreeMap<>();
    for (RadioMenuItem item : items) {
      String category =
          item.getUserObject() instanceof Preset preset ? preset.toColorMap().category() : null;
      if (category == null) {
        component.add(item);
      } else {
        categories.computeIfAbsent(category, k -> new ArrayList<>()).add(item);
      }
    }
    categories.forEach(
        (name, presets) -> {
          JMenu menu = new JMenu(name);
          presets.forEach(menu::add);
          component.add(menu);
        });
  }

  // A separator between sections, never at the top nor twice in a row.
  private static void separate(JComponent component) {
    Component[] entries =
        component instanceof JMenu menu ? menu.getMenuComponents() : component.getComponents();
    if (entries.length > 0 && !(entries[entries.length - 1] instanceof JSeparator)) {
      component.add(new JSeparator());
    }
  }
}
