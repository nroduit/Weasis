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
import java.awt.Graphics2D;
import java.awt.Window;
import java.util.concurrent.atomic.AtomicReference;
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
  private final AtomicReference<Float> alpha;

  public ColorLayerUI(final JXLayer<JComponent> comp, RootPaneContainer parent) {
    if (parent == null || comp == null) {
      throw new IllegalArgumentException();
    }
    this.parent = parent;
    this.xlayer = comp;
    this.alpha = new AtomicReference<>(0.0f);
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
    GuiUtils.showCenterScreen(window, container);
    if (layer != null) {
      layer.hideUI();
    }
  }

  @Override
  protected void paintLayer(final Graphics2D g, final JXLayer<? extends JComponent> comp) {
    super.paintLayer(g, this.xlayer);
    g.setColor(comp.getBackground());
    g.setComposite(AlphaComposite.SrcOver.derive(alpha.get() * MAX_ALPHA));
    g.fillRect(0, 0, comp.getWidth(), comp.getHeight());
  }

  public synchronized void showUI() {
    alpha.set(0.0f);
    final Timer timer = new Timer(3, null);
    timer.setRepeats(true);
    timer.addActionListener(
        _ -> {
          float newAlpha = Math.min(alpha.get() + 0.1f, 1.0f);
          alpha.set(newAlpha);
          if (newAlpha >= 1.0f) {
            timer.stop();
          }
          xlayer.repaint();
        });
    this.xlayer.repaint();
    timer.start();
  }

  public synchronized void hideUI() {
    alpha.set(1.0f);
    final Timer timer = new Timer(3, null);
    timer.setRepeats(true);
    timer.addActionListener(
        _ -> {
          float newAlpha = Math.max(alpha.get() - 0.1f, 0.0f);
          alpha.set(newAlpha);
          if (newAlpha <= 0.0f) {
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
