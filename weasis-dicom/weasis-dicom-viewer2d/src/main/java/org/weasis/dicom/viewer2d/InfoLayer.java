/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.viewer2d;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.HashMap;
import java.util.Hashtable;

import javax.media.jai.Histogram;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.vecmath.Vector3d;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.DecFormater;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.op.ByteLut;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.graphic.GraphicLabel;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.display.CornerDisplay;
import org.weasis.dicom.codec.display.CornerInfoData;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.display.ModalityInfoData;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.pref.ModalityPrefView;

/**
 * The Class InfoLayer.
 * 
 * @author Nicolas Roduit
 */
public class InfoLayer implements AnnotationsLayer {
    private static final Color highlight = new Color(255, 153, 153);

    private final HashMap<String, Boolean> displayPreferences = new HashMap<String, Boolean>();
    private boolean visible = true;
    private final Color color = Color.yellow;
    private static final int BORDER = 10;
    private final DefaultView2d view2DPane;
    private final DicomModel model;
    private String pixelInfo = ""; //$NON-NLS-1$
    private final Rectangle pixelInfoBound;
    private final Rectangle preloadingProgressBound;
    private int border = BORDER;
    private double thickLength = 15.0;
    private boolean showBottomScale = true;

    public InfoLayer(DefaultView2d view2DPane) {
        this.view2DPane = view2DPane;
        displayPreferences.put(ANNOTATIONS, true);
        displayPreferences.put(ANONYM_ANNOTATIONS, false);
        displayPreferences.put(IMAGE_ORIENTATION, true);
        displayPreferences.put(SCALE, true);
        displayPreferences.put(LUT, false);
        displayPreferences.put(PIXEL, true);
        displayPreferences.put(WINDOW_LEVEL, true);
        displayPreferences.put(ZOOM, true);
        displayPreferences.put(ROTATION, false);
        displayPreferences.put(FRAME, true);
        displayPreferences.put(PRELOADING_BAR, true);
        this.pixelInfoBound = new Rectangle();
        this.preloadingProgressBound = new Rectangle();

        // FIXME when config with no DICOM Explorer
        DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
        if (dicomView != null) {
            model = (DicomModel) dicomView.getDataExplorerModel();
        } else {
            model = null;
        }
    }

    @Override
    public AnnotationsLayer getLayerCopy(DefaultView2d view2DPane) {
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
        return layer;
    }

    @Override
    public int getBorder() {
        return border;
    }

    @Override
    public void setBorder(int border) {
        this.border = border;
    }

    @Override
    public boolean isShowBottomScale() {
        return showBottomScale;
    }

    @Override
    public void setShowBottomScale(boolean showBottomScale) {
        this.showBottomScale = showBottomScale;
    }

    @Override
    public void paint(Graphics2D g2) {
        ImageElement image = view2DPane.getImage();
        if (!visible || image == null) {
            return;
        }

        ModalityInfoData modality;
        Modality mod = Modality.getModality((String) view2DPane.getSeries().getTagValue(TagW.Modality));
        modality = ModalityPrefView.getModlatityInfos(mod);

        final Rectangle bound = view2DPane.getBounds();
        float midx = bound.width / 2f;
        float midy = bound.height / 2f;
        thickLength = g2.getFont().getSize() * 1.5f; // font 10 => 15 pixels
        thickLength = thickLength < 5.0 ? 5.0 : thickLength;

        g2.setPaint(color);

        final float fontHeight = FontTools.getAccurateFontHeight(g2);
        final float midfontHeight = fontHeight * FontTools.getMidFontHeightFactor();
        float drawY = bound.height - border - 1.5f; // -1.5 for outline
        DicomImageElement dcm = null;
        if (image instanceof DicomImageElement) {
            dcm = (DicomImageElement) image;
        }
        if (!image.isReadable()) {
            String message = Messages.getString("InfoLayer.msg_not_read"); //$NON-NLS-1$
            float y = midy;
            GraphicLabel.paintColorFontOutline(g2, message, midx - g2.getFontMetrics().stringWidth(message) / 2, y,
                Color.RED);
            String tsuid = (String) image.getTagValue(TagW.TransferSyntaxUID);
            if (tsuid != null) {
                tsuid = Messages.getString("InfoLayer.tsuid") + " " + tsuid; //$NON-NLS-1$ //$NON-NLS-2$
                y += fontHeight;
                GraphicLabel.paintColorFontOutline(g2, tsuid, midx - g2.getFontMetrics().stringWidth(tsuid) / 2, y,
                    Color.RED);
            }

            String[] desc = image.getMediaReader().getReaderDescription();
            if (desc != null) {
                for (String str : desc) {
                    if (str != null) {
                        y += fontHeight;
                        GraphicLabel.paintColorFontOutline(g2, str, midx - g2.getFontMetrics().stringWidth(str) / 2, y,
                            Color.RED);
                    }
                }
            }
        }
        if (image.isReadable() && getDisplayPreferences(SCALE)) {
            drawScale(g2, bound, fontHeight);
        }
        if (image.isReadable() && getDisplayPreferences(LUT)) {
            drawLUT(g2, bound, midfontHeight);
            // drawLUTgraph(g2, bound, midfontHeight);
        }
        // if (getDisplayPreferences(IMAGE_ORIENTATION)) {
        // For image Orientation and compression
        if (dcm != null) {
            drawY -= fontHeight;
            String tsuid = getLossyTransferSyntaxUID((String) dcm.getTagValue(TagW.TransferSyntaxUID));
            if (tsuid != null) {
                Integer rate = (Integer) view2DPane.getSeries().getTagValue(TagW.WadoCompressionRate);
                GraphicLabel.paintColorFontOutline(g2, Messages.getString("InfoLayer.lossy") //$NON-NLS-1$
                    + " " //$NON-NLS-1$
                    + tsuid + ((rate == null || rate < 1) ? "" : " " + rate + " " //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                        + Messages.getString("InfoLayer.percent_symb")), border, drawY, Color.RED); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                drawY -= fontHeight;
            }
        }
        // }

        if (getDisplayPreferences(PIXEL)) {
            String str = Messages.getString("InfoLayer.pixel") + pixelInfo; //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2, str, border, drawY - 1);
            drawY -= fontHeight + 2;
            pixelInfoBound.setBounds(border - 2, (int) drawY + 3, g2.getFontMetrics(view2DPane.getLayerFont())
                .stringWidth(str) + 4, (int) fontHeight + 2);
            // g2.draw(pixelInfoBound);
        }
        if (getDisplayPreferences(WINDOW_LEVEL)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.win") + " " + view2DPane.getActionValue(ActionW.WINDOW.cmd()) + " " + Messages.getString("InfoLayer.level") //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
                        + " " + view2DPane.getActionValue(ActionW.LEVEL.cmd()), border, drawY); //$NON-NLS-1$
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ZOOM)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.zoom") + " " + DecFormater.twoDecimal(view2DPane.getViewModel().getViewScale() * 100) //$NON-NLS-1$ //$NON-NLS-2$
                        + " " + Messages.getString("InfoLayer.percent_symb"), border, drawY); //$NON-NLS-1$ //$NON-NLS-2$
            drawY -= fontHeight;
        }
        if (getDisplayPreferences(ROTATION)) {
            GraphicLabel
                .paintFontOutline(
                    g2,
                    Messages.getString("InfoLayer.angle") + " " + view2DPane.getActionValue(ActionW.ROTATION.cmd()) + " " + Messages.getString("InfoLayer.angle_symb"), border, drawY); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
            drawY -= fontHeight;
        }

