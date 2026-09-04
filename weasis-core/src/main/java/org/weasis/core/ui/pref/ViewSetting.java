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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.GraphicRegistry;
import org.weasis.core.ui.model.graphic.ToolCategory;
import org.weasis.core.ui.model.graphic.profile.MeasurementProfiles;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.Measurement;

public class ViewSetting {
  public static final String PREFERENCE_NODE = "view2d.default";
  public static final FontItem DEFAULT_FONT = FontItem.SMALL_SEMIBOLD;
  public static final int DEFAULT_DECIMALS = MeasureFormat.AUTO;

  /**
   * Not "decimals": development builds of 4.8 stored a fixed 2 under that name before the automatic
   * mode existed, and no release ever wrote it, so the stale value is simply not read.
   */
  private static final String DECIMALS_KEY = "valueDecimals"; // NON-NLS

  public static final double DEFAULT_CIRCLE_RADIUS = 5.0;

  /**
   * Color of new graphics. Cyan, with the outline, keeps the best worst-case contrast over
   * grayscale images and stays apart from the hot, PET and Doppler color maps, where the former
   * yellow merges with the image.
   */
  public static final Color DEFAULT_LINE_COLOR = Color.CYAN;

  /** Marks preferences whose line color was checked against the default of this generation. */
  private static final int COLOR_GENERATION = 2;

  private FontItem fontItem;
  private boolean drawOnlyOnce;
  private boolean filled;
  private float fillOpacity;
  private Color lineColor;

