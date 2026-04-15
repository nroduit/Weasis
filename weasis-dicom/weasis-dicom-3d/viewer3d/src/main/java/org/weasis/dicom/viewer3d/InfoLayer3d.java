/*
 * Copyright (c) 2013 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.InfoLayer;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import org.weasis.dicom.viewer3d.vr.View3d;

public class InfoLayer3d extends AbstractInfoLayer<DicomImageElement> {

  public InfoLayer3d(View3d view3d) {
    this(view3d, false);
  }

  public InfoLayer3d(View3d view3d, boolean useGlobalPreferences) {
    super(view3d, useGlobalPreferences);
    displayPreferences.put(LayerItem.ANNOTATIONS, true);
    displayPreferences.put(LayerItem.MIN_ANNOTATIONS, true);
    displayPreferences.put(LayerItem.ANONYM_ANNOTATIONS, false);
    displayPreferences.put(LayerItem.SCALE, false);
    displayPreferences.put(LayerItem.LUT, false);
    displayPreferences.put(LayerItem.IMAGE_ORIENTATION, true);
    displayPreferences.put(LayerItem.WINDOW_LEVEL, true);
    displayPreferences.put(LayerItem.ZOOM, true);
    displayPreferences.put(LayerItem.ROTATION, false);
    displayPreferences.put(LayerItem.FRAME, false);
    displayPreferences.put(LayerItem.PIXEL, false);
  }

  @Override
  public LayerAnnotation<DicomImageElement> getLayerCopy(
      ViewCanvas<DicomImageElement> view2DPane, boolean useGlobalPreferences) {
    InfoLayer layer = new InfoLayer(view2DPane, useGlobalPreferences);
    setLayerValue(layer, LayerItem.ANNOTATIONS);
    setLayerValue(layer, LayerItem.MIN_ANNOTATIONS);
    setLayerValue(layer, LayerItem.ANONYM_ANNOTATIONS);
    setLayerValue(layer, LayerItem.SCALE);
    setLayerValue(layer, LayerItem.LUT);
    setLayerValue(layer, LayerItem.IMAGE_ORIENTATION);
    setLayerValue(layer, LayerItem.WINDOW_LEVEL);
    setLayerValue(layer, LayerItem.ZOOM);
    setLayerValue(layer, LayerItem.ROTATION);
    setLayerValue(layer, LayerItem.FRAME);
    setLayerValue(layer, LayerItem.PIXEL);
    return layer;
  }

  protected void setLayerValue(InfoLayer layer, LayerItem item) {
    layer.setDisplayPreferencesValue(item, getDisplayPreferences(item));
  }

  @Override
  public View3d getView2DPane() {
    return (View3d) view2DPane;
  }

  protected boolean ownerHasContent() {
    return getView2DPane().isReadyForRendering();
  }

  private boolean shouldPaint(FontMetrics fontMetrics, Rectangle bound) {
    int minSize =
        fontMetrics.stringWidth(
                org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.msg_outside_levels"))
            * 2;
    return visible && minSize <= bound.width && minSize / 2 <= bound.height;
  }

  @Override
  public void paint(Graphics2D g2d) {
    FontMetrics fontMetrics =
        view2DPane.getJComponent().getFontMetrics(FontItem.MICRO_SEMIBOLD.getFont());
    final Rectangle bound = view2DPane.getJComponent().getBounds();
    if (!shouldPaint(fontMetrics, bound)) {
      if (visible) {
        setPosition(Position.BottomLeft, border, (double) bound.height - border);
        setDefaultCornerPositions(bound);
        drawExtendedActions(g2d);
      }
      return;
    }

    Object[] oldRenderingHints =
        GuiUtils.setRenderingHints(g2d, true, false, view2DPane.requiredTextAntialiasing());

    try {
      paintContent(g2d, bound);
    } finally {
      GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
    }
  }

  private void paintContent(Graphics2D g2d, Rectangle bound) {
    Modality mod =
        Modality.getModality(TagD.getTagValue(view2DPane.getSeries(), Tag.Modality, String.class));
    ModalityInfoData modality = ModalityView.getModlatityInfos(mod);

    FontMetrics fontMetrics = g2d.getFontMetrics();
    final int fontHeight = fontMetrics.getHeight();
    thickLength = Math.max(fontHeight, GuiUtils.getScaleLength(5.0));

    g2d.setPaint(Color.BLACK);

    boolean hideMin = !getDisplayPreferences(LayerItem.MIN_ANNOTATIONS);
    View3d owner = getView2DPane();
    DicomVolTexture imSeries = owner.getVolTexture();

    float drawY = paintBottomLeftInfo(g2d, owner, imSeries, bound, fontHeight, hideMin);
    setPosition(Position.BottomLeft, border, drawY - GuiUtils.getScaleLength(5));

    if (getDisplayPreferences(LayerItem.ANNOTATIONS)) {
      paintAnnotations(g2d, modality, imSeries, bound, fontHeight, hideMin, mod);
    } else {
      setDefaultCornerPositions(bound);
    }

    drawExtendedActions(g2d);
  }

  private float paintBottomLeftInfo(
      Graphics2D g2d,
      View3d owner,
      DicomVolTexture imSeries,
      Rectangle bound,
      int fontHeight,
      boolean hideMin) {
    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f);

    drawY -= fontHeight;
    if (imSeries.getVolume().isTransformed()) {
      String message = org.weasis.dicom.viewer2d.Messages.getString("geometric.transformation.msg");
      FontTools.paintColorFontOutline(
          g2d, message, border, drawY, IconColor.ACTIONS_RED.getColor());
      drawY -= fontHeight;
    } else if (imSeries.getVolume().isSkipRectification()) {
      String message = org.weasis.dicom.viewer2d.Messages.getString("skip.rectification.msg");
      FontTools.paintColorFontOutline(
          g2d, message, border, drawY, IconColor.ACTIONS_RED.getColor());
      drawY -= fontHeight;
    }

    if (getDisplayPreferences(LayerItem.WINDOW_LEVEL) && hideMin) {
      drawY = paintWindowLevel(g2d, owner, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ZOOM) && hideMin) {
      drawY = paintZoom(g2d, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ROTATION) && hideMin) {
      drawY = paintRotation(g2d, drawY, fontHeight);
    }

    return drawY;
  }

  private float paintWindowLevel(Graphics2D g2d, View3d owner, float drawY, int fontHeight) {
    RenderingLayer<DicomImageElement> rendering = owner.getRenderingLayer();
    int window = rendering.getWindowWidth();
    int level = rendering.getWindowCenter();

    StringBuilder sb = new StringBuilder();
    sb.append(ActionW.WINLEVEL.getTitle());
    sb.append(StringUtil.COLON_AND_SPACE);
    sb.append(DecFormatter.allNumber(window));
    sb.append("/");
    sb.append(DecFormatter.allNumber(level));

    double minModLUT = owner.getVolTexture().getLevelMin();
    double maxModLUT = owner.getVolTexture().getLevelMax();
    double minp = level - window / 2.0;
    double maxp = level + window / 2.0;
    boolean outside = minp > maxModLUT || maxp < minModLUT;
    if (outside) {
      sb.append(" - ");
      sb.append(org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.msg_outside_levels"));
      FontTools.paintColorFontOutline(
          g2d, sb.toString(), border, drawY, IconColor.ACTIONS_RED.getColor());
    } else {
      FontTools.paintFontOutline(g2d, sb.toString(), border, drawY);
    }
    return drawY - fontHeight;
  }

  private float paintZoom(Graphics2D g2d, float drawY, int fontHeight) {
    FontTools.paintFontOutline(
        g2d,
        org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.zoom")
            + StringUtil.COLON_AND_SPACE
            + DecFormatter.percentTwoDecimal(getView2DPane().getZoom()),
        border,
        drawY);
    return drawY - fontHeight;
  }

  private float paintRotation(Graphics2D g2d, float drawY, int fontHeight) {
    FontTools.paintFontOutline(
        g2d,
        org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.angle")
            + StringUtil.COLON_AND_SPACE
            + view2DPane.getActionValue(ActionW.ROTATION.cmd())
            + " °",
        border,
        drawY);
    return drawY - fontHeight;
  }

  private void paintAnnotations(
      Graphics2D g2d,
      ModalityInfoData modality,
      DicomVolTexture imSeries,
      Rectangle bound,
      int fontHeight,
      boolean hideMin,
      Modality mod) {
    MediaSeries<DicomImageElement> series = view2DPane.getSeries();
    MediaSeriesGroup study = InfoLayer.getParent(series, DicomModel.study);
    MediaSeriesGroup patient = InfoLayer.getParent(series, DicomModel.patient);
    boolean anonymize = getDisplayPreferences(LayerItem.ANONYM_ANNOTATIONS);

    float drawY =
        paintTopLeftCorner(g2d, modality, patient, study, series, fontHeight, hideMin, anonymize);
    setPosition(Position.TopLeft, border, drawY - fontHeight + GuiUtils.getScaleLength(5));

    drawY =
        paintTopRightCorner(
            g2d, modality, patient, study, series, bound, fontHeight, hideMin, anonymize);
    setPosition(
        Position.TopRight,
        (double) bound.width - border,
        drawY - fontHeight + GuiUtils.getScaleLength(5));

    drawY =
        paintBottomRightCorner(
            g2d, modality, patient, study, series, bound, fontHeight, hideMin, anonymize);
    setPosition(
        Position.BottomRight, (double) bound.width - border, drawY - GuiUtils.getScaleLength(5));

    paintBottomLeftAnnotations(g2d, imSeries, bound, mod);
  }

  private float paintTopLeftCorner(
      Graphics2D g2d,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      int fontHeight,
      boolean hideMin,
      boolean anonymize) {
    CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
    float drawY = fontHeight;
    for (TagView tagView : corner.getInfos()) {
      if (tagView != null && (hideMin || tagView.containsTag(TagD.get(Tag.PatientName)))) {
        String text = getFormattedTag(tagView, patient, study, series, anonymize);
        if (text != null) {
          FontTools.paintFontOutline(g2d, text, border, drawY);
          drawY += fontHeight;
        }
      }
    }
    return drawY;
  }

  private float paintTopRightCorner(
      Graphics2D g2d,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      Rectangle bound,
      int fontHeight,
      boolean hideMin,
      boolean anonymize) {
    CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
    float drawY = fontHeight;
    for (TagView info : corner.getInfos()) {
      if (info != null && (hideMin || info.containsTag(TagD.get(Tag.SeriesDate)))) {
        String text = getFormattedTag(info, patient, study, series, anonymize);
        if (text != null) {
          FontTools.paintFontOutline(
              g2d,
              text,
              bound.width - g2d.getFontMetrics().stringWidth(text) - (float) border,
              drawY);
          drawY += fontHeight;
        }
      }
    }
    return drawY;
  }

  private float paintBottomRightCorner(
      Graphics2D g2d,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      Rectangle bound,
      int fontHeight,
      boolean hideMin,
      boolean anonymize) {
    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f);
    if (hideMin) {
      CornerInfoData corner = modality.getCornerInfo(CornerDisplay.BOTTOM_RIGHT);
      TagView[] infos = corner.getInfos();
      for (int j = infos.length - 1; j >= 0; j--) {
        if (infos[j] != null) {
          String text = getFormattedTag(infos[j], patient, study, series, anonymize);
          if (text != null) {
            FontTools.paintFontOutline(
                g2d,
                text,
                bound.width - g2d.getFontMetrics().stringWidth(text) - (float) border,
                drawY);
            drawY -= fontHeight;
          }
        }
      }
      drawY -= 5;
    }
    return drawY;
  }

  private String getFormattedTag(
      TagView tagView,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      boolean anonymize) {
    for (TagW tag : tagView.getTag()) {
      if (!anonymize || tag.getAnonymizationType() != 1) {
        Object value = getFrameTagValue(tag, patient, study, series);
        if (value != null) {
          String format = tag.addGMTOffset(tagView.getFormat(), series);
          String str = tag.getFormattedTagValue(value, format);
          if (StringUtil.hasText(str)) {
            return str;
          }
        }
      }
    }
    return null;
  }

  private void paintBottomLeftAnnotations(
      Graphics2D g2d, DicomVolTexture imSeries, Rectangle bound, Modality mod) {
    String orientation =
        mod.name()
            + " ("
            + imSeries.getWidth()
            + "x"
            + imSeries.getHeight() // NON-NLS
            + "x"
            + imSeries.getDepth() // NON-NLS
            + ")";

    FontTools.paintFontOutline(
        g2d,
        orientation,
        border,
        bound.height - border - GuiUtils.getScaleLength(1.5f)); // -1.5 for outline
  }

  private Object getFrameTagValue(
      final TagW tag,
      final MediaSeriesGroup patient,
      final MediaSeriesGroup study,
      final MediaSeries<DicomImageElement> series) {

    return getTagValue(tag, patient, study, series);
  }

  private Object getTagValue(
      TagW tag,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series) {
    if (series.containTagKey(tag)) {
      return series.getTagValue(tag);
    }
    if (study != null && study.containTagKey(tag)) {
      return study.getTagValue(tag);
    }
    if (patient != null && patient.containTagKey(tag)) {
      return patient.getTagValue(tag);
    }
    return null;
  }

  @Override
  public Rectangle getPreloadingProgressBound() {
    return null;
  }
}
