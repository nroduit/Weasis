package org.weasis.core.ui.util;

import java.awt.Color;
import java.awt.Font;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;

public class ViewSetting {
    public static final String PREFERENCE_NODE = "view2d.default"; //$NON-NLS-1$
    private int fontType;
    private int fontSize;
    private String fontName;
    private boolean drawOnlyOnce;
    private Color lineColor;
    private int lineWidth;

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            fontName = p.get("font.name", "Default"); //$NON-NLS-1$
            fontType = p.getInt("font.type", 0); //$NON-NLS-1$
            fontSize = p.getInt("font.size", 10); //$NON-NLS-1$
            drawOnlyOnce = p.getBoolean("draw.once", true); //$NON-NLS-1$
            lineWidth = p.getInt("line.width", 1); //$NON-NLS-1$
            int rgb = p.getInt("line.color", Color.YELLOW.getRGB()); //$NON-NLS-1$
            lineColor = new Color(rgb);
        }
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            BundlePreferences.putStringPreferences(p, "font.name", fontName); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "font.type", fontType); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "font.size", fontSize); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(p, "draw.once", drawOnlyOnce); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "line.width", lineWidth); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(p, "line.color", lineColor.getRGB()); //$NON-NLS-1$
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

    public void setDrawOnlyOnce(boolean drawOnlyOnce) {
        this.drawOnlyOnce = drawOnlyOnce;
    }

    public boolean isDrawOnlyOnce() {
        return drawOnlyOnce;
    }

    public Color getLineColor() {
        return lineColor;
    }

    public void setLineColor(Color lineColor) {
        this.lineColor = lineColor;
    }

    public int getLineWidth() {
        return lineWidth;
    }

    public void setLineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
    }
}
