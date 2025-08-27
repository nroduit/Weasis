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

import java.awt.*;
import java.awt.event.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.RootPaneContainer;
import javax.swing.Timer;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;

public class ColorLayerUI {

  protected static final float MAX_ALPHA = 0.75f;
  private static final int ANIMATION_DURATION = 300; // in ms
  private static final int FRAME_RATE = 60; // fps
  private static final int FRAME_DELAY = 1000 / FRAME_RATE; // ~16ms for 60fps

  private final RootPaneContainer parent;
  private final AtomicReference<Float> alpha;
  private TransparentGlassPane glassPane;
  private Component originalGlassPane;
  private Timer animationTimer;

  public ColorLayerUI(RootPaneContainer parent) {
    if (parent == null) {
      throw new IllegalArgumentException();
    }
    this.parent = parent;
    this.alpha = new AtomicReference<>(0.0f);
  }

  public static ColorLayerUI createTransparentLayerUI(RootPaneContainer parent) {
    if (parent != null && parent.getRootPane() != null) {
      final ColorLayerUI ui = new ColorLayerUI(parent);
      ui.installGlassPane();
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

  private void installGlassPane() {
    JRootPane rootPane = parent.getRootPane();
    if (rootPane != null) {
      originalGlassPane = rootPane.getGlassPane();
      glassPane = new TransparentGlassPane();
      rootPane.setGlassPane(glassPane);
    }
  }

  private void uninstallGlassPane() {
    if (glassPane != null) {
      glassPane.setVisible(false);
    }
    JRootPane rootPane = parent.getRootPane();
    if (rootPane != null && originalGlassPane != null) {
      rootPane.setGlassPane(originalGlassPane);
      glassPane = null;
      originalGlassPane = null;
    }
  }

  /** Easing function for smooth fade in/out Uses ease-out cubic for natural looking animation */
  private float easeOutCubic(float t) {
    return 1 - (float) Math.pow(1 - t, 3);
  }

  public synchronized void showUI() {
    if (glassPane == null) return;

    if (animationTimer != null && animationTimer.isRunning()) {
      animationTimer.stop();
    }
    glassPane.setVisible(true);
    final long startTime = System.currentTimeMillis();

    animationTimer =
        new Timer(
            FRAME_DELAY,
            e -> {
              long elapsed = System.currentTimeMillis() - startTime;
              float progress = Math.min(1.0f, (float) elapsed / ANIMATION_DURATION);

              float newAlpha = easeOutCubic(progress);
              alpha.set(newAlpha);
              if (glassPane != null) {
                glassPane.setAlpha(newAlpha);
              }
              if (progress >= 1.0f) {
                animationTimer.stop();
                animationTimer = null;
              }
            });
    animationTimer.setRepeats(true);
    animationTimer.start();
  }

  public synchronized void hideUI() {
    if (glassPane == null) return;

    if (animationTimer != null && animationTimer.isRunning()) {
      animationTimer.stop();
    }

    final float startAlpha = alpha.get();
    final long startTime = System.currentTimeMillis();

    animationTimer =
        new Timer(
            FRAME_DELAY,
            _ -> {
              long elapsed = System.currentTimeMillis() - startTime;
              float progress = Math.min(1.0f, (float) elapsed / ANIMATION_DURATION);

              // Apply easing function for smooth animation
              float easedProgress = easeOutCubic(progress);
              float newAlpha = startAlpha * (1.0f - easedProgress);
              alpha.set(newAlpha);
              if (glassPane != null) {
                glassPane.setAlpha(newAlpha);
              }
              if (progress >= 1.0f) {
                animationTimer.stop();
                animationTimer = null;
                uninstallGlassPane();
              }
            });
    animationTimer.setRepeats(true);
    animationTimer.start();
  }

  public static Container getContentPane(ColorLayerUI layer) {
    if (layer != null && layer.parent != null) {
      return layer.parent.getRootPane();
    }
    return null;
  }

  private static class TransparentGlassPane extends JComponent {
    private float currentAlpha = 0.0f;

    public TransparentGlassPane() {
      setOpaque(false);
    }

    public void setAlpha(float alpha) {
      if (Math.abs(this.currentAlpha - alpha) > 0.001f) {
        this.currentAlpha = alpha;
        repaint();
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (currentAlpha > 0.0f) {
        Graphics2D g2d = (Graphics2D) g.create();
        try {
          // Enable anti-aliasing for smoother rendering
          g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
          g2d.setComposite(AlphaComposite.SrcOver.derive(currentAlpha * MAX_ALPHA));
          g2d.setColor(getBackground());
          g2d.fillRect(0, 0, getWidth(), getHeight());
        } finally {
          g2d.dispose();
        }
      }
    }
  }
}
