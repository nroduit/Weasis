/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.image.lut.ColorMapRegistry.Origin;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;

/**
 * LUT menu with the same sections in every viewer: maps without a map behind them (identity, gray)
 * and maps scoped to the current modality at the root, then general maps, then one submenu per
 * category, per other modality, and for imported and user maps.
 */
public class ColorMapRadioMenu extends GroupRadioMenu<ByteLut> {

  private final ColorMapRegistry registry = ColorMapRegistry.getInstance();
  private String modality;

  public ColorMapRadioMenu() {
    super();
  }

  /** Modality of the displayed image; its maps go to the root of the menu. */
  public void setModality(String modality) {
    this.modality = modality;
  }

  @Override
  public JPopupMenu createJPopupMenu() {
    JPopupMenu popup = new JPopupMenu();
    fill(popup);
    return popup;
  }

  @Override
  public JMenu createMenu(String title, Icon icon) {
    JMenu menu = new JMenu(title);
    if (icon != null) {
      menu.setIcon(icon);
    }
    fill(menu);
    return menu;
  }

  private void fill(JComponent parent) {
    var root = new ArrayList<RadioMenuItem>();
    var general = new ArrayList<RadioMenuItem>();
    var categories = new LinkedHashMap<String, List<RadioMenuItem>>();
    var modalities = new LinkedHashMap<String, List<RadioMenuItem>>();
    var imported = new ArrayList<RadioMenuItem>();
    var user = new ArrayList<RadioMenuItem>();

    for (RadioMenuItem item : itemList) {
      ColorMap map = item.getUserObject() instanceof ByteLut lut ? lut.source() : null;
      if (map == null) {
        root.add(item);
        continue;
      }
      Origin origin = registry.origin(map);
      if (origin == Origin.USER) {
        user.add(item);
      } else if (origin == Origin.IMPORTED) {
        imported.add(item);
      } else if (modality != null && map.modalities().contains(modality)) {
        root.add(item);
      } else if (!map.modalities().isEmpty()) {
        new TreeSet<>(map.modalities())
            .forEach(code -> modalities.computeIfAbsent(code, k -> new ArrayList<>()).add(item));
      } else if (map.category() != null) {
        categories.computeIfAbsent(map.category(), k -> new ArrayList<>()).add(item);
      } else {
        general.add(item);
      }
    }

    root.forEach(parent::add);
    addSection(parent, Messages.getString("ColorMapEditor.general"), general, root.isEmpty());
    categories.forEach((name, items) -> addSubMenu(parent, name, items));
    if (!modalities.isEmpty()) {
      parent.add(new JSeparator());
      JMenu others = new JMenu(Messages.getString("ColorMapEditor.otherModalities"));
      modalities.forEach((code, items) -> addSubMenu(others, code, items));
      parent.add(others);
    }
    addSubMenu(parent, Messages.getString("ColorMapEditor.imported"), imported);
    addSubMenu(parent, Messages.getString("ColorMapEditor.user"), user);
  }

  private static void addSection(
      JComponent parent, String title, List<RadioMenuItem> items, boolean atRoot) {
    if (items.isEmpty()) {
      return;
    }
    if (atRoot) {
      items.forEach(parent::add);
    } else {
      addSubMenu(parent, title, items);
    }
  }

  private static void addSubMenu(JComponent parent, String title, List<RadioMenuItem> items) {
    if (items.isEmpty()) {
      return;
    }
    if (parent.getComponentCount() > 0
        && !(parent.getComponent(parent.getComponentCount() - 1) instanceof JSeparator)
        && !(parent instanceof JMenu m && m.getMenuComponentCount() == 0)) {
      parent.add(new JSeparator());
    }
    JMenu menu = new JMenu(title);
    items.forEach(menu::add);
    parent.add(menu);
  }
}
