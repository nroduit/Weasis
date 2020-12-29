/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.weasis.launcher;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

    private LookAndFeels() {
    }

    private static final String[] NAMES = { "Substance Autumn", "Substance Business Black Steel",  //NON-NLS
        "Substance Business Blue Steel", "Substance Business", "Substance Cerulean", // NON-NLS
        "Substance Creme Coffee", "Substance Creme", "Substance Dust Coffee (saturated)", "Substance Dust (saturated)", // NON-NLS
        "Substance Gemini", "Substance Graphite Aqua (dark)", "Substance Graphite Chalk (dark)", // NON-NLS
        "Substance Graphite Electric (dark)", "Substance Graphite Glass (dark)", "Substance Graphite Gold (dark)", // NON-NLS
        "Substance Graphite (dark)", "Substance Graphite Sienna (dark)", "Substance Graphite Sunset (dark)", "Substance Magellan (blue)", // NON-NLS
        "Substance Mariner", "Substance Mist Aqua",  //NON-NLS
        "Substance Mist Silver",  //NON-NLS
        "Substance Moderate", "Substance Nebula Amethyst", "Substance Nebula Brick Wall", "Substance Nebula", // NON-NLS
        "Substance Night Shade (dark)", "Substance Raven (dark)", "Substance Sahara", "Substance Sentine", // NON-NLS
        "Substance Twilight (dark)" };  //NON-NLS

    private static final String[] CLASSES = { "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel", 
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
