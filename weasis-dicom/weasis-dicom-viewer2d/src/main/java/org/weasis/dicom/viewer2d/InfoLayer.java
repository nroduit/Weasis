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
import java.util.HashMap;
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
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
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
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.DefaultWlPresentation;

/**
 * The Class InfoLayer.
 *
 * @author Nicolas Roduit
 */
public class InfoLayer extends AbstractInfoLayer<DicomImageElement> {

  public InfoLayer(ViewCanvas<DicomImageElement> view2DPane) {
    this(view2DPane, true);
  }

  public InfoLayer(ViewCanvas<DicomImageElement> view2DPane, boolean useGlobalPreferences) {
    super(view2DPane, useGlobalPreferences);
    displayPreferences.put(ANNOTATIONS, true);
    displayPreferences.put(MIN_ANNOTATIONS, false);
    displayPreferences.put(ANONYM_ANNOTATIONS, false);
    displayPreferences.put(SCALE, true);
    displayPreferences.put(LUT, false);
    displayPreferences.put(IMAGE_ORIENTATION, true);
    displayPreferences.put(WINDOW_LEVEL, true);
    displayPreferences.put(ZOOM, true);
    displayPreferences.put(ROTATION, false);
    displayPreferences.put(FRAME, true);
    displayPreferences.put(PIXEL, true);

    displayPreferences.put(PRELOADING_BAR, true);
  }

  @Override
  public LayerAnnotation getLayerCopy(ViewCanvas view2DPane, boolean useGlobalPreferences) {
    InfoLayer layer = new InfoLayer(view2DPane, useGlobalPreferences);
    HashMap<String, Boolean> prefs = layer.displayPreferences;
    prefs.put(ANNOTATIONS, getDisplayPreferences(ANNOTATIONS));
    prefs.put(ANONYM_ANNOTATIONS, getDisplayPreferences(ANONYM_ANNOTATIONS));
    prefs.put(IMAGE_ORIENTATION, getDisplayPreferences(IMAGE_ORIENTATION));
    prefs.put(SCALE, getDisplayPreferences(SCALE));
    prefs.put(LUT, getDisplayPreferences(LUT));
    prefs.put(PIXEL, getDisplayPreferences(PIXEL));
    prefs.put(WINDOW_LEVEL, getDisplayPreferences(WINDOW_LEVEL));
    prefs.put(ZOOM, getDisplayPreferences(ZOOM));
    prefs.put(ROTATION, getDisplayPreferences(ROTATION));
    prefs.put(FRAME, getDisplayPreferences(FRAME));
    prefs.put(PRELOADING_BAR, getDisplayPreferences(PRELOADING_BAR));
    prefs.put(MIN_ANNOTATIONS, getDisplayPreferences(MIN_ANNOTATIONS));
    return layer;
  }

