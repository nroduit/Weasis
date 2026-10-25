/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.FlatIconColors;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.extras.FlatSVGIcon.ColorFilter;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.util.ColorFunctions;
import java.awt.Color;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LookAndFeels {
  private static final Logger LOGGER = LoggerFactory.getLogger(LookAndFeels.class);

  private final List<ReadableLookAndFeelInfo> lookAndFeels;
  private final ReadableLookAndFeelInfo defaultLightTheme;
  private final ReadableLookAndFeelInfo defaultDarkTheme;

  private static final String[] FLAT_NAMES = {
    "Core Dark - FlatDarcula", // NON-NLS
    "Core Dark - FlatDark", // NON-NLS
    "Core Light - FlatLight", // NON-NLS
    "Core Dark - macOS", // NON-NLS
    "Core Light - macOS" // NON-NLS
  };

  private static final String[] FLAT_CLASSES = {
    "com.formdev.flatlaf.FlatDarculaLaf",
    "com.formdev.flatlaf.FlatDarkLaf",
    "com.formdev.flatlaf.FlatLightLaf",
    "com.formdev.flatlaf.themes.FlatMacDarkLaf",
    "com.formdev.flatlaf.themes.FlatMacLightLaf"
  };

  LookAndFeels() {
    FlatLaf.registerCustomDefaultsSource("org.weasis.theme", LookAndFeels.class.getClassLoader());
    defaultLightTheme =
        new ReadableLookAndFeelInfo(
            "Core Light - FlatIntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf", false); // NON-NLS
    defaultDarkTheme =
        new ReadableLookAndFeelInfo(
            "Core Dark - Auramed", // NON-NLS
            "org.weasis.launcher.FlatWeasisTheme",
            true);
    lookAndFeels = buildFlatLookAndFeels();
    lookAndFeels.add(defaultLightTheme);
    lookAndFeels.add(defaultDarkTheme);
  }

  private static List<ReadableLookAndFeelInfo> buildFlatLookAndFeels() {
    List<ReadableLookAndFeelInfo> infos = new ArrayList<>();
    Arrays.asList(FlatAllIJThemes.INFOS)
        .forEach(
            i ->
                infos.add(
                    new ReadableLookAndFeelInfo(
                        i.isDark() ? "Dark - " + i.getName() : "Light - " + i.getName(), // NON-NLS
                        i.getClassName(),
                        i.isDark())));

    for (int i = 0; i < FLAT_CLASSES.length; i++) {
      if (isClassExist(FLAT_CLASSES[i])) {
        infos.add(
            new ReadableLookAndFeelInfo(
                FLAT_NAMES[i], FLAT_CLASSES[i], FLAT_CLASSES[i].contains("Dark"))); // NON-NLS
      }
    }
    return infos;
  }

  private static boolean isClassExist(String clazz) {
    try {
      Class.forName(clazz);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  public List<ReadableLookAndFeelInfo> getLookAndFeels() {
    return lookAndFeels;
  }

  /** Auramed portal nav-active orange (`--nav-active-bg`). */
  private static final Color AURAMED_SELECTION_ORANGE = new Color(0xfb923c);

  private static final Color AURAMED_SELECTION_FG = new Color(0x0f172a);

  /** IntelliJ action-icon grey used in Weasis SVGs (`fill`/`stroke`). */
  private static final Color SVG_ICON_GREY = new Color(0x6e6e6e);

  /** Light glyph color for outline icons on dark-blue chrome (toolbar). */
  private static final Color SVG_ICON_ON_DARK = new Color(0xf8fafc);

  /** Changes the look and feel for the whole GUI */
  public String setLookAndFeel(ReadableLookAndFeelInfo look) {
    ReadableLookAndFeelInfo info = look == null ? defaultDarkTheme : look;
    boolean dark = info.isDark();
    try {
      UIManager.setLookAndFeel(info.getClassName());
      increaseToolbarSeparatorContrast(dark);
      applyAuramedSelectionColors();
      applyAuramedIconColors(dark);

      // TODO set as preference: preserve the default color action
      applyDefaultColor(FlatIconColors.ACTIONS_RED, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_YELLOW, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREEN, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_BLUE, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREY, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREYINLINE, dark);
    } catch (Exception e) {
      LOGGER.error("Unable to set the Look&Feel", e);
    }
    return info.getClassName();
  }

  /**
   * Auramed branding colors: orange app buttons (readable on dark blue) and orange menu
   * selection with black text. FlatLaf otherwise only lightens the blue button background.
   */
  private static void applyAuramedSelectionColors() {
    Color hover = new Color(0xf97316);
    Color pressed = new Color(0xea580c);

    UIManager.put("Button.background", AURAMED_SELECTION_ORANGE);
    UIManager.put("Button.foreground", AURAMED_SELECTION_FG);
    UIManager.put("Button.hoverBackground", hover);
    UIManager.put("Button.pressedBackground", pressed);
    UIManager.put("Button.borderColor", AURAMED_SELECTION_ORANGE);
    UIManager.put("Button.selectedBackground", hover);
    UIManager.put("Button.selectedForeground", AURAMED_SELECTION_FG);
    UIManager.put("Button.default.background", AURAMED_SELECTION_ORANGE);
    UIManager.put("Button.default.foreground", AURAMED_SELECTION_FG);
    UIManager.put("Button.default.hoverBackground", hover);
    UIManager.put("Button.default.pressedBackground", pressed);

    UIManager.put("Button.toolbar.selectedBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("Button.toolbar.selectedForeground", AURAMED_SELECTION_FG);
    UIManager.put("ToggleButton.selectedBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("ToggleButton.selectedForeground", AURAMED_SELECTION_FG);
    UIManager.put("ToggleButton.toolbar.selectedBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("ToggleButton.toolbar.selectedForeground", AURAMED_SELECTION_FG);

    UIManager.put("Menu.selectionBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("Menu.selectionForeground", AURAMED_SELECTION_FG);
    UIManager.put("MenuBar.selectionBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("MenuBar.selectionForeground", AURAMED_SELECTION_FG);
    UIManager.put("MenuItem.selectionBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("MenuItem.selectionForeground", AURAMED_SELECTION_FG);
    UIManager.put("CheckBoxMenuItem.selectionBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("CheckBoxMenuItem.selectionForeground", AURAMED_SELECTION_FG);
    UIManager.put("RadioButtonMenuItem.selectionBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("RadioButtonMenuItem.selectionForeground", AURAMED_SELECTION_FG);

    UIManager.put("TabbedPane.tabType", "card");
    UIManager.put("TabbedPane.selectedBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("TabbedPane.selectedForeground", AURAMED_SELECTION_FG);
    UIManager.put("TabbedPane.underlineColor", AURAMED_SELECTION_ORANGE);
    UIManager.put("ToggleButton.tab.selectedBackground", AURAMED_SELECTION_ORANGE);
    UIManager.put("ToggleButton.tab.selectedForeground", AURAMED_SELECTION_FG);
    UIManager.put("ToggleButton.tab.underlineColor", AURAMED_SELECTION_ORANGE);
  }

  /**
   * Outline SVGs use {@code #6E6E6E}. On dark chrome they must lighten; on orange Auramed buttons
   * (import, image tools, selected measure/draw) they must go black so strokes stay visible.
   */
  private static void applyAuramedIconColors(boolean dark) {
    ColorFilter global = FlatSVGIcon.ColorFilter.getInstance();
    global.remove(SVG_ICON_GREY);
    global.add(SVG_ICON_GREY, SVG_ICON_GREY, SVG_ICON_ON_DARK);
    global.setMapperEx(LookAndFeels::mapAuramedIconColor);
  }

  private static Color mapAuramedIconColor(Component c, Color color) {
    if (color == null || (color.getRGB() & 0xffffff) != 0x6e6e6e) {
      // After light/dark map, grey may already be SVG_ICON_ON_DARK — still check orange buttons.
      if (color != null
          && (color.getRGB() & 0xffffff) == (SVG_ICON_ON_DARK.getRGB() & 0xffffff)
          && needsDarkGlyphOnOrange(c)) {
        return AURAMED_SELECTION_FG;
      }
      return color;
    }
    if (needsDarkGlyphOnOrange(c)) {
      return AURAMED_SELECTION_FG;
    }
    return FlatLaf.isLafDark() ? SVG_ICON_ON_DARK : color;
  }

  private static boolean needsDarkGlyphOnOrange(Component c) {
    if (!(c instanceof AbstractButton button)) {
      return false;
    }
    // Toolbars keep a transparent/dark chrome; FlatLaf still reports Button.background
    // (orange) on getBackground(), so only flip to black when the tool is selected.
    if (isInToolBar(button)) {
      return button.isSelected();
    }
    if (button.isSelected()) {
      return true;
    }
    // Filled app buttons (import, image tools, etc.) use orange Button.background.
    return button.isContentAreaFilled()
        && (isAuramedOrange(UIManager.getColor("Button.background"))
            || isAuramedOrange(button.getBackground()));
  }

  private static boolean isInToolBar(Component c) {
    for (Component p = c; p != null; p = p.getParent()) {
      if (p instanceof JToolBar) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAuramedOrange(Color c) {
    if (c == null || c.getAlpha() < 200) {
      return false;
    }
    int r = c.getRed();
    int g = c.getGreen();
    int b = c.getBlue();
    return r > 200 && g > 90 && g < 190 && b < 120;
  }

  private static void increaseToolbarSeparatorContrast(boolean dark) {
    Color c = UIManager.getColor("ToolBar.separatorColor");
    if (dark) {
      c = ColorFunctions.lighten(c, 0.2f);
    } else {
      c = ColorFunctions.darken(c, 0.2f);
    }
    UIManager.put("ToolBar.separatorColor", c);
  }

  private static void applyDefaultColor(FlatIconColors flatIconColor, boolean dark) {
    if (dark) {
      FlatIconColors darkColor =
          switch (flatIconColor) {
            case ACTIONS_RED -> FlatIconColors.ACTIONS_RED_DARK;
            case ACTIONS_YELLOW -> FlatIconColors.ACTIONS_YELLOW_DARK;
            case ACTIONS_GREEN -> FlatIconColors.ACTIONS_GREEN_DARK;
            case ACTIONS_BLUE -> FlatIconColors.ACTIONS_BLUE_DARK;
            case ACTIONS_GREY -> FlatIconColors.ACTIONS_GREY_DARK;
            case ACTIONS_GREYINLINE -> FlatIconColors.ACTIONS_GREYINLINE_DARK;
            default -> flatIconColor;
          };
      UIManager.put(darkColor.key, new Color(darkColor.rgb));
    } else {
      UIManager.put(flatIconColor.key, new Color(flatIconColor.rgb));
    }
  }

  public ReadableLookAndFeelInfo getAvailableLookAndFeel(String look, String profileName) {
    if (Utils.hasText(look)) {
      for (ReadableLookAndFeelInfo lookAndFeelInfo : lookAndFeels) {
        if (lookAndFeelInfo.getClassName().equals(look)) {
          return lookAndFeelInfo;
        }
      }
    }
    if ("dicomizer".equalsIgnoreCase(profileName)) { // NON-NLS
      return defaultLightTheme;
    }
    return defaultDarkTheme;
  }

  public static class ReadableLookAndFeelInfo extends LookAndFeelInfo {
    private final boolean dark;

    public ReadableLookAndFeelInfo(String name, String className, boolean dark) {
      super(name, className);
      this.dark = dark;
    }

    public boolean isDark() {
      return dark;
    }

    @Override
    public String toString() {
      return getName();
    }

    @Override
    public boolean equals(Object obj) {
      if (!(obj instanceof LookAndFeelInfo other)) {
        return false;
      }
      return getClassName().equals(other.getClassName());
    }

    @Override
    public int hashCode() {
      return getClassName().hashCode();
    }
  }
}
