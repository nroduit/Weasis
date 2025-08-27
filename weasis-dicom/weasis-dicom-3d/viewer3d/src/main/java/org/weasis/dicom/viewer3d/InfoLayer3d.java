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
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
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
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer3d.vr.DicomVolTexture;
import org.weasis.dicom.viewer3d.vr.RenderingLayer;
import org.weasis.dicom.viewer3d.vr.RenderingType;
import org.weasis.dicom.viewer3d.vr.View3d;
import org.weasis.dicom.viewer3d.vr.View3d.ViewType;

public class InfoLayer3d extends AbstractInfoLayer<DicomImageElement> {

  private static final String UNDEFINED = "Undefined"; // NON-NLS
  static final double RESCALE = 1.0;

  public InfoLayer3d(View3d view3d) {
    this(view3d, false);
  }

  public InfoLayer3d(View3d view3d, boolean useGlobalPreferences) {
    super(view3d, useGlobalPreferences);
    displayPreferences.put(LayerItem.ANNOTATIONS, true);
    displayPreferences.put(LayerItem.MIN_ANNOTATIONS, true);
    displayPreferences.put(LayerItem.ANONYM_ANNOTATIONS, false);
    displayPreferences.put(LayerItem.SCALE, true);
    displayPreferences.put(LayerItem.LUT, false);
    displayPreferences.put(LayerItem.IMAGE_ORIENTATION, true);
    displayPreferences.put(LayerItem.WINDOW_LEVEL, true);
    displayPreferences.put(LayerItem.ZOOM, true);
    displayPreferences.put(LayerItem.ROTATION, false);
    displayPreferences.put(LayerItem.FRAME, false);
    displayPreferences.put(LayerItem.PIXEL, true);
  }

  @Override
  public View3d getView2DPane() {
    return (View3d) view2DPane;
  }

  protected boolean ownerHasContent() {
    return getView2DPane().isReadyForRendering();
  }

