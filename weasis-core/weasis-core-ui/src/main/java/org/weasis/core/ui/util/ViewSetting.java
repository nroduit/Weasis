package org.weasis.core.ui.util;

import java.awt.Font;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;

public class ViewSetting {
    public final static String PREFERENCE_NODE = "view2d.default"; //$NON-NLS-1$
    private int fontType;
    private int fontSize;
    private String fontName;

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            fontName = p.get("font.name", "Default"); //$NON-NLS-1$
            fontType = p.getInt("font.type", 0); //$NON-NLS-1$
            fontSize = p.getInt("font.size", 10); //$NON-NLS-1$
        }
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            BundlePreferences.putStringPreferences(p, "font.name", fontName); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "font.type", fontType); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "font.size", fontSize); //$NON-NLS-1$
        }
    }

    public int getFontType() {
        return fontType;
    }

    public void setFontType(int fontType) {
        this.fontType = fontType;
    }

    public int getFontSize() {
        return fontSize;
    }

    public void setFontSize(int fontSize) {
        this.fontSize = fontSize;
    }

    public String getFontName() {
        return fontName;
    }

    public void setFontName(String fontName) {
        this.fontName = fontName;
    }

    public Font getFont() {
        return new Font(fontName, fontType, fontSize);

    }
}
