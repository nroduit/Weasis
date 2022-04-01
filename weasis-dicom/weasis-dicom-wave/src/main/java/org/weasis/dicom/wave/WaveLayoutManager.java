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

public class WaveLayoutManager implements LayoutManager {

  public static final int AUTO_AMPLITUDE = -1;
  public static final double AUTO_SPEED = -1.0;
  public static final int DEFAULT_AMPLITUDE = 10;
  public static final int DEFAULT_SPEED = 25;

  private final WaveView view;
  private final WaveLayout layout;
  private final Map<Lead, LeadPanel> components;

  private Format format;
  private double speed;
  private int amplitude;

  public WaveLayoutManager(WaveView view, Format format, double speed, int amplitude) {
    this.view = view;
    this.components = new LinkedHashMap<>();
    this.speed = speed;
    this.amplitude = amplitude;
    this.layout = new StandardWaveLayout();
    setWaveFormat(format);
  }

  public void setSpeed(double mmPerS) {
    this.speed = mmPerS;
  }

  public void setAmplitude(int mmPerMV) {
    this.amplitude = mmPerMV;
  }

  public void setWaveFormat(Format format) {
    this.format = format;
  }

  public List<LeadPanel> getSortedComponents() {
    return layout.getSortedComponents(format, components);
  }

  public double getSpeed() {
    return speed;
  }

  public int getAmplitude() {
    return amplitude;
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

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    double ratio = Toolkit.getDefaultToolkit().getScreenResolution() / 25.4 * view.getZoomRatio();
    int width = (int) (speed * ratio);
    int height = (int) (amplitude * ratio);
    double sec = (format == Format.DEFAULT) ? view.getSeconds() : 10;

    int layoutHeight =
        format.getYlayoutSize() <= 0 ? view.getChannelNumber() : format.getYlayoutSize();
    if (amplitude == AUTO_AMPLITUDE) {
      double h = parent.getParent().getParent().getSize().height * view.getZoomRatio();
      int auto = (int) (h / view.getMvCells() / layoutHeight);
      height = max((int) ratio, min(auto, (int) (DEFAULT_AMPLITUDE * ratio)));
    }

    if (speed == AUTO_SPEED) {
      double w = parent.getParent().getParent().getSize().width * view.getZoomRatio();
      int auto = (int) (w / sec);
      width = max((int) ratio, min(auto, (int) (DEFAULT_SPEED * ratio)));
    }

    Insets insets = parent.getInsets();
    return new Dimension(
        insets.left + insets.right + (int) (sec * width),
        insets.top + insets.bottom + layoutHeight * view.getMvCells() * height);
  }

  @Override
  public void layoutContainer(Container parent) {
    Insets insets = parent.getInsets();
    int maxWidth = parent.getWidth() - insets.left - insets.right;
    int maxHeight = parent.getHeight() - insets.top - insets.bottom;

    List<LeadPanel> ordered = getSortedComponents();
    for (Component c : parent.getComponents()) {
      c.setVisible(ordered.contains(c));
    }

    int xLayoutSize = format.getXlayoutSize() <= 0 ? 1 : format.getXlayoutSize();
    int yLayoutSize = format.getYlayoutSize() <= 0 ? ordered.size() : format.getYlayoutSize();
    int w = maxWidth / xLayoutSize;
    int h = maxHeight / yLayoutSize;

    int offsetX = 0;
    int offsetY = 0;

    for (int i = 0; i < ordered.size(); i++) {
      LeadPanel p = ordered.get(i);
      if (p != null) {
        int mWidth = p.getChannels().getLead().equals(Lead.RHYTHM) ? maxWidth : w;
        p.setPreferredSize(new Dimension(mWidth, h));
        p.setBounds(offsetX, offsetY, mWidth, h);
      }
      if ((i + 1) % xLayoutSize == 0) {
        offsetX = 0;
        offsetY += h;
      } else {
        offsetX += w;
      }
    }
  }
}
