/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Toolkit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays the leads out on the paper: columns of {@link Format#getColumnSeconds()} seconds at the
 * paper speed, rhythm strips over a full row, superimposed leads over the whole area. The width of
 * a column is its calibration gutter plus its seconds at the current pixels per second, so the time
 * scale is the same in every lead.
 */
public class WaveLayoutManager implements LayoutManager {

  public static final int AUTO_AMPLITUDE = -1;
  public static final double AUTO_SPEED = -1.0;
  public static final int DEFAULT_AMPLITUDE = 10;
  public static final int DEFAULT_SPEED = 25;

  /** Superimposed leads (a single beat, usually) may fill the viewport up to these scales. */
  private static final int SUPERIMPOSED_AMPLITUDE = 40;

  private static final int SUPERIMPOSED_SPEED = 100;

  private final WaveView view;
  private final WaveLayout layout;
  private final Map<Lead, LeadPanel> components;

  private double speed;
  private int amplitude;
  private double pixelsPerSecond;

  public WaveLayoutManager(WaveView view, double speed, int amplitude) {
    this.view = view;
    this.components = new LinkedHashMap<>();
    this.speed = speed;
    this.amplitude = amplitude;
    this.layout = new StandardWaveLayout();
  }

  public void setSpeed(double mmPerS) {
    this.speed = mmPerS;
  }

  public void setAmplitude(int mmPerMV) {
    this.amplitude = mmPerMV;
  }

  public List<LeadPanel> getSortedComponents() {
    return layout.getSortedComponents(view.getCurrentFormat(), view.isCabrera(), components);
  }

  public double getSpeed() {
    return speed;
  }

  public int getAmplitude() {
    return amplitude;
  }

  /** Pixels of one second of signal at the current speed and zoom (after the last layout). */
  public double getPixelsPerSecond() {
    return pixelsPerSecond;
  }

  /** Screen pixels per millimetre at a zoom factor. */
  public static double pixelPerMm(double zoom) {
    return Toolkit.getDefaultToolkit().getScreenResolution() / 25.4 * zoom;
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    if (comp instanceof LeadPanel pane) {
      components.put(pane.getChannels().getLead(), pane);
    }
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    components.entrySet().removeIf(e -> e.getValue() == comp);
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return preferredLayoutSize(parent);
  }

  /**
   * The seconds of signal spanned by a row: 10 s for the paper formats, the recording otherwise.
   */
  private double rowSeconds(Format format) {
    double column = format.getColumnSeconds();
    double seconds = column < 0 ? view.getSeconds() : 10;
    return min(seconds, view.getSeconds());
  }

  /** The number of rows the ordered leads take, rhythm strips taking a row each. */
  private static int rows(List<LeadPanel> ordered, int columns) {
    int rows = 0;
    int col = 0;
    for (LeadPanel p : ordered) {
      if (p.getChannels().getLead().isRhythm()) {
        if (col > 0) {
          rows++;
          col = 0;
        }
        rows++;
      } else {
        col++;
        if (col == columns) {
          col = 0;
          rows++;
        }
      }
    }
    return col > 0 ? rows + 1 : max(1, rows);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    double ratio = pixelPerMm(view.getZoomRatio());
    Format format = view.getCurrentFormat();
    List<LeadPanel> ordered = getSortedComponents();
    int columns = format.isSuperimposed() ? 1 : max(1, format.getXlayoutSize());
    int rowCount = format.isSuperimposed() ? 1 : rows(ordered, columns);
    double seconds = rowSeconds(format);
    int gutter = view.getGutterWidth();
    Dimension viewport = parent.getParent().getParent().getSize();

    int maxAmplitude = format.isSuperimposed() ? SUPERIMPOSED_AMPLITUDE : DEFAULT_AMPLITUDE;
    int maxSpeed = format.isSuperimposed() ? SUPERIMPOSED_SPEED : DEFAULT_SPEED;
    int height = (int) (amplitude * ratio);
    if (amplitude == AUTO_AMPLITUDE) {
      double h = viewport.height * view.getZoomRatio() - 4;
      int auto = (int) (h / view.getMvCells() / rowCount);
      height = max((int) ratio, min(auto, (int) (maxAmplitude * ratio)));
    }

    double pps = speed * ratio;
    if (speed == AUTO_SPEED) {
      // The paper speed, or slower so that the whole row fits the viewport
      double w = viewport.width * view.getZoomRatio() - columns * gutter - 4;
      pps = seconds <= 0 ? 0 : min(w / seconds, maxSpeed * ratio);
    }
    this.pixelsPerSecond = max(0, pps);

    Insets insets = parent.getInsets();
    return new Dimension(
        insets.left + insets.right + columns * gutter + max(1, (int) Math.round(seconds * pps)),
        insets.top + insets.bottom + rowCount * view.getMvCells() * height);
  }

  @Override
  public void layoutContainer(Container parent) {
    Insets insets = parent.getInsets();
    int maxWidth = parent.getWidth() - insets.left - insets.right;
    int maxHeight = parent.getHeight() - insets.top - insets.bottom;

    List<LeadPanel> ordered = getSortedComponents();
    Format format = view.getCurrentFormat();
    Component[] children = parent.getComponents();
    for (Component c : children) {
      c.setVisible(ordered.contains(c));
      if (c instanceof LeadPanel p) {
        p.setOverlayIndex(format.isSuperimposed() ? ordered.indexOf(p) : -1);
        p.setOverlayBase(false);
      }
    }

    if (format.isSuperimposed()) {
      // Children paint from the last to the first: the last visible one is the base
      LeadPanel base = null;
      for (Component c : children) {
        if (c instanceof LeadPanel p && p.isVisible()) {
          base = p;
        }
      }
      for (LeadPanel p : ordered) {
        p.setOverlayBase(p == base);
        p.setPreferredSize(new Dimension(maxWidth, maxHeight));
        p.setBounds(insets.left, insets.top, maxWidth, maxHeight);
      }
      return;
    }

    int columns = max(1, format.getXlayoutSize());
    int rowCount = rows(ordered, columns);
    int w = maxWidth / columns;
    int h = maxHeight / rowCount;
    int gutter = view.getGutterWidth();
    int rhythmWidth =
        min(maxWidth, gutter + (int) Math.round(rowSeconds(format) * pixelsPerSecond));

    int col = 0;
    int row = 0;
    for (LeadPanel p : ordered) {
      if (p.getChannels().getLead().isRhythm()) {
        if (col > 0) {
          row++;
          col = 0;
        }
        p.setPreferredSize(new Dimension(rhythmWidth, h));
        p.setBounds(insets.left, insets.top + row * h, rhythmWidth, h);
        row++;
      } else {
        p.setPreferredSize(new Dimension(w, h));
        p.setBounds(insets.left + col * w, insets.top + row * h, w, h);
        col++;
        if (col == columns) {
          col = 0;
          row++;
        }
      }
    }
  }
}
