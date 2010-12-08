package org.weasis.launcher;

import java.util.ArrayList;
import java.util.List;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

public final class LookAndFeels {

    private final transient static String[] names =
        { "Substance Autumn", "Substance BusinessBlackSteel", "Substance BusinessBlueSteel", "Substance Business",
            "Substance ChallengerDeep", "Substance CremeCoffee", "Substance Creme", "Substance DustCoffee",
            "Substance Dust", "Substance EmeraldDusk", "Substance Gemini", "Substance GraphiteAqua",
            "Substance GraphiteGlass", "Substance Graphite", "Substance Magellan", "Substance MistAqua",
            "Substance MistSilver", "Substance Moderate", "Substance NebulaBrickWall", "Substance Nebula",
            "Substance OfficeBlue2007", "Substance OfficeSilver2007", "Substance Raven", "Substance Sahara",
            "Substance Twilight" };
    private final transient static String[] classes =
        { "org.pushingpixels.substance.api.skin.SubstanceAutumnLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceBusinessBlackSteelLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceBusinessBlueSteelLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceBusinessLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceChallengerDeepLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceCremeCoffeeLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceCremeLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceDustCoffeeLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceDustLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceEmeraldDuskLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceGeminiLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceGraphiteAquaLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceGraphiteGlassLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceGraphiteLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceMagellanLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceMistAquaLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceMistSilverLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceModerateLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceNebulaBrickWallLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceNebulaLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceOfficeBlue2007LookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceOfficeSilver2007LookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceRavenLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceSaharaLookAndFeel",
            "org.pushingpixels.substance.api.skin.SubstanceTwilightLookAndFeel" };

    public static LookAndFeelInfo[] commonInstalledLookAndFeels() {

        List<LookAndFeelInfo> tmp = new ArrayList<LookAndFeelInfo>();

        for (LookAndFeelInfo i : UIManager.getInstalledLookAndFeels()) {
            tmp.add(new ReadableLookAndFeelInfo(i.getName(), i.getClassName()));
        }

        for (int i = 0; i < classes.length; i++) {
            tmp.add(new ReadableLookAndFeelInfo(names[i], classes[i]));
        }
        UIManager.setInstalledLookAndFeels(tmp.toArray(new LookAndFeelInfo[tmp.size()]));
        return UIManager.getInstalledLookAndFeels();
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
