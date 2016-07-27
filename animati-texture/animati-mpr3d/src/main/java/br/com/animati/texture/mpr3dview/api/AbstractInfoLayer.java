/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;

import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 * Template for an InfoLayer.
 *
 * Implements the common drawing methods and leave for implementation the task of get the necessary data.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 21 sep
 */
public abstract class AbstractInfoLayer extends DefaultUUID  implements LayerAnnotation {

    private static final int BORDER = 10;
    protected int border = BORDER;

    public static final String SCALE_TOP = Messages.getString("AbstractInfoLayer.scaletop");
    public static final String SCALE_BOTTOM = Messages.getString("AbstractInfoLayer.scalebottom");
    public static final String SCALE_LEFT = Messages.getString("AbstractInfoLayer.scaleleft");
    public static final String SCALE_RIGHT = Messages.getString("AbstractInfoLayer.scaleright");

    public static final String ORIENTATION_TOP = Messages.getString("AbstractInfoLayer.orientationtop");
    public static final String ORIENTATION_BOTTOM = Messages.getString("AbstractInfoLayer.orientationbottom");
    public static final String ORIENTATION_LEFT = Messages.getString("AbstractInfoLayer.orientationleft");
    public static final String ORIENTATION_RIGHT = Messages.getString("AbstractInfoLayer.orientationright");

    // variável que controla a informação de modalidade apresentada na viewPane e no checkbox da displayTool.
    public static final String MODALITY = Messages.getString("AbstractInfoLayer.modality");

    /** Display preference implementation. */
    protected final Map<String, Boolean> displayPreferences = new HashMap<>();

    protected Color highlightColor = new Color(255, 153, 153);
    protected Boolean visible = Boolean.TRUE;

    
    @Override
    public int compareTo(Layer obj) {
        if (obj == null) {
            return 1;
        }
        int thisVal = this.getLevel();
        int anotherVal = obj.getLevel();
        return thisVal < anotherVal ? -1 : (thisVal == anotherVal ? 0 : 1);
    }

    @Override
    public LayerType getType() {
        return LayerType.IMAGE_ANNOTATION;
    }

    @Override
    public void setType(LayerType type) {
        // Cannot change this type
    }

    @Override
    public void setName(String graphicLayerName) {
     // Cannot change the name
    }

    @Override
    public String getName() {
        return getType().toString();
    }
    
    @Override
    public Integer getBorder() {
        return border;
    }

    @Override
    public void setBorder(Integer textBorder) {
        border = textBorder;
    }

    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setVisible(Boolean visible) {
        this.visible = visible;
    }

    @Override
    public Integer getLevel() {
        return getType().getLevel();
    }

    @Override
    public void setLevel(Integer i) {
        // Do Nothing
    }

    @Override
    public Boolean isShowBottomScale() {
        return displayPreferences.get(SCALE_BOTTOM);
    }

    @Override
    public void setShowBottomScale(Boolean showBottomScale) {
        displayPreferences.put(SCALE_BOTTOM, showBottomScale);
    }

    @Override
    public Boolean getDisplayPreferences(String item) {
        Boolean val = displayPreferences.get(item);
        return val == null ? false : val;
    }

    @Override
    public Boolean setDisplayPreferencesValue(String displayItem, Boolean selected) {
        boolean selectedBefore = getDisplayPreferences(displayItem);
        displayPreferences.put(displayItem, selected);
        return selected != selectedBefore;
    }

