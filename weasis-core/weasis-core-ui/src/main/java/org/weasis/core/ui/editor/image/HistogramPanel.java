/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.Messages;
import org.weasis.core.ui.editor.image.HistogramData.Model;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.op.lut.WlParams;

public class HistogramPanel extends JPanel {
  private static final Logger LOGGER = LoggerFactory.getLogger(HistogramPanel.class);

  private static final int SLIDER_HEIGHT = 15;
  private static final int SLIDER_WIDTH = 10;
  private static final int SLIDER_X = SLIDER_WIDTH / 2 + 5;
  private static final int SLIDER_Y = SLIDER_HEIGHT + 5;

  private HistogramData data;
  private boolean showIntensity = true;
  private boolean logarithmic = false;
  private boolean accumulate = false;

  private float zoom = 1.0f;
  private float xAxisHistoRescaleRatio = 1.0f;

  public HistogramPanel() {
    init();
    InternalMouseListener listener = new InternalMouseListener();
    this.addMouseListener(listener);
    this.addMouseMotionListener(listener);
  }

  private void init() {
    this.setPreferredSize(new Dimension(255 + SLIDER_WIDTH + 10, 70));
    this.setBackground(new Color(235, 236, 210));
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (!(g instanceof Graphics2D g2d) || data.getHistValues() == null || data.getLut() == null) {
      return;
    }
    Color oldColor = g2d.getColor();
    Stroke oldStroke = g2d.getStroke();
    Font oldFont = g2d.getFont();
    Font font = FontItem.DEFAULT_SEMIBOLD.getFont();
    g2d.setFont(font.deriveFont(font.getSize() - 3f));
    drawHistogramPane(g2d);
    g2d.setFont(oldFont);
    g2d.setColor(oldColor);
    g2d.setStroke(oldStroke);
  }

