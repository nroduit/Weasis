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
import java.util.prefs.Preferences;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.ui.editor.image.MeasureToolBar;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.bean.Measurement;

public class ViewSetting {
  public static final String PREFERENCE_NODE = "view2d.default";
  public static final FontItem DEFAULT_FONT = FontItem.SMALL_SEMIBOLD;

  private static final String NODE_FONT = "font"; // NON-NLS
  private static final String NODE_DRAWING = "drawing"; // NON-NLS
  private static final String NODE_STATISTICS = "statistics"; // NON-NLS
  private static final String KEY_LABEL = "label"; // NON-NLS

  private FontItem fontItem;
  private boolean drawOnlyOnce;
  private boolean filled;
  private float fillOpacity;
  private Color lineColor;

  private final SpinnerNumberModel spinnerModel = new SpinnerNumberModel(1, 1, 8, 1);
  private boolean basicStatistics;
  private boolean moreStatistics;
  private final List<Monitor> monitors = new ArrayList<>(2);

  public ViewSetting() {
    this.fontItem = DEFAULT_FONT;
    this.drawOnlyOnce = true;
    this.filled = Graphic.DEFAULT_FILLED;
    this.fillOpacity = Graphic.DEFAULT_FILL_OPACITY;
    this.lineColor = Graphic.DEFAULT_COLOR;
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
    if (prefs == null) {
      return;
    }

    Preferences rootNode = prefs.node(PREFERENCE_NODE);
    applyFontPreferences(rootNode.node(NODE_FONT));
    applyDrawingPreferences(rootNode.node(NODE_DRAWING));
    applyStatisticsPreferences(rootNode.node(NODE_STATISTICS));
    applyMeasurementLabels(rootNode);
  }

  private void applyFontPreferences(Preferences fontNode) {
    String fontKey = fontNode.get("type", DEFAULT_FONT.getKey()); // NON-NLS
    this.fontItem = FontItem.getFontItem(fontKey, DEFAULT_FONT);
  }

  private void applyDrawingPreferences(Preferences drawNode) {
    drawOnlyOnce = drawNode.getBoolean("once", drawOnlyOnce); // NON-NLS
    int lineWidth = drawNode.getInt("width", Graphic.DEFAULT_LINE_THICKNESS.intValue()); // NON-NLS
    setLineWidth(lineWidth);
    int rgb = drawNode.getInt("color", Graphic.DEFAULT_COLOR.getRGB()); // NON-NLS
    lineColor = new Color(rgb, true);
    filled = drawNode.getBoolean("fill", Graphic.DEFAULT_FILLED); // NON-NLS
    fillOpacity = drawNode.getFloat("fillOpacity", Graphic.DEFAULT_FILL_OPACITY); // NON-NLS
  }

  private void applyStatisticsPreferences(Preferences statsNode) {
    basicStatistics = statsNode.getBoolean("basic", basicStatistics); // NON-NLS
    moreStatistics = statsNode.getBoolean("more", moreStatistics); // NON-NLS

    updateBasicStatisticsComputation(basicStatistics);
    updateAdvancedStatisticsComputation(moreStatistics);

    String labels = statsNode.get(KEY_LABEL, null);
    if (labels != null) {
      parseMeasurementLabels(labels, List.of(ImageStatistics.ALL_MEASUREMENTS));
    }
  }

  private void updateBasicStatisticsComputation(boolean enabled) {
    Stream.of(
            ImageStatistics.IMAGE_PIXELS,
            ImageStatistics.IMAGE_MIN,
            ImageStatistics.IMAGE_MAX,
            ImageStatistics.IMAGE_MEAN)
        .forEach(stat -> stat.setComputed(enabled));
  }

  private void updateAdvancedStatisticsComputation(boolean enabled) {
    Stream.of(
            ImageStatistics.IMAGE_MEDIAN,
            ImageStatistics.IMAGE_STD,
            ImageStatistics.IMAGE_SKEW,
            ImageStatistics.IMAGE_KURTOSIS,
            ImageStatistics.IMAGE_ENTROPY)
        .forEach(stat -> stat.setComputed(enabled));
  }

  private void applyMeasurementLabels(Preferences rootNode) {
    // Skip the Selection Graphic (index 0)
    List<Graphic> graphicList = MeasureToolBar.getMeasureGraphicList();
    IntStream.range(1, graphicList.size())
        .mapToObj(graphicList::get)
        .forEach(graph -> applyGraphicLabels(rootNode, graph));
  }

  private void applyGraphicLabels(Preferences rootNode, Graphic graph) {
    List<Measurement> measurements = graph.getMeasurementList();
    if (measurements == null || measurements.isEmpty()) {
      return;
    }

    Preferences graphicNode = rootNode.node(graph.getClass().getSimpleName());
    String labels = graphicNode.get(KEY_LABEL, null);
    if (labels != null) {
      parseMeasurementLabels(labels, measurements);
    }
  }

  private void parseMeasurementLabels(String labelsString, Iterable<Measurement> measurements) {
    for (String item : labelsString.split(",")) {
      String[] parts = item.split(":", 2);
      if (parts.length == 2) {
        String measurementId = parts[0];
        boolean labelEnabled = isTrueValue(parts[1]);
        updateMeasurementLabel(measurements, measurementId, labelEnabled);
      }
    }
  }

