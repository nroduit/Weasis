/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.pref;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.Measurement;

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
    private final List<Monitor> monitors = new ArrayList<>(2);

    public void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            Preferences font = p.node("font"); //$NON-NLS-1$
            fontName = font.get("name", Messages.getString("LabelPrefView.default")); //$NON-NLS-1$ //$NON-NLS-2$
            fontType = font.getInt("type", 0); //$NON-NLS-1$
            fontSize = font.getInt("size", 12); //$NON-NLS-1$
            Preferences draw = p.node("drawing"); //$NON-NLS-1$
            drawOnlyOnce = draw.getBoolean("once", true); //$NON-NLS-1$
            lineWidth = draw.getInt("width", 1); //$NON-NLS-1$
            int rgb = draw.getInt("color", Color.YELLOW.getRGB()); //$NON-NLS-1$
            lineColor = new Color(rgb);
            Preferences stats = p.node("statistics"); //$NON-NLS-1$
            basicStatistics = stats.getBoolean("basic", true); //$NON-NLS-1$
            moreStatistics = stats.getBoolean("more", true); //$NON-NLS-1$

            ImageStatistics.IMAGE_PIXELS.setComputed(basicStatistics);
            ImageStatistics.IMAGE_MIN.setComputed(basicStatistics);
            ImageStatistics.IMAGE_MAX.setComputed(basicStatistics);
            ImageStatistics.IMAGE_MEAN.setComputed(basicStatistics);

            ImageStatistics.IMAGE_MEDIAN.setComputed(moreStatistics);
            ImageStatistics.IMAGE_STD.setComputed(moreStatistics);
            ImageStatistics.IMAGE_SKEW.setComputed(moreStatistics);
            ImageStatistics.IMAGE_KURTOSIS.setComputed(moreStatistics);
            ImageStatistics.IMAGE_ENTROPY.setComputed(moreStatistics);

            String labels = stats.get("label", null); //$NON-NLS-1$
            if (labels != null) {
                String[] items = labels.split(","); //$NON-NLS-1$
                for (int i = 0; i < items.length; i++) {
                    String[] val = items[i].split(":"); //$NON-NLS-1$
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
            for (int i = 1; i < MeasureToolBar.measureGraphicList.size(); i++) {
                Graphic graph = MeasureToolBar.measureGraphicList.get(i);
                List<Measurement> list = graph.getMeasurementList();
                if (list != null && !list.isEmpty()) {
                    Preferences gpref = p.node(graph.getClass().getSimpleName());
                    labels = gpref.get("label", null); //$NON-NLS-1$
                    if (labels != null) {
                        String[] items = labels.split(","); //$NON-NLS-1$
                        for (int k = 0; k < items.length; k++) {
                            String[] val = items[k].split(":"); //$NON-NLS-1$
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

    public List<Monitor> getMonitors() {
        return new ArrayList<>(monitors);
    }

    public Monitor getMonitor(GraphicsDevice device) {
        for (Monitor m : monitors) {
            if (m.getGraphicsDevice() == device) {
                return m;
            }
        }
        return null;
    }

    public void initMonitors() {
        monitors.clear();
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice[] gd = ge.getScreenDevices();

        for (int i = 0; i < gd.length; i++) {
            final GraphicsConfiguration config = gd[i].getDefaultConfiguration();
            if (config == null || gd[i].getType() != GraphicsDevice.TYPE_RASTER_SCREEN) {
                continue;
            }

            Monitor monitor = new Monitor(gd[i]);
            StringBuilder buf = new StringBuilder("screen."); //$NON-NLS-1$
            buf.append(monitor.getMonitorID());
            Rectangle b = monitor.getBounds();
            buf.append("."); //$NON-NLS-1$
            buf.append(b.width);
            buf.append("x"); //$NON-NLS-1$
            buf.append(b.height);
            buf.append(".pitch"); //$NON-NLS-1$
            double pitch = BundleTools.LOCAL_PERSISTENCE.getDoubleProperty(buf.toString(), 0.0);
            monitor.setRealScaleFactor(pitch);
            monitors.add(monitor);
        }
    }

    private static boolean isTrueValue(String val) {
        return "1".equals(val.trim()); //$NON-NLS-1$
    }

    private static void writeLabels(StringBuilder buffer, Measurement m) {
        buffer.append(m.getId());
        buffer.append(":"); //$NON-NLS-1$
        buffer.append(m.getGraphicLabel() ? "1" : "0"); //$NON-NLS-1$ //$NON-NLS-2$
    }

    public void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            Preferences font = p.node("font"); //$NON-NLS-1$
            BundlePreferences.putStringPreferences(font, "name", fontName); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(font, "type", fontType); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(font, "size", fontSize); //$NON-NLS-1$

            Preferences draw = p.node("drawing"); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(draw, "once", drawOnlyOnce); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(draw, "width", lineWidth); //$NON-NLS-1$
            BundlePreferences.putIntPreferences(draw, "color", lineColor.getRGB()); //$NON-NLS-1$

            Preferences stats = p.node("statistics"); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(stats, "basic", basicStatistics); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(stats, "more", moreStatistics); //$NON-NLS-1$
            StringBuilder buffer = new StringBuilder();
            writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[0]);
            for (int i = 1; i < ImageStatistics.ALL_MEASUREMENTS.length; i++) {
                buffer.append(","); //$NON-NLS-1$
                writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[i]);
            }
            BundlePreferences.putStringPreferences(stats, "label", buffer.toString()); //$NON-NLS-1$

            // Forget the Selection Graphic
            for (int i = 1; i < MeasureToolBar.measureGraphicList.size(); i++) {
                Graphic graph = MeasureToolBar.measureGraphicList.get(i);
                List<Measurement> list = graph.getMeasurementList();
                if (list != null && !list.isEmpty()) {
                    Preferences gpref = p.node(graph.getClass().getSimpleName());
                    buffer = new StringBuilder();
                    writeLabels(buffer, list.get(0));
                    for (int j = 1; j < list.size(); j++) {
                        buffer.append(","); //$NON-NLS-1$
                        writeLabels(buffer, list.get(j));
                    }
                    BundlePreferences.putStringPreferences(gpref, "label", buffer.toString()); //$NON-NLS-1$
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
