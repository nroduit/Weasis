package org.weasis.core.ui.editor.image;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
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
import java.util.Objects;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.border.CompoundBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;

public class HistogramPanel extends JPanel {
    private static final Logger LOGGER = LoggerFactory.getLogger(HistogramPanel.class);

    private static final int SLIDER_HEIGHT = 15;
    private static final int SLIDER_WIDTH = 10;
    private static final int SLIDER_X = SLIDER_WIDTH / 2 + 5;
    private static final int SLIDER_Y = SLIDER_HEIGHT + 5;

    private float[] histValues;
    private ByteLut lut;
    private WindLevelParameters winLevParam;

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
        if (!(g instanceof Graphics2D) || histValues == null || lut == null) {
            return;
        }
        Graphics2D g2d = (Graphics2D) g;
        Color oldColor = g2d.getColor();
        Stroke oldStroke = g2d.getStroke();
        drawHistogramPane(g2d);
        g2d.setColor(oldColor);
        g2d.setStroke(oldStroke);
    }

    private void drawHistogramPane(Graphics2D g2d) {
        float sum = 0.0f;
        float maxHistogramCounts = 1.0f;
        int nbBins = histValues.length;
        for (int i = 0; i < nbBins; i++) {
            float val = logarithmic ? (float) Math.log1p(histValues[i]) : histValues[i];
            if (accumulate) {
                sum += val;
            } else {
                maxHistogramCounts = Math.max(maxHistogramCounts, val);
            }
        }
        if (accumulate) {
            maxHistogramCounts = sum;
        }
        int lutHeight = 45;
        float bCanvas = this.getHeight() - 1.0f;
        float tLut = bCanvas - lutHeight;
        float bLut = bCanvas - 20.0f;
        float fj = (tLut - SLIDER_Y) * zoom / maxHistogramCounts;
        float lutLength = getWidth() - SLIDER_X * 2.0f;
        this.xAxisHistoRescaleRatio = lutLength / nbBins;
        g2d.setStroke(new BasicStroke(xAxisHistoRescaleRatio + 0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL));

        sum = 0.0f;
        for (int i = 0; i < nbBins; i++) {
            float val = logarithmic ? (float) Math.log1p(histValues[i]) : histValues[i];
            if (accumulate) {
                sum += val;
                val = sum;
            }
            float y1 = tLut - fj * val;

            // Convert BGR LUT to RBG color
            Color cLut = new Color((lut.getLutTable()[2][i] & 0xff), (lut.getLutTable()[1][i] & 0xff),
                (lut.getLutTable()[0][i] & 0xff));

            if (showIntensity) {
                g2d.setColor(cLut);
            } else {
                g2d.setColor(Color.BLACK);
            }
            float xVal = SLIDER_X + i * xAxisHistoRescaleRatio;
            g2d.draw(new Line2D.Float(xVal, y1, xVal, tLut));
            // TODO draw measure graphic selection
            // g2d.setColor(Color.ORANGE);
            // y1 = tLut - fj * val2;
            // g2d.draw(new Line2D.Float(xVal, y1, xVal, tLut));

            g2d.setColor(cLut);
            g2d.draw(new Line2D.Float(xVal, tLut + 7.0f, xVal, bLut));
        }

        g2d.setStroke(new BasicStroke(1.0f));

        final float fontHeight = FontTools.getAccurateFontHeight(g2d);
        final float midfontHeight = fontHeight + 3f;

        float x = SLIDER_X;
        float y = bLut;
        float offsetThick = (xAxisHistoRescaleRatio + 0.5f) / 2f;
        double min = winLevParam.getLevelMin();
        double max = winLevParam.getLevelMax();
        int separation = 4;
        float hRange = (float) (max - min);
        float stepWindow = hRange / separation;
        float firstlevel = (float) min;
        float spaceFactor = (lutLength - xAxisHistoRescaleRatio) / hRange;

        g2d.setPaint(Color.BLACK);
        Rectangle2D.Float rect = new Rectangle2D.Float();
        for (int i = 0; i <= separation; i++) {
            int val = Math.round(firstlevel + i * stepWindow);
            float posX = x + (val - firstlevel) * spaceFactor;
            rect.setRect(posX - 2f, y - 1f, 2f, 7f);
            g2d.draw(rect);
        }
        rect.setRect(x - 2f - offsetThick, tLut + 5f, lutLength + 3f, 20f);
        g2d.draw(rect);

        g2d.setPaint(Color.WHITE);

        Line2D.Float line = new Line2D.Float();
        for (int i = 0; i <= separation; i++) {
            int val = Math.round(firstlevel + i * stepWindow);
            float posX = x + (val - firstlevel) * spaceFactor;
            line.setLine(posX - 1f, y, posX - 1f, y + 5f);
            g2d.draw(line);
            String str = Integer.toString(val); // $NON-NLS-1$
            AbstractGraphicLabel.paintFontOutline(g2d, str, posX - g2d.getFontMetrics().stringWidth(str) / 2f,
                y + midfontHeight);
        }

        rect.setRect(x - 1f - offsetThick, tLut + 6f, lutLength + 1f, 18f);
        g2d.draw(rect);

        g2d.setPaint(Color.ORANGE);

        g2d.setStroke(
            new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5f, new float[] { 5.0f }, 0.0f));
        double low = winLevParam.getLevel() - winLevParam.getWindow() / 2.0;
        double high = winLevParam.getLevel() + winLevParam.getWindow() / 2.0;
        float plow = x + ((float) low - firstlevel) * spaceFactor;
        float phigh = x + ((float) high - firstlevel) * spaceFactor;
        boolean drawWl = false;
        if (low > min) {
            line.setLine(plow, SLIDER_Y, plow, tLut);
            g2d.draw(line);
            drawWl = true;
        }
        if (high < max) {
            line.setLine(phigh, SLIDER_Y, phigh, tLut);
            g2d.draw(line);
            drawWl = true;
        }

        if (drawWl) {
            g2d.setStroke(new BasicStroke(1.0F));
            line.setLine(plow, tLut, phigh, SLIDER_Y);
            g2d.draw(line);
        }
    }

    public void setHistogramBins(float[] histValues, ByteLut lut, boolean accumulate, boolean logarithmic,
        boolean showIntensity) {
        this.histValues = histValues;
        this.lut = lut;
        this.accumulate = accumulate;
        this.logarithmic = logarithmic;
        this.showIntensity = showIntensity;
    }

    public void setWindLevelParameters(WindLevelParameters p) {
        this.winLevParam = p;
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

    public float[] getHistValues() {
        return histValues;
    }

    public ByteLut getLut() {
        return lut;
    }

    public void saveHistogramInCSV(File csvOutputFile) {
        try (PrintWriter pw = new PrintWriter(csvOutputFile)) {
            pw.println("Class,Occurrences");
            double min = winLevParam.getLevelMin();
            double max = winLevParam.getLevelMax() + 1.0;
            double factor = (max - min) / histValues.length;
            for (int i = 0; i < histValues.length; i++) {
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
                pw.println(buf.toString());
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
            float lpos = (e.getX() - SLIDER_X) / xAxisHistoRescaleRatio;
            int i = Math.round(lpos);
            if (i >= 0 && i < histValues.length) {
                double min = winLevParam.getLevelMin();
                double max = winLevParam.getLevelMax() + 1.0;
                int val = (int) Math.ceil(i * (max - min) / histValues.length + min);
                int val2 = (int) Math.floor((i + 1) * (max - min) / histValues.length + min);

                StringBuilder buf = new StringBuilder();
                buf.append("<html>"); //$NON-NLS-1$
                buf.append("Intensity");
                buf.append(StringUtil.COLON_AND_SPACE);
                buf.append(val);
                if (val2 != val && val2 < max) {
                    buf.append("...");
                    buf.append(val2);
                }
                buf.append("<br>");
                buf.append("Pixels");
                buf.append(StringUtil.COLON_AND_SPACE);
                buf.append((int) histValues[i]);
                JLabel text = new JLabel(buf.toString());
                if (popup != null) {
                    popup.hide();
                }
                text.setBorder(new CompoundBorder(BorderFactory.createEtchedBorder(),
                    BorderFactory.createEmptyBorder(3, 5, 3, 5)));
                popup = PopupFactory.getSharedInstance().getPopup(e.getComponent(), text, e.getXOnScreen() + 15,
                    e.getYOnScreen() - 40);
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
