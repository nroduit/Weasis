/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d;

import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashMap;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.util.StringUtil;

/**
 * The Class InfoLayer.
 *
 * @author Nicolas Roduit
 */
public class InfoLayer extends AbstractInfoLayer<ImageElement> {

  public InfoLayer(ViewCanvas<ImageElement> view2DPane) {
    this(view2DPane, true);
  }

  public InfoLayer(ViewCanvas<ImageElement> view2DPane, boolean useGlobalPreferences) {
    super(view2DPane, useGlobalPreferences);
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
  public LayerAnnotation getLayerCopy(ViewCanvas view2dPane, boolean useGlobalPreferences) {
    InfoLayer layer = new InfoLayer(view2DPane, useGlobalPreferences);
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
    FontMetrics fontMetrics = g2.getFontMetrics();
    final Rectangle bound = view2DPane.getJComponent().getBounds();
    int minSize = fontMetrics.stringWidth(Messages.getString("InfoLayer.error_msg")) * 3;
    if (!visible || image == null || minSize > bound.width || minSize > bound.height) {
      return;
    }

    Object[] oldRenderingHints =
        GuiUtils.setRenderingHints(g2, true, false, view2DPane.requiredTextAntialiasing());

    OpManager disOp = view2DPane.getDisplayOpManager();
    float midX = bound.width / 2f;
    float midY = bound.height / 2f;
    final int fontHeight = fontMetrics.getHeight();
    final int midFontHeight = fontHeight - fontMetrics.getDescent();

    g2.setPaint(color);

    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline

    if (!image.isReadable()) {
      String message = Messages.getString("InfoLayer.error_msg");
      float y = midY;
      FontTools.paintColorFontOutline(
          g2,
          message,
          midX - g2.getFontMetrics().stringWidth(message) / 2.0F,
          y,
          IconColor.ACTIONS_RED.getColor());
      String[] desc = image.getMediaReader().getReaderDescription();
      if (desc != null) {
        for (String str : desc) {
          if (StringUtil.hasText(str)) {
            y += fontHeight;
            FontTools.paintColorFontOutline(
                g2,
                str,
                midX - g2.getFontMetrics().stringWidth(str) / 2.0F,
                y,
                IconColor.ACTIONS_RED.getColor());
          }
        }
      }
    }

    if (image.isReadable() && getDisplayPreferences(SCALE)) {
      drawScale(g2, bound, fontHeight);
    }
    if (image.isReadable() && getDisplayPreferences(LUT)) {
      drawLUT(g2, bound, midFontHeight);
    }

    if (getDisplayPreferences(PIXEL)) {
      StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer.pix"));
      sb.append(StringUtil.COLON_AND_SPACE);
      if (pixelInfo != null) {
        sb.append(pixelInfo.getPixelValueText());
        sb.append(" - ");
        sb.append(pixelInfo.getPixelPositionText());
      }
      String str = sb.toString();
      FontTools.paintFontOutline(g2, str, border, drawY);
      drawY -= fontHeight;
      pixelInfoBound.setBounds(
          border,
          (int) drawY + fontMetrics.getDescent(),
          fontMetrics.stringWidth(str) + GuiUtils.getScaleLength(2),
          fontHeight);
    }
    if (getDisplayPreferences(WINDOW_LEVEL)) {
      StringBuilder sb = new StringBuilder();
      Number window = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd());
      Number level = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd());
      if (window != null && level != null) {
        sb.append(ActionW.WINLEVEL.getTitle());
        sb.append(StringUtil.COLON_AND_SPACE);
        sb.append(DecFormatter.allNumber(window));
        sb.append("/");
        sb.append(DecFormatter.allNumber(level));
      }
      FontTools.paintFontOutline(g2, sb.toString(), border, drawY);
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(ZOOM)) {
      FontTools.paintFontOutline(
          g2,
          Messages.getString("InfoLayer.zoom")
              + StringUtil.COLON_AND_SPACE
              + DecFormatter.percentTwoDecimal(view2DPane.getViewModel().getViewScale()),
          border,
          drawY);
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(ROTATION)) {
      FontTools.paintFontOutline(
          g2,
          Messages.getString("InfoLayer.angle")
              + StringUtil.COLON_AND_SPACE
              + view2DPane.getActionValue(ActionW.ROTATION.cmd())
              + " °",
          border,
          drawY);
      drawY -= fontHeight;
    }

    if (getDisplayPreferences(FRAME)) {
      FontTools.paintFontOutline(
          g2,
          Messages.getString("InfoLayer.frame")
              + StringUtil.COLON_AND_SPACE
              + (view2DPane.getFrameIndex() + 1)
              + " / "
              + view2DPane
                  .getSeries()
                  .size(
                      (Filter<ImageElement>)
                          view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())),
          border,
          drawY);
      drawY -= fontHeight;
    }

    // if (getDisplayPreferences(ANNOTATIONS)) {
    // MediaSeries<ImageElement> series = view2DPane.getSeries();
    //
    // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
    // String str = synchLink != null && synchLink ? "linked" : "unlinked";
    // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - border,
    // drawY);
    //
    // }

    GuiUtils.resetRenderingHints(g2, oldRenderingHints);
  }
}
