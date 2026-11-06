/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ThreadUtil;

/**
 * Shows one page rendered to fit the component. A request is debounced, rendered on a worker thread
 * at the pixels per millimeter that fill the component, and dropped when a newer request has been
 * made meanwhile; one raster is kept.
 */
public class PagePreview extends JComponent {
  private static final Logger LOGGER = LoggerFactory.getLogger(PagePreview.class);

  private static final int DEBOUNCE_MS = 150;
  private static final int MAX_WIDTH_PX = 1600;
  private static final int PADDING = 12;
  private static final int SHADOW = 3;
  private static final long CLOSE_TIMEOUT_S = 30;

  private final ExecutorService worker = ThreadUtil.newSingleThreadDaemonExecutor("print-preview");
  private final AtomicInteger generation = new AtomicInteger();
  private final Timer debounce = new Timer(DEBOUNCE_MS, e -> render());

  private PageSpec spec;
  private DoubleFunction<BufferedImage> renderer;
  private Consumer<Throwable> errorHandler = e -> LOGGER.error("Page preview", e);
  private BufferedImage page;
  private boolean closed;

  public PagePreview() {
    debounce.setRepeats(false);
    setOpaque(true);
    setPreferredSize(new Dimension(GuiUtils.getScaleLength(440), GuiUtils.getScaleLength(340)));
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            if (renderer != null) {
              request(spec, renderer);
            }
          }
        });
  }

  public void setErrorHandler(Consumer<Throwable> errorHandler) {
    this.errorHandler = errorHandler;
  }

  /**
   * Asks for the page to be rendered again: {@code renderer} receives the pixels per millimeter
   * that fill the component and returns the raster. Requests within the debounce delay coalesce.
   */
  public void request(PageSpec spec, DoubleFunction<BufferedImage> renderer) {
    if (closed) {
      return;
    }
    this.spec = spec;
    this.renderer = renderer;
    debounce.restart();
  }

  /** Pixels per millimeter at which the page fills the component on its screen. */
  public double pixelsPerMm(PageSpec spec) {
    double deviceScale = 1.0;
    if (getGraphicsConfiguration() != null) {
      deviceScale = getGraphicsConfiguration().getDefaultTransform().getScaleX();
    }
    Dimension size = getWidth() > 0 && getHeight() > 0 ? getSize() : getPreferredSize();
    double fit = fitScale(spec, size.width, size.height) * deviceScale;
    return Math.min(fit, MAX_WIDTH_PX / spec.widthMm());
  }

  /** Stops the worker and waits for a rendering in progress, so the layout can be disposed. */
  public void close() {
    if (closed) {
      return;
    }
    closed = true;
    debounce.stop();
    worker.shutdown();
    try {
      if (!worker.awaitTermination(CLOSE_TIMEOUT_S, TimeUnit.SECONDS)) {
        LOGGER.warn("Page preview still rendering");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    page = null;
  }

  private void render() {
    PageSpec currentSpec = spec;
    DoubleFunction<BufferedImage> currentRenderer = renderer;
    if (closed || currentSpec == null || currentRenderer == null) {
      return;
    }
    int id = generation.incrementAndGet();
    double pixelsPerMm = pixelsPerMm(currentSpec);
    worker.execute(
        () -> {
          if (id != generation.get()) {
            return;
          }
          try {
            BufferedImage raster = currentRenderer.apply(pixelsPerMm);
            SwingUtilities.invokeLater(
                () -> {
                  if (id == generation.get() && !closed) {
                    page = raster;
                    repaint();
                  }
                });
          } catch (Throwable t) {
            SwingUtilities.invokeLater(() -> errorHandler.accept(t));
          }
        });
  }

  private static double fitScale(PageSpec spec, int width, int height) {
    int pad = GuiUtils.getScaleLength(PADDING);
    double w = Math.max(1, width - 2 * pad);
    double h = Math.max(1, height - 2 * pad);
    return Math.min(w / spec.widthMm(), h / spec.heightMm());
  }

  @Override
  protected void paintComponent(Graphics g) {
    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setColor(
          getBackground() == null ? UIManager.getColor("Panel.background") : getBackground());
      g2.fillRect(0, 0, getWidth(), getHeight());
      if (spec == null) {
        return;
      }
      double scale = fitScale(spec, getWidth(), getHeight());
      int w = (int) Math.round(spec.widthMm() * scale);
      int h = (int) Math.round(spec.heightMm() * scale);
      int x = (getWidth() - w) / 2;
      int y = (getHeight() - h) / 2;
      int shadow = GuiUtils.getScaleLength(SHADOW);
      g2.setColor(new Color(0, 0, 0, 60));
      g2.fillRect(x + shadow, y + shadow, w, h);
      g2.setColor(spec.background() == null ? Color.WHITE : spec.background());
      g2.fillRect(x, y, w, h);
      if (page != null) {
        g2.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.drawImage(page, x, y, w, h, null);
      }
      g2.setColor(Color.GRAY);
      g2.drawRect(x, y, w - 1, h - 1);
    } finally {
      g2.dispose();
    }
  }
}
