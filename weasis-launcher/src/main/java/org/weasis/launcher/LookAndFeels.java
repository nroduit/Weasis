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
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.util.ColorFunctions;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
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
            "Core Dark - Flat Weasis", // NON-NLS
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

  /** Changes the look and feel for the whole GUI */
  public String setLookAndFeel(ReadableLookAndFeelInfo look) {
    ReadableLookAndFeelInfo info = look == null ? defaultDarkTheme : look;
    try {
      install(info.getClassName());
    } catch (Exception e) {
      LOGGER.error("Unable to set the Look&Feel", e);
    }
    return info.getClassName();
  }

  /**
   * Sets a look and feel with what Weasis adds to every theme: a toolbar separator that stands out,
   * a selected tab that keeps its color under the pointer, and the standard colors of the action
   * icons, whose red, yellow and green carry a meaning (stop, warning, go) that the palette of a
   * theme must not change. The start-up and the preview of the preferences both go through here, so
   * that a preview shows what the next start gives.
   */
  public static void install(String className)
      throws ClassNotFoundException,
          InstantiationException,
          IllegalAccessException,
          UnsupportedLookAndFeelException {
    UIManager.setLookAndFeel(className);
    boolean dark = FlatLaf.isLafDark();
    // a selected tab keeps its selection color under the pointer, as a selected button does
    UIManager.put("TabbedPaneUI", SelectedTabUI.class.getName()); // NON-NLS
    UIManager.put(SelectedTabUI.class.getName(), SelectedTabUI.class);
    increaseToolbarSeparatorContrast(dark);
    applyDefaultColor(FlatIconColors.ACTIONS_RED, dark);
    applyDefaultColor(FlatIconColors.ACTIONS_YELLOW, dark);
    applyDefaultColor(FlatIconColors.ACTIONS_GREEN, dark);
    applyDefaultColor(FlatIconColors.ACTIONS_BLUE, dark);
    applyDefaultColor(FlatIconColors.ACTIONS_GREY, dark);
    applyDefaultColor(FlatIconColors.ACTIONS_GREYINLINE, dark);
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
