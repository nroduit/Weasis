/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.ui.model.layer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.osgi.service.prefs.Preferences;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.op.ByteLutCollection;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.image.DisplayByteLut;
import org.weasis.core.ui.editor.image.HistogramData;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.core.ui.pref.ViewSetting;
import org.weasis.opencv.data.PlanarImage;

public abstract class AbstractInfoLayer<E extends ImageElement> extends DefaultUUID implements LayerAnnotation {

    private static final long serialVersionUID = 1338490067849040408L;

    public static final String P_ALL_VIEWS = "annotations.all.views"; //$NON-NLS-1$
    public static volatile boolean applyToAllView = true;
    public static final Map<String, Boolean> defaultDisplayPreferences = new HashMap<>();
    private static final Map<String, String> conversionMapForStorage = new HashMap<>();
    static {
        defaultDisplayPreferences.put(ANNOTATIONS, true);
        defaultDisplayPreferences.put(MIN_ANNOTATIONS, false);
        defaultDisplayPreferences.put(ANONYM_ANNOTATIONS, false);
        defaultDisplayPreferences.put(SCALE, true);
        defaultDisplayPreferences.put(LUT, false);
        defaultDisplayPreferences.put(IMAGE_ORIENTATION, true);
        defaultDisplayPreferences.put(WINDOW_LEVEL, true);
        defaultDisplayPreferences.put(ZOOM, true);
        defaultDisplayPreferences.put(ROTATION, false);
        defaultDisplayPreferences.put(FRAME, true);
        defaultDisplayPreferences.put(PIXEL, true);

        conversionMapForStorage.put(ANNOTATIONS, "annotations"); //$NON-NLS-1$
        conversionMapForStorage.put(MIN_ANNOTATIONS, "minAnnotations"); //$NON-NLS-1$
        conversionMapForStorage.put(ANONYM_ANNOTATIONS, "anonym"); //$NON-NLS-1$
        conversionMapForStorage.put(SCALE, "scale"); //$NON-NLS-1$
        conversionMapForStorage.put(LUT, "lut"); //$NON-NLS-1$
        conversionMapForStorage.put(IMAGE_ORIENTATION, "orientation"); //$NON-NLS-1$
        conversionMapForStorage.put(WINDOW_LEVEL, "wl"); //$NON-NLS-1$
        conversionMapForStorage.put(ZOOM, "zoom"); //$NON-NLS-1$
        conversionMapForStorage.put(ROTATION, "rotation"); //$NON-NLS-1$
        conversionMapForStorage.put(FRAME, "frame"); //$NON-NLS-1$
        conversionMapForStorage.put(PIXEL, "pixel"); //$NON-NLS-1$
    }

    protected static final int P_BORDER = 10;

    protected final HashMap<String, Boolean> displayPreferences = new HashMap<>();
    protected boolean visible = true;
    protected static final Color color = Color.yellow;
    protected final ViewCanvas<E> view2DPane;
    protected PixelInfo pixelInfo = null;
    protected final Rectangle pixelInfoBound;
    protected final Rectangle preloadingProgressBound;
    protected int border = P_BORDER;
    protected double thickLength = 15.0;
    protected boolean showBottomScale = true;
    protected String name;

    public AbstractInfoLayer(ViewCanvas<E> view2DPane) {
        this.view2DPane = view2DPane;
        this.pixelInfoBound = new Rectangle();
        this.preloadingProgressBound = new Rectangle();
    }

    public static void applyPreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            Preferences pref = p.node("infolayer"); //$NON-NLS-1$
            applyToAllView = pref.getBoolean("allViews", true); //$NON-NLS-1$

