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
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {
  private static final Logger LOGGER = System.getLogger(LookAndFeels.class.getName());

  private final List<ReadableLookAndFeelInfo> lookAndFeels;
  private final ReadableLookAndFeelInfo defaultLightTheme;
  private final ReadableLookAndFeelInfo defaultDarkTheme;

  private static final String[] FLAT_NAMES = {
    "Core Dark - FlatDarcula", "Core Dark - FlatDark", "Core Light - FlatLight" // NON-NLS
  };

  private static final String[] FLAT_CLASSES = {
    "com.formdev.flatlaf.FlatDarculaLaf",
    "com.formdev.flatlaf.FlatDarkLaf",
    "com.formdev.flatlaf.FlatLightLaf"
  };

  LookAndFeels() {
    FlatLaf.registerCustomDefaultsSource("org.weasis.theme");
    defaultLightTheme =
        new ReadableLookAndFeelInfo(
            "Core Light - FlatIntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf", false); // NON-NLS
    defaultDarkTheme =
        new ReadableLookAndFeelInfo(
            "Core Dark - FlatWeasis", "org.weasis.launcher.FlatWeasisTheme", true); // NON-NLS
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
    boolean dark = info.isDark();
    try {
      UIManager.setLookAndFeel(info.getClassName());
      increaseToolbarSeparatorContrast(dark);

      // TODO set as preference: preserve the default color action
      applyDefaultColor(FlatIconColors.ACTIONS_RED, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_YELLOW, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREEN, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_BLUE, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREY, dark);
      applyDefaultColor(FlatIconColors.ACTIONS_GREYINLINE, dark);
    } catch (Exception e) {
      LOGGER.log(Level.ERROR, "Unable to set the Look&Feel", e);
    }
    return info.getClassName();
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
