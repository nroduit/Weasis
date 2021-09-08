/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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
  public static final String PREFERENCE_NODE = "view2d.default";
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
      Preferences font = p.node("font"); // NON-NLS
      fontName = font.get("name", Messages.getString("LabelPrefView.default")); // NON-NLS
      fontType = font.getInt("type", 0);
      fontSize = font.getInt("size", 12); // NON-NLS
      Preferences draw = p.node("drawing"); // NON-NLS
      drawOnlyOnce = draw.getBoolean("once", true); // NON-NLS
      lineWidth = draw.getInt("width", 1); // NON-NLS
      int rgb = draw.getInt("color", Color.YELLOW.getRGB()); // NON-NLS
      lineColor = new Color(rgb);
      Preferences stats = p.node("statistics"); // NON-NLS
      basicStatistics = stats.getBoolean("basic", true); // NON-NLS
      moreStatistics = stats.getBoolean("more", true); // NON-NLS

      ImageStatistics.IMAGE_PIXELS.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MIN.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MAX.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MEAN.setComputed(basicStatistics);

      ImageStatistics.IMAGE_MEDIAN.setComputed(moreStatistics);
      ImageStatistics.IMAGE_STD.setComputed(moreStatistics);
      ImageStatistics.IMAGE_SKEW.setComputed(moreStatistics);
      ImageStatistics.IMAGE_KURTOSIS.setComputed(moreStatistics);
      ImageStatistics.IMAGE_ENTROPY.setComputed(moreStatistics);

      String labels = stats.get("label", null); // NON-NLS
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
      for (int i = 1; i < MeasureToolBar.measureGraphicList.size(); i++) {
        Graphic graph = MeasureToolBar.measureGraphicList.get(i);
        List<Measurement> list = graph.getMeasurementList();
        if (list != null && !list.isEmpty()) {
          Preferences gpref = p.node(graph.getClass().getSimpleName());
          labels = gpref.get("label", null); // NON-NLS
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
      StringBuilder buf = new StringBuilder("screen."); // NON-NLS
      buf.append(monitor.getMonitorID());
      Rectangle b = monitor.getBounds();
      buf.append(".");
      buf.append(b.width);
      buf.append("x"); // NON-NLS
      buf.append(b.height);
      buf.append(".pitch");
      double pitch = BundleTools.LOCAL_UI_PERSISTENCE.getDoubleProperty(buf.toString(), 0.0);
      monitor.setRealScaleFactor(pitch);
      monitors.add(monitor);
    }
  }

  private static boolean isTrueValue(String val) {
    return "1".equals(val.trim());
  }

  private static void writeLabels(StringBuilder buffer, Measurement m) {
    buffer.append(m.getId());
    buffer.append(":");
    buffer.append(m.getGraphicLabel() ? "1" : "0"); // NON-NLS
  }

  public void savePreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
      Preferences font = p.node("font"); // NON-NLS
      BundlePreferences.putStringPreferences(font, "name", fontName); // NON-NLS
      BundlePreferences.putIntPreferences(font, "type", fontType); // NON-NLS
      BundlePreferences.putIntPreferences(font, "size", fontSize); // NON-NLS

      Preferences draw = p.node("drawing"); // NON-NLS
      BundlePreferences.putBooleanPreferences(draw, "once", drawOnlyOnce); // NON-NLS
      BundlePreferences.putIntPreferences(draw, "width", lineWidth); // NON-NLS
      BundlePreferences.putIntPreferences(draw, "color", lineColor.getRGB()); // NON-NLS

      Preferences stats = p.node("statistics"); // NON-NLS
      BundlePreferences.putBooleanPreferences(stats, "basic", basicStatistics); // NON-NLS
      BundlePreferences.putBooleanPreferences(stats, "more", moreStatistics); // NON-NLS
      StringBuilder buffer = new StringBuilder();
      writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[0]);
      for (int i = 1; i < ImageStatistics.ALL_MEASUREMENTS.length; i++) {
        buffer.append(",");
        writeLabels(buffer, ImageStatistics.ALL_MEASUREMENTS[i]);
      }
      BundlePreferences.putStringPreferences(stats, "label", buffer.toString()); // NON-NLS

      // Forget the Selection Graphic
      for (int i = 1; i < MeasureToolBar.measureGraphicList.size(); i++) {
        Graphic graph = MeasureToolBar.measureGraphicList.get(i);
        List<Measurement> list = graph.getMeasurementList();
        if (list != null && !list.isEmpty()) {
          Preferences gpref = p.node(graph.getClass().getSimpleName());
          buffer = new StringBuilder();
          writeLabels(buffer, list.get(0));
          for (int j = 1; j < list.size(); j++) {
            buffer.append(",");
            writeLabels(buffer, list.get(j));
          }
          BundlePreferences.putStringPreferences(gpref, "label", buffer.toString()); // NON-NLS
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
