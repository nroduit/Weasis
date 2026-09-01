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

import java.awt.Component;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.TreeSet;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GroupRadioMenu;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.RadioMenuItem;
import org.weasis.core.api.image.lut.ColorMapRegistry;
import org.weasis.core.api.image.lut.ColorMapRegistry.Origin;
import org.weasis.opencv.op.lut.ByteLut;
import org.weasis.opencv.op.lut.colormap.ColorMap;

/**
 * LUT menu with the same sections in every viewer: at the root, maps without a map behind them
 * (identity, gray), the user's favorites and the maps scoped to the current modality, each group
 * under a dim title; then general maps, one submenu per category, per other modality, and for
 * imported and user maps. A favorite carries, in dim text, the title of the submenu it would
 * otherwise sit in.
 */
public class ColorMapRadioMenu extends GroupRadioMenu<ByteLut> {

  private static final Logger LOGGER = LoggerFactory.getLogger(ColorMapRadioMenu.class);

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
      GuiUtils.applySelectedIconEffect(menu);
    }
    fill(menu);
    return menu;
  }

  /**
   * Menu entry flagging the map the model shows as a favorite, or unflagging it; null when the
   * selection has no registered map behind it.
   */
  public JMenuItem createFavoriteItem() {
    ColorMap map =
        dataModel != null && dataModel.getSelectedItem() instanceof ByteLut lut
            ? lut.source()
            : null;
    return createFavoriteItem(map);
  }

  /** Menu entry toggling whether {@code map} is a favorite; null when its id is not registered. */
  public static JMenuItem createFavoriteItem(ColorMap map) {
    ColorMapRegistry registry = ColorMapRegistry.getInstance();
    if (map == null || registry.findById(map.id()).isEmpty()) {
      return null;
    }
    boolean favorite = registry.isFavorite(map);
    String key = favorite ? "ColorMapEditor.removeFavorite" : "ColorMapEditor.addFavorite";
    JMenuItem item = new JMenuItem(MessageFormat.format(Messages.getString(key), map.name()));
    item.addActionListener(
        e -> {
          try {
            registry.setFavorite(map.id(), !favorite);
          } catch (IOException ex) {
            LOGGER.error("Cannot save favorite color maps", ex);
          }
        });
    return item;
  }

  private void fill(JComponent parent) {
    var root = new ArrayList<RadioMenuItem>();
    var favorites = new ArrayList<RadioMenuItem>();
    var scoped = new ArrayList<RadioMenuItem>();
    var general = new ArrayList<RadioMenuItem>();
    var categories = new LinkedHashMap<String, List<RadioMenuItem>>();
    var modalities = new LinkedHashMap<String, List<RadioMenuItem>>();
    var imported = new ArrayList<RadioMenuItem>();
    var user = new ArrayList<RadioMenuItem>();

    for (RadioMenuItem item : itemList) {
      ColorMap map = item.getUserObject() instanceof ByteLut lut ? lut.source() : null;
      item.setText(item.getUserObject().toString()); // items are reused: drop a former hint
      if (map == null) {
        root.add(item);
        continue;
      }
      Origin origin = registry.origin(map);
      if (registry.isFavorite(map)) {
        item.setText(withHint(map.name(), sectionOf(map, origin)));
        favorites.add(item);
      } else if (origin == Origin.USER) {
        user.add(item);
      } else if (origin == Origin.IMPORTED) {
        imported.add(item);
      } else if (modality != null && map.modalities().contains(modality)) {
        scoped.add(item);
      } else if (!map.modalities().isEmpty()) {
        new TreeSet<>(map.modalities())
            .forEach(code -> modalities.computeIfAbsent(code, k -> new ArrayList<>()).add(item));
      } else if (map.category() != null) {
        categories.computeIfAbsent(map.category(), k -> new ArrayList<>()).add(item);
      } else {
        general.add(item);
      }
    }

    addRootSection(parent, null, root);
    addRootSection(parent, Messages.getString("ColorMapEditor.favorites"), favorites);
    addRootSection(parent, modality, scoped);
    if (entries(parent).length == 0) {
      addRootSection(parent, null, general);
    } else {
      addSubMenu(parent, Messages.getString("ColorMapEditor.general"), general);
    }
    categories.forEach((name, items) -> addSubMenu(parent, name, items));
    if (!modalities.isEmpty()) {
      separate(parent);
      JMenu others = new JMenu(Messages.getString("ColorMapEditor.otherModalities"));
      modalities.forEach((code, items) -> addSubMenu(others, code, items));
      parent.add(others);
    }
    addSubMenu(parent, Messages.getString("ColorMapEditor.imported"), imported);
    addSubMenu(parent, Messages.getString("ColorMapEditor.user"), user);
  }

  // The title of the submenu the map would sit in without the favorite flag.
  private static String sectionOf(ColorMap map, Origin origin) {
    if (origin == Origin.USER) {
      return Messages.getString("ColorMapEditor.user");
    }
    if (origin == Origin.IMPORTED) {
      return Messages.getString("ColorMapEditor.imported");
    }
    if (!map.modalities().isEmpty()) {
      return String.join(", ", new TreeSet<>(map.modalities()));
    }
    return map.category() != null ? map.category() : Messages.getString("ColorMapEditor.general");
  }

  /** {@code text} followed by {@code hint} in the dim menu color, as one HTML label. */
  public static String withHint(String text, String hint) {
    return "<html>%s&nbsp;&nbsp;<span style=\"color:%s\">%s</span></html>" // NON-NLS
        .formatted(escape(text), GuiUtils.toHtmlColor(GuiUtils.getMenuHintColor()), escape(hint));
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); // NON-NLS
  }

  private static void addRootSection(JComponent parent, String title, List<RadioMenuItem> items) {
    if (items.isEmpty()) {
      return;
    }
    separate(parent);
    if (title != null) {
      parent.add(GuiUtils.createMenuSectionLabel(title));
    }
    items.forEach(parent::add);
  }

  private static void addSubMenu(JComponent parent, String title, List<RadioMenuItem> items) {
    if (items.isEmpty()) {
      return;
    }
    separate(parent);
    JMenu menu = new JMenu(title);
    items.forEach(menu::add);
    parent.add(menu);
  }

  // A separator between sections, never at the top nor twice in a row.
  private static void separate(JComponent parent) {
    Component[] entries = entries(parent);
    if (entries.length > 0 && !(entries[entries.length - 1] instanceof JSeparator)) {
      parent.add(new JSeparator());
    }
  }

  // A JMenu keeps its entries in its popup, not among its own children.
  private static Component[] entries(JComponent parent) {
    return parent instanceof JMenu menu ? menu.getMenuComponents() : parent.getComponents();
  }
}
