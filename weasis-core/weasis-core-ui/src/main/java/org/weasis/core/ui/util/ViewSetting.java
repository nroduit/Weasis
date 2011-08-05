package org.weasis.core.ui.util;

import java.awt.Color;
import java.awt.Font;
import java.util.List;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.graphic.Graphic;
import org.weasis.core.ui.graphic.ImageStatistics;
import org.weasis.core.ui.graphic.Measurement;

public class ViewSetting {
    public static final String PREFERENCE_NODE = "view2d.default"; //$NON-NLS-1$
    private int fontType;
    private int fontSize;
    private String fontName;
    private boolean drawOnlyOnce;
    private Color lineColor;
    private int lineWidth;
    private boolean basicStatistics;
    private boolean moreStatistics;

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            fontName = p.get("font.name", "Default"); //$NON-NLS-1$
            fontType = p.getInt("font.type", 0); //$NON-NLS-1$
            fontSize = p.getInt("font.size", 12); //$NON-NLS-1$
            drawOnlyOnce = p.getBoolean("draw.once", true); //$NON-NLS-1$
            lineWidth = p.getInt("line.width", 1); //$NON-NLS-1$
            int rgb = p.getInt("line.color", Color.YELLOW.getRGB()); //$NON-NLS-1$
            lineColor = new Color(rgb);
            basicStatistics = p.getBoolean("statistics.basic", true); //$NON-NLS-1$
            moreStatistics = p.getBoolean("statistics.more", false); //$NON-NLS-1$

            String labels = p.get("statistics.labels", null); //$NON-NLS-1$
            if (labels != null) {
                String[] items = labels.split(",");
                for (int i = 0; i < items.length; i++) {
                    String[] val = items[i].split(":");
                    if (val.length == 2) {
                        for (Measurement m : ImageStatistics.ALL_MEASUREMENTS) {
                            if (val[0].equals(String.valueOf(m.getId()))) {
                                m.setGraphicLabel(isTrueValue(val[1]));
                                break;
                            }
                        }
                    }
                }
            }

            // Forget the Selection Graphic
            for (int i = 1; i < MeasureToolBar.graphicList.size(); i++) {
                Graphic graph = MeasureToolBar.graphicList.get(i);
                List<Measurement> list = graph.getMeasurementList();
                if (list != null && list.size() > 0) {
                    labels = p.get(graph.getClass().getSimpleName() + ".labels", null); //$NON-NLS-1$
                    if (labels != null) {
                        String[] items = labels.split(",");
                        for (int k = 0; k < items.length; k++) {
                            String[] val = items[k].split(":");
                            if (val.length == 2) {
                                for (Measurement m : list) {
                                    if (val[0].equals(String.valueOf(m.getId()))) {
                                        m.setGraphicLabel(isTrueValue(val[1]));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isTrueValue(String val) {
        return "1".equals(val.trim());
    }

    private void writeLabels(StringBuffer buffer, Measurement m) {
        buffer.append(m.getId());
        buffer.append(":");
        buffer.append((m.isGraphicLabel() ? "1" : "0"));
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
            BundlePreferences.putBooleanPreferences(p, "statistics.basic", basicStatistics); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(p, "statistics.more", moreStatistics); //$NON-NLS-1$

            StringBuffer buffer = new StringBuffer();
            writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[0]);
            for (int i = 1; i < ImageStatistics.ALL_MEASUREMENTS.length; i++) {
                buffer.append(",");
                writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[i]);
            }
            BundlePreferences.putStringPreferences(p, "statistics.labels", buffer.toString()); //$NON-NLS-1$

            // Forget the Selection Graphic
            for (int i = 1; i < MeasureToolBar.graphicList.size(); i++) {
                Graphic graph = MeasureToolBar.graphicList.get(i);
                List<Measurement> list = graph.getMeasurementList();
                if (list != null && list.size() > 0) {
                    buffer = new StringBuffer();
                    writeLabels(buffer, list.get(0));
                    for (int j = 1; j < list.size(); j++) {
                        buffer.append(",");
                        writeLabels(buffer, list.get(j));
                    }
                    BundlePreferences.putStringPreferences(p,
                        graph.getClass().getSimpleName() + ".labels", buffer.toString()); //$NON-NLS-1$
                }
            }
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

    public boolean isBasicStatistics() {
        return basicStatistics;
    }

    public void setBasicStatistics(boolean basicStatistics) {
        this.basicStatistics = basicStatistics;
    }

    public boolean isMoreStatistics() {
        return moreStatistics;
    }

    public void setMoreStatistics(boolean moreStatistics) {
        this.moreStatistics = moreStatistics;
    }

}
