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

import com.formdev.flatlaf.intellijthemes.FlatAllIJThemes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

  private LookAndFeels() {}

  private static final String[] FLAT_NAMES = {
    "Core Dark - FlatDarcula",
    "Core Dark - FlatDark",
    "Core Light - FlatIntelliJ",
    "Core Light - FlatLight"
  };

  private static final String[] FLAT_CLASSES = {
    "com.formdev.flatlaf.FlatDarculaLaf",
    "com.formdev.flatlaf.FlatDarkLaf",
    "com.formdev.flatlaf.FlatIntelliJLaf",
    "com.formdev.flatlaf.FlatLightLaf"
  };

  public static boolean installFlatLaf() {
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
    return true;
  }

  private static boolean isClassExist(String clazz) {
    try {
      Class.forName(clazz);
      return true;
    } catch (Exception e) {
      return false;
    }
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
