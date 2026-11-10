/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JViewport;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;

/**
 * The whole recording in one strip above the leads: the envelope of the reference lead (II when
 * present), a time axis, and the span the viewport shows. Clicking or dragging moves the viewport
 * there; the buttons page through the recording. Shown for recordings longer than a page.
 */
public class OverviewPanel extends JPanel {

  /** Recordings shorter than this (seconds) fit a page and need no overview. */
  public static final double MIN_SECONDS = 12;

  private static final int STRIP_HEIGHT = 46;
  private static final int AXIS_HEIGHT = 14;
  private static final Color TRACE = new Color(60, 60, 60);
  private static final Color WINDOW = new Color(30, 110, 220, 60);
  private static final Color WINDOW_BORDER = new Color(30, 110, 220);
  private static final double[] TICK_STEPS = {
    1, 2, 5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600, 7200, 10800, 21600
  };

  private final WaveView view;
  private final JViewport viewport;
  private final JComponent pane;
  private final Strip strip = new Strip();

  public OverviewPanel(WaveView view, JViewport viewport, JComponent pane) {
    super(new BorderLayout(2, 0));
    this.view = view;
    this.viewport = viewport;
    this.pane = pane;
    setOpaque(false);
    JButton previous = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.PREVIOUS));
    previous.setToolTipText(Messages.getString("OverviewPanel.previous"));
    previous.setFocusable(false);
    previous.addActionListener(e -> view.scrollPages(-1));
    JButton next = new JButton(ResourceUtil.getToolBarIcon(ActionIcon.NEXT));
    next.setToolTipText(Messages.getString("OverviewPanel.next"));
    next.setFocusable(false);
    next.addActionListener(e -> view.scrollPages(1));
    add(previous, BorderLayout.WEST); // absolute side: the time axis runs left to right
    add(strip, BorderLayout.CENTER);
    add(next, BorderLayout.EAST); // absolute side: the time axis runs left to right
    viewport.addChangeListener(e -> strip.repaint());
  }

  /** The envelope strip with the time axis and the viewport window. */
  private final class Strip extends JComponent {

    Strip() {
      setPreferredSize(new Dimension(100, STRIP_HEIGHT + AXIS_HEIGHT));
      setOpaque(true);
      MouseAdapter mouse =
          new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
              moveTo(e.getX());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
              moveTo(e.getX());
            }
          };
      addMouseListener(mouse);
      addMouseMotionListener(mouse);
    }

    /** Centres the viewport on the time under an x of the strip. */
    private void moveTo(int x) {
      double seconds = view.getSeconds();
      if (seconds <= 0 || getWidth() <= 0) {
        return;
      }
      double time = x / (double) getWidth() * seconds;
      double visibleSeconds =
          view.getPixelsPerSecond() <= 0 ? 0 : viewport.getWidth() / view.getPixelsPerSecond();
      view.scrollToSeconds(time - visibleSeconds / 2);
    }

    private ChannelDefinition reference() {
      List<ChannelDefinition> channels = view.getChannels();
      if (channels.isEmpty()) {
        return null;
      }
      for (ChannelDefinition c : channels) {
        if (Lead.II.equals(c.getLead())) {
          return c;
        }
      }
      return channels.get(0);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g;
      Object[] hints = GuiUtils.setRenderingHints(g, true, true, true);
      int width = getWidth();
      g2.setColor(new Color(235, 235, 235));
      g2.fillRect(0, 0, width, STRIP_HEIGHT);
      g2.setColor(getBackground());
      g2.fillRect(0, STRIP_HEIGHT, width, AXIS_HEIGHT);

      WaveDataReadable data = view.getWaveData();
      ChannelDefinition channel = reference();
      double seconds = view.getSeconds();
      if (data != null && channel != null && seconds > 0 && width > 0) {
        drawEnvelope(g2, data, channel, width);
        drawAxis(g2, seconds, width);
        drawWindow(g2, seconds, width);
      }
      GuiUtils.resetRenderingHints(g, hints);
    }

    private void drawEnvelope(
        Graphics2D g2, WaveDataReadable data, ChannelDefinition channel, int width) {
      int samples = data.getNbSamplesPerChannel();
      double perPixel = samples / (double) width;
      int block = data.summaryBlock(0, samples);
      boolean summary = block > 0 && perPixel >= 4 * block;
      if (!summary && samples > (1 << 21)) {
        return; // painted again when the summary covers the recording
      }
      double min = Math.min(channel.getMinValue(), 0);
      double max = Math.max(channel.getMaxValue(), 0);
      double span = Math.max(1e-9, max - min);
      double pixelsPerUnit = (STRIP_HEIGHT - 4) / span;
      double base = 2 + max * pixelsPerUnit;
      int[] range = new int[2];
      double[] top = new double[width];
      double[] bottom = new double[width];
      int n = 0;
      for (int px = 0; px < width; px++) {
        int s0 = Math.max(0, (int) Math.floor(px * perPixel) - 1);
        int s1 = Math.min(samples - 1, (int) Math.floor((px + 1) * perPixel) - 1);
        if (s1 < s0) {
          continue;
        }
        if (summary) {
          data.summaryMinMax(s0, s1 - s0 + 1, channel, range);
        } else {
          data.rawMinMax(s0, s1 - s0 + 1, channel, range);
        }
        double a = channel.toDisplay(channel.toPhysical(range[0]));
        double b = channel.toDisplay(channel.toPhysical(range[1]));
        top[n] = base - Math.max(a, b) * pixelsPerUnit - 0.5;
        bottom[n] = base - Math.min(a, b) * pixelsPerUnit + 0.5;
        n++;
      }
      if (n == 0) {
        return;
      }
      Path2D path = new Path2D.Float(Path2D.WIND_NON_ZERO, 2 * n + 2);
      path.moveTo(0.5, top[0]);
      for (int i = 1; i < n; i++) {
        path.lineTo(i + 0.5, top[i]);
      }
      for (int i = n - 1; i >= 0; i--) {
        path.lineTo(i + 0.5, bottom[i]);
      }
      path.closePath();
      g2.setColor(TRACE);
      g2.fill(path);
    }

    private void drawAxis(Graphics2D g2, double seconds, int width) {
      g2.setFont(new Font("SanSerif", Font.PLAIN, 10));
      FontMetrics metrics = g2.getFontMetrics();
      double step = TICK_STEPS[TICK_STEPS.length - 1];
      for (double candidate : TICK_STEPS) {
        if (candidate / seconds * width >= 70) {
          step = candidate;
          break;
        }
      }
      g2.setColor(Color.GRAY);
      for (double t = 0; t <= seconds; t += step) {
        int x = (int) Math.round(t / seconds * width);
        g2.drawLine(x, STRIP_HEIGHT - 4, x, STRIP_HEIGHT + 2);
        String label = formatTime(t);
        int labelWidth = metrics.stringWidth(label);
        int lx = Math.min(x + 2, width - labelWidth);
        g2.drawString(label, lx, STRIP_HEIGHT + AXIS_HEIGHT - 2);
      }
    }

    private void drawWindow(Graphics2D g2, double seconds, int width) {
      double pps = view.getPixelsPerSecond();
      int paneWidth = pane.getWidth();
      if (pps <= 0 || paneWidth <= 0) {
        return;
      }
      Rectangle visible = viewport.getViewRect();
      int gutter = view.getGutterWidth();
      double start = Math.max(0, (visible.x - gutter) / pps);
      double end = Math.min(seconds, (visible.x + visible.width - gutter) / pps);
      int x0 = (int) Math.round(start / seconds * width);
      int x1 = (int) Math.round(end / seconds * width);
      g2.setColor(WINDOW);
      g2.fillRect(x0, 0, Math.max(2, x1 - x0), STRIP_HEIGHT);
      g2.setColor(WINDOW_BORDER);
      g2.drawRect(x0, 0, Math.max(2, x1 - x0) - 1, STRIP_HEIGHT - 1);
    }
  }

  /** Seconds as m:ss, or h:mm:ss beyond an hour. */
  static String formatTime(double seconds) {
    long total = Math.round(seconds);
    long h = total / 3600;
    long m = (total % 3600) / 60;
    long s = total % 60;
    return h > 0
        ? String.format("%d:%02d:%02d", h, m, s)
        : String.format("%d:%02d", m, s); // NON-NLS
  }
}
