/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Map;
import javax.swing.Icon;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.data.PrDicomObject;
import org.joml.Vector3d;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormatter;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Plan;
import org.weasis.dicom.codec.geometry.PatientOrientation.Biped;
import org.weasis.dicom.codec.geometry.VectorUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

/**
 * Displays image information overlay on a 2D DICOM image viewer canvas.
 *
 * @author Nicolas Roduit
 */
public class InfoLayer extends AbstractInfoLayer<DicomImageElement> {

  public InfoLayer(ViewCanvas<DicomImageElement> view2DPane) {
    this(view2DPane, true);
  }

  public InfoLayer(ViewCanvas<DicomImageElement> view2DPane, boolean useGlobalPreferences) {
    super(view2DPane, useGlobalPreferences);
    initializeDisplayPreferences();
  }

  private void initializeDisplayPreferences() {
    displayPreferences.put(LayerItem.ANNOTATIONS, true);
    displayPreferences.put(LayerItem.MIN_ANNOTATIONS, false);
    displayPreferences.put(LayerItem.ANONYM_ANNOTATIONS, false);
    displayPreferences.put(LayerItem.SCALE, true);
    displayPreferences.put(LayerItem.LUT, false);
    displayPreferences.put(LayerItem.IMAGE_ORIENTATION, true);
    displayPreferences.put(LayerItem.WINDOW_LEVEL, true);
    displayPreferences.put(LayerItem.ZOOM, true);
    displayPreferences.put(LayerItem.ROTATION, false);
    displayPreferences.put(LayerItem.FRAME, true);
    displayPreferences.put(LayerItem.PIXEL, true);
    displayPreferences.put(LayerItem.PRELOADING_BAR, true);
  }

  @Override
  public LayerAnnotation getLayerCopy(ViewCanvas view2DPane, boolean useGlobalPreferences) {
    InfoLayer layer = new InfoLayer(view2DPane, useGlobalPreferences);
    copyLayerValues(layer.displayPreferences);
    return layer;
  }

  private void copyLayerValues(Map<LayerItem, Boolean> prefMap) {
    setLayerValue(prefMap, LayerItem.ANNOTATIONS);
    setLayerValue(prefMap, LayerItem.ANONYM_ANNOTATIONS);
    setLayerValue(prefMap, LayerItem.IMAGE_ORIENTATION);
    setLayerValue(prefMap, LayerItem.SCALE);
    setLayerValue(prefMap, LayerItem.LUT);
    setLayerValue(prefMap, LayerItem.PIXEL);
    setLayerValue(prefMap, LayerItem.WINDOW_LEVEL);
    setLayerValue(prefMap, LayerItem.ZOOM);
    setLayerValue(prefMap, LayerItem.ROTATION);
    setLayerValue(prefMap, LayerItem.FRAME);
    setLayerValue(prefMap, LayerItem.PRELOADING_BAR);
    setLayerValue(prefMap, LayerItem.MIN_ANNOTATIONS);
  }

  @Override
  public void paint(Graphics2D g2) {
    DicomImageElement image = view2DPane.getImage();
    // Get the smallest font for better size calculations
    FontMetrics fontMetrics =
        view2DPane.getJComponent().getFontMetrics(FontItem.MICRO_SEMIBOLD.getFont());
    Rectangle bound = view2DPane.getJComponent().getBounds();

    if (!shouldPaint(image, fontMetrics, bound)) {
      if (visible && image != null) {
        setPosition(Position.BottomLeft, border, (double) bound.height - border);
        setDefaultCornerPositions(bound);
        drawExtendedActions(g2);
      }
      return;
    }

    Object[] oldRenderingHints =
        GuiUtils.setRenderingHints(g2, true, false, view2DPane.requiredTextAntialiasing());

    try {
      paintContent(g2, image, fontMetrics, bound);
    } finally {
      GuiUtils.resetRenderingHints(g2, oldRenderingHints);
    }
  }

  private boolean shouldPaint(DicomImageElement image, FontMetrics fontMetrics, Rectangle bound) {
    int minSize = fontMetrics.stringWidth(Messages.getString("InfoLayer.msg_outside_levels")) * 2;
    return visible && image != null && minSize <= bound.width && minSize / 2 <= bound.height;
  }