            Iterator<Entry<String, Boolean>> d = defaultDisplayPreferences.entrySet().iterator();
            while (d.hasNext()) {
                Entry<String, Boolean> v = d.next();
                v.setValue(pref.getBoolean(conversionMapForStorage.get(v.getKey()), v.getValue()));
            }
        }
    }

    public static void savePreferences(Preferences prefs) {
        if (prefs != null) {
            Preferences p = prefs.node(ViewSetting.PREFERENCE_NODE);
            Preferences pref = p.node("infolayer"); //$NON-NLS-1$
            BundlePreferences.putBooleanPreferences(pref, "allViews", applyToAllView); //$NON-NLS-1$

            Iterator<Entry<String, String>> d = conversionMapForStorage.entrySet().iterator();
            while (d.hasNext()) {
                Entry<String, String> v = d.next();
                BundlePreferences.putBooleanPreferences(pref, v.getValue(), defaultDisplayPreferences.get(v.getKey()));
            }
        }
    }

    public static Boolean setDefaultDisplayPreferencesValue(String item, Boolean selected) {
        Boolean selected2 = Optional.ofNullable(defaultDisplayPreferences.get(item)).orElse(Boolean.FALSE);
        defaultDisplayPreferences.put(item, selected);
        return !Objects.equals(selected, selected2);
    }

    @Override
    public void resetToDefault() {
        displayPreferences.putAll(defaultDisplayPreferences);
    }

    @Override
    public Boolean isShowBottomScale() {
        return showBottomScale;
    }

    @Override
    public void setShowBottomScale(Boolean showBottomScale) {
        this.showBottomScale = showBottomScale;
    }

    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setVisible(Boolean visible) {
        this.visible = Optional.ofNullable(visible).orElse(getType().getVisible());
    }

    @Override
    public Integer getLevel() {
        return getType().getLevel();
    }

    @Override
    public void setLevel(Integer i) {
        // Do nothing
    }

    @Override
    public Integer getBorder() {
        return border;
    }

    @Override
    public void setBorder(Integer border) {
        this.border = border;
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
    public void setName(String layerName) {
        this.name = layerName;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return Optional.ofNullable(getName()).orElse(getType().getDefaultName());
    }

    @Override
    public Boolean getDisplayPreferences(String item) {
        if (applyToAllView) {
            return Optional.ofNullable(defaultDisplayPreferences.get(item)).orElse(Boolean.FALSE);
        }
        return Optional.ofNullable(displayPreferences.getOrDefault(item, defaultDisplayPreferences.get(item)))
            .orElse(Boolean.FALSE);
    }

    @Override
    public Boolean setDisplayPreferencesValue(String displayItem, Boolean selected) {
        Boolean selected2 = getDisplayPreferences(displayItem);
        displayPreferences.put(displayItem, selected);
        return !Objects.equals(selected, selected2);
    }

    @Override
    public Rectangle getPreloadingProgressBound() {
        return preloadingProgressBound;
    }

    @Override
    public Rectangle getPixelInfoBound() {
        return pixelInfoBound;
    }

    @Override
    public void setPixelInfo(PixelInfo pixelInfo) {
        this.pixelInfo = pixelInfo;
    }

    @Override
    public PixelInfo getPixelInfo() {
        return pixelInfo;
    }

    public Rectangle2D getOutLine(Line2D l) {
        Rectangle2D r = l.getBounds2D();
        r.setFrame(r.getX() - 1.0, r.getY() - 1.0, r.getWidth() + 2.0, r.getHeight() + 2.0);
        return r;
    }

    public void drawLUT(Graphics2D g2, Rectangle bound, float midfontHeight) {
        WindLevelParameters p = getWinLeveParameters();
        if (p != null && bound.height > 350) {
            DisplayByteLut lut = getLut(p);
            if (lut == null) {
                return;
            }
            byte[][] table = lut.getLutTable();
            float length = table[0].length;

            int width = 0;
            for (ViewButton b : view2DPane.getViewButtons()) {
                if (b.isVisible() && b.getPosition() == GridBagConstraints.EAST) {
                    int w = b.getIcon().getIconWidth() + 5;
                    if (w > width) {
                        width = w;
                    }
                }
            }
            float x = bound.width - 30f - width;
            float y = bound.height / 2f - length / 2f;

            g2.setPaint(Color.BLACK);
            Rectangle2D.Float rect = new Rectangle2D.Float(x - 11f, y - 2f, 12f, 2f);
            g2.draw(rect);
            int separation = 4;
            float step = length / separation;
            for (int i = 1; i < separation; i++) {
                float posY = y + i * step;
                rect.setRect(x - 6f, posY - 1f, 7f, 2f);
                g2.draw(rect);
            }
            rect.setRect(x - 11f, y + length, 12f, 2f);
            g2.draw(rect);
            rect.setRect(x - 2f, y - 2f, 23f, length + 4f);
            g2.draw(rect);

            g2.setPaint(Color.WHITE);
            ImageElement img = view2DPane.getImage();
            double pixMin = img.getPixelMin();
            double pixMax = img.getPixelMax();
            HistogramData data =
                new HistogramData(new float[0], lut, 0, p, pixMin, pixMax, view2DPane.getMeasurableLayer());
            data.updateVoiLut(view2DPane);
            double binFactor = (pixMax - pixMin) / (length - 1);
            double stepWindow = (pixMax - pixMin)  / separation;

            Line2D.Float line = new Line2D.Float();
            for (int i = 0; i <= separation; i++) {
                float posY = y + i * step;
                line.setLine(x - 5f, posY, x - 1f, posY);
                g2.draw(line);
                double level = data.getLayer().pixelToRealValue((separation - i) * stepWindow + pixMin);
                String str = DecFormater.allNumber(level);
                AbstractGraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 7,
                    posY + midfontHeight);
            }
            rect.setRect(x - 1f, y - 1f, 21f, length + 2f);
            g2.draw(rect);

            int limit = table[0].length - 1;
            for (int k = 0; k <= limit; k++) {
                double level = data.getLayer().pixelToRealValue( (limit - k) * binFactor + pixMin);
                Color cLut = data.getFinalVoiLutColor(level);
                g2.setPaint(cLut);
                rect.setRect(x, y + k, 19f, 1f);
                g2.draw(rect);
            }
        }
    }
    


    private WindLevelParameters getWinLeveParameters() {
        if (view2DPane != null) {
            OpManager dispOp = view2DPane.getDisplayOpManager();
            WindowOp wlOp = (WindowOp) dispOp.getNode(WindowOp.OP_NAME);
            if (wlOp != null) {
                return wlOp.getWindLevelParameters();
            }
        }
        return null;
    }

    private DisplayByteLut getLut(WindLevelParameters p) {
        DisplayByteLut lut = null;
        if (view2DPane != null) {
            int channels = view2DPane.getSourceImage().channels();
            if (channels == 1) {
                DisplayByteLut disLut = null;
                OpManager dispOp = view2DPane.getDisplayOpManager();
                PseudoColorOp lutOp = (PseudoColorOp) dispOp.getNode(PseudoColorOp.OP_NAME);
                if (lutOp != null) {
                    ByteLut lutTable = (ByteLut) lutOp.getParam(PseudoColorOp.P_LUT);
                    if (lutTable != null && lutTable.getLutTable() != null) {
                        disLut = new DisplayByteLut(lutTable);
                    }
                }

                if (disLut == null) {
                    disLut = new DisplayByteLut(ByteLutCollection.Lut.GRAY.getByteLut());
                }
                disLut.setInvert(p.isInverseLut());
                lut = disLut;
            }
        }

        if (lut == null) {
            lut = new DisplayByteLut(ByteLutCollection.Lut.GRAY.getByteLut());
        }
        return lut;
    }

    public void drawScale(Graphics2D g2d, Rectangle bound, float fontHeight) {
        ImageElement image = view2DPane.getImage();
        PlanarImage source = image.getImage();
        if (source == null) {
            return;
        }

        double zoomFactor = view2DPane.getViewModel().getViewScale();

        double scale = image.getPixelSize() / zoomFactor;
        double scaleSizex =
            ajustShowScale(scale, (int) Math.min(zoomFactor * source.width() * image.getRescaleX(), bound.width / 2.0));

        if (showBottomScale && scaleSizex > 50.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizex * scale, unit);
            g2d.setStroke(new BasicStroke(1.0F));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(Color.BLACK);

            double posx = bound.width / 2.0 - scaleSizex / 2.0;
            double posy = bound.height - border - 1.5; // - 1.5 is for outline
            Line2D line = new Line2D.Double(posx, posy, posx + scaleSizex, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy - thickLength, posx, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx + scaleSizex, posy - thickLength, posx + scaleSizex, posy);
            g2d.draw(getOutLine(line));
            int divisor = str.indexOf("5") == -1 ? str.indexOf("2") == -1 ? 10 : 2 : 5; //$NON-NLS-1$ //$NON-NLS-2$
            double midThick = thickLength * 2.0 / 3.0;
            double smallThick = thickLength / 3.0;
            double divSquare = scaleSizex / divisor;
            for (int i = 1; i < divisor; i++) {
                line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy - midThick);
                g2d.draw(getOutLine(line));
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posx + divSquare * i + secondSquare * k;
                        line.setLine(secBar, posy, secBar, posy - smallThick);
                        g2d.draw(getOutLine(line));
                    }
                }
            }

            g2d.setPaint(Color.white);
            line.setLine(posx, posy, posx + scaleSizex, posy);
            g2d.draw(line);
            line.setLine(posx, posy - thickLength, posx, posy);
            g2d.draw(line);
            line.setLine(posx + scaleSizex, posy - thickLength, posx + scaleSizex, posy);
            g2d.draw(line);

            for (int i = 0; i < divisor; i++) {
                line.setLine(posx + divSquare * i, posy, posx + divSquare * i, posy - midThick);
                g2d.draw(line);
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posx + divSquare * i + secondSquare * k;
                        line.setLine(secBar, posy, secBar, posy - smallThick);
                        g2d.draw(line);
                    }
                }
            }
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);
            String pixSizeDesc = image.getPixelSizeCalibrationDescription();
            if (StringUtil.hasText(pixSizeDesc)) {
                AbstractGraphicLabel.paintFontOutline(g2d, pixSizeDesc, (float) (posx + scaleSizex + 5),
                    (float) posy - fontHeight);
            }
            str += " " + unit[0].getAbbreviation(); //$NON-NLS-1$
            AbstractGraphicLabel.paintFontOutline(g2d, str, (float) (posx + scaleSizex + 5), (float) posy);
        }

        double scaleSizeY = ajustShowScale(scale,
            (int) Math.min(zoomFactor * source.height() * image.getRescaleY(), bound.height / 2.0));

        if (scaleSizeY > 30.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizeY * scale, unit);

            float strokeWidth = g2d.getFont().getSize() / 15.0f;
            strokeWidth = strokeWidth < 1.0f ? 1.0f : strokeWidth;
            g2d.setStroke(new BasicStroke(strokeWidth));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(Color.black);

            double posx = border - 1.5f; // -1.5 for outline
            double posy = bound.height / 2.0 - scaleSizeY / 2.0;
            Line2D line = new Line2D.Double(posx, posy, posx, posy + scaleSizeY);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy, posx + thickLength, posy);
            g2d.draw(getOutLine(line));
            line.setLine(posx, posy + scaleSizeY, posx + thickLength, posy + scaleSizeY);
            g2d.draw(getOutLine(line));
            int divisor = str.indexOf("5") == -1 ? str.indexOf("2") == -1 ? 10 : 2 : 5; //$NON-NLS-1$ //$NON-NLS-2$
            double divSquare = scaleSizeY / divisor;
            double midThick = thickLength * 2.0 / 3.0;
            double smallThick = thickLength / 3.0;
            for (int i = 0; i < divisor; i++) {
                line.setLine(posx, posy + divSquare * i, posx + midThick, posy + divSquare * i);
                g2d.draw(getOutLine(line));
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posy + divSquare * i + secondSquare * k;
                        line.setLine(posx, secBar, posx + smallThick, secBar);
                        g2d.draw(getOutLine(line));
                    }
                }
            }

            g2d.setPaint(Color.WHITE);
            line.setLine(posx, posy, posx, posy + scaleSizeY);
            g2d.draw(line);
            line.setLine(posx, posy, posx + thickLength, posy);
            g2d.draw(line);
            line.setLine(posx, posy + scaleSizeY, posx + thickLength, posy + scaleSizeY);
            g2d.draw(line);
            for (int i = 0; i < divisor; i++) {
                line.setLine(posx, posy + divSquare * i, posx + midThick, posy + divSquare * i);
                g2d.draw(line);
            }
            if (divSquare > 90) {
                double secondSquare = divSquare / 10.0;
                for (int i = 0; i < divisor; i++) {
                    for (int k = 1; k < 10; k++) {
                        double secBar = posy + divSquare * i + secondSquare * k;
                        line.setLine(posx, secBar, posx + smallThick, secBar);
                        g2d.draw(line);
                    }
                }
            }

            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_DEFAULT);

            AbstractGraphicLabel.paintFontOutline(g2d, str + " " + unit[0].getAbbreviation(), (int) posx, //$NON-NLS-1$
                (int) (posy - 5 * strokeWidth));
        }

    }

    private double ajustShowScale(double ratio, int maxLength) {
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

    public double findGeometricSuite(double length) {
        int shift = (int) ((Math.log(length) / Math.log(10)) + 0.1);
        int firstDigit = (int) (length / Math.pow(10, shift) + 0.5);
        if (firstDigit == 5) {
            return 2.5;
        }
        return 2.0;

    }

    public String ajustLengthDisplay(double scaleLength, Unit[] unit) {
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

}