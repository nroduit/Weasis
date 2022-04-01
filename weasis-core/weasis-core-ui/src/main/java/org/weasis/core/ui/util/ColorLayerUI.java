/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.util;

import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;
import org.jdesktop.jxlayer.JXLayer;
import org.jdesktop.jxlayer.plaf.AbstractLayerUI;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;

public class ColorLayerUI extends AbstractLayerUI<JComponent> {

  protected static final float MAX_ALPHA = 0.75f;

  private final RootPaneContainer parent;
  protected final JXLayer<JComponent> xlayer;
  protected volatile float alpha;

  public ColorLayerUI(final JXLayer<JComponent> comp, RootPaneContainer parent) {
    if (parent == null || comp == null) {
      throw new IllegalArgumentException();
    }
    this.parent = parent;
    this.xlayer = comp;
  }

  public static ColorLayerUI createTransparentLayerUI(RootPaneContainer parent) {
    if (parent != null && parent.getContentPane() instanceof JComponent jComponent) {
      JXLayer<JComponent> layer = new JXLayer<>(jComponent);
      final ColorLayerUI ui = new ColorLayerUI(layer, parent);
      layer.setUI(ui);
      parent.setContentPane(layer);
      ui.showUI();
      return ui;
    }
    return null;
  }

  public static ColorLayerUI createTransparentLayerUI(Component parent) {
    if (parent != null) {
      return createTransparentLayerUI(WinUtil.getRootPaneContainer(parent));
    }
    return null;
  }

  public static void showCenterScreen(Window window, ColorLayerUI layer) {
    Container container = getContentPane(layer);
    if (container == null) {
      GuiUtils.showCenterScreen(window);
    } else {
      Dimension sSize = container.getSize();
      Dimension wSize = window.getSize();
      Point p = container.getLocationOnScreen();
      window.setLocation(
          p.x + ((sSize.width - wSize.width) / 2), p.y + ((sSize.height - wSize.height) / 2));
      window.setVisible(true);

      layer.hideUI();
    }
  }

  @Override
  protected void paintLayer(final Graphics2D g, final JXLayer<? extends JComponent> comp) {
    super.paintLayer(g, this.xlayer);
    g.setColor(comp.getBackground());
    g.setComposite(AlphaComposite.SrcOver.derive(alpha * MAX_ALPHA));
    g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
  }

  public synchronized void showUI() {
    this.alpha = 0.0f;
    final Timer timer = new Timer(3, null);
    timer.setRepeats(true);
    timer.addActionListener(
        e -> {
          alpha = Math.min(alpha + 0.1f, 1.0F);
          if (alpha >= 1.0) {
            timer.stop();
          }
          xlayer.repaint();
        });
    this.xlayer.repaint();
    timer.start();
  }

  public synchronized void hideUI() {
    this.alpha = 1.0f;
    final Timer timer = new Timer(3, null);
    timer.setRepeats(true);
    timer.addActionListener(
        e -> {
          alpha = Math.max(alpha - 0.1f, 0.0F);
          if (alpha <= 0.0) {
            timer.stop();
            parent.setContentPane(xlayer.getView());
            return;
          }
          xlayer.repaint();
        });
    this.xlayer.repaint();
    timer.start();
  }

  public static Container getContentPane(ColorLayerUI layer) {
    if (layer != null && layer.parent != null) {
      return layer.parent.getRootPane();
    }
    return null;
  }
}