        if (getDisplayPreferences(FRAME)) {
            String instance = " "; //$NON-NLS-1$
            if (dcm != null) {
                Integer inst = (Integer) dcm.getTagValue(TagW.InstanceNumber);
                if (inst != null && inst != 0) {
                    instance = " [" + inst + "] "; //$NON-NLS-1$ //$NON-NLS-2$
                }
            }
            GraphicLabel.paintFontOutline(
                g2,
                Messages.getString("InfoLayer.frame") + instance + (view2DPane.getFrameIndex() + 1) + " / " //$NON-NLS-1$ //$NON-NLS-2$
                    + view2DPane.getSeries().size(
                        (Filter<DicomImageElement>) view2DPane.getActionValue(ActionW.FILTERED_SERIES.cmd())), border,
                drawY);
            drawY -= fontHeight;

            Double imgProgression = (Double) view2DPane.getActionValue(ActionW.PROGRESSION.cmd());
            if (imgProgression != null) {
                drawY -= 13;
                int pColor = (int) (510 * imgProgression);
                g2.setPaint(new Color(510 - pColor > 255 ? 255 : 510 - pColor, pColor > 255 ? 255 : pColor, 0));
                g2.fillOval(border, (int) drawY, 13, 13);
                drawY -= 2;
            }
        }

