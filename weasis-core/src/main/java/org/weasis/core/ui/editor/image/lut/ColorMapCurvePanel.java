/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.lut;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.opencv.op.lut.colormap.ColorMap;
import org.weasis.opencv.op.lut.colormap.ColorMapDomain;
import org.weasis.opencv.op.lut.colormap.ColorMapEdits;
import org.weasis.opencv.op.lut.colormap.ColorMapSampler;
import org.weasis.opencv.op.lut.colormap.ColorStop;
import org.weasis.opencv.op.lut.colormap.Rgba;

/**
 * Direct manipulation of a map's stops: the color strip along the bottom, the alpha curve above it,
 * one node per stop. Drag moves a stop (vertically its alpha), double-click adds one at the sampled
 * color, right-click removes one.
 */
public class ColorMapCurvePanel extends JPanel {

  /** Receives every edit; {@code commit} is false while a drag is in progress. */
  public interface Listener {
    void mapEdited(ColorMap map, boolean commit);

    void stopSelected(int index);
  }

  private static final int PAD_LEFT = 36;
  private static final int PAD_RIGHT = 12;
  private static final int PAD_TOP = 12;
  private static final int PAD_BOTTOM = 26;
  private static final int STRIP_HEIGHT = 16;
  private static final int NODE_RADIUS = 5;
  private static final int HIT_RADIUS = 7;

  private final transient Listener listener;
  private transient ColorMap map;
  private transient ColorMap dragBase;
  private double[] histogram;
  private int selected = -1;
  private int hover = -1;
  private int dragging = -1;
  private boolean editable = true;

  public ColorMapCurvePanel(Listener listener) {
    this.listener = listener;
    setOpaque(true);
    setPreferredSize(new Dimension(GuiUtils.getScaleLength(420), GuiUtils.getScaleLength(200)));
    MouseAdapter mouse =
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            onPressed(e);
          }

          @Override
          public void mouseDragged(MouseEvent e) {
            onDragged(e);
          }

          @Override
          public void mouseReleased(MouseEvent e) {
            onReleased();
          }

          @Override
          public void mouseMoved(MouseEvent e) {
            int idx = hitTest(e.getPoint());
            if (idx != hover) {
              hover = idx;
              repaint();
            }
          }