  private final SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 8, 1);
  private boolean basicStatistics;
  private int decimals = DEFAULT_DECIMALS;
  private boolean uprightByDrag;
  private boolean outline = true;
  private double circleRadius = DEFAULT_CIRCLE_RADIUS;
  private boolean moreStatistics;
  private final List<Monitor> monitors = new ArrayList<>(2);
  // Tools whose stored labels are applied; a plugin tool registered later is not among them yet
  private final Set<String> labelledTools = ConcurrentHashMap.newKeySet();

  public ViewSetting() {
    this.fontItem = DEFAULT_FONT;
    this.drawOnlyOnce = true;
    this.filled = Graphic.DEFAULT_FILLED;
    this.fillOpacity = Graphic.DEFAULT_FILL_OPACITY;
    this.lineColor = DEFAULT_LINE_COLOR;
    this.basicStatistics = true;
    this.moreStatistics = true;
    spinnerModel.addChangeListener(
        _ -> {
          if (spinnerModel.getValue() instanceof Integer intVal) {
            setLineWidth(intVal);
            MeasureTool.updateMeasureProperties();
          }
        });
  }

  public void applyPreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
      Preferences font = p.node("font"); // NON-NLS
      String fontKey = font.get("type", DEFAULT_FONT.getKey()); // NON-NLS
      this.fontItem = FontItem.getFontItem(fontKey, DEFAULT_FONT);

      Preferences draw = p.node("drawing"); // NON-NLS
      drawOnlyOnce = draw.getBoolean("once", drawOnlyOnce); // NON-NLS
      int lineWidth = draw.getInt("width", Graphic.DEFAULT_LINE_THICKNESS.intValue()); // NON-NLS
      setLineWidth(lineWidth);
      lineColor = storedLineColor(draw);
      outline = draw.getBoolean("outline", true); // NON-NLS
      filled = draw.getBoolean("fill", Graphic.DEFAULT_FILLED); // NON-NLS
      fillOpacity = draw.getFloat("fillOpacity", Graphic.DEFAULT_FILL_OPACITY); // NON-NLS
      setDecimals(draw.getInt(DECIMALS_KEY, DEFAULT_DECIMALS));
      uprightByDrag = draw.getBoolean("upright", false); // NON-NLS
      setCircleRadius(draw.getDouble("circleRadius", DEFAULT_CIRCLE_RADIUS)); // NON-NLS

      Preferences stats = p.node("statistics"); // NON-NLS
      basicStatistics = stats.getBoolean("basic", basicStatistics); // NON-NLS
      moreStatistics = stats.getBoolean("more", moreStatistics); // NON-NLS

      ImageStatistics.IMAGE_PIXELS.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MIN.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MAX.setComputed(basicStatistics);
      ImageStatistics.IMAGE_MEAN.setComputed(basicStatistics);

      ImageStatistics.IMAGE_MEDIAN.setComputed(moreStatistics);
      ImageStatistics.IMAGE_STD.setComputed(moreStatistics);
      ImageStatistics.IMAGE_SKEW.setComputed(moreStatistics);
      ImageStatistics.IMAGE_KURTOSIS.setComputed(moreStatistics);
      ImageStatistics.IMAGE_ENTROPY.setComputed(moreStatistics);
      ImageStatistics.IMAGE_SUM.setComputed(moreStatistics);

      applyLabels(stats.get("label", null), List.of(ImageStatistics.ALL_MEASUREMENTS)); // NON-NLS
      labelledTools.clear();
      forEachMeasurementTool((key, graph) -> applyToolLabels(p, key, graph));
    }
  }

  /**
   * Applies the stored labels of the tools registered since the preferences were applied, such as
   * those of a plugin started after the core; the tools already labelled keep the current choice.
   */
  public void applyLabelsOfNewTools(Preferences prefs) {
    if (prefs == null) {
      return;
    }
    Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
    Map<String, Graphic> added = new LinkedHashMap<>();
    forEachMeasurementTool(added::put);
    labelledTools.retainAll(added.keySet());
    added.keySet().removeAll(labelledTools);
    if (!added.isEmpty()) {
      MeasurementProfiles.changeUserSettings(
          () -> added.forEach((key, graph) -> applyToolLabels(p, key, graph)));
    }
  }

  private void applyToolLabels(Preferences parent, String key, Graphic graph) {
    labelledTools.add(key);
    Preferences node = toolNode(parent, key, graph);
    if (node != null) {
      applyLabels(node.get("label", null), graph.getMeasurementList()); // NON-NLS
    }
  }

  /**
   * The color was always saved, chosen or not, so a stored yellow from before the default changed
   * tells nothing: it follows the new default once. A yellow picked afterwards is kept.
   */
  static Color storedLineColor(Preferences draw) {
    Color stored = new Color(draw.getInt("color", DEFAULT_LINE_COLOR.getRGB()), true); // NON-NLS
    boolean formerDefault =
        draw.getInt("colorGeneration", 1) < COLOR_GENERATION // NON-NLS
            && stored.equals(Graphic.DEFAULT_COLOR);
    return formerDefault ? DEFAULT_LINE_COLOR : stored;
  }

  /** Visits the measurement tools that expose measurements, with their registry key. */
  private static void forEachMeasurementTool(BiConsumer<String, Graphic> visitor) {
    GraphicRegistry registry = GraphicRegistry.getInstance();
    for (ToolCategory category : List.of(ToolCategory.MEASURE, ToolCategory.ADVANCED)) {
      for (Graphic graph : registry.prototypes(category)) {
        List<Measurement> list = graph.getMeasurementList();
        if (list != null && !list.isEmpty()) {
          registry.keyOf(graph).ifPresent(key -> visitor.accept(key, graph));
        }
      }
    }
  }

  /** Node of a tool: its key, or the class name used before 4.8 when only that one exists. */
  private static Preferences toolNode(Preferences parent, String key, Graphic graph) {
    try {
      if (parent.nodeExists(key)) {
        return parent.node(key);
      }
      String legacy = graph.getClass().getSimpleName();
      return parent.nodeExists(legacy) ? parent.node(legacy) : null;
    } catch (BackingStoreException e) {
      return null;
    }
  }

  /** Reads a {@code token:0|1} list where the token is a measurement key or a legacy id. */
  private static void applyLabels(String labels, List<Measurement> measurements) {
    if (labels == null || measurements == null) {
      return;
    }
    for (String item : labels.split(",")) {
      String[] val = item.split(":");
      if (val.length == 2) {
        measurements.stream()
            .filter(m -> m.matches(val[0].trim()))
            .findFirst()
            .ifPresent(m -> m.setGraphicLabel(isTrueValue(val[1])));
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

    for (GraphicsDevice graphicsDevice : gd) {
      final GraphicsConfiguration config = graphicsDevice.getDefaultConfiguration();
      if (config == null || graphicsDevice.getType() != GraphicsDevice.TYPE_RASTER_SCREEN) {
        continue;
      }

      Monitor monitor = new Monitor(graphicsDevice);
      String screen = buildScreenItem(monitor);
      double pitch = GuiUtils.getUICore().getLocalPersistence().getDoubleProperty(screen, 0.0);
      monitor.setRealScaleFactor(pitch);
      monitors.add(monitor);
    }
  }

  static String buildScreenItem(Monitor monitor) {
    StringBuilder buf = new StringBuilder("screen."); // NON-NLS
    buf.append(monitor.getMonitorID());
    Rectangle b = monitor.getBounds();
    buf.append(".");
    buf.append(b.width);
    buf.append("x"); // NON-NLS
    buf.append(b.height);
    buf.append(".pitch");
    return buf.toString();
  }

  private static boolean isTrueValue(String val) {
    return "1".equals(val.trim());
  }

  private static String labels(List<Measurement> measurements) {
    return measurements.stream()
        .map(m -> m.getKey() + ":" + (m.getGraphicLabel() ? "1" : "0")) // NON-NLS
        .collect(Collectors.joining(","));
  }

  public void savePreferences(Preferences prefs) {
    if (prefs != null) {
      Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
      Preferences font = p.node("font"); // NON-NLS
      BundlePreferences.putStringPreferences(font, "type", getFontItem().getKey()); // NON-NLS

      Preferences draw = p.node("drawing"); // NON-NLS
      BundlePreferences.putBooleanPreferences(draw, "once", drawOnlyOnce); // NON-NLS
      BundlePreferences.putIntPreferences(draw, "width", getLineWidth()); // NON-NLS
      BundlePreferences.putIntPreferences(draw, "color", lineColor.getRGB()); // NON-NLS
      BundlePreferences.putIntPreferences(draw, "colorGeneration", COLOR_GENERATION); // NON-NLS
      BundlePreferences.putBooleanPreferences(draw, "outline", outline); // NON-NLS
      BundlePreferences.putBooleanPreferences(draw, "fill", filled); // NON-NLS
      BundlePreferences.putFloatPreferences(draw, "fillOpacity", fillOpacity); // NON-NLS
      BundlePreferences.putIntPreferences(draw, DECIMALS_KEY, decimals);
      BundlePreferences.putBooleanPreferences(draw, "upright", uprightByDrag); // NON-NLS
      BundlePreferences.putDoublePreferences(draw, "circleRadius", circleRadius); // NON-NLS

      Preferences stats = p.node("statistics"); // NON-NLS
      BundlePreferences.putBooleanPreferences(stats, "basic", basicStatistics); // NON-NLS
      BundlePreferences.putBooleanPreferences(stats, "more", moreStatistics); // NON-NLS
      BundlePreferences.putStringPreferences(
          stats, "label", labels(List.of(ImageStatistics.ALL_MEASUREMENTS))); // NON-NLS
      forEachMeasurementTool(
          (key, graph) ->
              BundlePreferences.putStringPreferences(
                  p.node(key), "label", labels(graph.getMeasurementList()))); // NON-NLS
    }
  }

  public void setFontItem(FontItem fontItem) {
    this.fontItem = fontItem == null ? DEFAULT_FONT : fontItem;
  }

  public Font getFont() {
    return getFontItem().getFont();
  }

  public FontItem getFontItem() {
    FontItem item = fontItem;
    return item == null ? DEFAULT_FONT : fontItem;
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
    return (int) spinnerModel.getValue();
  }

  public void setLineWidth(int lineWidth) {
    spinnerModel.setValue(Math.max(1, Math.min(8, lineWidth)));
  }

  public boolean isFilled() {
    return filled;
  }

  public void setFilled(boolean filled) {
    this.filled = filled;
  }

  public float getFillOpacity() {
    return fillOpacity;
  }

  public void setFillOpacity(float fillOpacity) {
    this.fillOpacity = fillOpacity;
  }

  /**
   * Decimals of the measured values on the image and in the table: {@link MeasureFormat#AUTO} to
   * follow the precision of each value, or a fixed number. Copies keep the full precision.
   */
  public int getDecimals() {
    return decimals;
  }

  public void setDecimals(int decimals) {
    this.decimals =
        decimals < 0 ? MeasureFormat.AUTO : Math.min(decimals, MeasureFormat.MAX_DECIMALS);
  }

  /** True when a dark halo is painted under the lines of measurements and drawings. */
  public boolean isOutline() {
    return outline;
  }

  public void setOutline(boolean outline) {
    this.outline = outline;
  }

  /** True when dragging draws an upright rectangle or ellipse and Shift the oblique one. */
  public boolean isUprightByDrag() {
    return uprightByDrag;
  }

  public void setUprightByDrag(boolean uprightByDrag) {
    this.uprightByDrag = uprightByDrag;
  }

  /** Radius in millimeters of the circle dropped by a click of the circle tool. */
  public double getCircleRadius() {
    return circleRadius;
  }

  public void setCircleRadius(double circleRadius) {
    this.circleRadius = circleRadius > 0 ? circleRadius : DEFAULT_CIRCLE_RADIUS;
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

  public void initLineWidthSpinner(JSpinner spinner) {
    spinner.setModel(spinnerModel);
    GuiUtils.formatCheckAction(spinner);
  }
}