  private void paintContent(
      Graphics2D g2, DicomImageElement image, FontMetrics fontMetrics, Rectangle bound) {
    OpManager disOp = view2DPane.getDisplayOpManager();
    Modality mod =
        Modality.getModality(TagD.getTagValue(view2DPane.getSeries(), Tag.Modality, String.class));
    ModalityInfoData modality = ModalityView.getModlatityInfos(mod);

    float midX = bound.width / 2f;
    float midY = bound.height / 2f;
    int fontHeight = fontMetrics.getHeight();
    thickLength = Math.max(fontHeight, GuiUtils.getScaleLength(5.0));

    g2.setPaint(Color.BLACK);

    boolean hideMin = !getDisplayPreferences(LayerItem.MIN_ANNOTATIONS);
    int midFontHeight = fontHeight - fontMetrics.getDescent();
    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f);

    if (!image.isReadable()) {
      paintNotReadable(g2, image, midX, midY, fontHeight);
    }

    if (image.isReadable() && view2DPane.getSourceImage() != null) {
      drawImageInfo(g2, image, bound, fontHeight, midFontHeight, hideMin);
    }

    drawY = paintBottomLeftInfo(g2, image, disOp, drawY, fontHeight, hideMin);

    Integer frame = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
    RejectedKOSpecialElement koElement =
        DicomModel.getRejectionKoSpecialElement(
            view2DPane.getSeries(),
            TagD.getTagValue(image, Tag.SOPInstanceUID, String.class),
            frame);

    if (koElement != null) {
      paintRejectedImage(g2, koElement, midX, midY);
    }

    setPosition(Position.BottomLeft, border, drawY - GuiUtils.getScaleLength(5));

    if (getDisplayPreferences(LayerItem.ANNOTATIONS)) {
      paintAnnotations(
          g2,
          image,
          modality,
          bound,
          fontMetrics,
          fontHeight,
          midFontHeight,
          hideMin,
          midX,
          midY,
          mod);
    } else {
      setDefaultCornerPositions(bound);
    }