    public void copyLayerSettings(AbstractInfoLayer layer) {
        layer.setBorder(getBorder());
        Iterator<String> iterator = displayPreferences.keySet().iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            layer.setDisplayPreferencesValue(next, displayPreferences.get(next));
        }
    }

    public Color getHighlightColor() {
        return highlightColor;
    }

    @Override
    public void paint(Graphics2D g2d) {
        if (!visible) {
            return;
        }
        if (ownerHasContent()) {
            if (isOwnerContentReadable()) {
                if (getDisplayPreferences(SCALE)) {
                    drawScale(g2d);
                }
                if (getDisplayPreferences(IMAGE_ORIENTATION)) {
                    drawOrientation(g2d);
                }
                if (getDisplayPreferences(LUT)) {
                    drawLUT(g2d);
                }
            } else {
                paintNotReadable(g2d);
            }

            paintTextCorners(g2d);
        }
    }

    protected abstract void paintNotReadable(Graphics2D g2d);

    protected void drawScale(Graphics2D g2d) {

        // Delegated data:
        Rectangle bounds = getOwnerBounds();
        double zoomFactor = getOwnerZoomFactor();
        double pixelSize = getOwnerPixelSize();
        Unit unit = getOwnerPixelSpacingUnit();
        Dimension sourceDim = getOwnerContentDimensions();
        double rescaleX = getOwnerContentRescaleX();
        double rescaleY = getOwnerContentRescaleY();
        final String pixSizeDesc = getPixelSizeCalibrationDescription();

        ScaleHelper scDecorator =
            new ScaleHelper(g2d, bounds, zoomFactor, pixelSize, unit, sourceDim, rescaleX, rescaleY, pixSizeDesc);

        if (getDisplayPreferences(SCALE_BOTTOM)) {
            scDecorator.drawBottomScale();
        }
        if (getDisplayPreferences(SCALE_TOP)) {
            scDecorator.drawTopScale();
        }
        if (getDisplayPreferences(SCALE_LEFT)) {
            scDecorator.drawLeftScale();
        }
        if (getDisplayPreferences(SCALE_RIGHT)) {
            scDecorator.drawRightScale();
        }

    }

    private void drawOrientation(Graphics2D g2d) {
        Rectangle bounds = getOwnerBounds();
        float midx = bounds.width / 2f;
        float midy = bounds.height / 2f;

        String[] leftTopRiBot = getContent4OrientationFlags();
        if (noNullStrings(leftTopRiBot)) {
            for (String string : leftTopRiBot) {
                if (string.length() < 1) {
                    string = " ";
                }
            }

            Font oldFont = g2d.getFont();
            Font bigFont = oldFont.deriveFont(Font.BOLD, oldFont.getSize() + 1.0f);
            g2d.setFont(bigFont);
            Hashtable<TextAttribute, Object> map = new Hashtable<>(1);
            map.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
            String fistLetter = leftTopRiBot[1].substring(0, 1);
            int shiftx = g2d.getFontMetrics().stringWidth(fistLetter);
            Font subscriptFont = bigFont.deriveFont(map);

            // font 10 => 15 pixels
            double thickLength = g2d.getFont().getSize() * 1.5f;
            thickLength = thickLength < 5.0 ? 5.0 : thickLength;

            if (getDisplayPreferences(ORIENTATION_TOP)) {
                g2d.setFont(bigFont);
                float placeY = (float) (thickLength + border);
                paintFontOutline(g2d, fistLetter, midx, placeY, getHighlightColor(), Color.BLACK);
                if (leftTopRiBot[1].length() > 1) {
                    g2d.setFont(subscriptFont);
                    paintFontOutline(g2d, leftTopRiBot[1].substring(1, leftTopRiBot[1].length()), midx + shiftx, placeY,
                        getHighlightColor(), Color.BLACK);
                }
            }
            if (getDisplayPreferences(ORIENTATION_LEFT)) {
                g2d.setFont(bigFont);
                paintFontOutline(g2d, leftTopRiBot[0].substring(0, 1), (float) (thickLength + border), midy,
                    getHighlightColor(), Color.BLACK);
                if (leftTopRiBot[0].length() > 1) {
                    g2d.setFont(subscriptFont);
                    paintFontOutline(g2d, leftTopRiBot[0].substring(1, leftTopRiBot[0].length()),
                        (float) (thickLength + border + shiftx), midy, getHighlightColor(), Color.BLACK);
                }
            }
            if (getDisplayPreferences(ORIENTATION_BOTTOM)) {
                g2d.setFont(bigFont);
                paintFontOutline(g2d, leftTopRiBot[3].substring(0, 1), midx,
                    (float) (bounds.height - (thickLength + border)), getHighlightColor(), Color.BLACK);
                if (leftTopRiBot[3].length() > 1) {
                    g2d.setFont(subscriptFont);
                    paintFontOutline(g2d, leftTopRiBot[3].substring(1, leftTopRiBot[3].length()), midx + shiftx,
                        (float) (bounds.height - (thickLength + border)), getHighlightColor(), Color.BLACK);
                }
            }
            if (getDisplayPreferences(ORIENTATION_RIGHT)) {
                g2d.setFont(bigFont);
                float placeX = (float) (bounds.width - (thickLength + border + (2 * shiftx)));
                paintFontOutline(g2d, leftTopRiBot[2].substring(0, 1), placeX, midy, getHighlightColor(), Color.BLACK);
                if (leftTopRiBot[2].length() > 1) {
                    g2d.setFont(subscriptFont);
                    paintFontOutline(g2d, leftTopRiBot[2].substring(1, leftTopRiBot[2].length()), placeX + shiftx, midy,
                        getHighlightColor(), Color.BLACK);
                }
            }
            g2d.setFont(oldFont);
        }
    }

    private void drawLUT(Graphics2D g2d) {
        // Not implemented yet
    }

    public void paintFontOutline(Graphics2D g2, String str, float x, float y, Color front, Color back) {
        g2.setPaint(back);
        g2.drawString(str, x - getOutlineDif(), y - getOutlineDif());
        g2.drawString(str, x - getOutlineDif(), y);
        g2.drawString(str, x - getOutlineDif(), y + getOutlineDif());
        g2.drawString(str, x, y - getOutlineDif());
        g2.drawString(str, x, y + getOutlineDif());
        g2.drawString(str, x + getOutlineDif(), y - getOutlineDif());
        g2.drawString(str, x + getOutlineDif(), y);
        g2.drawString(str, x + getOutlineDif(), y + getOutlineDif());
        g2.setPaint(front);
        g2.drawString(str, x, y);
    }

    protected float getOutlineDif() {
        return 1f;
    }

    /**
     * Here so it can be Overriden.
     * 
     * @param line
     *            Line to outline
     * @return Outline rectangle.
     */
    protected Shape getOutLine(Line2D line) {
        Rectangle2D r = line.getBounds2D();
        r.setFrame(r.getX() - 1.0, r.getY() - 1.0, r.getWidth() + 2.0, r.getHeight() + 2.0);
        return r;
    }

    private boolean noNullStrings(String[] leftTopRiBot) {
        if (leftTopRiBot == null) {
            return false;
        }
        for (String string : leftTopRiBot) {
            if (string == null) {
                return false;
            }
        }
        return true;
    }

    public static double ajustShowScale(double ratio, int maxLength) {
        int digits = (int) ((Math.log(maxLength * ratio) / Math.log(10)) + 1);
        double scaleLength = Math.pow(10, digits);
        double scaleSize = scaleLength / ratio;

        int loop = 0;
        while ((int) scaleSize > maxLength) {
            scaleLength /= findGeometricSuite(scaleLength);
            scaleSize = scaleLength / ratio;
            loop++;
            if (loop > 50) {
                return 0.0;
            }
        }
        return scaleSize;
    }

    public static double findGeometricSuite(double length) {
        int shift = (int) ((Math.log(length) / Math.log(10)) + 0.1);
        int firstDigit = (int) (length / Math.pow(10, shift) + 0.5);
        if (firstDigit == 5) {
            return 2.5;
        }
        return 2.0;

    }

    public static String ajustLengthDisplay(double scaleLength, Unit[] unit) {
        double ajustScaleLength = scaleLength;

        Unit ajustUnit = unit[0];

        if (scaleLength < 1.0) {
            Unit down = ajustUnit;
            while ((down = down.getDownUnit()) != null) {
                double length = scaleLength * down.getConversionRatio(unit[0].getConvFactor());
                if (length > 1) {
                    ajustUnit = down;
                    ajustScaleLength = length;
                    break;
                }
            }
        } else if (scaleLength > 10.0) {
            Unit up = ajustUnit;
            while ((up = up.getUpUnit()) != null) {
                double length = scaleLength * up.getConversionRatio(unit[0].getConvFactor());
                if (length < 1) {
                    break;
                }
                ajustUnit = up;
                ajustScaleLength = length;
            }
        }
        // Trick to keep the value as a return parameter
        unit[0] = ajustUnit;
        if (ajustScaleLength < 1.0) {
            return ajustScaleLength < 0.001 ? DecFormater.scientificFormat(ajustScaleLength)
                : DecFormater.fourDecimal(ajustScaleLength);
        }
        return ajustScaleLength > 50000.0 ? DecFormater.scientificFormat(ajustScaleLength)
            : DecFormater.twoDecimal(ajustScaleLength);
    }

    protected abstract boolean ownerHasContent();

    protected abstract boolean isOwnerContentReadable();

    protected abstract Rectangle getOwnerBounds();

    /**
     * Draw the text on the corners.
     * 
     * @param g2d
     */
    public abstract void paintTextCorners(Graphics2D g2d);

    /** @return left Top Ri Bot */
    public abstract String[] getContent4OrientationFlags();

    /** @return Pixel Spacing unit tu use on Scale. */
    public abstract Unit getOwnerPixelSpacingUnit();

    /** @return Owner object's zoom factor. */
    public abstract double getOwnerZoomFactor();

    /**
     * Owner's content pixel size. Return 0 if its unknown ot not trustable.
     *
     * @return Owner's content pixel size.
     */
    public abstract double getOwnerPixelSize();

    /** @return Owner's content dimensionsl */
    public abstract Dimension getOwnerContentDimensions();

    /** @return Owner's content rescaleX (see ImageElement:getRescaleX). */
    public abstract double getOwnerContentRescaleX();

    public abstract double getOwnerContentRescaleY();

    /**
     * @return Owner's content PixelSizeCalibrationDescription. (see DicomImageElement constructor).
     */
    public abstract String getPixelSizeCalibrationDescription();

    protected class ScaleHelper {
        Graphics2D g2d;
        Rectangle canvasBounds;
        Unit unit;
        double scale;
        double scaleSizex;
        double scaleSizey;

        protected ScaleHelper(Graphics2D g2d, Rectangle bounds, double zoomFactor, double pixelSize, Unit unit,
            Dimension sourceDim, double rescaleX, double rescaleY, String pixSizeDesc) {

            this.g2d = g2d;
            this.unit = unit;
            scale = pixelSize / zoomFactor;
            canvasBounds = bounds;
            scaleSizex =
                ajustShowScale(scale, (int) Math.min(zoomFactor * sourceDim.getWidth() * rescaleX, bounds.width / 2.0));
            scaleSizey = ajustShowScale(scale,
                (int) Math.min(zoomFactor * sourceDim.getHeight() * rescaleY, bounds.height / 2.0));

        }

        protected void drawBottomScale() {
            if (scaleSizex > 30.0d) {
                Unit[] unitToPaint = { unit }; // may be modifyed
                String str = ajustLengthDisplay(scaleSizex * scale, unitToPaint);
                str += " " + unitToPaint[0].getAbbreviation();

                double posx = canvasBounds.width / 2.0 - scaleSizex / 2.0;
                double posy = canvasBounds.height - border;

                drawScale(str, scaleSizex, posx, posy, -1, 0);
            }
        }

        private void drawTopScale() {
            if (scaleSizex > 30.0d) {
                Unit[] unitToPaint = { unit }; // may be modifyed
                String str = ajustLengthDisplay(scaleSizex * scale, unitToPaint);
                str += " " + unitToPaint[0].getAbbreviation();

                double posx = canvasBounds.width / 2.0 - scaleSizex / 2.0;
                double posy = border;

                drawScale(str, scaleSizex, posx, posy, 1, 0);
            }
        }

        private void drawLeftScale() {
            if (scaleSizey > 30.0d) {
                Unit[] unitToPaint = { unit }; // may be modifyed
                String str = ajustLengthDisplay(scaleSizey * scale, unitToPaint);
                str += " " + unitToPaint[0].getAbbreviation();

                double posx = border; // do not account for outline
                double posy = canvasBounds.height / 2.0 - scaleSizey / 2.0;

                drawScale(str, scaleSizey, posx, posy, 1, 1);

            }
        }

        private void drawRightScale() {
            if (scaleSizey > 30.0d) {
                Unit[] unitToPaint = { unit }; // may be modifyed
                String str = ajustLengthDisplay(scaleSizey * scale, unitToPaint);
                str += " " + unitToPaint[0].getAbbreviation();

                double posx = canvasBounds.width - border;
                double posy = canvasBounds.height / 2.0 - scaleSizey / 2.0;

                drawScale(str, scaleSizey, posx, posy, -1, 1);
            }
        }

        private void drawScale(String str, double scaleSize, double posx, double posy, int thickMultiplyer,
            int orientation) {
            // orientation: 0=horiz / 1=vertical

            // old g2d
            Stroke oldStroke = g2d.getStroke();
            Paint oldPaint = g2d.getPaint();

            final float fontHeight = FontTools.getAccurateFontHeight(g2d);
            g2d.setStroke(new BasicStroke(fontHeight / 10));
            g2d.setPaint(Color.black); // TODO getOutlineColor
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            double thickLength = g2d.getFont().getSize() * 1.5f;
            thickLength = thickLength < 5.0 ? 5.0 : thickLength;

            int divisor = str.indexOf("5") == -1 ? str.indexOf("2") == -1 ? 10 : 2 : 5;
            double midThick = thickLength * 2.0 / 3.0;
            double smallThick = thickLength / 3.0;
            double divSquare = scaleSize / divisor;

            double targetX = posx + scaleSize;
            double targetY = posy;
            if (orientation == 1) {
                targetX = posx;
                targetY = posy + scaleSize;
            }

            // Main line Outline
            Line2D line = new Line2D.Double(posx, posy, targetX, targetY);
            g2d.draw(getOutLine(line));

            // First thick Outline
            if (orientation == 0) {
                line.setLine(posx, posy + (thickLength * thickMultiplyer), posx, posy);
            } else {
                line.setLine(posx, posy, posx + (thickLength * thickMultiplyer), posy);
            }
            g2d.draw(getOutLine(line));

            // Last thick Outline
            if (orientation == 0) {
                line.setLine(targetX, targetY + (thickLength * thickMultiplyer), targetX, targetY);
            } else {
                line.setLine(targetX, targetY, targetX + (thickLength * thickMultiplyer), targetY);
            }
            g2d.draw(getOutLine(line));

            // Midthicks Outlines
            for (int i = 1; i < divisor; i++) {
                if (orientation == 0) {
                    line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy + (midThick * thickMultiplyer));
                } else {
                    line.setLine(posx, posy + divSquare * i, posx + (midThick * thickMultiplyer), posy + divSquare * i);
                }
                g2d.draw(getOutLine(line));
            }

            // smallThick Outlines
            if (divSquare > (90 / 12 * fontHeight)) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        if (orientation == 0) {
                            double secBar = posx + divSquare * i + secondSquare * k;
                            line.setLine(secBar, posy, secBar, posy + (smallThick * thickMultiplyer));
                        } else {
                            double secBar = posy + divSquare * i + secondSquare * k;
                            line.setLine(posx, secBar, posx + (smallThick * thickMultiplyer), secBar);
                        }
                        g2d.draw(getOutLine(line));
                    }
                }
            }

            g2d.setPaint(Color.white);
            line.setLine(posx, posy, targetX, targetY);
            g2d.draw(line);

            if (orientation == 0) {
                line.setLine(posx, posy + (thickLength * thickMultiplyer), posx, posy);
            } else {
                line.setLine(posx, posy, posx + (thickLength * thickMultiplyer), posy);
            }
            g2d.draw(line);

            if (orientation == 0) {
                line.setLine(targetX, targetY + (thickLength * thickMultiplyer), targetX, targetY);
            } else {
                line.setLine(targetX, targetY, targetX + (thickLength * thickMultiplyer), targetY);
            }
            g2d.draw(line);

            for (int i = 1; i < divisor; i++) {
                if (orientation == 0) {
                    line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy + (midThick * thickMultiplyer));
                } else {
                    line.setLine(posx, posy + divSquare * i, posx + (midThick * thickMultiplyer), posy + divSquare * i);
                }
                g2d.draw(line);
            }
            if (divSquare > (90 / 12 * fontHeight)) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        if (orientation == 0) {
                            double secBar = posx + divSquare * i + secondSquare * k;
                            line.setLine(secBar, posy, secBar, posy + (smallThick * thickMultiplyer));
                        } else {
                            double secBar = posy + divSquare * i + secondSquare * k;
                            line.setLine(posx, secBar, posx + (smallThick * thickMultiplyer), secBar);
                        }
                        g2d.draw(line);
                    }
                }
            }

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

            paintStrings(str, posx, posy, fontHeight, thickMultiplyer, orientation);

            // recover old settings
            g2d.setPaint(oldPaint);
            g2d.setStroke(oldStroke);

        }

        private void paintStrings(String str, double posx, double posy, float fontHeight, int thickMultiplyer,
            int orientation) {

            float drawY = (float) (posy + (fontHeight * thickMultiplyer));
            float drawX = (float) (posx + scaleSizex + (fontHeight / 2));
            if (orientation == 0) {
                final String pixSizeDesc = getPixelSizeCalibrationDescription();
                if (StringUtil.hasText(pixSizeDesc)) {
                    paintFontOutline(g2d, pixSizeDesc, drawX, drawY, Color.white, Color.black);
                }
                drawY = drawY + fontHeight;
            } else {
                drawX = (float) posx;
                if (thickMultiplyer == -1) {
                    drawX = drawX - (float) g2d.getFontMetrics().getStringBounds(str, g2d).getWidth();
                }
                drawY = (float) posy - (fontHeight / 2);
            }

            paintFontOutline(g2d, str, drawX, drawY, Color.white, Color.black);
        }
    }

}