  private void drawHistogramPane(Graphics2D g2d) {
    float sum = 0.0f;
    float maxHistogramCounts = 1.0f;
    float[] histValues = data.getHistValues();
    int nbBins = histValues.length;
    for (float histValue : histValues) {
      float val = logarithmic ? (float) Math.log1p(histValue) : histValue;
      if (accumulate) {
        sum += val;
      } else {
        maxHistogramCounts = Math.max(maxHistogramCounts, val);
      }
    }
    if (accumulate) {
      maxHistogramCounts = sum;
    }
    FontMetrics fontMetrics = g2d.getFontMetrics();
    final int fontHeight = fontMetrics.getHeight();
    final int midFontHeight = fontHeight + 3;

    float bCanvas = this.getHeight() - 1.0f;
    float tLut = bCanvas - (SLIDER_Y + fontHeight + 15f);
    float bLut = tLut + SLIDER_Y + 5f;
    float fj = (tLut - SLIDER_Y) * zoom / maxHistogramCounts;
    float lutLength = getWidth() - SLIDER_X * 2.0f;
    this.xAxisHistoRescaleRatio = lutLength / nbBins;

    WlParams windLevel = data.getWindLevel();
    double min = data.getPixMin();
    double max = data.getPixMax();
    double low = windLevel.getLevel() - windLevel.getWindow() / 2.0;
    double high = windLevel.getLevel() + windLevel.getWindow() / 2.0;
    float firstLevel = (float) min;
    float hRange = (float) (max - min);
    float spaceFactor = (lutLength - xAxisHistoRescaleRatio) / hRange;
    float x = SLIDER_X;
    int piLow = nbBins + 1;
    int piHigh = -1;
    double diffLow = Double.MAX_VALUE;
    double diffHigh = Double.MAX_VALUE;

    g2d.setStroke(
        new BasicStroke(
            xAxisHistoRescaleRatio + 0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

    double binFactor = (max - min) / (nbBins - 1);
    sum = 0.0f;
    for (int i = 0; i < nbBins; i++) {
      float val = logarithmic ? (float) Math.log1p(histValues[i]) : histValues[i];
      if (accumulate) {
        sum += val;
        val = sum;
      }
      float y1 = tLut - fj * val;
      float xVal = SLIDER_X + i * xAxisHistoRescaleRatio;
      double level = data.getLayer().pixelToRealValue(i * binFactor + min);
      Color cLut = data.getFinalVoiLutColor(level);
      double diff = Math.abs(level - low);
      if (diff < diffLow) {
        diffLow = diff;
        piLow = i;
      }
      diff = Math.abs(level - high);
      if (diff < diffHigh) {
        diffHigh = diff;
        piHigh = i;
      }

      if (showIntensity) {
        g2d.setPaint(cLut);
      } else {
        g2d.setPaint(Color.BLACK);
      }
      g2d.draw(new Line2D.Float(xVal, y1, xVal, tLut));
      g2d.setPaint(cLut);
      g2d.draw(new Line2D.Float(xVal, tLut + 7.0f, xVal, bLut));
    }

    g2d.setStroke(new BasicStroke(1.0f));

    float offsetThick = (xAxisHistoRescaleRatio + 0.5f) / 2f;

    int separation =
        (int) (lutLength / (g2d.getFontMetrics().stringWidth(String.valueOf(max)) * 3));
    separation = Math.max(separation, 1);
    float stepWindow = hRange / separation;

    g2d.setPaint(Color.BLACK);
    Rectangle2D.Float rect = new Rectangle2D.Float();
    for (int i = 0; i <= separation; i++) {
      float val = firstLevel + i * stepWindow;
      float posX = x + (val - firstLevel) * spaceFactor;
      rect.setRect(posX - 2f, bLut - 1f, 2f, 7f);
      g2d.draw(rect);
    }
    rect.setRect(x - 2f - offsetThick, tLut + 5f, lutLength + 3f, 20f);
    g2d.draw(rect);

    g2d.setPaint(Color.WHITE);

    Object[] oldRenderingHints = GuiUtils.setRenderingHints(g2d, true, false, true);
    Line2D.Float line = new Line2D.Float();
    for (int i = 0; i <= separation; i++) {
      float val = firstLevel + i * stepWindow;
      float posX = x + (val - firstLevel) * spaceFactor;
      line.setLine(posX - 1f, bLut, posX - 1f, bLut + 5f);
      g2d.draw(line);
      String str =
          DecFormatter.allNumber(data.getLayer().pixelToRealValue(firstLevel + i * stepWindow));
      float offsetLabel =
          i == separation
              ? g2d.getFontMetrics().stringWidth(str) - SLIDER_X / 2f
              : g2d.getFontMetrics().stringWidth(str) / 2f;
      float xlabel = i == 0 ? posX / 2f : posX - offsetLabel;
      FontTools.paintFontOutline(g2d, str, xlabel, bLut + midFontHeight);
    }

    rect.setRect(x - 1f - offsetThick, tLut + 6f, lutLength + 1f, 18f);
    g2d.draw(rect);

    Color yellow = IconColor.ACTIONS_YELLOW.getColor();
    g2d.setPaint(yellow);

    g2d.setStroke(
        new BasicStroke(
            0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5f, new float[] {5.0f}, 0.0f));

    Model m = data.getColorModel();
    boolean hls = HistogramData.Model.HLS.equals(m) || HistogramData.Model.HSV.equals(m);
    if (!hls) {
      boolean drawWl = false;
      float plow = x + (piLow * (float) binFactor) * spaceFactor;
      float phigh = x + (piHigh * (float) binFactor) * spaceFactor;
      if (low > windLevel.getLevelMin()) {
        line.setLine(plow, SLIDER_Y, plow, tLut);
        g2d.draw(line);
        String label =
            DecFormatter.allNumber(data.getLayer().pixelToRealValue(piLow * binFactor + min));
        FontTools.paintFontOutline(
            g2d,
            label,
            plow - g2d.getFontMetrics().stringWidth(label) / 2.f,
            SLIDER_Y + (float) midFontHeight);
        drawWl = true;
      }
      if (high < windLevel.getLevelMax()) {
        g2d.setPaint(yellow);
        line.setLine(phigh, SLIDER_Y, phigh, tLut);
        g2d.draw(line);
        String label =
            DecFormatter.allNumber(data.getLayer().pixelToRealValue(piHigh * binFactor + min));
        FontTools.paintFontOutline(
            g2d,
            label,
            phigh - g2d.getFontMetrics().stringWidth(label) / 2.f,
            SLIDER_Y + (float) midFontHeight);
        drawWl = true;
      }

      if (drawWl) {
        g2d.setPaint(yellow);
        line.setLine(plow, SLIDER_Y, phigh, SLIDER_Y);
        g2d.draw(line);
      }
    }
    GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
  }

  public void setHistogram(
      HistogramData data, boolean accumulate, boolean logarithmic, boolean showIntensity) {
    this.data = data;
    this.accumulate = accumulate;
    this.logarithmic = logarithmic;
    this.showIntensity = showIntensity;
  }

  public void setWindLevelParameters(WindLevelParameters p) {
    this.data.setWindLevel(p);
  }

  public void updateZoom(boolean in) {
    if (in) {
      zoom *= 1.4f;
    } else {
      zoom *= (1 / 1.4f);
    }
    repaint();
  }

  public boolean isLogarithmic() {
    return logarithmic;
  }

  public void setLogarithmic(boolean logarithmic) {
    boolean update = !Objects.equals(this.logarithmic, logarithmic);
    this.logarithmic = logarithmic;
    if (update) {
      this.zoom = 1.0f;
      repaint();
    }
  }

  public boolean isShowIntensity() {
    return showIntensity;
  }

  public void setShowIntensity(boolean showIntensity) {
    boolean update = !Objects.equals(this.showIntensity, showIntensity);
    this.showIntensity = showIntensity;
    if (update) {
      repaint();
    }
  }

  public boolean isAccumulate() {
    return accumulate;
  }

  public void setAccumulate(boolean accumulate) {
    boolean update = !Objects.equals(this.accumulate, accumulate);
    this.accumulate = accumulate;
    if (update) {
      this.zoom = 1.0f;
      repaint();
    }
  }

  public void resetDisplay() {
    this.zoom = 1.0f;
    this.accumulate = false;
    this.logarithmic = false;
    this.showIntensity = true;
    repaint();
  }

  public float getZoom() {
    return zoom;
  }

  public HistogramData getData() {
    return data;
  }

  public void saveHistogramInCSV(File csvOutputFile) {
    try (PrintWriter pw = new PrintWriter(csvOutputFile, StandardCharsets.UTF_8)) {
      pw.println("Class,Occurrences"); // NON-NLS
      float[] histValues = data.getHistValues();
      WlParams windLevel = data.getWindLevel();
      double min = windLevel.getLevelMin();
      double max = windLevel.getLevelMax() + 1.0;
      double factor = (max - min) / histValues.length;
      for (int i = 0; i < histValues.length; i++) {
        // TODO convert in real value (modality lut)
        int val = (int) Math.ceil(i * factor + min);
        int val2 = (int) Math.floor((i + 1) * factor + min);
        StringBuilder buf = new StringBuilder();
        buf.append(val);
        if (val2 != val && val2 < max) {
          buf.append("...");
          buf.append(val2);
        }
        buf.append(",");
        buf.append(histValues[i]);
        pw.println(buf);
      }
    } catch (IOException e) {
      LOGGER.error("Cannot save histogram values", e);
    }
  }

  private class InternalMouseListener implements MouseListener, MouseMotionListener {
    private Popup popup;

    @Override
    public void mouseClicked(MouseEvent e) {
      // Do nothing
    }

    @Override
    public void mousePressed(MouseEvent e) {
      showPopup(e);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (popup != null) {
        popup.hide();
      }
    }

    private void showPopup(MouseEvent e) {
      float[] histValues = data.getHistValues();
      float lpos = (e.getX() - SLIDER_X) / xAxisHistoRescaleRatio;
      int i = Math.round(lpos);
      if (i >= 0 && i < histValues.length) {
        WlParams windLevel = data.getWindLevel();
        double min = windLevel.getLevelMin();
        double max = windLevel.getLevelMax() + 1.0;
        int val = (int) Math.ceil(i * (max - min) / histValues.length + min);
        int val2 = (int) Math.floor((i + 1) * (max - min) / histValues.length + min);

        StringBuilder buf = new StringBuilder();
        buf.append("<html>");
        buf.append(Messages.getString("HistogramPanel.intensity"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append(val);
        if (val2 != val && val2 < max) {
          buf.append("...");
          buf.append(val2);
        }
        buf.append("<br>");
        buf.append(Messages.getString("HistogramPanel.pixels"));
        buf.append(StringUtil.COLON_AND_SPACE);
        buf.append((int) histValues[i]);
        JLabel text = new JLabel(buf.toString());
        if (popup != null) {
          popup.hide();
        }
        text.setBorder(GuiUtils.getEmptyBorder(3, 5, 3, 5));
        popup =
            PopupFactory.getSharedInstance()
                .getPopup(
                    e.getComponent(),
                    text,
                    e.getXOnScreen() + 15,
                    e.getYOnScreen() - GuiUtils.getScaleLength(40));
        popup.show();
      } else {
        if (popup != null) {
          popup.hide();
        }
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      setCursor(DefaultView2d.CROSS_CURSOR);
    }

    @Override
    public void mouseExited(MouseEvent e) {
      setCursor(DefaultView2d.DEFAULT_CURSOR);
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      showPopup(e);
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      // Do nothing
    }
  }
}
