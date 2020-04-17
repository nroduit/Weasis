/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.base.viewer2d;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.util.StringUtil;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;

/**
 * The Class InfoLayer.
 *
 * @author Nicolas Roduit
 */
public class InfoLayer extends AbstractInfoLayer<ImageElement> {
    private static final long serialVersionUID = 1782300490253793711L;

    public InfoLayer(ViewCanvas<ImageElement> view2DPane) {
        super(view2DPane);
        displayPreferences.put(ANNOTATIONS, true);
        displayPreferences.put(IMAGE_ORIENTATION, true);
        displayPreferences.put(SCALE, true);
        displayPreferences.put(LUT, false);
        displayPreferences.put(PIXEL, true);
        displayPreferences.put(WINDOW_LEVEL, true);
        displayPreferences.put(ZOOM, true);
        displayPreferences.put(ROTATION, false);
        displayPreferences.put(FRAME, true);
    }

    @Override
    public LayerAnnotation getLayerCopy(ViewCanvas view2dPane) {
        InfoLayer layer = new InfoLayer(view2DPane);
        HashMap<String, Boolean> prefs = layer.displayPreferences;
        prefs.put(ANNOTATIONS, getDisplayPreferences(ANNOTATIONS));
        prefs.put(SCALE, getDisplayPreferences(SCALE));
        prefs.put(LUT, getDisplayPreferences(LUT));
        prefs.put(PIXEL, getDisplayPreferences(PIXEL));
        prefs.put(WINDOW_LEVEL, getDisplayPreferences(WINDOW_LEVEL));
        prefs.put(ZOOM, getDisplayPreferences(ZOOM));
        prefs.put(ROTATION, getDisplayPreferences(ROTATION));
        return layer;
    }

    @Override
    public void paint(Graphics2D g2) {
        ImageElement image = view2DPane.getImage();
        if (!visible || image == null) {
            return;
        }

        OpManager disOp = view2DPane.getDisplayOpManager();
        final Rectangle bound = view2DPane.getJComponent().getBounds();
        float midx = bound.width / 2f;
        float midy = bound.height / 2f;

        g2.setPaint(color);

        final float fontHeight = FontTools.getAccurateFontHeight(g2);
        final float midfontHeight = fontHeight * FontTools.getMidFontHeightFactor();
        float drawY = bound.height - border - 1.5f; // -1.5 for outline

        if (!image.isReadable()) {
            String message = Messages.getString("InfoLayer.error_msg"); //$NON-NLS-1$
            float y = midy;
            AbstractGraphicLabel.paintColorFontOutline(g2, message, midx - g2.getFontMetrics().stringWidth(message) / 2.0F,
                y, Color.RED);
            String[] desc = image.getMediaReader().getReaderDescription();
            if (desc != null) {
                for (String str : desc) {
                    if (StringUtil.hasText(str)) {
                        y += fontHeight;
                        AbstractGraphicLabel.paintColorFontOutline(g2, str,
                            midx - g2.getFontMetrics().stringWidth(str) / 2.0F, y, Color.RED);
                    }
                }
            }
        }
        if (image.isReadable() && getDisplayPreferences(SCALE)) {
            drawScale(g2, bound, fontHeight);
        }
        if (image.isReadable() && getDisplayPreferences(LUT)) {
            drawLUT(g2, bound, midfontHeight);
        }

        if (getDisplayPreferences(PIXEL)) {
            StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer.pix")); //$NON-NLS-1$
            sb.append(StringUtil.COLON_AND_SPACE);
            if (pixelInfo != null) {
                sb.append(pixelInfo.getPixelValueText());
                sb.append(" - "); //$NON-NLS-1$
                sb.append(pixelInfo.getPixelPositionText());
            }
            String str = sb.toString();
            AbstractGraphicLabel.paintFontOutline(g2, str, border, drawY - 1);
            drawY -= fontHeight + 2;
            pixelInfoBound.setBounds(border - 2, (int) drawY + 3, g2.getFontMetrics().stringWidth(str) + 4,
                (int) fontHeight + 2);
            // g2.draw(pixelInfoBound);
        }
        if (getDisplayPreferences(WINDOW_LEVEL)) {
            StringBuilder sb = new StringBuilder();
            Number window = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd());
            Number level = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd());
            if (window != null && level != null) {
                sb.append(ActionW.WINLEVEL.getTitle());
                sb.append(StringUtil.COLON_AND_SPACE);
                sb.append(DecFormater.allNumber(window));
                sb.append("/");//$NON-NLS-1$
                sb.append(DecFormater.allNumber(level));
            }
            AbstractGraphicLabel.paintFontOutline(g2, sb.toString(), border, drawY);
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ZOOM)) {
            AbstractGraphicLabel.paintFontOutline(g2, Messages.getString("InfoLayer.zoom") + StringUtil.COLON_AND_SPACE //$NON-NLS-1$
                + DecFormater.percentTwoDecimal(view2DPane.getViewModel().getViewScale()), border, drawY);
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ROTATION)) {
            AbstractGraphicLabel.paintFontOutline(g2, Messages.getString("InfoLayer.angle") + StringUtil.COLON_AND_SPACE //$NON-NLS-1$
                + view2DPane.getActionValue(ActionW.ROTATION.cmd()) + " " //$NON-NLS-1$
                + Messages.getString("InfoLayer.angle_symb"), //$NON-NLS-1$
                border, drawY);
            drawY -= fontHeight;
        }

        if (getDisplayPreferences(FRAME)) {
            AbstractGraphicLabel.paintFontOutline(g2, Messages.getString("InfoLayer.frame") //$NON-NLS-1$
                + StringUtil.COLON_AND_SPACE + (view2DPane.getFrameIndex() + 1) + " / " //$NON-NLS-1$
                + view2DPane.getSeries()
                    .size((Filter<ImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())),
                border, drawY);
            drawY -= fontHeight;
        }

        // if (getDisplayPreferences(ANNOTATIONS)) {
        // MediaSeries<ImageElement> series = view2DPane.getSeries();
        //
        // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
        // String str = synchLink != null && synchLink ? "linked" : "unlinked";
        // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - border, drawY);
        //
        // }

    }
}