  private void updateMeasurementLabel(
      Iterable<Measurement> measurements, String id, boolean enabled) {
    for (Measurement measurement : measurements) {
      if (id.equals(String.valueOf(measurement.getId()))) {
        measurement.setGraphicLabel(enabled);
        break;
      }
    }
  }

  public List<Monitor> getMonitors() {
    return new ArrayList<>(monitors);
  }

  public Monitor getMonitor(GraphicsDevice device) {
    return monitors.stream().filter(m -> m.getGraphicsDevice() == device).findFirst().orElse(null);
  }

  public void initMonitors(WProperties localPersistence) {
    monitors.clear();
    GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();

    Stream.of(ge.getScreenDevices())
        .filter(this::isValidRasterScreen)
        .map(device -> createMonitor(device, localPersistence))
        .forEach(monitors::add);
  }

  private boolean isValidRasterScreen(GraphicsDevice device) {
    GraphicsConfiguration config = device.getDefaultConfiguration();
    return config != null && device.getType() == GraphicsDevice.TYPE_RASTER_SCREEN;
  }

  private Monitor createMonitor(GraphicsDevice device, WProperties localPersistence) {
    Monitor monitor = new Monitor(device);
    String screenKey = buildScreenItem(monitor);
    double pitch = localPersistence.getDoubleProperty(screenKey, 0.0);
    monitor.setRealScaleFactor(pitch);
    return monitor;
  }

  static String buildScreenItem(Monitor monitor) {
    Rectangle bounds = monitor.getBounds();
    return "screen.%s.%dx%d.pitch"
        .formatted(monitor.getMonitorID(), bounds.width, bounds.height); // NON-NLS
  }

  private static boolean isTrueValue(String val) {
    return "1".equals(val.trim());
  }

  private static String formatMeasurementLabel(Measurement measurement) {
    return "%d:%s"
        .formatted(measurement.getId(), measurement.getGraphicLabel() ? "1" : "0"); // NON-NLS
  }

  public void savePreferences(Preferences prefs) {
    if (prefs == null) {
      return;
    }

    Preferences rootNode = prefs.node(PREFERENCE_NODE);
    saveFontPreferences(rootNode.node(NODE_FONT));
    saveDrawingPreferences(rootNode.node(NODE_DRAWING));
    saveStatisticsPreferences(rootNode.node(NODE_STATISTICS));
    saveMeasurementLabels(rootNode);
  }

  private void saveFontPreferences(Preferences fontNode) {
    BundlePreferences.putStringPreferences(fontNode, "type", getFontItem().getKey()); // NON-NLS
  }

  private void saveDrawingPreferences(Preferences drawNode) {
    BundlePreferences.putBooleanPreferences(drawNode, "once", drawOnlyOnce); // NON-NLS
    BundlePreferences.putIntPreferences(drawNode, "width", getLineWidth()); // NON-NLS
    BundlePreferences.putIntPreferences(drawNode, "color", lineColor.getRGB()); // NON-NLS
    BundlePreferences.putBooleanPreferences(drawNode, "fill", filled); // NON-NLS
    BundlePreferences.putFloatPreferences(drawNode, "fillOpacity", fillOpacity); // NON-NLS
  }

  private void saveStatisticsPreferences(Preferences statsNode) {
    BundlePreferences.putBooleanPreferences(statsNode, "basic", basicStatistics); // NON-NLS
    BundlePreferences.putBooleanPreferences(statsNode, "more", moreStatistics); // NON-NLS

    String labels = serializeMeasurementLabels(List.of(ImageStatistics.ALL_MEASUREMENTS));
    BundlePreferences.putStringPreferences(statsNode, KEY_LABEL, labels);
  }

  private void saveMeasurementLabels(Preferences rootNode) {
    // Skip the Selection Graphic (index 0)
    List<Graphic> graphicList = MeasureToolBar.getMeasureGraphicList();
    IntStream.range(1, graphicList.size())
        .mapToObj(graphicList::get)
        .forEach(graph -> saveGraphicLabels(rootNode, graph));
  }

  private void saveGraphicLabels(Preferences rootNode, Graphic graph) {
    List<Measurement> measurements = graph.getMeasurementList();
    if (measurements == null || measurements.isEmpty()) {
      return;
    }

    Preferences graphicNode = rootNode.node(graph.getClass().getSimpleName());
    String labels = serializeMeasurementLabels(measurements);
    BundlePreferences.putStringPreferences(graphicNode, KEY_LABEL, labels);
  }

  private String serializeMeasurementLabels(Iterable<Measurement> measurements) {
    List<String> labelStrings = new ArrayList<>();
    for (Measurement measurement : measurements) {
      labelStrings.add(formatMeasurementLabel(measurement));
    }
    return String.join(",", labelStrings);
  }

  public void setFontItem(FontItem fontItem) {
    this.fontItem = fontItem == null ? DEFAULT_FONT : fontItem;
  }

  public Font getFont() {
    return getFontItem().getFont();
  }

  public FontItem getFontItem() {
    return fontItem == null ? DEFAULT_FONT : fontItem;
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
    spinnerModel.setValue(Math.clamp(lineWidth, 1, 8));
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
