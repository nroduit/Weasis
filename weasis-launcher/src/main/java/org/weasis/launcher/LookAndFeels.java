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

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

  private LookAndFeels() {}

  private static final String[] NAMES = {
    "Substance Autumn", // NON-NLS
    "Substance Business Black Steel", // NON-NLS
    "Substance Business Blue Steel", // NON-NLS
    "Substance Business", // NON-NLS
    "Substance Cerulean", // NON-NLS
    "Substance Creme Coffee", // NON-NLS
    "Substance Creme", // NON-NLS
    "Substance Dust Coffee (saturated)", // NON-NLS
    "Substance Dust (saturated)", // NON-NLS
    "Substance Gemini", // NON-NLS
    "Substance Graphite Aqua (dark)", // NON-NLS
    "Substance Graphite Chalk (dark)", // NON-NLS
    "Substance Graphite Electric (dark)", // NON-NLS
    "Substance Graphite Glass (dark)", // NON-NLS
    "Substance Graphite Gold (dark)", // NON-NLS
    "Substance Graphite (dark)", // NON-NLS
    "Substance Graphite Sienna (dark)", // NON-NLS
    "Substance Graphite Sunset (dark)", // NON-NLS
    "Substance Magellan (blue)", // NON-NLS
    "Substance Mariner", // NON-NLS
    "Substance Mist Aqua", // NON-NLS
    "Substance Mist Silver", // NON-NLS
    "Substance Moderate", // NON-NLS
    "Substance Nebula Amethyst", // NON-NLS
    "Substance Nebula Brick Wall", // NON-NLS
    "Substance Nebula", // NON-NLS
    "Substance Night Shade (dark)", // NON-NLS
    "Substance Raven (dark)", // NON-NLS
    "Substance Sahara", // NON-NLS
    "Substance Sentine", // NON-NLS
    "Substance Twilight (dark)" // NON-NLS
  };

  private static final String[] CLASSES = {
    "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceCeruleanLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteChalkLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteElectricLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteGoldLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteSiennaLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceGraphiteSunsetLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceMarinerLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceNebulaAmethystLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceNightShadeLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceSentinelLookAndFeel",
    "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel"
  };

  public static boolean installSubstanceLookAndFeels() {
    try {
      Class.forName(CLASSES[0]);
    } catch (Exception e) {
      return false;
    }
    List<LookAndFeelInfo> tmp = new ArrayList<>();

    for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
      if (!"com.sun.java.swing.plaf.motif.MotifLookAndFeel".equals(i.getClassName())) {
        tmp.add(new ReadableLookAndFeelInfo(i.getName(), i.getClassName()));
      }
    }

    for (int i = 0; i < CLASSES.length; i++) {
      if (isClassExist(CLASSES[i])) {
        tmp.add(new ReadableLookAndFeelInfo(NAMES[i], CLASSES[i]));
      }
    }
    UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[tmp.size()]));
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
      if (!(obj instanceof LookAndFeelInfo)) {
        return false;
      }
      LookAndFeelInfo other = (LookAndFeelInfo) obj;
      return getClassName().equals(other.getClassName());
    }

    @Override
    public int hashCode() {
      return getClassName().hashCode();
    }
  }

  public static void setUIFont(javax.swing.plaf.FontUIResource font) {
    Enumeration<Object> keys = UIManager.getDefaults().keys();
    while (keys.hasMoreElements()) {
      Object key = keys.nextElement();
      if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource) {
        UIManager.put(key, font);
      }
    }
  }
}
