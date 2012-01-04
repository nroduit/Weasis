/*******************************************************************************
 * Copyright (c) 2011 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.launcher;

import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

    private static final transient String[] NAMES = { "Substance Autumn", "Substance BusinessBlackSteel", //$NON-NLS-1$ //$NON-NLS-2$
        "Substance BusinessBlueSteel", "Substance Business", "Substance Cerulean", "Substance ChallengerDeep (dark)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        "Substance CremeCoffee", "Substance Creme", "Substance DustCoffee (saturated)", "Substance Dust (saturated)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "Substance EmeraldDusk (dark)", "Substance Gemini", "Substance GraphiteAqua", "Substance GraphiteGlass (dark)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "Substance Graphite (dark)", "Substance Magellan (dark)", "Substance MistAqua", "Substance MistSilver", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "Substance Moderate", "Substance NebulaBrickWall", "Substance Nebula", "Substance OfficeBlue2007 (saturated)", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
        "Substance OfficeSilver2007", "Substance Raven (dark)", "Substance Sahara", "Substance Twilight (dark)" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
    private static final transient String[] CLASSES = {
        "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCeruleanLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceChallengerDeepLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceEmeraldDuskLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceOfficeBlue2007LookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceOfficeSilver2007LookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel", //$NON-NLS-1$
        "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel" }; //$NON-NLS-1$

    public static boolean installSubstanceLookAndFeels() {
        try {
            Class.forName(CLASSES[0]);
        } catch (Exception e) {
            return false;
        }
        List<LookAndFeelInfo> tmp = new ArrayList<LookAndFeelInfo>();

        for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
            tmp.add(new ReadableLookAndFeelInfo(i.getName(), i.getClassName()));
        }

        for (int i = 0; i < CLASSES.length; i++) {
            tmp.add(new ReadableLookAndFeelInfo(NAMES[i], CLASSES[i]));
        }
        UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[tmp.size()]));
        return true;
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
}
