/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.task;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.geom.Arc2D;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.LocalUtil;

public class CircularProgressBar extends JProgressBar {

  private static final int TEXT_GAP = 2;

  private Timer timer;
  private int animateAngle;

  private final float arcThickness;
  private final boolean displayText;

  public CircularProgressBar() {
    this(0, 100);
  }

  public CircularProgressBar(int min, int max) {
    this(min, max, 5f, true, null);
  }

  public CircularProgressBar(
      int min, int max, float arcThickness, boolean displayText, Float fontSize) {
    super(min, max);
    this.arcThickness = arcThickness;
    this.displayText = displayText;
    Font font =
        fontSize == null
            ? FontItem.MINI.getFont()
            : FontItem.DEFAULT.getFont().deriveFont(fontSize);
    this.setFont(font.deriveFont((float) Math.max(font.getSize() - 1, 8)));
    init();
  }

  private void init() {
    this.setOpaque(false);
    this.setBorder(GuiUtils.getEmptyBorder(TEXT_GAP + (int) Math.ceil(arcThickness)));
    Dimension dim;
    if (displayText) {
      int size = GuiUtils.getComponentWidthFromText(this, "90%");
      dim = new Dimension(size, size);
    } else {
      dim = GuiUtils.getBigIconButtonSize(this);
    }
    this.setSize(dim.width, dim.height);
    this.setPreferredSize(dim);
    this.setMaximumSize(dim);
  }

  @Override
  public void paint(Graphics g) {
    if (g instanceof Graphics2D g2d) {
      draw(g2d);
    }
  }

  @Override
  public void setIndeterminate(boolean newValue) {
    super.setIndeterminate(newValue);
    if (newValue) {
      if (timer == null) {
        animateAngle = 360;
        timer =
            new Timer(
                5,
                e -> {
                  animateAngle -= 1;
                  if (animateAngle <= 0) {
                    animateAngle = 360;
                  }
                  repaint();
                });
      }

      timer.setInitialDelay(0);
      timer.start();
    } else {
      stopAnimation();
    }
  }

  protected void stopAnimation() {
    if (timer != null) timer.stop();
  }

  private void draw(Graphics2D g2d) {
    if (isEnabled()) {
      Stroke oldStroke = g2d.getStroke();
      Font oldFont = g2d.getFont();

      int h = this.getHeight();
      int w = this.getWidth();
      int range = this.getMaximum() - this.getMinimum();
      if (range < 1) {
        range = 1;
      }

      double angle = 360 - this.getValue() * 360.0 / range;
      String text = LocalUtil.getPercentInstance().format((double) this.getValue() / range);
      float textGap = GuiUtils.getScaleLength(TEXT_GAP);
      float arcSize = getInsets().top - textGap;
      double shift = arcSize / 2.0;

      Object[] oldRenderingHints = GuiUtils.setRenderingHints(g2d, true, false, true);

      g2d.setStroke(new BasicStroke(arcSize, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND));
      Color background = FlatUIUtils.getUIColor("ProgressBar.background", Color.WHITE);
      Color foreground = FlatUIUtils.getUIColor("ProgressBar.foreground", Color.LIGHT_GRAY);
      g2d.setPaint(background);
      g2d.draw(new Arc2D.Double(shift, shift, w - arcSize, h - arcSize, 0, 360, Arc2D.OPEN));
      g2d.setPaint(foreground);

      boolean animated = isIndeterminate();
      if (animated) {
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double radian = Math.toRadians(animateAngle);
        float r = w / 2f;
        float dx1 = r + (float) (Math.sin(radian) * r);
        float dy1 = r + (float) (Math.cos(radian) * r);
        float dx2 = r + (float) (Math.sin(radian + Math.PI) * w / 3f);
        float dy2 = r + (float) (Math.cos(radian + Math.PI) * w / 3f);
        GradientPaint gradient = new GradientPaint(dx1, dy1, foreground, dx2, dy2, background);
        g2d.setPaint(gradient);
        g2d.draw(
            new Arc2D.Double(
                shift, shift, w - arcSize, h - arcSize, animateAngle + 90.0, 360, Arc2D.OPEN));
      } else {
        g2d.draw(
            new Arc2D.Double(
                shift, shift, w - arcSize, h - arcSize, angle + 90, 360 - angle, Arc2D.OPEN));

        if (displayText) {
          FontMetrics fm = g2d.getFontMetrics();
          float x = (w - fm.stringWidth(text)) / 2f + textGap;
          float y = (h - fm.getHeight()) / 2f + fm.getAscent();
          Color textColor = FlatUIUtils.getUIColor("ProgressBar.selectionBackground", Color.BLACK);
          g2d.setPaint(textColor);
          g2d.setFont(getFont());
          g2d.drawString(text, x, y);
        }
      }

      GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
      g2d.setStroke(oldStroke);
      g2d.setFont(oldFont);
    }
  }
}