  @Override
  public void paint(Graphics2D g2) {
    DicomImageElement image = view2DPane.getImage();
    FontMetrics fontMetrics = g2.getFontMetrics();
    final Rectangle bound = view2DPane.getJComponent().getBounds();
    int minSize = fontMetrics.stringWidth(Messages.getString("InfoLayer.msg_outside_levels")) * 2;
    if (!visible || image == null || minSize > bound.width || minSize > bound.height) {
      return;
    }

    Object[] oldRenderingHints =
        GuiUtils.setRenderingHints(g2, true, false, view2DPane.requiredTextAntialiasing());

    OpManager disOp = view2DPane.getDisplayOpManager();
    Modality mod =
        Modality.getModality(TagD.getTagValue(view2DPane.getSeries(), Tag.Modality, String.class));
    ModalityInfoData modality = ModalityView.getModlatityInfos(mod);

    float midX = bound.width / 2f;
    float midY = bound.height / 2f;
    final int fontHeight = fontMetrics.getHeight();
    thickLength = Math.max(fontHeight, GuiUtils.getScaleLength(5.0));

    g2.setPaint(Color.BLACK);

    boolean hideMin = !getDisplayPreferences(MIN_ANNOTATIONS);
    final int midFontHeight = fontHeight - fontMetrics.getDescent();
    float drawY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline

    if (!image.isReadable()) {
      String message = Messages.getString("InfoLayer.msg_not_read");
      float y = midY;
      FontTools.paintColorFontOutline(
          g2,
          message,
          midX - g2.getFontMetrics().stringWidth(message) / 2.0F,
          y,
          IconColor.ACTIONS_RED.getColor());
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

    if (image.isReadable() && view2DPane.getSourceImage() != null) {
      if (getDisplayPreferences(SCALE)) {
        PlanarImage source = image.getImage();
        if (source != null) {
          ImageProperties props =
              new ImageProperties(
                  source.width(),
                  source.height(),
                  image.getPixelSize(),
                  image.getRescaleX(),
                  image.getRescaleY(),
                  image.getPixelSpacingUnit(),
                  image.getPixelSizeCalibrationDescription());
          drawScale(g2, bound, fontHeight, props);
        }
      }
      if (getDisplayPreferences(LUT) && hideMin) {
        drawLUT(g2, bound, midFontHeight);
      }
    }

    /*
     * IHE BIR RAD TF-­‐2: 4.16.4.2.2.5.8
     *
     * Whether lossy compression has been applied, derived from Lossy Image 990 Compression (0028,2110),
     * and if so, the value of Lossy Image Compression Ratio (0028,2112) and Lossy Image Compression Method
     * (0028,2114), if present (as per FDA Guidance for the Submission Of Premarket Notifications for Medical
     * Image Management Devices, July 27, 2000).
     */
    drawY -= fontHeight;
    if ("01".equals(TagD.getTagValue(image, Tag.LossyImageCompression))) {
      double[] rates = TagD.getTagValue(image, Tag.LossyImageCompressionRatio, double[].class);
      StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.lossy"));
      buf.append(StringUtil.COLON_AND_SPACE);
      if (rates != null && rates.length > 0) {
        for (int i = 0; i < rates.length; i++) {
          if (i > 0) {
            buf.append(",");
          }
          buf.append(" [");
          buf.append(Math.round(rates[i]));
          buf.append(":1");
          buf.append(']');
        }
      } else {
        String val = TagD.getTagValue(image, Tag.DerivationDescription, String.class);
        if (val != null) {
          buf.append(StringUtil.getTruncatedString(val, 25, Suffix.THREE_PTS));
        }
      }

      FontTools.paintColorFontOutline(
          g2, buf.toString(), border, drawY, IconColor.ACTIONS_RED.getColor());
      drawY -= fontHeight;
    }

    Integer frame = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
    RejectedKOSpecialElement koElement =
        DicomModel.getRejectionKoSpecialElement(
            view2DPane.getSeries(),
            TagD.getTagValue(image, Tag.SOPInstanceUID, String.class),
            frame);

    if (koElement != null) {
      String message = "Not a valid image: " + koElement.getDocumentTitle(); // NON-NLS
      FontTools.paintColorFontOutline(
          g2,
          message,
          midX - g2.getFontMetrics().stringWidth(message) / 2F,
          midY,
          IconColor.ACTIONS_RED.getColor());
    }

    if (getDisplayPreferences(PIXEL) && hideMin) {
      StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer.pixel"));
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
    if (getDisplayPreferences(WINDOW_LEVEL) && hideMin) {
      StringBuilder sb = new StringBuilder();
      Number window = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd());
      Number level = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd());
      boolean outside = false;
      if (window != null && level != null) {
        sb.append(ActionW.WINLEVEL.getTitle());
        sb.append(StringUtil.COLON_AND_SPACE);
        sb.append(DecFormatter.allNumber(window));
        sb.append("/");
        sb.append(DecFormatter.allNumber(level));

        PrDicomObject prDicomObject =
            PRManager.getPrDicomObject(view2DPane.getActionValue(ActionW.PR_STATE.cmd()));
        boolean pixelPadding =
            (Boolean) disOp.getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd());
        DefaultWlPresentation wlp = new DefaultWlPresentation(prDicomObject, pixelPadding);
        double minModLUT = image.getMinValue(wlp);
        double maxModLUT = image.getMaxValue(wlp);
        double minp = level.doubleValue() - window.doubleValue() / 2.0;
        double maxp = level.doubleValue() + window.doubleValue() / 2.0;
        if (minp > maxModLUT || maxp < minModLUT) {
          outside = true;
          sb.append(" - ");
          sb.append(Messages.getString("InfoLayer.msg_outside_levels"));
        }
      }
      if (outside) {
        FontTools.paintColorFontOutline(
            g2, sb.toString(), border, drawY, IconColor.ACTIONS_RED.getColor());
      } else {
        FontTools.paintFontOutline(g2, sb.toString(), border, drawY);
      }
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(ZOOM) && hideMin) {
      FontTools.paintFontOutline(
          g2,
          Messages.getString("InfoLayer.zoom")
              + StringUtil.COLON_AND_SPACE
              + DecFormatter.percentTwoDecimal(view2DPane.getViewModel().getViewScale()),
          border,
          drawY);
      drawY -= fontHeight;
    }
    if (getDisplayPreferences(ROTATION) && hideMin) {
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

    if (getDisplayPreferences(FRAME) && hideMin) {
      StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.frame"));
      buf.append(StringUtil.COLON_AND_SPACE);
      Integer inst = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
      if (inst != null) {
        buf.append("[");
        buf.append(inst);
        buf.append("] ");
      }
      buf.append(view2DPane.getFrameIndex() + 1);
      buf.append(" / ");
      buf.append(
          view2DPane
              .getSeries()
              .size(
                  (Filter<DicomImageElement>)
                      view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())));
      FontTools.paintFontOutline(g2, buf.toString(), border, drawY);
      drawY -= fontHeight;

      Double imgProgression = (Double) view2DPane.getActionValue(ActionW.PROGRESSION.cmd());
      if (imgProgression != null) {
        int inset = GuiUtils.getScaleLength(13);
        drawY -= inset;
        int pColor = (int) (510 * imgProgression);
        g2.setPaint(new Color(Math.min(510 - pColor, 255), Math.min(pColor, 255), 0));
        g2.fillOval(border, (int) drawY, inset, inset);
      }
    }
    Point2D.Float[] positions = new Point2D.Float[4];
    positions[3] = new Point2D.Float(border, drawY - GuiUtils.getScaleLength(5));

