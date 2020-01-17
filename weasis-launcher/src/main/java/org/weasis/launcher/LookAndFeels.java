/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.launcher;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

    private LookAndFeels() {
    }

    private static final String[] NAMES = { "Substance Autumn", "Substance Business Black Steel", //$NON-NLS-1$//$NON-NLS-2$
        "Substance Business Blue Steel", "Substance Business", "Substance Cerulean", //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
        "Substance Creme Coffee", "Substance Creme", "Substance Dust Coffee (saturated)", "Substance Dust (saturated)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
        "Substance Gemini", "Substance Graphite Aqua (dark)", "Substance Graphite Chalk (dark)", //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
        "Substance Graphite Electric (dark)", "Substance Graphite Glass (dark)", "Substance Graphite Gold (dark)", //$NON-NLS-1$ //$NON-NLS-2$//$NON-NLS-3$
        "Substance Graphite (dark)", "Substance Graphite Sunset (dark)", "Substance Magellan (blue)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        "Substance Mariner", "Substance Mist Aqua", //$NON-NLS-1$//$NON-NLS-2$
        "Substance Mist Silver", //$NON-NLS-1$
        "Substance Moderate", "Substance Nebula Amethyst", "Substance Nebula Brick Wall", "Substance Nebula", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$//$NON-NLS-4$
        "Substance Night Shade (dark)", "Substance Raven (dark)", "Substance Sahara", "Substance Sentine", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "Substance Twilight (dark)" }; //$NON-NLS-1$

    private static final String[] CLASSES = { "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCeruleanLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteChalkLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteElectricLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteGoldLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteSunsetLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMarinerLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNebulaAmethystLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNightShadeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceSentinelLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel" //$NON-NLS-1$
    };

    public static boolean installSubstanceLookAndFeels() {
        try {
            Class.forName(CLASSES[0]);
        } catch (Exception e) {
            return false;
        }
        List<LookAndFeelInfo> tmp = new ArrayList<>();

        for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
            if (!"com.sun.java.swing.plaf.motif.MotifLookAndFeel".equals(i.getClassName())) { //$NON-NLS-1$
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