    drawExtendedActions(g2);
  }

  private void drawImageInfo(
      Graphics2D g2,
      DicomImageElement image,
      Rectangle bound,
      int fontHeight,
      int midFontHeight,
      boolean hideMin) {
    if (getDisplayPreferences(LayerItem.SCALE)) {
      PlanarImage source = image.getImage();
      if (source != null) {
        ImageProperties props = createImageProperties(image, source);
        drawScale(g2, bound, fontHeight, props);
      }
    }
    if (getDisplayPreferences(LayerItem.LUT) && hideMin) {
      drawLUT(g2, bound, midFontHeight);
    }
  }

  private ImageProperties createImageProperties(DicomImageElement image, PlanarImage source) {
    return new ImageProperties(
        source.width(),
        source.height(),
        image.getPixelSize(),
        image.getRescaleX(),
        image.getRescaleY(),
        image.getPixelSpacingUnit(),
        image.getPixelSizeCalibrationDescription());
  }

  private float paintBottomLeftInfo(
      Graphics2D g2,
      DicomImageElement image,
      OpManager disOp,
      float drawY,
      int fontHeight,
      boolean hideMin) {
    drawY -= fontHeight;
    drawY = checkAndPaintLossyImage(g2, image, drawY, fontHeight, border);

    if (view2DPane instanceof MprView mprView) {
      drawY = paintMprTransformationMessage(g2, mprView, drawY, fontHeight);
    }

    if (getDisplayPreferences(LayerItem.PIXEL) && hideMin) {
      drawY = paintPixelInfo(g2, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.WINDOW_LEVEL) && hideMin) {
      drawY = paintWindowLevel(g2, image, disOp, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ZOOM) && hideMin) {
      drawY = paintZoom(g2, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.ROTATION) && hideMin) {
      drawY = paintRotation(g2, drawY, fontHeight);
    }
    if (getDisplayPreferences(LayerItem.FRAME) && hideMin) {
      drawY = paintFrame(g2, image, drawY, fontHeight);
    }

    return drawY;
  }

  private float paintMprTransformationMessage(
      Graphics2D g2, MprView mprView, float drawY, int fontHeight) {
    MprController controller = mprView.getMprController();
    if (controller != null && controller.getVolume() != null) {
      if (controller.getVolume().isTransformed()) {
        drawY = drawGeometricTransformationMessage(g2, drawY, fontHeight, border);
      }
    }
    return drawY;
  }

  private float paintPixelInfo(Graphics2D g2, float drawY, int fontHeight) {
    String pixelText = buildPixelInfoText();
    FontTools.paintFontOutline(g2, pixelText, border, drawY);
    FontMetrics fontMetrics = g2.getFontMetrics();
    drawY -= fontHeight;
    pixelInfoBound.setBounds(
        border,
        (int) drawY + fontMetrics.getDescent(),
        fontMetrics.stringWidth(pixelText) + GuiUtils.getScaleLength(2),
        fontHeight);
    return drawY;
  }

  private String buildPixelInfoText() {
    StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer.pixel"));
    sb.append(StringUtil.COLON_AND_SPACE);
    if (pixelInfo != null) {
      sb.append(pixelInfo.getPixelValueText());
      sb.append(" - ");
      sb.append(pixelInfo.getPixelPositionText());
    }
    return sb.toString();
  }

  private float paintWindowLevel(
      Graphics2D g2, DicomImageElement image, OpManager disOp, float drawY, int fontHeight) {
    Number window =
        disOp.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Number.class).orElse(null);
    Number level =
        disOp.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Number.class).orElse(null);

    if (window != null && level != null) {
      boolean outside = isWindowLevelOutside(image, disOp, window, level);
      String text = buildWindowLevelText(window, level, outside);

      if (outside) {
        FontTools.paintColorFontOutline(g2, text, border, drawY, IconColor.ACTIONS_RED.getColor());
      } else {
        FontTools.paintFontOutline(g2, text, border, drawY);
      }
    }
    return drawY - fontHeight;
  }

  private boolean isWindowLevelOutside(
      DicomImageElement image, OpManager disOp, Number window, Number level) {
    PrDicomObject prDicomObject =
        PRManager.getPrDicomObject(view2DPane.getActionValue(ActionW.PR_STATE.cmd()));
    boolean pixelPadding =
        disOp
            .getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.class)
            .orElse(true);
    DefaultWlPresentation wlp = new DefaultWlPresentation(prDicomObject, pixelPadding);

    double minModLUT = image.getMinValue(wlp);
    double maxModLUT = image.getMaxValue(wlp);
    double minp = level.doubleValue() - window.doubleValue() / 2.0;
    double maxp = level.doubleValue() + window.doubleValue() / 2.0;

    return minp > maxModLUT || maxp < minModLUT;
  }

  private String buildWindowLevelText(Number window, Number level, boolean outside) {
    StringBuilder sb = new StringBuilder();
    sb.append(ActionW.WINLEVEL.getTitle());
    sb.append(StringUtil.COLON_AND_SPACE);
    sb.append(DecFormatter.allNumber(window));
    sb.append("/");
    sb.append(DecFormatter.allNumber(level));

    if (outside) {
      sb.append(" - ");
      sb.append(Messages.getString("InfoLayer.msg_outside_levels"));
    }
    return sb.toString();
  }

  private float paintZoom(Graphics2D g2, float drawY, int fontHeight) {
    String zoomText =
        Messages.getString("InfoLayer.zoom")
            + StringUtil.COLON_AND_SPACE
            + DecFormatter.percentTwoDecimal(view2DPane.getViewModel().getViewScale());
    FontTools.paintFontOutline(g2, zoomText, border, drawY);
    return drawY - fontHeight;
  }

  private float paintRotation(Graphics2D g2, float drawY, int fontHeight) {
    String rotationText =
        Messages.getString("InfoLayer.angle")
            + StringUtil.COLON_AND_SPACE
            + view2DPane.getActionValue(ActionW.ROTATION.cmd())
            + " °";
    FontTools.paintFontOutline(g2, rotationText, border, drawY);
    return drawY - fontHeight;
  }

  private float paintFrame(Graphics2D g2, DicomImageElement image, float drawY, int fontHeight) {
    String frameText = buildFrameText(image);
    FontTools.paintFontOutline(g2, frameText, border, drawY);
    drawY -= fontHeight;

    Double imgProgression = (Double) view2DPane.getActionValue(ActionW.PROGRESSION.cmd());
    if (imgProgression != null) {
      drawY = paintProgressionIndicator(g2, imgProgression, drawY);
    }
    return drawY;
  }

  private String buildFrameText(DicomImageElement image) {
    StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.frame"));
    buf.append(StringUtil.COLON_AND_SPACE);

    Integer inst = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
    if (inst != null) {
      buf.append("[").append(inst).append("] ");
    }

    buf.append(view2DPane.getFrameIndex() + 1);
    buf.append(" / ");
    buf.append(
        view2DPane
            .getSeries()
            .size(
                (Filter<DicomImageElement>)
                    view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())));

    return buf.toString();
  }

  private float paintProgressionIndicator(Graphics2D g2, double imgProgression, float drawY) {
    int inset = GuiUtils.getScaleLength(13);
    drawY -= inset;
    int pColor = (int) (510 * imgProgression);
    g2.setPaint(new Color(Math.min(510 - pColor, 255), Math.min(pColor, 255), 0));
    g2.fillOval(border, (int) drawY, inset, inset);
    return drawY;
  }

  private void paintRejectedImage(
      Graphics2D g2, RejectedKOSpecialElement koElement, float midX, float midY) {
    String message = "Not a valid image: " + koElement.getDocumentTitle(); // NON-NLS
    FontTools.paintColorFontOutline(
        g2,
        message,
        midX - g2.getFontMetrics().stringWidth(message) / 2F,
        midY,
        IconColor.ACTIONS_RED.getColor());
  }

  private void paintAnnotations(
      Graphics2D g2,
      DicomImageElement image,
      ModalityInfoData modality,
      Rectangle bound,
      FontMetrics fontMetrics,
      int fontHeight,
      int midFontHeight,
      boolean hideMin,
      float midX,
      float midY,
      Modality mod) {
    Series series = (Series) view2DPane.getSeries();
    MediaSeriesGroup study = getParent(series, DicomModel.study);
    MediaSeriesGroup patient = getParent(series, DicomModel.patient);
    boolean anonymize = getDisplayPreferences(LayerItem.ANONYM_ANNOTATIONS);

    float drawY =
        paintTopLeftCorner(
            g2, modality, patient, study, series, image, fontHeight, hideMin, anonymize);
    setPosition(Position.TopLeft, border, drawY - fontHeight + GuiUtils.getScaleLength(5));

    drawY =
        paintTopRightCorner(
            g2, modality, patient, study, series, image, bound, fontHeight, hideMin, anonymize);
    setPosition(
        Position.TopRight,
        (double) bound.width - border,
        drawY - fontHeight + GuiUtils.getScaleLength(5));

    drawY =
        paintBottomRightCorner(
            g2, modality, patient, study, series, image, bound, fontHeight, hideMin, anonymize);
    setPosition(
        Position.BottomRight, (double) bound.width - border, drawY - GuiUtils.getScaleLength(5));

    paintBottomLeftAnnotations(
        g2, image, series, bound, fontMetrics, fontHeight, midFontHeight, midX, midY, mod);
  }

  private float paintTopLeftCorner(
      Graphics2D g2,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      Series series,
      DicomImageElement image,
      int fontHeight,
      boolean hideMin,
      boolean anonymize) {
    CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
    float drawY = fontHeight;

    for (TagView tagView : corner.getInfos()) {
      if (tagView != null && (hideMin || tagView.containsTag(TagD.get(Tag.PatientName)))) {
        String text = getFormattedTag(tagView, patient, study, series, image, anonymize);
        if (text != null) {
          FontTools.paintFontOutline(g2, text, border, drawY);
          drawY += fontHeight;
        }
      }
    }
    return drawY;
  }

  private float paintTopRightCorner(
      Graphics2D g2,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      Series series,
      DicomImageElement image,
      Rectangle bound,
      int fontHeight,
      boolean hideMin,
      boolean anonymize) {
    CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
    float drawY = fontHeight;

    for (TagView info : corner.getInfos()) {
      if (info != null && (hideMin || info.containsTag(TagD.get(Tag.SeriesDate)))) {
        String text = getFormattedTag(info, patient, study, series, image, anonymize);
        if (text != null) {
          FontTools.paintFontOutline(
              g2,
              text,
              bound.width - g2.getFontMetrics().stringWidth(text) - (float) border,
              drawY);
          drawY += fontHeight;
        }
      }
    }
    return drawY;
  }

  private float paintBottomRightCorner(
      Graphics2D g2,
      ModalityInfoData modality,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      Series series,
      DicomImageElement image,
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
          String text = getFormattedTag(infos[j], patient, study, series, image, anonymize);
          if (text != null) {
            FontTools.paintFontOutline(
                g2,
                text,
                bound.width - g2.getFontMetrics().stringWidth(text) - (float) border,
                drawY);
            drawY -= fontHeight;
          }
        }
      }
      drawY -= 5;
      drawSeriesInMemoryState(g2, view2DPane.getSeries(), bound.width - border, (int) drawY);
    }
    return drawY;
  }

  private String getFormattedTag(
      TagView tagView,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      Series series,
      DicomImageElement image,
      boolean anonymize) {
    for (TagW tag : tagView.getTag()) {
      if (!anonymize || tag.getAnonymizationType() != 1) {
        Object value = getTagValue(tag, patient, study, series, image);
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
      Graphics2D g2,
      DicomImageElement image,
      Series series,
      Rectangle bound,
      FontMetrics fontMetrics,
      int fontHeight,
      int midFontHeight,
      float midX,
      float midY,
      Modality mod) {
    Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
    Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
    StringBuilder orientation = new StringBuilder(mod.name());

    if (rows != null && columns != null) {
      orientation.append(" (").append(columns).append("x").append(rows).append(")");
    }

    OrientationInfo orientInfo = getOrientationInfo(image, series);

    if (orientInfo != null) {
      paintOrientationLabels(g2, orientInfo, midX, midY, fontHeight);
      if (orientInfo.plan() != null) {
        orientation.append(" - ");
        orientation.append(orientInfo.plan());
      }
      orientation.append(StringUtil.SPACE);
    }

    float offsetY = bound.height - border - GuiUtils.getScaleLength(1.5f);
    FontTools.paintFontOutline(g2, orientation.toString(), border, offsetY);

    if (orientInfo != null && orientInfo.plan() != null && view2DPane instanceof MprView) {
      paintPlanIndicator(
          g2, orientInfo.plan(), orientation.toString(), bound, fontMetrics, midFontHeight);
    }
  }

  private OrientationInfo getOrientationInfo(DicomImageElement image, Series series) {
    boolean quadruped =
        "QUADRUPED"
            .equalsIgnoreCase(
                TagD.getTagValue(series, Tag.AnatomicalOrientationType, String.class));

    Vector3d vr = ImageOrientation.getRowImagePosition(image);
    Vector3d vc = ImageOrientation.getColumnImagePosition(image);

    if (getDisplayPreferences(LayerItem.IMAGE_ORIENTATION)) {
      if (vr != null && vc != null) {
        return buildOrientationFromVectors(vr, vc, quadruped);
      } else {
        return buildOrientationFromPatientOrientation(image, quadruped);
      }
    }
    return null;
  }

  private OrientationInfo buildOrientationFromVectors(Vector3d vr, Vector3d vc, boolean quadruped) {
    Plan plan = ImageOrientation.getPlan(vr, vc);
    Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());

    if (rotationAngle != null && rotationAngle != 0) {
      applyRotation(vr, vc, rotationAngle);
    } else {
      vr.negate();
      vc.negate();
    }

    if (LangUtil.nullToFalse((Boolean) view2DPane.getActionValue(ActionW.FLIP.cmd()))) {
      vr.negate();
    }

    String colLeft = ImageOrientation.getOrientation(vr, quadruped);
    String rowTop = ImageOrientation.getOrientation(vc, quadruped);

    return new OrientationInfo(plan, colLeft, rowTop);
  }

  private void applyRotation(Vector3d vr, Vector3d vc, int angle) {
    double rad = Math.toRadians(angle);
    Vector3d normal = VectorUtils.computeNormalOfSurface(vr, vc);
    vr.negate();
    vr.rotateAxis(-rad, normal.x, normal.y, normal.z);
    vc.negate();
    vc.rotateAxis(-rad, normal.x, normal.y, normal.z);
  }

  private OrientationInfo buildOrientationFromPatientOrientation(
      DicomImageElement image, boolean quadruped) {
    String[] po = TagD.getTagValue(image, Tag.PatientOrientation, String[].class);
    Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());

    if (po != null && po.length == 2 && (rotationAngle == null || rotationAngle == 0)) {
      String colLeft = getColumnLeftOrientation(po[0], quadruped);
      String rowTop = getRowTopOrientation(po[1], quadruped);
      return new OrientationInfo(null, colLeft, rowTop);
    }
    return null;
  }

  private String getColumnLeftOrientation(String orientation, boolean quadruped) {
    if (LangUtil.nullToFalse((Boolean) view2DPane.getActionValue(ActionW.FLIP.cmd()))) {
      return orientation;
    }
    StringBuilder buf = new StringBuilder();
    for (String s : orientation.split("(?=\\p{Upper})")) {
      buf.append(ImageOrientation.getImageOrientationOpposite(s, quadruped));
    }
    return buf.toString();
  }

  private String getRowTopOrientation(String orientation, boolean quadruped) {
    StringBuilder buf = new StringBuilder();
    for (String s : orientation.split("(?=\\p{Upper})")) {
      buf.append(ImageOrientation.getImageOrientationOpposite(s, quadruped));
    }
    return buf.toString();
  }

  private void paintOrientationLabels(
      Graphics2D g2, OrientationInfo info, float midX, float midY, int fontHeight) {
    if (info.colLeft() == null || info.rowTop() == null) {
      return;
    }

    String[] left = info.colLeft().split(StringUtil.SPACE);
    String[] top = info.rowTop().split(StringUtil.SPACE);

    Font oldFont = g2.getFont();
    Font bigFont = oldFont.deriveFont(oldFont.getSize() + 5.0f);
    g2.setFont(bigFont);

    Map<TextAttribute, Object> subscriptAttr =
        Map.of(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);

    paintTopOrientation(g2, top, midX, fontHeight, bigFont, subscriptAttr);
    paintLeftOrientation(g2, left, midY, fontHeight, bigFont, subscriptAttr);

    g2.setFont(oldFont);
  }

  private void paintTopOrientation(
      Graphics2D g2,
      String[] top,
      float midX,
      int fontHeight,
      Font bigFont,
      Map<TextAttribute, Object> subscriptAttr) {
    String bigLetter = top.length > 0 && !top[0].isEmpty() ? top[0] : StringUtil.SPACE;
    int shiftX = g2.getFontMetrics().stringWidth(bigLetter);
    int shiftY = fontHeight + GuiUtils.getScaleLength(5);
    FontTools.paintColorFontOutline(g2, bigLetter, midX - shiftX, shiftY, highlight);

    if (top.length > 1) {
      g2.setFont(bigFont.deriveFont(subscriptAttr));
      FontTools.paintColorFontOutline(
          g2, String.join("-", Arrays.copyOfRange(top, 1, top.length)), midX, shiftY, highlight);
      g2.setFont(bigFont);
    }
  }

  private void paintLeftOrientation(
      Graphics2D g2,
      String[] left,
      float midY,
      int fontHeight,
      Font bigFont,
      Map<TextAttribute, Object> subscriptAttr) {
    String bigLetter = left.length > 0 && !left[0].isEmpty() ? left[0] : StringUtil.SPACE;
    FontTools.paintColorFontOutline(
        g2, bigLetter, (float) (border + thickLength), midY + fontHeight / 2.0f, highlight);

    if (left.length > 1) {
      int shiftX = g2.getFontMetrics().stringWidth(bigLetter);
      g2.setFont(bigFont.deriveFont(subscriptAttr));
      FontTools.paintColorFontOutline(
          g2,
          String.join("-", Arrays.copyOfRange(left, 1, left.length)),
          (float) (border + thickLength + shiftX),
          midY + fontHeight / 2.0f,
          highlight);
    }
  }

  private void paintPlanIndicator(
      Graphics2D g2,
      Plan plan,
      String orientationText,
      Rectangle bound,
      FontMetrics fontMetrics,
      int midFontHeight) {
    Color planColor =
        switch (plan) {
          case AXIAL -> Biped.F.getColor();
          case CORONAL -> Biped.A.getColor();
          case SAGITTAL -> Biped.L.getColor();
          default -> null;
        };

    if (planColor != null) {
      int shiftX = g2.getFontMetrics().stringWidth(orientationText);
      g2.setColor(planColor);
      int size = midFontHeight - fontMetrics.getDescent();
      int shiftY = bound.height - border - size;
      g2.fillRect(border + shiftX, shiftY, size - 1, size - 1);
    }
  }

  private void setDefaultCornerPositions(Rectangle bound) {
    setPosition(Position.TopLeft, border, border);
    setPosition(Position.TopRight, (double) bound.width - border, border);
    setPosition(
        Position.BottomRight, (double) bound.width - border, (double) bound.height - border);
  }

  public static float drawGeometricTransformationMessage(
      Graphics2D g2d, float drawY, int fontHeight, int border) {
    String message = Messages.getString("geometric.transformation.msg");
    FontTools.paintColorFontOutline(g2d, message, border, drawY, IconColor.ACTIONS_RED.getColor());
    return drawY - fontHeight;
  }

  public static MediaSeriesGroup getParent(
      MediaSeries<DicomImageElement> series, TreeModelNode node) {
    if (series != null) {
      Object tagValue = series.getTagValue(TagW.ExplorerModel);
      if (tagValue instanceof DicomModel model) {
        return model.getParent(series, node);
      }
    }
    return null;
  }

  public static void paintNotReadable(
      Graphics2D g2, DicomImageElement image, float midX, float midY, float fontHeight) {
    String message = Messages.getString("InfoLayer.msg_not_read");
    float y = midY;
    FontTools.paintColorFontOutline(
        g2,
        message,
        midX - g2.getFontMetrics().stringWidth(message) / 2.0F,
        y,
        IconColor.ACTIONS_RED.getColor());

    if (image != null) {
      y = paintTransferSyntax(g2, image, midX, y, fontHeight);
      paintReaderDescription(g2, image, midX, y, fontHeight);
    }
  }

  private static float paintTransferSyntax(
      Graphics2D g2, DicomImageElement image, float midX, float y, float fontHeight) {
    String tsuid = TagD.getTagValue(image, Tag.TransferSyntaxUID, String.class);
    if (StringUtil.hasText(tsuid)) {
      tsuid = Messages.getString("InfoLayer.tsuid") + StringUtil.COLON_AND_SPACE + tsuid;
      y += fontHeight;
      FontTools.paintColorFontOutline(
          g2,
          tsuid,
          midX - g2.getFontMetrics().stringWidth(tsuid) / 2.0F,
          y,
          IconColor.ACTIONS_RED.getColor());
    }
    return y;
  }

  private static void paintReaderDescription(
      Graphics2D g2, DicomImageElement image, float midX, float y, float fontHeight) {
    String[] desc = image.getMediaReader().getReaderDescription();
    if (desc != null) {
      for (String str : desc) {
        if (StringUtil.hasText(str)) {
          y += fontHeight;
          FontTools.paintColorFontOutline(
              g2,
              str,
              midX - g2.getFontMetrics().stringWidth(str) / 2F,
              y,
              IconColor.ACTIONS_RED.getColor());
        }
      }
    }
  }

  public static float checkAndPaintLossyImage(
      Graphics2D g2d, TagReadable taggable, float drawY, float fontHeight, int border) {
    if ("01".equals(TagD.getTagValue(taggable, Tag.LossyImageCompression))) {
      String lossyInfo = buildLossyCompressionInfo(taggable);
      FontTools.paintColorFontOutline(
          g2d, lossyInfo, border, drawY, IconColor.ACTIONS_RED.getColor());
      return drawY - fontHeight;
    }
    return drawY;
  }

  private static String buildLossyCompressionInfo(TagReadable taggable) {
    double[] rates = TagD.getTagValue(taggable, Tag.LossyImageCompressionRatio, double[].class);
    StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.lossy"));
    buf.append(StringUtil.COLON_AND_SPACE);

    if (rates != null && rates.length > 0) {
      for (int i = 0; i < rates.length; i++) {
        if (i > 0) {
          buf.append(",");
        }
        buf.append(" [").append(Math.round(rates[i])).append(":1]");
      }
    } else {
      String val = TagD.getTagValue(taggable, Tag.DerivationDescription, String.class);
      if (val != null) {
        buf.append(StringUtil.getTruncatedString(val, 25, Suffix.THREE_PTS));
      }
    }
    return buf.toString();
  }

  private void drawSeriesInMemoryState(
      Graphics2D g2d, MediaSeries<DicomImageElement> series, int x, int y) {
    if (getDisplayPreferences(LayerItem.PRELOADING_BAR)
        && series instanceof DicomSeries dicomSeries) {
      boolean[] list = dicomSeries.getImageInMemoryList();
      int maxLength = GuiUtils.getScaleLength(120);
      int height = GuiUtils.getScaleLength(5);
      int length = Math.min(list.length, maxLength);
      x -= length;

      preloadingProgressBound.setBounds(x - 1, y - 1, length + 1, height + 1);
      g2d.fillRect(x, y, length, height);
      g2d.setPaint(Color.BLACK);
      g2d.draw(preloadingProgressBound);

      double factorResize = list.length > maxLength ? (double) maxLength / list.length : 1;
      for (int i = 0; i < list.length; i++) {
        if (!list[i]) {
          int val = x + (int) (i * factorResize);
          g2d.fillRect(x, y, val, height);
        }
      }
    }
  }

  private Object getTagValue(
      TagW tag,
      MediaSeriesGroup patient,
      MediaSeriesGroup study,
      MediaSeries<DicomImageElement> series,
      ImageElement image) {
    if (image.containTagKey(tag)) {
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

  protected void drawExtendedActions(Graphics2D g2d) {
    if (view2DPane.getViewButtons().isEmpty()) {
      return;
    }

    int space = GuiUtils.getScaleLength(10);
    Point2D topRight = getPosition(Position.TopRight);
    Point2D.Double midy = calculateMiddleYPosition(space, topRight);

    SynchData synchData = (SynchData) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
    boolean tile = synchData != null && SynchData.Mode.TILE.equals(synchData.getMode());

    for (ViewButton b : view2DPane.getViewButtons()) {
      if (shouldDrawButton(b, tile)) {
        positionAndDrawButton(g2d, b, space, topRight, midy);
      }
    }
  }

  private Point2D.Double calculateMiddleYPosition(int space, Point2D topRight) {
    int height = 0;
    for (ViewButton b : view2DPane.getViewButtons()) {
      if (b.isVisible() && b.getPosition() == GridBagConstraints.EAST) {
        height += b.getIcon().getIconHeight() + space;
      }
    }
    return new Point2D.Double(
        topRight.getX(), view2DPane.getJComponent().getHeight() * 0.5 - (height - space) * 0.5);
  }

  private boolean shouldDrawButton(ViewButton b, boolean tile) {
    return b.isVisible() && !(tile && ActionW.KO_SELECTION.getTitle().equals(b.getName()));
  }

  private void positionAndDrawButton(
      Graphics2D g2d, ViewButton b, int space, Point2D topRight, Point2D.Double midy) {
    Icon icon = b.getIcon();
    int position = b.getPosition();

    switch (position) {
      case GridBagConstraints.EAST -> {
        b.x = midy.x - icon.getIconWidth();
        b.y = midy.y;
        midy.y += icon.getIconHeight() + space;
      }
      case GridBagConstraints.NORTHEAST -> {
        b.x = topRight.getX() - icon.getIconWidth();
        b.y = topRight.getY();
        topRight.setLocation(topRight.getX() - icon.getIconWidth() + space, topRight.getY());
      }
      case GridBagConstraints.SOUTHEAST -> {
        Point2D bottomRight = getPosition(Position.BottomRight);
        b.x = bottomRight.getX() - icon.getIconWidth();
        b.y = bottomRight.getY() - icon.getIconHeight();
        bottomRight.setLocation(
            bottomRight.getX() - icon.getIconWidth() + space, bottomRight.getY());
      }
      case GridBagConstraints.NORTHWEST -> {
        Point2D topLeft = getPosition(Position.TopLeft);
        b.x = topLeft.getX();
        b.y = topLeft.getY();
        topLeft.setLocation(topLeft.getX() + icon.getIconWidth() + space, topLeft.getY());
      }
      case GridBagConstraints.SOUTHWEST -> {
        Point2D bottomLeft = getPosition(Position.BottomLeft);
        b.x = bottomLeft.getX();
        b.y = bottomLeft.getY() - icon.getIconHeight();
        bottomLeft.setLocation(bottomLeft.getX() + icon.getIconWidth() + space, bottomLeft.getY());
      }
    }
    ViewButton.drawButtonBackground(g2d, view2DPane.getJComponent(), b, icon);
  }

  private record OrientationInfo(Plan plan, String colLeft, String rowTop) {}
}