    if (getDisplayPreferences(ANNOTATIONS)) {
      Series series = (Series) view2DPane.getSeries();
      MediaSeriesGroup study = getParent(series, DicomModel.study);
      MediaSeriesGroup patient = getParent(series, DicomModel.patient);
      CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
      boolean anonymize = getDisplayPreferences(ANONYM_ANNOTATIONS);
      drawY = fontHeight;
      TagView[] infos = corner.getInfos();
      for (TagView tagView : infos) {
        if (tagView != null && (hideMin || tagView.containsTag(TagD.get(Tag.PatientName)))) {
          for (TagW tag : tagView.getTag()) {
            if (!anonymize || tag.getAnonymizationType() != 1) {
              Object value = getTagValue(tag, patient, study, series, image);
              if (value != null) {
                String str = tag.getFormattedTagValue(value, tagView.getFormat());
                if (StringUtil.hasText(str)) {
                  FontTools.paintFontOutline(g2, str, border, drawY);
                  drawY += fontHeight;
                }
                break;
              }
            }
          }
        }
      }
      positions[0] = new Point2D.Float(border, drawY - fontHeight + GuiUtils.getScaleLength(5));

      corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
      drawY = fontHeight;
      infos = corner.getInfos();
      for (TagView info : infos) {
        if (info != null) {
          if (hideMin || info.containsTag(TagD.get(Tag.SeriesDate))) {
            Object value;
            for (TagW tag : info.getTag()) {
              if (!anonymize || tag.getAnonymizationType() != 1) {
                value = getTagValue(tag, patient, study, series, image);
                if (value != null) {
                  String str = tag.getFormattedTagValue(value, info.getFormat());
                  if (StringUtil.hasText(str)) {
                    FontTools.paintFontOutline(
                        g2,
                        str,
                        bound.width - g2.getFontMetrics().stringWidth(str) - (float) border,
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
      positions[1] =
          new Point2D.Float(
              (float) bound.width - border, drawY - fontHeight + GuiUtils.getScaleLength(5));

      drawY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline
      if (hideMin) {
        corner = modality.getCornerInfo(CornerDisplay.BOTTOM_RIGHT);
        infos = corner.getInfos();
        for (int j = infos.length - 1; j >= 0; j--) {
          if (infos[j] != null) {
            Object value;
            for (TagW tag : infos[j].getTag()) {
              if (!anonymize || tag.getAnonymizationType() != 1) {
                value = getTagValue(tag, patient, study, series, image);
                if (value != null) {
                  String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                  if (StringUtil.hasText(str)) {
                    FontTools.paintFontOutline(
                        g2,
                        str,
                        bound.width - g2.getFontMetrics().stringWidth(str) - (float) border,
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
        drawSeriesInMemoryState(g2, view2DPane.getSeries(), bound.width - border, (int) (drawY));
      }
      positions[2] =
          new Point2D.Float((float) bound.width - border, drawY - GuiUtils.getScaleLength(5));

      // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK);
      // String str = synchLink != null && synchLink ? "linked" : "unlinked"; // NON-NLS
      // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - BORDER,
      // drawY);

      Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
      Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
      StringBuilder orientation = new StringBuilder(mod.name());
      Plan plan = null;
      if (rows != null && columns != null) {
        orientation.append(" (");
        orientation.append(columns);
        orientation.append("x"); // NON-NLS
        orientation.append(rows);
        orientation.append(")");
      }
      String colLeft = null;
      String rowTop = null;
      boolean quadruped =
          LangUtil.getNULLtoFalse(
              TagD.getTagValue(series, Tag.AnatomicalOrientationType, Boolean.class));
      Vector3d vr = ImageOrientation.getRowImagePosition(image);
      Vector3d vc = ImageOrientation.getColumnImagePosition(image);
      if (getDisplayPreferences(IMAGE_ORIENTATION) && vr != null && vc != null) {
        orientation.append(" - ");
        plan = ImageOrientation.getPlan(vr, vc);
        orientation.append(plan);
        orientation.append(" ");

        // Set the opposite vector direction (otherwise label should be placed in mid-right and
        // mid-bottom

        Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());
        if (rotationAngle != null && rotationAngle != 0) {
          double rad = Math.toRadians(rotationAngle);
          Vector3d normal = VectorUtils.computeNormalOfSurface(vr, vc);
          vr.negate();
          vr.rotateAxis(-rad, normal.x, normal.y, normal.z);
          vc.negate();
          vc.rotateAxis(-rad, normal.x, normal.y, normal.z);
        } else {
          vr.negate();
          vc.negate();
        }

        if (LangUtil.getNULLtoFalse((Boolean) view2DPane.getActionValue((ActionW.FLIP.cmd())))) {
          vr.negate();
        }

        colLeft = ImageOrientation.getOrientation(vr, quadruped);
        rowTop = ImageOrientation.getOrientation(vc, quadruped);

      } else {
        String[] po = TagD.getTagValue(image, Tag.PatientOrientation, String[].class);
        Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());
        if (po != null && po.length == 2 && (rotationAngle == null || rotationAngle == 0)) {
          // Do not display if there is a transformation
          if (LangUtil.getNULLtoFalse((Boolean) view2DPane.getActionValue((ActionW.FLIP.cmd())))) {
            colLeft = po[0];
          } else {
            StringBuilder buf = new StringBuilder();
            for (String s : po[0].split("(?=\\p{Upper})")) {
              buf.append(ImageOrientation.getImageOrientationOpposite(s, quadruped));
            }
            colLeft = buf.toString();
          }
          StringBuilder buf = new StringBuilder();
          for (String s : po[1].split("(?=\\p{Upper})")) {
            buf.append(ImageOrientation.getImageOrientationOpposite(s, quadruped));
          }
          rowTop = buf.toString();
        }
      }
      if (rowTop != null && colLeft != null) {
        if (colLeft.length() < 1) {
          colLeft = " ";
        }
        if (rowTop.length() < 1) {
          rowTop = " ";
        }
        Font oldFont = g2.getFont();
        Font bigFont = oldFont.deriveFont(oldFont.getSize() + 5.0f);
        g2.setFont(bigFont);
        Map<TextAttribute, Object> map = new HashMap<>(1);
        map.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
        String fistLetter = rowTop.substring(0, 1);
        int shiftX = g2.getFontMetrics().stringWidth(fistLetter);
        int shiftY = fontHeight + GuiUtils.getScaleLength(5);
        FontTools.paintColorFontOutline(g2, fistLetter, midX - shiftX, shiftY, highlight);
        Font subscriptFont = bigFont.deriveFont(map);
        if (rowTop.length() > 1) {
          g2.setFont(subscriptFont);
          FontTools.paintColorFontOutline(g2, rowTop.substring(1), midX, shiftY, highlight);
          g2.setFont(bigFont);
        }

        FontTools.paintColorFontOutline(
            g2,
            colLeft.substring(0, 1),
            (float) (border + thickLength),
            midY + fontHeight / 2.0f,
            highlight);

        if (colLeft.length() > 1) {
          g2.setFont(subscriptFont);
          FontTools.paintColorFontOutline(
              g2,
              colLeft.substring(1),
              (float) (border + thickLength + shiftX),
              midY + fontHeight / 2.0f,
              highlight);
        }
        g2.setFont(oldFont);
      }

      float offsetY = bound.height - border - GuiUtils.getScaleLength(1.5f); // -1.5 for outline
      FontTools.paintFontOutline(g2, orientation.toString(), border, offsetY);

      if (plan != null) {
        if (view2DPane instanceof MprView) {
          Color planColor = null;
          if (Plan.AXIAL.equals(plan)) {
            planColor = Biped.F.getColor();
          } else if (Plan.CORONAL.equals(plan)) {
            planColor = Biped.A.getColor();
          } else if (Plan.SAGITTAL.equals(plan)) {
            planColor = Biped.L.getColor();
          }

          int shiftX = g2.getFontMetrics().stringWidth(orientation.toString());
          g2.setColor(planColor);
          int size = midFontHeight - fontMetrics.getDescent();
          int shiftY = bound.height - border - size;
          g2.fillRect(border + shiftX, shiftY, size - 1, size - 1);
        }
      }
    } else {
      positions[0] = new Point2D.Float(border, border);
      positions[1] = new Point2D.Float((float) bound.width - border, border);
      positions[2] = new Point2D.Float((float) bound.width - border, (float) bound.height - border);
    }

    drawExtendedActions(g2, positions);
    GuiUtils.resetRenderingHints(g2, oldRenderingHints);
  }

  public static MediaSeriesGroup getParent(Series series, TreeModelNode node) {
    if (series != null) {
      Object tagValue = series.getTagValue(TagW.ExplorerModel);
      if (tagValue instanceof DicomModel model) {
        return model.getParent(series, node);
      }
    }
    return null;
  }

  private void drawSeriesInMemoryState(Graphics2D g2d, MediaSeries series, int x, int y) {
    if (getDisplayPreferences(PRELOADING_BAR) && series instanceof DicomSeries dicomSeries) {
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
      Series series,
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

  protected void drawExtendedActions(Graphics2D g2d, Point2D.Float[] positions) {
    if (!view2DPane.getViewButtons().isEmpty()) {
      int space = GuiUtils.getScaleLength(5);
      int height = 0;
      for (ViewButton b : view2DPane.getViewButtons()) {
        if (b.isVisible() && b.getPosition() == GridBagConstraints.EAST) {
          height += b.getIcon().getIconHeight() + space;
        }
      }

      Point2D.Float midy =
          new Point2D.Float(
              positions[1].x,
              (float) (view2DPane.getJComponent().getHeight() * 0.5 - (height - space) * 0.5));
      SynchData synchData = (SynchData) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
      boolean tile = synchData != null && SynchData.Mode.TILE.equals(synchData.getMode());
      for (ViewButton b : view2DPane.getViewButtons()) {
        if (b.isVisible() && !(tile && ActionW.KO_SELECTION.getTitle().equals(b.getName()))) {
          Icon icon = b.getIcon();
          int p = b.getPosition();

          if (p == GridBagConstraints.EAST) {
            b.x = midy.x - icon.getIconWidth();
            b.y = midy.y;
            midy.y += icon.getIconHeight() + space;
          } else if (p == GridBagConstraints.NORTHEAST) {
            b.x = positions[1].x - icon.getIconWidth();
            b.y = positions[1].y;
            positions[1].x -= icon.getIconWidth() + space;
          } else if (p == GridBagConstraints.SOUTHEAST) {
            b.x = positions[2].x - icon.getIconWidth();
            b.y = positions[2].y - icon.getIconHeight();
            positions[2].x -= icon.getIconWidth() + space;
          } else if (p == GridBagConstraints.NORTHWEST) {
            b.x = positions[0].x;
            b.y = positions[0].y;
            positions[0].x += icon.getIconWidth() + space;
          } else if (p == GridBagConstraints.SOUTHWEST) {
            b.x = positions[3].x;
            b.y = positions[3].y - icon.getIconHeight();
            positions[3].x += icon.getIconWidth() + space;
          } else {
            b.x = midy.x - icon.getIconWidth();
            b.y = midy.y;
            midy.y += icon.getIconHeight() + space;
          }
          icon.paintIcon(view2DPane.getJComponent(), g2d, (int) b.x, (int) b.y);
        }
      }
    }
  }
}
