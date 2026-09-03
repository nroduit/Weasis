/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.dockable;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.lang.ref.WeakReference;
import javax.swing.JComponent;
import javax.swing.UIManager;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.ui.editor.image.IntensityProfile;
import org.weasis.core.ui.editor.image.IntensityProfile.Samples;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.GraphicToolContext;
import org.weasis.core.ui.model.graphic.ToolPanel;
import org.weasis.core.ui.model.graphic.imp.line.LineGraphic;
import org.weasis.core.ui.model.utils.ImageStatistics;
import org.weasis.core.ui.model.utils.MeasureFormat;
import org.weasis.core.ui.model.utils.bean.MeasureItem;

/**
 * Curve of the pixel values along the selected open measurement (line, polyline). Pointing at the
 * curve reads the value and the distance from the start of the path, and marks the pixel on the
 * image.
 */
public class IntensityProfilePanel extends JComponent implements ToolPanel {

  private static final int NO_SAMPLE = -1;

  private Samples samples = IntensityProfile.samples(null, null);
  private String valueUnit;
  private String lengthUnit;
  private double lengthRatio = 1.0;
  private MeasureFormat format = new MeasureFormat(null, null, MeasureFormat.AUTO);
  private WeakReference<ViewCanvas<?>> view = new WeakReference<>(null);
  private int pointed = NO_SAMPLE;

  public IntensityProfilePanel() {
    // The width is the one of the tool: only the height is asked for
    setPreferredSize(GuiUtils.getDimension(60, 120));
    setMinimumSize(GuiUtils.getDimension(0, 80));
    MouseAdapter pointer =
        new MouseAdapter() {
          @Override
          public void mouseMoved(MouseEvent e) {
            point(sampleAt(e.getX(), getWidth(), samples.size()));
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            mouseMoved(e);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            point(NO_SAMPLE);
          }
        };
    addMouseMotionListener(pointer);
    addMouseListener(pointer);
  }

  @Override
  public void update(GraphicToolContext context) {
    point(NO_SAMPLE);
    samples = IntensityProfile.samples(null, null);
    valueUnit = null;
    view = new WeakReference<>(context.view());
    if (context.graphic() != null
        && IntensityProfile.isOpenPath(context.graphic())
        && context.view() != null) {
      MeasurableLayer layer = context.view().getMeasurableLayer();
      Unit unit = (Unit) context.view().getActionValue(ActionW.SPATIAL_UNIT.cmd());
      samples = IntensityProfile.samples(context.graphic().getPts(), layer);
      valueUnit = layer == null ? null : layer.getPixelValueUnit();
      MeasurementsAdapter adapter =
          layer == null || !layer.hasContent() ? null : layer.getMeasurementAdapter(unit);
      lengthRatio = adapter == null ? 1.0 : adapter.calibrationRatio();
      lengthUnit = adapter == null ? null : adapter.unit();
      format = new MeasureFormat(layer, unit, MeasureTool.viewSetting.getDecimals());
    }
    repaint();
  }

  /** Empties the chart and removes the mark from the image. */
  public void clear() {
    point(NO_SAMPLE);
    samples = IntensityProfile.samples(null, null);
    repaint();
  }

  /** Marks a sample on the curve and its pixel on the image; {@link #NO_SAMPLE} clears both. */
  private void point(int index) {
    if (index == pointed) {
      return;
    }
    pointed = index;
    ViewCanvas<?> canvas = view.get();
    if (canvas != null) {
      canvas.setProbePosition(index == NO_SAMPLE ? null : samples.positions().get(index));
    }
    repaint();
  }

  private static int margin() {
    return GuiUtils.getScaleLength(6);
  }

  /** Sample under an abscissa of the panel, the curve spanning the width inside its margins. */
  static int sampleAt(int x, int width, int count) {
    int left = margin();
    int span = width - 2 * left;
    if (count < 2 || span <= 0) {
      return NO_SAMPLE;
    }
    return Math.clamp(Math.round((x - left) * (count - 1) / (float) span), 0, count - 1);
  }

  private String text(double value, boolean length) {
    MeasureItem item =
        length
            ? new MeasureItem(LineGraphic.LINE_LENGTH, value, lengthUnit)
            : new MeasureItem(ImageStatistics.IMAGE_MAX, value, valueUnit);
    String unit = length ? lengthUnit : valueUnit;
    return format.format(item) + (unit == null ? "" : " " + unit);
  }

  /** What is read under the pointer: the distance from the start of the path, then the value. */
  String readout(int index) {
    double value = samples.values()[index];
    String distance = text(samples.distanceTo(index) * lengthRatio, true);
    return distance
        + "  "
        + (Double.isNaN(value)
            ? Messages.getString("IntensityProfilePanel.outside")
            : text(value, false));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      Color fg = UIManager.getColor("Label.foreground"); // NON-NLS
      Color accent = UIManager.getColor("Component.accentColor"); // NON-NLS
      g2.setColor(fg == null ? Color.GRAY : fg);
      double[] values = samples.values();
      double min = Double.POSITIVE_INFINITY;
      double max = Double.NEGATIVE_INFINITY;
      for (double v : values) {
        if (!Double.isNaN(v)) {
          min = Math.min(min, v);
          max = Math.max(max, v);
        }
      }
      if (values.length < 2 || min > max) {
        g2.drawString(Messages.getString("IntensityProfilePanel.no_data"), 8, 20);
        return;
      }
      int left = margin();
      int top = GuiUtils.getScaleLength(14);
      int bottom = getHeight() - GuiUtils.getScaleLength(6);
      int right = getWidth() - left;
      double range = max > min ? max - min : 1;
      Path2D path = new Path2D.Double();
      boolean started = false;
      for (int i = 0; i < values.length; i++) {
        if (Double.isNaN(values[i])) {
          started = false;
          continue;
        }
        double x = left + (right - left) * i / (double) (values.length - 1);
        double y = bottom - (bottom - top) * (values[i] - min) / range;
        if (started) {
          path.lineTo(x, y);
        } else {
          path.moveTo(x, y);
          started = true;
        }
      }
      g2.drawLine(left, bottom, right, bottom);
      String label =
          pointed == NO_SAMPLE ? text(min, false) + " – " + text(max, false) : readout(pointed);
      g2.drawString(label, left, top - GuiUtils.getScaleLength(3));
      g2.setColor(accent == null ? Color.ORANGE : accent);
      g2.setStroke(new BasicStroke(1.5f));
      g2.draw(path);
      if (pointed != NO_SAMPLE) {
        paintPointer(g2, fg, left, right, top, bottom, min, range);
      }
    } finally {
      g2.dispose();
    }
  }

  private void paintPointer(
      Graphics2D g2, Color fg, int left, int right, int top, int bottom, double min, double range) {
    double x = left + (right - left) * pointed / (double) (samples.size() - 1);
    g2.setColor(fg == null ? Color.GRAY : fg);
    g2.setStroke(new BasicStroke(1f));
    g2.draw(new Line2D.Double(x, top, x, bottom));
    double value = samples.values()[pointed];
    if (!Double.isNaN(value)) {
      double y = bottom - (bottom - top) * (value - min) / range;
      double r = GuiUtils.getScaleLength(3);
      g2.fill(new Ellipse2D.Double(x - r, y - r, 2 * r, 2 * r));
    }
  }
}