  @Override
  public void paint(Graphics2D g2d) {
    FontMetrics fontMetrics = g2d.getFontMetrics();
    final Rectangle bound = view2DPane.getJComponent().getBounds();
    int minSize =
        fontMetrics.stringWidth(
                org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.msg_outside_levels"))
            * 2;
    if (!visible || minSize > bound.width || minSize > bound.height || !ownerHasContent()) {
      return;
    }

    Object[] oldRenderingHints =
        GuiUtils.setRenderingHints(g2d, true, false, view2DPane.requiredTextAntialiasing());

    Modality mod =
        Modality.getModality(TagD.getTagValue(view2DPane.getSeries(), Tag.Modality, String.class));
    ModalityInfoData modality = ModalityView.getModlatityInfos(mod);

    final int fontHeight = fontMetrics.getHeight();
    thickLength = Math.max(fontHeight, GuiUtils.getScaleLength(5.0));

    g2d.setPaint(Color.BLACK);

    boolean hideMin = !getDisplayPreferences(LayerItem.MIN_ANNOTATIONS);
    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline

    View3d owner = getView2DPane();
    DicomVolTexture imSeries = owner.getVolTexture();

    if (getView2DPane().isReadyForRendering()) {
      if (getView2DPane().getViewType() != ViewType.VOLUME3D
          && getDisplayPreferences(LayerItem.SCALE)) {
        Dimension sourceDim = getOwnerContentDimensions();
        ImageProperties props =
            new ImageProperties(
                sourceDim.width,
                sourceDim.height,
                getPixelSize(),
                getRescaleX(),
                getRescaleY(),
                getPixelSpacingUnit(),
                getPixelSizeCalibrationDescription());
        drawScale(g2d, bound, fontHeight, props);
      }
      if (getDisplayPreferences(LayerItem.IMAGE_ORIENTATION)) {
        //  drawOrientation(g2);
      }
      if (getDisplayPreferences(LayerItem.LUT)) {
        // drawLUT(g2, bound, fontHeight);
      }
    }
    drawY -= fontHeight;
    // TODO checkif transformed to display message
    drawY = InfoLayer.drawGeometricTransformationMessage(g2d, drawY, fontHeight, border);

    if (imSeries.getVolumeGeometry().isVariablePixelSpacing()) {
      String message = Messages.getString("non.regular.volume.msg");
      FontTools.paintColorFontOutline(
          g2d, message, border, drawY, IconColor.ACTIONS_RED.getColor());
      drawY -= fontHeight;
    }

    if (!isVolumetricView() && getDisplayPreferences(LayerItem.PIXEL) && hideMin) {
      StringBuilder sb =
          new StringBuilder(org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.pixel"));
      sb.append(StringUtil.COLON_AND_SPACE);
      if (pixelInfo != null) {
        if (pixelInfo.getPixelValueText() != null) {
          sb.append(pixelInfo.getPixelValueText());
          sb.append(" - ");
        }
        sb.append(pixelInfo.getPixelPositionText());
      }
      String str = sb.toString();
      FontTools.paintFontOutline(g2d, str, border, drawY);
      drawY -= fontHeight;
      pixelInfoBound.setBounds(
          border,
          (int) drawY + fontMetrics.getDescent(),
          fontMetrics.stringWidth(str) + GuiUtils.getScaleLength(2),
          fontHeight);
    }

    if (getDisplayPreferences(LayerItem.WINDOW_LEVEL) && hideMin) {
      StringBuilder sb = new StringBuilder();
      RenderingLayer rendering = owner.getRenderingLayer();
      int window = rendering.getWindowWidth();
      int level = rendering.getWindowCenter();
      boolean outside = false;

      sb.append(ActionW.WINLEVEL.getTitle());
      sb.append(StringUtil.COLON_AND_SPACE);
      sb.append(DecFormatter.allNumber(window));
      sb.append("/");
      sb.append(DecFormatter.allNumber(level));

      double minModLUT = owner.getVolTexture().getLevelMin();
      double maxModLUT = owner.getVolTexture().getLevelMax();
      double minp = level - window / 2.0;
      double maxp = level + window / 2.0;
      if (minp > maxModLUT || maxp < minModLUT) {
        outside = true;
        sb.append(" - ");
        sb.append(org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.msg_outside_levels"));
      }

      if (outside) {
        FontTools.paintColorFontOutline(
            g2d, sb.toString(), border, drawY, IconColor.ACTIONS_RED.getColor());
      } else {
        FontTools.paintFontOutline(g2d, sb.toString(), border, drawY);
      }
      drawY -= fontHeight;
    }

    if (getDisplayPreferences(LayerItem.ZOOM) && hideMin) {
      FontTools.paintFontOutline(
          g2d,
          org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.zoom")
              + StringUtil.COLON_AND_SPACE
              + DecFormatter.percentTwoDecimal(getView2DPane().getZoom()),
          border,
          drawY);
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(LayerItem.ROTATION) && hideMin) {
      FontTools.paintFontOutline(
          g2d,
          org.weasis.dicom.viewer2d.Messages.getString("InfoLayer.angle")
              + StringUtil.COLON_AND_SPACE
              + view2DPane.getActionValue(ActionW.ROTATION.cmd())
              + " °",
          border,
          drawY);
      drawY -= fontHeight;
    }

    if (getDisplayPreferences(LayerItem.ANNOTATIONS)) {
      MediaSeries<DicomImageElement> series = view2DPane.getSeries();
      MediaSeriesGroup study = InfoLayer.getParent(series, DicomModel.study);
      MediaSeriesGroup patient = InfoLayer.getParent(series, DicomModel.patient);
      CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
      boolean anonymize = getDisplayPreferences(LayerItem.ANONYM_ANNOTATIONS);
      drawY = fontHeight;
      TagView[] infos = corner.getInfos();
      for (TagView tagView : infos) {
        if (tagView != null && (hideMin || tagView.containsTag(TagD.get(Tag.PatientName)))) {
          for (TagW tag : tagView.getTag()) {
            if (!anonymize || tag.getAnonymizationType() != 1) {
              Object value = getFrameTagValue(tag, patient, study, series);
              if (value != null) {
                String format = tag.addGMTOffset(tagView.getFormat(), series);
                String str = tag.getFormattedTagValue(value, format);
                if (StringUtil.hasText(str)) {
                  FontTools.paintFontOutline(g2d, str, border, drawY);
                  drawY += fontHeight;
                }
                break;
              }
            }
          }
        }
      }

      corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
      drawY = fontHeight;
      infos = corner.getInfos();
      for (TagView info : infos) {
        if (info != null) {
          if (hideMin || info.containsTag(TagD.get(Tag.SeriesDate))) {
            Object value;
            for (TagW tag : info.getTag()) {
              if (!anonymize || tag.getAnonymizationType() != 1) {
                value = getFrameTagValue(tag, patient, study, series);
                if (value != null) {
                  String format = tag.addGMTOffset(info.getFormat(), series);
                  String str = tag.getFormattedTagValue(value, format);
                  if (StringUtil.hasText(str)) {
                    FontTools.paintFontOutline(
                        g2d,
                        str,
                        bound.width - g2d.getFontMetrics().stringWidth(str) - (float) border,
                        drawY);
                    drawY += fontHeight;
                  }
                  break;
                }
              }
            }
          }
        }
      }

      drawY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline
      if (hideMin) {
        corner = modality.getCornerInfo(CornerDisplay.BOTTOM_RIGHT);
        infos = corner.getInfos();
        for (int j = infos.length - 1; j >= 0; j--) {
          if (infos[j] != null) {
            Object value;
            for (TagW tag : infos[j].getTag()) {
              if (!anonymize || tag.getAnonymizationType() != 1) {
                value = getFrameTagValue(tag, patient, study, series);
                if (value != null) {
                  String format = tag.addGMTOffset(infos[j].getFormat(), series);
                  String str = tag.getFormattedTagValue(value, format);
                  if (StringUtil.hasText(str)) {
                    FontTools.paintFontOutline(
                        g2d,
                        str,
                        bound.width - g2d.getFontMetrics().stringWidth(str) - (float) border,
                        drawY);
                    drawY -= fontHeight;
                  }
                  break;
                }
              }
            }
          }
        }
        drawY -= 5;
        //    drawSeriesInMemoryState(g2, view2DPane.getSeries(), bound.width - border, (int)
        // (drawY));
      }

      // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK);
      // String str = synchLink != null && synchLink ? "linked" : "unlinked"; // NON-NLS
      // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - BORDER,
      // drawY);

      StringBuilder orientation = new StringBuilder();
      orientation.append(mod.name());
      orientation.append(" (").append(imSeries.getWidth());
      orientation.append("x").append(imSeries.getHeight()); // NON-NLS
      orientation.append("x").append(imSeries.getDepth()); // NON-NLS
      orientation.append(")");

      if (getDisplayPreferences(LayerItem.IMAGE_ORIENTATION)) {
        //          double[] imagePosition = owner.getImagePatientOrientation();
        //          if (imagePosition != null) {
        //            Plan imgOrientation =
        //                ImageOrientation.getPlan(
        //                    new Vector3d(imagePosition),
        //                    new Vector3d(imagePosition[3], imagePosition[4], imagePosition[5]));
        //            if (imgOrientation != null) {
        //              orientation.append(" - ");
        //              orientation.append(imgOrientation);
        //            }
        //          }
      }

      FontTools.paintFontOutline(
          g2d,
          orientation.toString(),
          border,
          bound.height - border - GuiUtils.getScaleLength(1.5f)); // -1.5 for outline
    }

    GuiUtils.resetRenderingHints(g2d, oldRenderingHints);
  }

  private boolean isMipActive() {
    return !MipView.Type.NONE.equals(getView2DPane().getRenderingLayer().getMipType());
  }

  private boolean isSliceActive() {
    return RenderingType.SLICE.equals(getView2DPane().getRenderingLayer().getRenderingType());
  }

  private Object getFrameTagValue(
      final TagW tag,
      final MediaSeriesGroup patient,
      final MediaSeriesGroup study,
      final MediaSeries<DicomImageElement> series) {

    if ((tag.getKeyword().equals("SliceLocation") || tag.getKeyword().equals("SliceThickness"))
        && getView2DPane().getVolTexture() != null
        //      && owner.isShowingAcquisitionAxis()
        && !isVolumetricView()) {

      if (isMipActive()) {
        return UNDEFINED;
      } else {
      }
    }
    return getTagValue(tag, patient, study, series, null);
  }

  private Object getTagValue(
      TagW tag,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      ImageElement image) {
    if (image != null && image.containTagKey(tag)) {
      return image.getTagValue(tag);
    }
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

  public Unit getPixelSpacingUnit() {
    DicomVolTexture volTexture = getView2DPane().getVolTexture();
    return volTexture == null ? Unit.PIXEL : volTexture.getPixelSpacingUnit();
  }

  public double getZoomFactor() {
    Object zoom = getView2DPane().getActionValue(ActionW.ZOOM.cmd());
    if (zoom instanceof Double val) {
      return Math.abs(val);
    }
    return 0;
  }

  public double getPixelSize() {
    return getView2DPane().getVolTexture().getMaxDimensionLength();
  }

  public Dimension getOwnerContentDimensions() {
    View3d owner = (View3d) view2DPane;
    Vector3d imageSize = owner.getVolTexture().getVolumeSize();
    return new Dimension((int) imageSize.x, (int) imageSize.y);
  }

  public double getRescaleX() {
    return RESCALE;
  }

  public double getRescaleY() {
    return RESCALE;
  }

  public String getPixelSizeCalibrationDescription() {
    String tagValue = null;
    DicomVolTexture ser = getView2DPane().getVolTexture();
    if (ser != null) {
      tagValue = TagD.getTagValue(ser, Tag.PixelSpacingCalibrationDescription, String.class);
    }
    return tagValue;
  }

  @Override
  public Rectangle getPreloadingProgressBound() {
    return null;
  }

  @Override
  public LayerAnnotation getLayerCopy(ViewCanvas view2DPane, boolean useGlobalPreferences) {
    throw new UnsupportedOperationException("Not supported yet.");
  }

  private boolean isVolumetricView() {
    return getView2DPane().getRenderingLayer().getRenderingType() != RenderingType.SLICE;
  }

  @Override
  public void resetToDefault() {
    // TODO implement the persistence
  }
}
