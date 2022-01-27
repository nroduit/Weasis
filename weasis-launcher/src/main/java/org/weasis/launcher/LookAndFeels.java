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

import static org.weasis.launcher.WeasisLauncher.P_WEASIS_NAME;

import com.formdev.flatlaf.FlatIconColors;
import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {
  private static final Logger LOGGER = Logger.getLogger(WeasisLauncher.class.getName());

  private LookAndFeels() {}

  private static final String[] FLAT_NAMES = {
    "Core Dark - FlatWeasis",
    "Core Dark - FlatDarcula",
    "Core Dark - FlatDark",
    "Core Light - FlatIntelliJ",
    "Core Light - FlatLight"
  };

  private static final String[] FLAT_CLASSES = {
    "org.weasis.launcher.FlatWeasisTheme",
    "com.formdev.flatlaf.FlatDarculaLaf",
    "com.formdev.flatlaf.FlatDarkLaf",
    "com.formdev.flatlaf.FlatIntelliJLaf",
    "com.formdev.flatlaf.FlatLightLaf"
  };

  public static void installFlatLaf() {
    List<LookAndFeelInfo> tmp = new ArrayList<>();
    Arrays.asList(FlatAllIJThemes.INFOS)
        .forEach(
            i ->
                tmp.add(
                    new ReadableLookAndFeelInfo(
                        i.isDark() ? "Dark - " + i.getName() : "Light - " + i.getName(),
                        i.getClassName())));

    for (int i = 0; i < FLAT_CLASSES.length; i++) {
      if (isClassExist(FLAT_CLASSES[i])) {
        tmp.add(new ReadableLookAndFeelInfo(FLAT_NAMES[i], FLAT_CLASSES[i]));
      }
    }
    UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[0]));
  }

  private static boolean isClassExist(String clazz) {
    try {
      Class.forName(clazz);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Changes the look and feel for the whole GUI */
  public static String setLookAndFeel(String look, String profileName) {
    String laf = getAvailableLookAndFeel(look, profileName);
    try {
      UIManager.setLookAndFeel(laf);
      for (LookAndFeelInfo lf : UIManager.getInstalledLookAndFeels()) {
        if (laf.equals(lf.getClassName())) {
          boolean dark = lf.getName().contains("Dark");
          increaseToolbarSeparatorContrast(lf, dark);

          // TODO set as preference: preserve the default color action
          applyDefaultColor(FlatIconColors.ACTIONS_RED, dark);
          applyDefaultColor(FlatIconColors.ACTIONS_YELLOW, dark);
          applyDefaultColor(FlatIconColors.ACTIONS_GREEN, dark);
          applyDefaultColor(FlatIconColors.ACTIONS_BLUE, dark);
          applyDefaultColor(FlatIconColors.ACTIONS_GREY, dark);
          applyDefaultColor(FlatIconColors.ACTIONS_GREYINLINE, dark);
          break;
        }
      }

    } catch (Exception e) {
      LOGGER.log(Level.SEVERE, "Unable to set the Look&Feel", e);
    }
    adaptSystemProperties();
    return laf;
  }

  private static void adaptSystemProperties() {
    if (SystemInfo.isMacOS) {
      // Enable screen menu bar
      System.setProperty("apple.laf.useScreenMenuBar", "true");
      System.setProperty("apple.awt.application.name", System.getProperty(P_WEASIS_NAME));
      System.setProperty("apple.awt.application.appearance", "system");
    }
  }

  private static void increaseToolbarSeparatorContrast(LookAndFeelInfo lf, boolean dark) {
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

  public static String getAvailableLookAndFeel(String look, String profileName) {
    UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
    String laf = null;
    if (look != null) {
      for (UIManager.LookAndFeelInfo lookAndFeelInfo : lafs) {
        if (lookAndFeelInfo.getClassName().equals(look)) {
          laf = look;
          break;
        }
      }
    }
    if (laf == null) {
      if ("dicomizer".equalsIgnoreCase(profileName)) { // NON-NLS
        laf = "com.formdev.flatlaf.FlatIntelliJLaf";
      } else {
        laf = "org.weasis.launcher.FlatWeasisTheme";
      }
    }
    return laf;
  }

  private static class ReadableLookAndFeelInfo extends LookAndFeelInfo {

    public ReadableLookAndFeelInfo(String name, String className) {
      super(name, className);
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
