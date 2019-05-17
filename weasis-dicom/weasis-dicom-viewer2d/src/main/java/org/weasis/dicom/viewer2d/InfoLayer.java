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
package org.weasis.dicom.viewer2d;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Rectangle;
import java.awt.font.TextAttribute;
import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Icon;
import javax.vecmath.Vector3d;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.api.util.StringUtil.Suffix;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.graphic.AbstractGraphicLabel;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.RejectedKOSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.display.ModalityView;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.codec.geometry.ImageOrientation.Label;
import org.weasis.dicom.explorer.DicomModel;

/**
 * The Class InfoLayer.
 *
 * @author Nicolas Roduit
 */
public class InfoLayer extends AbstractInfoLayer<DicomImageElement> {
    private static final long serialVersionUID = 3234560631747133075L;

    private static final Color highlight = new Color(255, 153, 153);

    public InfoLayer(ViewCanvas<DicomImageElement> view2DPane) {
        super(view2DPane);
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
    public LayerAnnotation getLayerCopy(ViewCanvas view2DPane) {
        InfoLayer layer = new InfoLayer(view2DPane);
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
        if (!visible || image == null) {
            return;
        }
        OpManager disOp = view2DPane.getDisplayOpManager();
        ModalityInfoData modality;
        Modality mod = Modality.getModality(TagD.getTagValue(view2DPane.getSeries(), Tag.Modality, String.class));
        modality = ModalityView.getModlatityInfos(mod);

        final Rectangle bound = view2DPane.getJComponent().getBounds();
        float midx = bound.width / 2f;
        float midy = bound.height / 2f;
        thickLength = g2.getFont().getSize() * 1.5f; // font 10 => 15 pixels
        thickLength = thickLength < 5.0 ? 5.0 : thickLength;

        g2.setPaint(Color.BLACK);

        boolean hideMin = !getDisplayPreferences(MIN_ANNOTATIONS);
        final float fontHeight = FontTools.getAccurateFontHeight(g2);
        final float midfontHeight = fontHeight * FontTools.getMidFontHeightFactor();
        float drawY = bound.height - border - 1.5f; // -1.5 for outline

        if (!image.isReadable()) {
            String message = Messages.getString("InfoLayer.msg_not_read"); //$NON-NLS-1$
            float y = midy;
            AbstractGraphicLabel.paintColorFontOutline(g2, message,
                midx - g2.getFontMetrics().stringWidth(message) / 2.0F, y, Color.RED);
            String tsuid = TagD.getTagValue(image, Tag.TransferSyntaxUID, String.class);
            if (StringUtil.hasText(tsuid)) {
                tsuid = Messages.getString("InfoLayer.tsuid") + StringUtil.COLON_AND_SPACE + tsuid; //$NON-NLS-1$
                y += fontHeight;
                AbstractGraphicLabel.paintColorFontOutline(g2, tsuid,
                    midx - g2.getFontMetrics().stringWidth(tsuid) / 2.0F, y, Color.RED);
            }

            String[] desc = image.getMediaReader().getReaderDescription();
            if (desc != null) {
                for (String str : desc) {
                    if (StringUtil.hasText(str)) {
                        y += fontHeight;
                        AbstractGraphicLabel.paintColorFontOutline(g2, str,
                            midx - g2.getFontMetrics().stringWidth(str) / 2F, y, Color.RED);
                    }
                }
            }
        }

        if (image.isReadable() && getDisplayPreferences(SCALE)) {
            drawScale(g2, bound, fontHeight);
        }
        if (image.isReadable() && getDisplayPreferences(LUT) && hideMin) {
            drawLUT(g2, bound, midfontHeight);
        }

        if (image != null) {
            /*
             * IHE BIR RAD TF-­‐2: 4.16.4.2.2.5.8
             *
             * Whether or not lossy compression has been applied, derived from Lossy Image 990 Compression (0028,2110),
             * and if so, the value of Lossy Image Compression Ratio (0028,2112) and Lossy Image Compression Method
             * (0028,2114), if present (as per FDA Guidance for the Submission Of Premarket Notifications for Medical
             * Image Management Devices, July 27, 2000).
             */
            drawY -= fontHeight;
            if ("01".equals(TagD.getTagValue(image, Tag.LossyImageCompression))) { //$NON-NLS-1$
                double[] rates = TagD.getTagValue(image, Tag.LossyImageCompressionRatio, double[].class);
                StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.lossy"));//$NON-NLS-1$
                buf.append(StringUtil.COLON_AND_SPACE);
                if (rates != null && rates.length > 0) {
                    for (int i = 0; i < rates.length; i++) {
                        if (i > 0) {
                            buf.append(","); //$NON-NLS-1$
                        }
                        buf.append(" ["); //$NON-NLS-1$
                        buf.append(Math.round(rates[i]));
                        buf.append(":1"); //$NON-NLS-1$
                        buf.append(']');
                    }
                } else {
                    String val = TagD.getTagValue(image, Tag.DerivationDescription, String.class);
                    if (val != null) {
                        buf.append(StringUtil.getTruncatedString(val, 25, Suffix.THREE_PTS));
                    }
                }

                AbstractGraphicLabel.paintColorFontOutline(g2, buf.toString(), border, drawY, Color.RED);
                drawY -= fontHeight;
            }

            Integer frame = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
            RejectedKOSpecialElement koElement = DicomModel.getRejectionKoSpecialElement(view2DPane.getSeries(),
                TagD.getTagValue(image, Tag.SOPInstanceUID, String.class), frame);

            if (koElement != null) {
                float y = midy;
                String message = "Not a valid image: " + koElement.getDocumentTitle(); //$NON-NLS-1$
                AbstractGraphicLabel.paintColorFontOutline(g2, message,
                    midx - g2.getFontMetrics().stringWidth(message) / 2F, y, Color.RED);
            }
        }

        if (getDisplayPreferences(PIXEL) && hideMin) {
            StringBuilder sb = new StringBuilder(Messages.getString("InfoLayer.pixel")); //$NON-NLS-1$
            sb.append(StringUtil.COLON_AND_SPACE);
            if (pixelInfo != null) {
                sb.append(pixelInfo.getPixelValueText());
                sb.append(" - "); //$NON-NLS-1$
                sb.append(pixelInfo.getPixelPositionText());
            }
            String str = sb.toString();
            AbstractGraphicLabel.paintFontOutline(g2, str, border, drawY - 1);
            drawY -= fontHeight + 2;
            pixelInfoBound.setBounds(border - 2, (int) drawY + 3,
                g2.getFontMetrics(view2DPane.getLayerFont()).stringWidth(str) + 4, (int) fontHeight + 2);
        }
        if (getDisplayPreferences(WINDOW_LEVEL) && hideMin) {
            StringBuilder sb = new StringBuilder();
            Number window = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd());
            Number level = (Number) disOp.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd());
            boolean outside = false;
            if (window != null && level != null) {
                sb.append(ActionW.WINLEVEL.getTitle());
                sb.append(StringUtil.COLON_AND_SPACE);
                sb.append(DecFormater.allNumber(window));
                sb.append("/");//$NON-NLS-1$
                sb.append(DecFormater.allNumber(level));

                if (image != null) {
                    PresentationStateReader prReader =
                        (PresentationStateReader) view2DPane.getActionValue(PresentationStateReader.TAG_PR_READER);
                    boolean pixelPadding =
                        (Boolean) disOp.getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd());
                    double minModLUT = image.getMinValue(prReader, pixelPadding);
                    double maxModLUT = image.getMaxValue(prReader, pixelPadding);
                    double minp = level.doubleValue() - window.doubleValue() / 2.0;
                    double maxp = level.doubleValue() + window.doubleValue() / 2.0;
                    if (minp > maxModLUT || maxp < minModLUT) {
                        outside = true;
                        sb.append(" - "); //$NON-NLS-1$
                        sb.append(Messages.getString("InfoLayer.msg_outside_levels")); //$NON-NLS-1$
                    }
                }
            }
            if (outside) {
                AbstractGraphicLabel.paintColorFontOutline(g2, sb.toString(), border, drawY, Color.RED);
            } else {
                AbstractGraphicLabel.paintFontOutline(g2, sb.toString(), border, drawY);
            }
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ZOOM) && hideMin) {
            AbstractGraphicLabel.paintFontOutline(g2, Messages.getString("InfoLayer.zoom") + StringUtil.COLON_AND_SPACE //$NON-NLS-1$
                + DecFormater.percentTwoDecimal(view2DPane.getViewModel().getViewScale()), border, drawY);
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ROTATION) && hideMin) {
            AbstractGraphicLabel.paintFontOutline(g2, Messages.getString("InfoLayer.angle") + StringUtil.COLON_AND_SPACE //$NON-NLS-1$
                + view2DPane.getActionValue(ActionW.ROTATION.cmd()) + " " //$NON-NLS-1$
                + Messages.getString("InfoLayer.angle_symb"), //$NON-NLS-1$
                border, drawY);
            drawY -= fontHeight;
        }

        if (getDisplayPreferences(FRAME) && hideMin) {
            StringBuilder buf = new StringBuilder(Messages.getString("InfoLayer.frame")); //$NON-NLS-1$
            buf.append(StringUtil.COLON_AND_SPACE);
            if (image != null) {
                Integer inst = TagD.getTagValue(image, Tag.InstanceNumber, Integer.class);
                if (inst != null) {
                    buf.append("["); //$NON-NLS-1$
                    buf.append(inst);
                    buf.append("] "); //$NON-NLS-1$
                }
            }
            buf.append(view2DPane.getFrameIndex() + 1);
            buf.append(" / "); //$NON-NLS-1$
            buf.append(view2DPane.getSeries()
                .size((Filter<DicomImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())));
            AbstractGraphicLabel.paintFontOutline(g2, buf.toString(), border, drawY);
            drawY -= fontHeight;

            Double imgProgression = (Double) view2DPane.getActionValue(ActionW.PROGRESSION.cmd());
            if (imgProgression != null) {
                drawY -= 13;
                int pColor = (int) (510 * imgProgression);
                g2.setPaint(new Color(510 - pColor > 255 ? 255 : 510 - pColor, pColor > 255 ? 255 : pColor, 0));
                g2.fillOval(border, (int) drawY, 13, 13);
            }
        }
        Point2D.Float[] positions = new Point2D.Float[4];
        positions[3] = new Point2D.Float(border, drawY - 5);

        if (getDisplayPreferences(ANNOTATIONS) && image != null) {
            Series series = (Series) view2DPane.getSeries();
            MediaSeriesGroup study = getParent(series, DicomModel.study);
            MediaSeriesGroup patient = getParent(series, DicomModel.patient);
            CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
            boolean anonymize = getDisplayPreferences(ANONYM_ANNOTATIONS);
            drawY = fontHeight;
            TagView[] infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null) {
                    if (hideMin || infos[j].containsTag(TagD.get(Tag.PatientName))) {
                        for (TagW tag : infos[j].getTag()) {
                            if (!anonymize || tag.getAnonymizationType() != 1) {
                                Object value = getTagValue(tag, patient, study, series, image);
                                if (value != null) {
                                    String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                                    if (StringUtil.hasText(str)) {
                                        AbstractGraphicLabel.paintFontOutline(g2, str, border, drawY);
                                        drawY += fontHeight;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            positions[0] = new Point2D.Float(border, drawY - fontHeight + 5);

            corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
            drawY = fontHeight;
            infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null) {
                    if (hideMin || infos[j].containsTag(TagD.get(Tag.SeriesDate))) {
                        Object value;
                        for (TagW tag : infos[j].getTag()) {
                            if (!anonymize || tag.getAnonymizationType() != 1) {
                                value = getTagValue(tag, patient, study, series, image);
                                if (value != null) {
                                    String str = tag.getFormattedTagValue(value, infos[j].getFormat());
                                    if (StringUtil.hasText(str)) {
                                        AbstractGraphicLabel.paintFontOutline(g2, str,
                                            bound.width - g2.getFontMetrics().stringWidth(str) - (float) border, drawY);
                                        drawY += fontHeight;
                                    }
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            positions[1] = new Point2D.Float((float) bound.width - border, drawY - fontHeight + 5f);

            drawY = bound.height - border - 1.5f; // -1.5 for outline
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
                                        AbstractGraphicLabel.paintFontOutline(g2, str,
                                            bound.width - g2.getFontMetrics().stringWidth(str) - (float) border, drawY);
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
            positions[2] = new Point2D.Float((float) bound.width - border, drawY - 5);

            // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK);
            // String str = synchLink != null && synchLink ? "linked" : "unlinked"; //$NON-NLS-1$ //$NON-NLS-2$
            // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - BORDER, drawY);

            double[] v = TagD.getTagValue(image, Tag.ImageOrientationPatient, double[].class);
            Integer columns = TagD.getTagValue(image, Tag.Columns, Integer.class);
            Integer rows = TagD.getTagValue(image, Tag.Rows, Integer.class);
            StringBuilder orientation = new StringBuilder(mod.name());
            if (rows != null && columns != null) {
                orientation.append(" (");//$NON-NLS-1$
                orientation.append(columns);
                orientation.append("x");//$NON-NLS-1$
                orientation.append(rows);
                orientation.append(")");//$NON-NLS-1$

            }
            String colLeft = null;
            String rowTop = null;
            if (getDisplayPreferences(IMAGE_ORIENTATION) && v != null && v.length == 6) {
                orientation.append(" - ");//$NON-NLS-1$
                Label imgOrientation = ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1],
                    v[2], v[3], v[4], v[5]);
                orientation.append(imgOrientation);

                // Set the opposite vector direction (otherwise label should be placed in mid-right and mid-bottom
                Vector3d vr = new Vector3d(-v[0], -v[1], -v[2]);
                Vector3d vc = new Vector3d(-v[3], -v[4], -v[5]);

                Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());
                if (rotationAngle != null && rotationAngle != 0) {
                    double rad = Math.toRadians(rotationAngle);
                    double[] normal = ImageOrientation.computeNormalVectorOfPlan(v);
                    if (normal != null && normal.length == 3) {
                        Vector3d result = new Vector3d(0.0, 0.0, 0.0);
                        Vector3d axis = new Vector3d(normal);
                        rotate(vr, axis, -rad, result);
                        vr = result;

                        result = new Vector3d(0.0, 0.0, 0.0);
                        rotate(vc, axis, -rad, result);
                        vc = result;
                    }
                }

                if (LangUtil.getNULLtoFalse((Boolean) view2DPane.getActionValue((ActionW.FLIP.cmd())))) {
                    vr.x = -vr.x;
                    vr.y = -vr.y;
                    vr.z = -vr.z;
                }

                colLeft = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vr.x, vr.y, vr.z);
                rowTop = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vc.x, vc.y, vc.z);

            } else {
                String[] po = TagD.getTagValue(image, Tag.PatientOrientation, String[].class);
                Integer rotationAngle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());
                if (po != null && po.length == 2 && (rotationAngle == null || rotationAngle == 0)) {
                    // Do not display if there is a transformation
                    if (LangUtil.getNULLtoFalse((Boolean) view2DPane.getActionValue((ActionW.FLIP.cmd())))) {
                        colLeft = po[0];
                    } else {
                        StringBuilder buf = new StringBuilder();
                        for (char c : po[0].toCharArray()) {
                            buf.append(ImageOrientation.getImageOrientationOposite(c));
                        }
                        colLeft = buf.toString();
                    }
                    StringBuilder buf = new StringBuilder();
                    for (char c : po[1].toCharArray()) {
                        buf.append(ImageOrientation.getImageOrientationOposite(c));
                    }
                    rowTop = buf.toString();
                }
            }
            if (rowTop != null && colLeft != null) {
                if (colLeft.length() < 1) {
                    colLeft = " "; //$NON-NLS-1$
                }
                if (rowTop.length() < 1) {
                    rowTop = " "; //$NON-NLS-1$
                }
                Font oldFont = g2.getFont();
                Font bigFont = oldFont.deriveFont(oldFont.getSize() + 5.0f);
                g2.setFont(bigFont);
                Map<TextAttribute, Object> map = new HashMap<>(1);
                map.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
                String fistLetter = rowTop.substring(0, 1);
                AbstractGraphicLabel.paintColorFontOutline(g2, fistLetter, midx, fontHeight + 5f, highlight);
                int shiftx = g2.getFontMetrics().stringWidth(fistLetter);
                Font subscriptFont = bigFont.deriveFont(map);
                if (rowTop.length() > 1) {
                    g2.setFont(subscriptFont);
                    AbstractGraphicLabel.paintColorFontOutline(g2, rowTop.substring(1, rowTop.length()), midx + shiftx,
                        fontHeight + 5f, highlight);
                    g2.setFont(bigFont);
                }

                AbstractGraphicLabel.paintColorFontOutline(g2, colLeft.substring(0, 1), (float) (border + thickLength),
                    midy + fontHeight / 2.0f, highlight);

                if (colLeft.length() > 1) {
                    g2.setFont(subscriptFont);
                    AbstractGraphicLabel.paintColorFontOutline(g2, colLeft.substring(1, colLeft.length()),
                        (float) (border + thickLength + shiftx), midy + fontHeight / 2.0f, highlight);
                }
                g2.setFont(oldFont);
            }

            AbstractGraphicLabel.paintFontOutline(g2, orientation.toString(), border, bound.height - border - 1.5f); // -1.5
                                                                                                                     // for
            // outline
        } else {
            positions[0] = new Point2D.Float(border, border);
            positions[1] = new Point2D.Float((float) bound.width - border, border);
            positions[2] = new Point2D.Float((float) bound.width - border, (float) bound.height - border);
        }
        drawExtendedActions(g2, positions);
    }

    private MediaSeriesGroup getParent(Series series, TreeModelNode node) {
        if (series != null) {
            Object tagValue = series.getTagValue(TagW.ExplorerModel);
            if (tagValue instanceof DicomModel) {
                return ((DicomModel) tagValue).getParent(series, node);
            }
        }
        return null;
    }

    private static void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x = axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.x * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y = axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.y * Math.cos(angle) + (axis.z * vSrc.x - axis.x * vSrc.z) * Math.sin(angle);
        vDst.z = axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle))
            + vSrc.z * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
    }

    private void drawSeriesInMemoryState(Graphics2D g2d, MediaSeries series, int x, int y) {
        if (getDisplayPreferences(PRELOADING_BAR) && series instanceof DicomSeries) {
            DicomSeries s = (DicomSeries) series;
            boolean[] list = s.getImageInMemoryList();
            int length = list.length > 120 ? 120 : list.length;
            x -= length;
            preloadingProgressBound.setBounds(x - 1, y - 1, length + 1, 5 + 1);
            g2d.fillRect(x, y, length, 5);
            g2d.setPaint(Color.BLACK);
            g2d.draw(preloadingProgressBound);
            double factorResize = list.length > 120 ? 120.0 / list.length : 1;
            for (int i = 0; i < list.length; i++) {
                if (!list[i]) {
                    int val = x + (int) (i * factorResize);
                    g2d.drawLine(val, y, val, y + 3);
                }
            }
        }
    }

    private Object getTagValue(TagW tag, MediaSeriesGroup patient, MediaSeriesGroup study, Series series,
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
            int space = 5;
            int height = 0;
            for (ViewButton b : view2DPane.getViewButtons()) {
                if (b.isVisible() && b.getPosition() == GridBagConstraints.EAST) {
                    height += b.getIcon().getIconHeight() + 5;
                }
            }

            Point2D.Float midy = new Point2D.Float(positions[1].x,
                (float) (view2DPane.getJComponent().getHeight() * 0.5 - (height - space) * 0.5));
            SynchData synchData = (SynchData) view2DPane.getActionValue(ActionW.SYNCH_LINK.cmd());
            boolean tile = synchData != null && SynchData.Mode.TILE.equals(synchData.getMode());

            for (ViewButton b : view2DPane.getViewButtons()) {
                if (b.isVisible() && (tile && b.getIcon() == View2d.KO_ICON) == false) {
                    Icon icon = b.getIcon();
                    int p = b.getPosition();

                    if (p == GridBagConstraints.EAST) {
                        b.x = midy.x - icon.getIconWidth();
                        b.y = midy.y;
                        midy.y += icon.getIconHeight() + 5;
                    } else if (p == GridBagConstraints.NORTHEAST) {
                        b.x = positions[1].x - icon.getIconWidth();
                        b.y = positions[1].y;
                        positions[1].x -= icon.getIconWidth() + 5;
                    } else if (p == GridBagConstraints.SOUTHEAST) {
                        b.x = positions[2].x - icon.getIconWidth();
                        b.y = positions[2].y - icon.getIconHeight();
                        positions[2].x -= icon.getIconWidth() + 5;
                    } else if (p == GridBagConstraints.NORTHWEST) {
                        b.x = positions[0].x;
                        b.y = positions[0].y;
                        positions[0].x += icon.getIconWidth() + 5;
                    } else if (p == GridBagConstraints.SOUTHWEST) {
                        b.x = positions[3].x;
                        b.y = positions[3].y - icon.getIconHeight();
                        positions[3].x += icon.getIconWidth() + 5;
                    } else {
                        b.x = midy.x - icon.getIconWidth();
                        b.y = midy.y;
                        midy.y += icon.getIconHeight() + 5;
                    }
                    icon.paintIcon(view2DPane.getJComponent(), g2d, (int) b.x, (int) b.y);
                }
            }
        }
    }

}