        if (getDisplayPreferences(ANNOTATIONS) && dcm != null) {
            Series series = (Series) view2DPane.getSeries();
            MediaSeriesGroup study = model.getParent(series, DicomModel.study);
            MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
            CornerInfoData corner = modality.getCornerInfo(CornerDisplay.TOP_LEFT);
            boolean anonymize = getDisplayPreferences(ANONYM_ANNOTATIONS);
            drawY = fontHeight;
            TagW[] infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null && (!anonymize || infos[j].getAnonymizationType() != 1)) {
                    Object value = getTagValue(infos[j], patient, study, series, dcm);
                    if (value != null) {
                        GraphicLabel.paintFontOutline(g2, infos[j].getFormattedText(value), border, drawY);
                        drawY += fontHeight;
                    }
                }
            }
            corner = modality.getCornerInfo(CornerDisplay.TOP_RIGHT);
            drawY = fontHeight;
            infos = corner.getInfos();
            for (int j = 0; j < infos.length; j++) {
                if (infos[j] != null && (!anonymize || infos[j].getAnonymizationType() != 1)) {
                    Object value = getTagValue(infos[j], patient, study, series, dcm);
                    if (value != null) {
                        String str = infos[j].getFormattedText(value);
                        GraphicLabel.paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str)
                            - border, drawY);
                        drawY += fontHeight;
                    }
                }
            }
            corner = modality.getCornerInfo(CornerDisplay.BOTTOM_RIGHT);
            drawY = bound.height - border - 1.5f; // -1.5 for outline
            infos = corner.getInfos();
            for (int j = infos.length - 1; j >= 0; j--) {
                if (infos[j] != null && (!anonymize || infos[j].getAnonymizationType() != 1)) {
                    Object value = getTagValue(infos[j], patient, study, series, dcm);
                    if (value != null) {
                        String str = infos[j].getFormattedText(value);
                        GraphicLabel.paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str)
                            - border, drawY);
                        drawY -= fontHeight;
                    }
                }
            }
            drawSeriesInMemoryState(g2, view2DPane.getSeries(), bound.width - border, (int) (drawY - 5));
            drawY -= 8;

            // Boolean synchLink = (Boolean) view2DPane.getActionValue(ActionW.SYNCH_LINK);
            //            String str = synchLink != null && synchLink ? "linked" : "unlinked"; //$NON-NLS-1$ //$NON-NLS-2$
            // paintFontOutline(g2, str, bound.width - g2.getFontMetrics().stringWidth(str) - BORDER, drawY);

            double[] v = (double[]) dcm.getTagValue(TagW.ImageOrientationPatient);
            Integer rows = (Integer) dcm.getTagValue(TagW.Rows);
            Integer columns = (Integer) dcm.getTagValue(TagW.Columns);
            StringBuffer orientation = new StringBuffer(mod.name());
            if (rows != null && columns != null) {
                orientation.append(" (");//$NON-NLS-1$ 
                orientation.append(dcm.getTagValue(TagW.Columns));
                orientation.append("x");//$NON-NLS-1$ 
                orientation.append(dcm.getTagValue(TagW.Rows));
                orientation.append(")");//$NON-NLS-1$ 

            }
            String colLeft = null;
            String rowTop = null;
            if (getDisplayPreferences(IMAGE_ORIENTATION) && v != null && v.length == 6) {
                orientation.append(" - ");//$NON-NLS-1$ 
                String imgOrientation =
                    ImageOrientation.makeImageOrientationLabelFromImageOrientationPatient(v[0], v[1], v[2], v[3], v[4],
                        v[5]);
                if (imgOrientation != null) {
                    orientation.append(imgOrientation);
                }
                // Set the opposite vector direction (otherwise label should be placed in mid-right and mid-bottom
                Vector3d vr = new Vector3d(-v[0], -v[1], -v[2]);
                Vector3d vc = new Vector3d(-v[3], -v[4], -v[5]);

                int angle = (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd());

                if (angle != 0) {
                    double rad = Math.toRadians(angle);
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

                if ((Boolean) view2DPane.getActionValue(ActionW.FLIP.cmd())) {
                    vr.x = -vr.x;
                    vr.y = -vr.y;
                    vr.z = -vr.z;
                }

                colLeft = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vr.x, vr.y, vr.z);
                rowTop = ImageOrientation.makePatientOrientationFromPatientRelativeDirectionCosine(vc.x, vc.y, vc.z);

            } else {
                String[] po = (String[]) dcm.getTagValue(TagW.PatientOrientation);
                if (po != null && po.length == 2 && (Integer) view2DPane.getActionValue(ActionW.ROTATION.cmd()) == 0) {
                    // Do not display if there is a transformation
                    if ((Boolean) view2DPane.getActionValue(ActionW.FLIP.cmd())) {
                        colLeft = po[0];
                    } else {
                        StringBuffer buf = new StringBuffer();
                        for (char c : po[0].toCharArray()) {
                            buf.append(getImageOrientationOposite(c));
                        }
                        colLeft = buf.toString();
                    }
                    StringBuffer buf = new StringBuffer();
                    for (char c : po[1].toCharArray()) {
                        buf.append(getImageOrientationOposite(c));
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
                Hashtable<TextAttribute, Object> map = new Hashtable<TextAttribute, Object>(1);
                map.put(TextAttribute.SUPERSCRIPT, TextAttribute.SUPERSCRIPT_SUB);
                String fistLetter = rowTop.substring(0, 1);
                GraphicLabel.paintColorFontOutline(g2, fistLetter, midx, fontHeight + 5f, highlight);
                int shiftx = g2.getFontMetrics().stringWidth(fistLetter);
                Font subscriptFont = bigFont.deriveFont(map);
                if (rowTop.length() > 1) {
                    g2.setFont(subscriptFont);
                    GraphicLabel.paintColorFontOutline(g2, rowTop.substring(1, rowTop.length()), midx + shiftx,
                        fontHeight + 5f, highlight);
                    g2.setFont(bigFont);
                }

                GraphicLabel.paintColorFontOutline(g2, colLeft.substring(0, 1), (float) (border + thickLength), midy
                    + fontHeight / 2.0f, highlight);

                if (colLeft.length() > 1) {
                    g2.setFont(subscriptFont);
                    GraphicLabel.paintColorFontOutline(g2, colLeft.substring(1, colLeft.length()), (float) (border
                        + thickLength + shiftx), midy + fontHeight / 2.0f, highlight);
                }
                g2.setFont(oldFont);
            }

            GraphicLabel.paintFontOutline(g2, orientation.toString(), border, bound.height - border - 1.5f); // -1.5 for
                                                                                                             // outline

        }
    }

    private void rotate(Vector3d vSrc, Vector3d axis, double angle, Vector3d vDst) {
        axis.normalize();
        vDst.x =
            axis.x * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.x
                * Math.cos(angle) + (-axis.z * vSrc.y + axis.y * vSrc.z) * Math.sin(angle);
        vDst.y =
            axis.y * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.y
                * Math.cos(angle) + (axis.z * vSrc.x + axis.x * vSrc.z) * Math.sin(angle);
        vDst.z =
            axis.z * (axis.x * vSrc.x + axis.y * vSrc.y + axis.z * vSrc.z) * (1 - Math.cos(angle)) + vSrc.z
                * Math.cos(angle) + (-axis.y * vSrc.x + axis.x * vSrc.y) * Math.sin(angle);
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

    private String getLossyTransferSyntaxUID(String tsuid) {
        if (tsuid != null) {
            if ("1.2.840.10008.1.2.4.50".equals(tsuid)) { //$NON-NLS-1$
                return "JPEG Baseline"; //$NON-NLS-1$
            }
            if ("1.2.840.10008.1.2.4.51".equals(tsuid)) { //$NON-NLS-1$
                return "JPEG Extended"; //$NON-NLS-1$
            }
            if ("1.2.840.10008.1.2.4.81".equals(tsuid)) { //$NON-NLS-1$
                return "JPEG-LS (Near-Lossless)"; //$NON-NLS-1$
            }
            if ("1.2.840.10008.1.2.4.91".equals(tsuid)) { //$NON-NLS-1$
                return "JPEG 2000"; //$NON-NLS-1$
            }
        }
        return null;
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

    public Rectangle2D getOutLine(Line2D l) {
        Rectangle2D r = l.getBounds2D();
        r.setFrame(r.getX() - 1.0, r.getY() - 1.0, r.getWidth() + 2.0, r.getHeight() + 2.0);
        return r;
    }

    public void drawLUT(Graphics2D g2, Rectangle bound, float midfontHeight) {
        ByteLut lut = (ByteLut) view2DPane.getActionValue(ActionW.LUT.cmd());
        if (lut != null && bound.height > 350) {

            if (lut.getLutTable() == null) {
                lut = ByteLut.grayLUT;
            }
            byte[][] table =
                (Boolean) view2DPane.getActionValue(ActionW.INVERSELUT.cmd()) ? lut.getInvertedLutTable() : lut
                    .getLutTable();
            float length = table[0].length;
            float x = bound.width - 30f;
            float y = bound.height / 2f - length / 2f;

            g2.setPaint(Color.black);
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

            g2.setPaint(Color.white);
            Line2D.Float line = new Line2D.Float(x - 10f, y - 1f, x - 1f, y - 1f);
            g2.draw(line);
            float stepWindow = (Float) view2DPane.getActionValue(ActionW.WINDOW.cmd()) / separation;
            float firstlevel = (Float) view2DPane.getActionValue(ActionW.LEVEL.cmd()) - stepWindow * 2f;
            String str = "" + (int) firstlevel; //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 12f, y + midfontHeight);
            for (int i = 1; i < separation; i++) {
                float posY = y + i * step;
                line.setLine(x - 5f, posY, x - 1f, posY);
                g2.draw(line);
                str = "" + (int) (firstlevel + i * stepWindow); //$NON-NLS-1$
                GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 7, posY
                    + midfontHeight);
            }

            line.setLine(x - 10f, y + length + 1f, x - 1f, y + length + 1f);
            g2.draw(line);
            str = "" + (int) (firstlevel + 4 * stepWindow); //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2, str, x - g2.getFontMetrics().stringWidth(str) - 12, y + length
                + midfontHeight);
            rect.setRect(x - 1f, y - 1f, 21f, length + 2f);
            g2.draw(rect);

            for (int k = 0; k < length; k++) {
                g2.setPaint(new Color(table[0][k] & 0xff, table[1][k] & 0xff, table[2][k] & 0xff));
                rect.setRect(x, y + k, 19f, 1f);
                g2.draw(rect);
            }
        }
    }

    // TODO must be implemented as component of the layout (must inherit Jcomponent and implements SeriesViewerListener)
    public void drawLUTgraph(Graphics2D g2d, Rectangle viewPaneBound, float midfontHeight) {
        boolean pixelPadding = true;

        final Paint oldPaint = g2d.getPaint();
        final RenderingHints oldRenderingHints = g2d.getRenderingHints();
        final Stroke oldStroke = g2d.getStroke();

        // /////////////////////////////////////////////////////////////////////////////////////

        final DicomImageElement image = (DicomImageElement) view2DPane.getImage();

        // Min/Max out Lut pixel values defined as unsigned 8 bits data
        final int minOutputValue = 0;
        final int maxOutputValue = 255;

        final float window = (Float) view2DPane.getActionValue(ActionW.WINDOW.cmd());
        final float level = (Float) view2DPane.getActionValue(ActionW.LEVEL.cmd());

        final float lowLevel = Math.round(level - window / 2);
        final float highLevel = Math.round(level + window / 2);

        int lowInputValue =
            (int) (image.getMinValue(pixelPadding) < lowLevel ? lowLevel : image.getMinValue(pixelPadding));
        int highInputValue =
            (int) (image.getMaxValue(pixelPadding) > highLevel ? highLevel : image.getMaxValue(pixelPadding));

        final boolean inverseLut = (Boolean) view2DPane.getActionValue(ActionW.INVERSELUT.cmd());

        LutShape lutShape = (LutShape) view2DPane.getActionValue(ActionW.LUT_SHAPE.cmd());

        LookupTableJAI lookup =
            image.getVOILookup(image.getModalityLookup(pixelPadding), window, level, lutShape, true, pixelPadding);
        // Note : when fillLutOutside argument is true lookupTable returned is full range allocated

        // System.out.println(lutShape.toString());
        final byte[] fullRangeVoiLUT = lookup.getByteData(0);

        final int lutInputRange = fullRangeVoiLUT.length - 1;
        final int minInputValue = lookup.getOffset();
        final int maxInputValue = minInputValue + lutInputRange;

        lowInputValue = (lowInputValue < minInputValue) ? minInputValue : lowInputValue;
        highInputValue = (highInputValue > maxInputValue) ? maxInputValue : highInputValue;

        // /////////////////////////////////////////////////////////////////////////////////////

        // Size in pixel of Input/Ouput LUT Range
        final float xAxisCoordinateSystemRange = 511;
        final float yAxisCoordinateSystemRange = 255;

        // Offset in pixel for the Left/Down side of the coordinate system
        boolean isMinInputValueNegative = minInputValue < 0;
        final float xOffsetCoordinateSystemOrigin = isMinInputValueNegative ? (-xAxisCoordinateSystemRange / 2f) : -5f;
        final float yOffsetCoordinateSystemOrigin = -5f;

        final float xAxisCoordinateSystemMinValue = isMinInputValueNegative ? (-xAxisCoordinateSystemRange / 2f) : 0;
        final float xAxisCoordinateSystemMaxValue = xAxisCoordinateSystemRange + xOffsetCoordinateSystemOrigin;

        // TODO - better to use a scaleTransform instead of scale ratio with many variables!!!
        final float xAxisRescaleRatio = xAxisCoordinateSystemRange / lutInputRange;
        final float yAxisRescaleRatio = yAxisCoordinateSystemRange / maxOutputValue;

        // /////////////////////////////////////////////////////////////////////////////////////
        // Coordinate system arrows and lines defined in a CW system
        final Path2D upArrow = new Path2D.Float();
        upArrow.moveTo(0, 0);
        upArrow.lineTo(0, 3);
        upArrow.lineTo(-3, 3);
        upArrow.lineTo(0, 10);
        upArrow.lineTo(3, 3);
        upArrow.lineTo(0, 3);

        final Path2D rightArrow = (Path2D) upArrow.clone();
        rightArrow.transform(AffineTransform.getQuadrantRotateInstance(3));

        final Shape upArrowCoordinateSystemPath =
            AffineTransform.getTranslateInstance(0, yAxisCoordinateSystemRange + 1).createTransformedShape(upArrow);
        final Shape rightArrowCoordinateSystemPath =
            AffineTransform.getTranslateInstance(xAxisCoordinateSystemMaxValue + 1, 0).createTransformedShape(
                rightArrow);

        final Path2D coordinateSystemPath = new Path2D.Float();
        coordinateSystemPath.moveTo(0, yOffsetCoordinateSystemOrigin);
        coordinateSystemPath.lineTo(0, yAxisCoordinateSystemRange);
        coordinateSystemPath.append(upArrowCoordinateSystemPath, false);
        coordinateSystemPath.moveTo(xOffsetCoordinateSystemOrigin, 0);
        coordinateSystemPath.lineTo(xAxisCoordinateSystemMaxValue, 0);
        coordinateSystemPath.append(rightArrowCoordinateSystemPath, false);

        // /////////////////////////////////////////////////////////////////////////////////
        // LUT graph bounding rectangle defined in a CCW system
        final float lutGraphMargin = 30f;
        final float lutGraphWidth = (float) coordinateSystemPath.getBounds2D().getWidth() + 2f * lutGraphMargin;
        final float lutGraphHeight = (float) coordinateSystemPath.getBounds2D().getHeight() + 2f * lutGraphMargin;

        final Path2D lutGraphBoundingRect =
            new Path2D.Float(new Rectangle2D.Float(0, 0, lutGraphWidth, lutGraphHeight));

        // /////////////////////////////////////////////////////////////////////////////////
        // Selected LUT defined in a CW system with the full range input.
        // Note : two path are distinct from the inside and ouside range part of lowInput and highInput values

        final Path2D insideRangeLutPath = new Path2D.Float();
        final Path2D outsideRangeLutPath = new Path2D.Float();

        boolean isOutsideRangeLutPathMoveToDefined = false;
        boolean isRealValuesLutPathMoveToDefined = false;

        for (int i = 0; i < fullRangeVoiLUT.length; i++) {
            int xVal = Math.round((minInputValue + i) * xAxisRescaleRatio);
            int yVal = fullRangeVoiLUT[i] & 0x000000FF; // Mask because byte is signed by default
            yVal = Math.round(yAxisRescaleRatio * (inverseLut ? (maxOutputValue - yVal) : yVal));

            // if (yVal == maxOutputValue || yVal == minOutputValue) {
            // isRealValuesLutPathMoveToDefined = false;
            // isOutsideRangeLutPathMoveToDefined = false;
            // } else {
            if ((minInputValue + i) < lowInputValue || (minInputValue + i) > highInputValue) {
                if (isOutsideRangeLutPathMoveToDefined) {
                    outsideRangeLutPath.lineTo(xVal, yVal);
                    isRealValuesLutPathMoveToDefined = false;
                } else {
                    outsideRangeLutPath.moveTo(xVal, yVal);
                    isOutsideRangeLutPathMoveToDefined = true;
                }
            } else {
                if (isRealValuesLutPathMoveToDefined) {
                    insideRangeLutPath.lineTo(xVal, yVal);
                    isOutsideRangeLutPathMoveToDefined = false;
                } else {
                    insideRangeLutPath.moveTo(xVal, yVal);
                    isRealValuesLutPathMoveToDefined = true;
                }
            }
            // }
        }

        // /////////////////////////////////////////////////////////////////////////////////
        // Path of Interest defined in a CW system

        final Path2D xAxisMaxOutValueLine = new Path2D.Float();
        xAxisMaxOutValueLine.moveTo(xAxisCoordinateSystemMinValue, yAxisCoordinateSystemRange);
        xAxisMaxOutValueLine.lineTo(xAxisCoordinateSystemMaxValue, yAxisCoordinateSystemRange);

        int xLowLevel = Math.round(xAxisRescaleRatio * lowLevel);
        int xHighLevel = Math.round(xAxisRescaleRatio * highLevel);
        int xLevel = Math.round(xAxisRescaleRatio * level);

        final Path2D yAxisOnLowLevelLine = new Path2D.Float();
        if (lowLevel >= lowInputValue) {
            yAxisOnLowLevelLine.moveTo(xLowLevel, 0);
            yAxisOnLowLevelLine.lineTo(xLowLevel, yAxisCoordinateSystemRange);
        }
        final Path2D yAxisOnHighLevelLine = new Path2D.Float();
        if (highLevel <= highInputValue) {
            yAxisOnHighLevelLine.moveTo(xHighLevel, 0);
            yAxisOnHighLevelLine.lineTo(xHighLevel, yAxisCoordinateSystemRange);
        }
        // final Path2D yAxisOnLevelLine = new Path2D.Float();
        // yAxisOnLevelLine.moveTo(xLevel, 0);
        // yAxisOnLevelLine.lineTo(xLevel, yAxisCoordinateSystemRange);
        //
        // final Path2D xAxisOnLevelLine = new Path2D.Float();
        // int yLevel = lookup.lookup(0, (int) level) & 0x000000FF;
        // yLevel = Math.round(yAxisRescaleRatio * (inverseLut ? (maxOutputValue - yLevel) : yLevel));
        // xAxisOnLevelLine.moveTo(0, yLevel);
        // xAxisOnLevelLine.lineTo(xLevel, yLevel);

        // if (((int) level >= 0 && (int) level < fullRangeVoiLUT.length)) {
        // int yLevel = fullRangeVoiLUT[(int) level] & 0x000000FF;
        // yLevel = Math.round(yAxisRescaleRatio * (inverseLut ? (maxOutputValue - yLevel) : yLevel));
        //
        // xAxisOnLevelLine.moveTo(0, yLevel);
        // xAxisOnLevelLine.lineTo(xLevel, yLevel);
        // }

        final Path2D xAxisOnMinValueLine = new Path2D.Float();
        int xMinVal = lowInputValue;
        // int yMinVal = fullRangeVoiLUT[lowInputValue] & 0x000000FF;
        int yMinVal = lookup.lookup(0, lowInputValue) & 0x000000FF;
        yMinVal = inverseLut ? maxOutputValue - yMinVal : yMinVal;

        if (yMinVal != minOutputValue && yMinVal != maxOutputValue) {
            xAxisOnMinValueLine.moveTo(0, Math.round(yAxisRescaleRatio * yMinVal));
            xAxisOnMinValueLine
                .lineTo(Math.round(xAxisRescaleRatio * xMinVal), Math.round(yAxisRescaleRatio * yMinVal));
        }

        final Path2D yAxisOnMinValueLine = new Path2D.Float();
        // if (xMinVal != xLowLevel && xMinVal != xLevel && xMinVal != xHighLevel) {
        yAxisOnMinValueLine.moveTo(Math.round(xAxisRescaleRatio * xMinVal), 0);
        yAxisOnMinValueLine.lineTo(Math.round(xAxisRescaleRatio * xMinVal), Math.round(yAxisRescaleRatio * yMinVal));
        // }

        int xMaxVal = highInputValue;
        // int yMaxVal = fullRangeVoiLUT[highInputValue] & 0x000000FF;
        int yMaxVal = lookup.lookup(0, highInputValue) & 0x000000FF;
        yMaxVal = inverseLut ? maxOutputValue - yMaxVal : yMaxVal;

        final Path2D xAxisOnMaxValueLine = new Path2D.Float();
        if (yMaxVal != minOutputValue && yMaxVal != maxOutputValue) {
            xAxisOnMaxValueLine.moveTo(0, Math.round(yAxisRescaleRatio * yMaxVal));
            xAxisOnMaxValueLine
                .lineTo(Math.round(xAxisRescaleRatio * xMaxVal), Math.round(yAxisRescaleRatio * yMaxVal));
        }
        final Path2D yAxisOnMaxValue = new Path2D.Float();
        // if (xMaxVal != xLowLevel && xMaxVal != xLevel && xMaxVal != xHighLevel) {
        yAxisOnMaxValue.moveTo(Math.round(xAxisRescaleRatio * xMaxVal), 0);
        yAxisOnMaxValue.lineTo(Math.round(xAxisRescaleRatio * xMaxVal), Math.round(yAxisRescaleRatio * yMaxVal));
        // }

        // /////////////////////////////////////////////////////////////////////////////////
        // ViewPane transform in a CCW system

        final float lutGraphXPos = (viewPaneBound.width - lutGraphWidth) / 2;
        final float lutGraphYPos = (viewPaneBound.height - lutGraphHeight) / 2;

        final AffineTransform lutGraphViewPaneTranslate =
            AffineTransform.getTranslateInstance(lutGraphXPos, lutGraphYPos);

        final AffineTransform coordinateSystemViewPaneTransform =
            AffineTransform.getTranslateInstance(-xOffsetCoordinateSystemOrigin, coordinateSystemPath.getBounds2D()
                .getHeight() + yOffsetCoordinateSystemOrigin);
        coordinateSystemViewPaneTransform.translate(lutGraphMargin, lutGraphMargin);
        coordinateSystemViewPaneTransform.concatenate(lutGraphViewPaneTranslate);

        final AffineTransform flipVerticalTransform = AffineTransform.getScaleInstance(1, -1);
        // Note : this flipVertical transform has to be used when drawing is defined in a CW system knowing that
        // graphics2D coordinate system is CCW
        coordinateSystemViewPaneTransform.concatenate(flipVerticalTransform);

        // /////////////////////////////////////////////////////////////////////////////////
        // Transform all path

        lutGraphBoundingRect.transform(lutGraphViewPaneTranslate);

        coordinateSystemPath.transform(coordinateSystemViewPaneTransform);
        xAxisMaxOutValueLine.transform(coordinateSystemViewPaneTransform);
        insideRangeLutPath.transform(coordinateSystemViewPaneTransform);
        outsideRangeLutPath.transform(coordinateSystemViewPaneTransform);
        xAxisOnMinValueLine.transform(coordinateSystemViewPaneTransform);
        yAxisOnMinValueLine.transform(coordinateSystemViewPaneTransform);
        xAxisOnMaxValueLine.transform(coordinateSystemViewPaneTransform);
        yAxisOnMaxValue.transform(coordinateSystemViewPaneTransform);
        yAxisOnLowLevelLine.transform(coordinateSystemViewPaneTransform);
        yAxisOnHighLevelLine.transform(coordinateSystemViewPaneTransform);
        // yAxisOnLevelLine.transform(coordinateSystemViewPaneTransform);
        // xAxisOnLevelLine.transform(coordinateSystemViewPaneTransform);

        // /////////////////////////////////////////////////////////////////////////////////
        // Draw Background

        float alphaReal = 0.75f; // [0.0 ; 1.0]
        int alphaMask = 0x00FFFFFF | (Math.round(alphaReal * 255) << 24);
        g2d.setPaint(new Color(Color.GRAY.getRGB() & alphaMask, true));
        g2d.fill(lutGraphBoundingRect); // Handles background transparency inside bounding rectangle
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // /////////////////////////////////////////////////////////////////////////////////
        // Draw Histogram

        boolean showHistogram = true;
        Histogram histogram = showHistogram ? image.getHistogram(view2DPane.getSourceImage(), pixelPadding) : null;

        if (histogram != null) {

            boolean logarithmRescale = true;

            final int[] histoData = histogram.getBins(0);
            int histoCount = Integer.MIN_VALUE;
            for (int c : histoData) {
                histoCount = Math.max(histoCount, c);
            }

            double maxHistoCount = logarithmRescale ? Math.log1p(histoCount) : histoCount;

            final float yAxisHistoRescaleRatio = (float) (yAxisCoordinateSystemRange / maxHistoCount);
            // final float xAxisHistoRescaleRatio = xAxisCoordinateSystemRange / histogram.getNumBins(0);
            final float xAxisHistoRescaleRatio = xAxisCoordinateSystemRange / lutInputRange;

            // assert histogram.getNumBins(0) == lutInputRange;

            final Point2D pt0 = new Point2D.Float();
            final Point2D pt1 = new Point2D.Float();

            g2d.setPaint(Color.DARK_GRAY);
            g2d.setStroke(new BasicStroke(1.0F));

            // for (int i = 0; i < histogram.getNumBins(0); i++) {
            for (int i = 0; i < lutInputRange; i++) {
                double xVal = (minInputValue + i) * xAxisHistoRescaleRatio;
                double yVal =
                    (logarithmRescale ? Math.log1p(histoData[i]) : (double) histoData[i]) * yAxisHistoRescaleRatio;
                pt0.setLocation(xVal, 0);
                pt1.setLocation(xVal, yVal);

                coordinateSystemViewPaneTransform.transform(pt0, pt0);
                coordinateSystemViewPaneTransform.transform(pt1, pt1);

                g2d.drawLine((int) Math.round(pt0.getX()), (int) Math.round(pt0.getY()), (int) Math.round(pt1.getX()),
                    (int) Math.round(pt1.getY()));
            }
        }
        // /////////////////////////////////////////////////////////////////////////////////
        // Draw Path

        g2d.setPaint(Color.ORANGE);
        g2d.setStroke(new BasicStroke(2.0F));
        g2d.draw(lutGraphBoundingRect);

        g2d.setPaint(Color.RED);
        g2d.setStroke(new BasicStroke(1.0F));
        g2d.draw(coordinateSystemPath);

        g2d.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] { 5.0f },
            0.0f));
        g2d.draw(xAxisMaxOutValueLine);

        g2d.setPaint(Color.BLUE);
        g2d.setStroke(new BasicStroke(1.0F));
        g2d.draw(insideRangeLutPath);

        g2d.setStroke(new BasicStroke(0.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5f, new float[] { 5.0f },
            0.0f));
        g2d.draw(outsideRangeLutPath);

        g2d.draw(xAxisOnMinValueLine);
        g2d.draw(yAxisOnMinValueLine);
        g2d.draw(xAxisOnMaxValueLine);
        g2d.draw(yAxisOnMaxValue);

        g2d.setPaint(Color.CYAN);
        g2d.draw(yAxisOnLowLevelLine);
        g2d.draw(yAxisOnHighLevelLine);
        // g2d.draw(yAxisOnLevelLine);
        // g2d.draw(xAxisOnLevelLine);

        // /////////////////////////////////////////////////////////////////////////////////
        // Draw Strings

        String str = Integer.toString(maxOutputValue);
        int strWidth = g2d.getFontMetrics().stringWidth(str);
        float xStrPos = -strWidth - 8;
        float yStrPos = Math.round(maxOutputValue * yAxisRescaleRatio) - midfontHeight;
        Point2D ptStr = new Point2D.Float(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        str = Integer.toString(minOutputValue);
        strWidth = g2d.getFontMetrics().stringWidth(str);
        xStrPos = -strWidth - 8;
        yStrPos = Math.round(minOutputValue * yAxisRescaleRatio) - midfontHeight;
        ptStr.setLocation(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        str = Integer.toString(yMinVal);
        strWidth = g2d.getFontMetrics().stringWidth(str);
        xStrPos = -strWidth - 8;
        yStrPos = Math.round(yAxisRescaleRatio * yMinVal) - midfontHeight;
        ptStr.setLocation(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        str = Integer.toString(yMaxVal);
        strWidth = g2d.getFontMetrics().stringWidth(str);
        xStrPos = -strWidth - 8;
        yStrPos = Math.round(yAxisRescaleRatio * yMaxVal) - midfontHeight;
        ptStr.setLocation(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        str = Integer.toString(xMinVal);
        strWidth = g2d.getFontMetrics().stringWidth(str);
        xStrPos = Math.round(xAxisRescaleRatio * xMinVal) - strWidth / 2;
        yStrPos = -midfontHeight - 8;
        ptStr.setLocation(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        str = Integer.toString(xMaxVal);
        strWidth = g2d.getFontMetrics().stringWidth(str);
        xStrPos = Math.round(xAxisRescaleRatio * xMaxVal) - strWidth / 2;
        yStrPos = -midfontHeight - 8;
        ptStr.setLocation(xStrPos, yStrPos);
        coordinateSystemViewPaneTransform.transform(ptStr, ptStr);
        GraphicLabel.paintFontOutline(g2d, str, Math.round(ptStr.getX()), Math.round(ptStr.getY()));

        // ///////////////////////////////////////////////////////////////////////////////
        g2d.setPaint(oldPaint);
        g2d.setStroke(oldStroke);
        g2d.setRenderingHints(oldRenderingHints);
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
            ajustShowScale(scale,
                (int) Math.min(zoomFactor * source.getWidth() * image.getRescaleX(), bound.width / 2.0));
        if (showBottomScale && scaleSizex > 30.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizex * scale, unit);
            g2d.setPaint(color);
            g2d.setStroke(new BasicStroke(1.0F));
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setPaint(Color.black);

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
            if (pixSizeDesc != null) {
                GraphicLabel.paintFontOutline(g2d, pixSizeDesc, (float) (posx + scaleSizex + 5), (float) posy
                    - fontHeight);
            }
            str += " " + unit[0].getAbbreviation(); //$NON-NLS-1$
            GraphicLabel.paintFontOutline(g2d, str, (float) (posx + scaleSizex + 5), (float) posy);
        }

        double scaleSizeY =
            ajustShowScale(scale,
                (int) Math.min(zoomFactor * source.getHeight() * image.getRescaleY(), bound.height / 2.0));

        if (scaleSizeY > 30.0d) {
            Unit[] unit = { image.getPixelSpacingUnit() };
            String str = ajustLengthDisplay(scaleSizeY * scale, unit);

            g2d.setPaint(color);
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

            g2d.setPaint(Color.white);
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

            GraphicLabel.paintFontOutline(g2d,
                str + " " + unit[0].getAbbreviation(), (int) posx, (int) (posy - 5 * strokeWidth)); //$NON-NLS-1$
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
            return ajustScaleLength < 0.001 ? DecFormater.scientificFormat(ajustScaleLength) : DecFormater
                .fourDecimal(ajustScaleLength);
        }
        return ajustScaleLength > 50000.0 ? DecFormater.scientificFormat(ajustScaleLength) : DecFormater
            .twoDecimal(ajustScaleLength);
    }

    public static final char getImageOrientationOposite(char c) {
        switch (c) {
            case 'L':
                return 'R';
            case 'R':
                return 'L';
            case 'P':
                return 'A';
            case 'A':
                return 'P';
            case 'H':
                return 'F';
            case 'F':
                return 'H';
        }
        return ' ';
    }

    public static final char getMajorAxisFromPatientRelativeDirectionCosine(double x, double y, double z) {
        char axis = ' ';

        char orientationX = x < 0 ? 'L' : 'R';
        char orientationY = y < 0 ? 'P' : 'A';
        char orientationZ = z < 0 ? 'H' : 'F';

        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        // The tests here really don't need to check the other dimensions,
        // just the threshold, since the sum of the squares should be == 1.0
        // but just in case ...

        if (absX > 0.8 && absX > absY && absX > absZ) {
            axis = orientationX;
        } else if (absY > 0.8 && absY > absX && absY > absZ) {
            axis = orientationY;
        } else if (absZ > 0.8 && absZ > absX && absZ > absY) {
            axis = orientationZ;
        }

        return axis;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getDisplayPreferences(java.lang.String)
     */
    @Override
    public boolean getDisplayPreferences(String item) {
        Boolean val = displayPreferences.get(item);
        return val == null ? false : val;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public int getLevel() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public void setLevel(int i) {
        // TODO Auto-generated method stub

    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#setDisplayPreferencesValue(java.lang.String, boolean)
     */
    @Override
    public boolean setDisplayPreferencesValue(String displayItem, boolean selected) {
        boolean selected2 = getDisplayPreferences(displayItem);
        displayPreferences.put(displayItem, selected);
        return selected != selected2;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getPreloadingProgressBound()
     */
    @Override
    public Rectangle getPreloadingProgressBound() {
        return preloadingProgressBound;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#getPixelInfoBound()
     */
    @Override
    public Rectangle getPixelInfoBound() {
        return pixelInfoBound;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.weasis.dicom.viewer2d.AnnotationsLayer#setPixelInfo(java.lang.String)
     */
    @Override
    public void setPixelInfo(String pixelInfo) {
        this.pixelInfo = pixelInfo;
    }
}