          @Override
          public void mouseExited(MouseEvent e) {
            hover = -1;
            repaint();
          }
        };
    addMouseListener(mouse);
    addMouseMotionListener(mouse);
  }

  public void setMap(ColorMap map) {
    this.map = map;
    if (map != null && selected >= map.stops().size()) {
      selected = -1;
    }
    repaint();
  }

  /** Bin counts spread over the domain range, or null to draw none. */
  public void setHistogram(double[] bins) {
    this.histogram = bins;
    repaint();
  }

  public void setEditable(boolean editable) {
    this.editable = editable;
  }

  public void setSelectedStop(int index) {
    selected = index;
    repaint();
  }

  public int getSelectedStop() {
    return selected;
  }

  /** Removes the selected stop, if any. */
  public void removeSelected() {
    if (editable && map != null && selected >= 0 && map.stops().size() > 1) {
      ColorMap edited = ColorMapEdits.withoutStop(map, selected);
      selected = -1;
      listener.mapEdited(edited, true);
    }
  }

  // ── geometry ──

  private int plotX() {
    return PAD_LEFT;
  }

  private int plotY() {
    return PAD_TOP;
  }

  private int plotW() {
    return Math.max(1, getWidth() - PAD_LEFT - PAD_RIGHT);
  }

  private int plotH() {
    return Math.max(1, getHeight() - PAD_TOP - PAD_BOTTOM - STRIP_HEIGHT);
  }

  private int xFor(double position) {
    return (int) Math.round(plotX() + map.domain().normalize(position) * plotW());
  }

  private int yFor(double alpha) {
    return (int) Math.round(plotY() + (1 - Math.clamp(alpha, 0.0, 1.0)) * plotH());
  }

  private double positionForX(int x) {
    ColorMapDomain d = map.domain();
    double f = (x - plotX()) / (double) plotW();
    return Math.clamp(d.denormalize(f), d.min(), d.max());
  }

  private double alphaForY(int y) {
    return Math.clamp(1 - (y - plotY()) / (double) plotH(), 0.0, 1.0);
  }

  private static double nodeAlpha(ColorStop stop) {
    return stop.hasAlpha() ? stop.alpha() : 1.0;
  }

  private int hitTest(Point p) {
    if (map == null) {
      return -1;
    }
    List<ColorStop> stops = map.stops();
    for (int i = stops.size() - 1; i >= 0; i--) {
      ColorStop stop = stops.get(i);
      if (Math.abs(p.x - xFor(stop.position())) <= HIT_RADIUS
          && Math.abs(p.y - yFor(nodeAlpha(stop))) <= HIT_RADIUS) {
        return i;
      }
    }
    return -1;
  }

  // ── interaction ──

  private void onPressed(MouseEvent e) {
    requestFocusInWindow();
    if (map == null) {
      return;
    }
    int idx = hitTest(e.getPoint());
    if (idx >= 0) {
      selected = idx;
      listener.stopSelected(idx);
      if (!editable) {
        return;
      }
      if (SwingUtilities.isRightMouseButton(e)) {
        removeSelected();
        return;
      }
      dragging = idx;
      dragBase = map;
    } else if (editable && e.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(e)) {
      addStopAt(e.getPoint());
    }
    repaint();
  }

  private void onDragged(MouseEvent e) {
    if (dragging < 0 || map == null) {
      return;
    }
    ColorStop stop = map.stops().get(dragging);
    double position = positionForX(e.getX());
    Float alpha = stop.hasAlpha() || map.hasAlpha() ? (float) alphaForY(e.getY()) : null;
    ColorStop moved = stop.withPosition(position).withAlpha(alpha);
    ColorMap edited = ColorMapEdits.withStop(map, dragging, moved);
    // The stop list is re-sorted: follow the moved stop to its new index.
    dragging = edited.stops().indexOf(moved);
    selected = dragging;
    map = edited;
    listener.mapEdited(edited, false);
    repaint();
  }

  private void onReleased() {
    if (dragging >= 0) {
      dragging = -1;
      if (dragBase != null && !dragBase.equals(map)) {
        listener.mapEdited(map, true);
      }
      dragBase = null;
      listener.stopSelected(selected);
    }
  }

  private void addStopAt(Point p) {
    double position = positionForX(p.x);
    Rgba sample = map.sampler().sample(position);
    Float alpha = map.hasAlpha() ? (float) alphaForY(p.y) : null;
    ColorStop added = new ColorStop(position, sample.withAlpha(1f).toColor(), alpha, null, null);
    ColorMap edited = ColorMapEdits.withStop(map, added);
    selected = edited.stops().indexOf(added);
    listener.mapEdited(edited, true);
    listener.stopSelected(selected);
  }

  // ── painting ──

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (map == null) {
      return;
    }
    Graphics2D g2 = (Graphics2D) g.create();
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    int px = plotX();
    int py = plotY();
    int pw = plotW();
    int ph = plotH();
    paintGrid(g2, px, py, pw, ph);
    paintHistogram(g2, px, py, pw, ph);
    paintStrip(g2, px, py + ph, pw);
    paintCurve(g2, py + ph);
    paintNodes(g2);
    paintLabels(g2, px, py, pw, ph);
    g2.dispose();
  }

  private void paintGrid(Graphics2D g2, int px, int py, int pw, int ph) {
    g2.setColor(getBackground().darker());
    g2.setStroke(new BasicStroke(1f));
    for (int i = 0; i <= 4; i++) {
      int y = py + i * ph / 4;
      g2.drawLine(px, y, px + pw, y);
    }
  }

  // Log-scaled so the few dense bins do not flatten the rest.
  private void paintHistogram(Graphics2D g2, int px, int py, int pw, int ph) {
    if (histogram == null || histogram.length == 0) {
      return;
    }
    double max = 0;
    for (double v : histogram) {
      max = Math.max(max, v);
    }
    if (max <= 0) {
      return;
    }
    Color fg = getForeground();
    g2.setColor(new Color(fg.getRed(), fg.getGreen(), fg.getBlue(), 60));
    double logMax = Math.log1p(max);
    int n = histogram.length;
    for (int i = 0; i < n; i++) {
      int x0 = px + (int) Math.round((double) i * pw / n);
      int x1 = px + (int) Math.round((double) (i + 1) * pw / n);
      int h = (int) Math.round(Math.log1p(histogram[i]) / logMax * ph);
      g2.fillRect(x0, py + ph - h, Math.max(1, x1 - x0), h);
    }
  }

  private void paintStrip(Graphics2D g2, int px, int top, int pw) {
    ColorMapSampler sampler = map.sampler();
    ColorMapDomain d = map.domain();
    for (int i = 0; i < pw; i++) {
      Rgba c = sampler.sample(d.denormalize(i / (double) pw));
      g2.setColor(c.withAlpha(1f).toColor());
      g2.drawLine(px + i, top, px + i, top + STRIP_HEIGHT);
    }
    g2.setColor(getForeground().darker());
    g2.drawRect(px, top, pw, STRIP_HEIGHT);
  }

  private void paintCurve(Graphics2D g2, int baseline) {
    List<ColorStop> stops = map.stops();
    Path2D line = new Path2D.Float();
    Path2D fill = new Path2D.Float();
    for (int i = 0; i < stops.size(); i++) {
      int x = xFor(stops.get(i).position());
      int y = yFor(nodeAlpha(stops.get(i)));
      if (i == 0) {
        fill.moveTo(x, baseline);
        fill.lineTo(x, y);
        line.moveTo(x, y);
      } else {
        fill.lineTo(x, y);
        line.lineTo(x, y);
      }
    }
    int lastX = xFor(stops.getLast().position());
    fill.lineTo(lastX, baseline);
    fill.closePath();
    Color accent = getForeground();
    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40));
    g2.fill(fill);
    g2.setColor(accent);
    g2.setStroke(new BasicStroke(1.6f));
    g2.draw(line);
  }

  private void paintNodes(Graphics2D g2) {
    List<ColorStop> stops = map.stops();
    for (int i = 0; i < stops.size(); i++) {
      ColorStop stop = stops.get(i);
      int x = xFor(stop.position());
      int y = yFor(nodeAlpha(stop));
      int r = (i == hover || i == selected) ? NODE_RADIUS + 2 : NODE_RADIUS;
      g2.setColor(stop.hasColor() ? stop.color() : Color.LIGHT_GRAY);
      g2.fillOval(x - r, y - r, 2 * r, 2 * r);
      g2.setStroke(new BasicStroke(i == selected ? 2f : 1f));
      g2.setColor(i == selected ? getForeground() : Color.DARK_GRAY);
      g2.drawOval(x - r, y - r, 2 * r, 2 * r);
    }
  }

  private void paintLabels(Graphics2D g2, int px, int py, int pw, int ph) {
    g2.setColor(getForeground());
    g2.setFont(getFont().deriveFont(Font.PLAIN, GuiUtils.getScaleLength(10)));
    int ascent = g2.getFontMetrics().getAscent();
    g2.drawString("1", px - GuiUtils.getScaleLength(12), py + ascent); // NON-NLS
    g2.drawString("0", px - GuiUtils.getScaleLength(12), py + ph); // NON-NLS
    ColorMapDomain d = map.domain();
    String unit = d.unit() != null ? " " + d.unit() : "";
    String min = format(d.min()) + unit;
    String max = format(d.max()) + unit;
    int baseY = py + ph + STRIP_HEIGHT + ascent + GuiUtils.getScaleLength(2);
    g2.drawString(min, px, baseY);
    g2.drawString(max, px + pw - g2.getFontMetrics().stringWidth(max), baseY);
  }

  static String format(double value) {
    return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.3g", value);
  }
}
