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
package org.weasis.dicom.codec.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.util.HashMap;
import java.util.Optional;

import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.TiledImage;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.MergeImgOp;
import org.weasis.core.api.image.op.ShutterDescriptor;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LangUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.OverlayUtils;

public class ShutterOp extends AbstractOp {

    public static final String OP_NAME = ActionW.IMAGE_SHUTTER.getTitle();

    /**
     * Set whether the shutter is applied (Required parameter).
     *
     * Boolean value.
     */
    public static final String P_SHOW = "show"; //$NON-NLS-1$
    public static final String P_SHAPE = "shape"; //$NON-NLS-1$
    public static final String P_RGB_COLOR = "rgb.color"; //$NON-NLS-1$
    public static final String P_PS_VALUE = "ps.value"; //$NON-NLS-1$
    public static final String P_PR_ELEMENT = "pr.element"; //$NON-NLS-1$
    public static final String P_IMAGE_ELEMENT = "img.element"; //$NON-NLS-1$

    public ShutterOp() {
        setName(OP_NAME);
    }

    public ShutterOp(ShutterOp op) {
        super(op);
    }

    @Override
    public ShutterOp copy() {
        return new ShutterOp(this);
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        OpEvent type = event.getEventType();
        if (OpEvent.ImageChange.equals(type) || OpEvent.ResetDisplay.equals(type)) {
            ImageElement img = event.getImage();
            // If no image, reset the shutter
            boolean noMedia = img == null;
            setParam(P_SHAPE, noMedia ? null : img.getTagValue(TagW.ShutterFinalShape));
            setParam(P_PS_VALUE, noMedia ? null : img.getTagValue(TagW.ShutterPSValue));
            setParam(P_RGB_COLOR, noMedia ? null : img.getTagValue(TagW.ShutterRGBColor));
            setParam(P_PR_ELEMENT, null);
            setParam(P_IMAGE_ELEMENT, noMedia ? null : img);
        } else if (OpEvent.ApplyPR.equals(type)) {
            HashMap<String, Object> p = event.getParams();
            if (p != null) {
                PRSpecialElement pr = Optional.ofNullable(p.get(ActionW.PR_STATE.cmd()))
                    .filter(PRSpecialElement.class::isInstance).map(PRSpecialElement.class::cast).orElse(null);
                setParam(P_SHAPE, pr == null ? null : pr.getTagValue(TagW.ShutterFinalShape));
                setParam(P_PS_VALUE, pr == null ? null : pr.getTagValue(TagW.ShutterPSValue));
                setParam(P_RGB_COLOR, pr == null ? null : pr.getTagValue(TagW.ShutterRGBColor));
                setParam(P_PR_ELEMENT, pr);
                setParam(P_IMAGE_ELEMENT, event.getImage());

                Area shape = (Area) params.get(P_SHAPE);
                if (shape != null) {
                    Rectangle area = (Rectangle) p.get(ActionW.CROP.cmd());
                    if (area != null) {
                        Area trArea = new Area(shape);
                        trArea.transform(AffineTransform.getTranslateInstance(-area.getX(), -area.getY()));
                        setParam(P_SHAPE, trArea);
                    }
                }
            }
        }
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;

        boolean shutter = LangUtil.getNULLtoFalse((Boolean) params.get(P_SHOW));
        Area area = (Area) params.get(P_SHAPE);
        Object pr = params.get(P_PR_ELEMENT);

        if (shutter && area != null) {
            Byte[] color = getShutterColor();
            if (isBlack(color)) {
                result = ShutterDescriptor.create(source, new ROIShape(area), getShutterColor(), null);
            } else {
                result = MergeImgOp.combineTwoImages(source,
                    ImageFiler.getEmptyImage(color, source.getWidth(), source.getHeight()), getAsImage(area, source));
            }
        }

        // Potentially override the shutter in the original dicom
        if (shutter && params.get(P_PS_VALUE) != null && (pr instanceof PRSpecialElement)) {
            DicomMediaIO prReader = ((PRSpecialElement) pr).getMediaReader();
            RenderedImage imgOverlay = null;
            ImageElement image = (ImageElement) params.get(P_IMAGE_ELEMENT);
            boolean overlays = LangUtil.getNULLtoFalse((Boolean) prReader.getTagValue(TagW.HasOverlay));

            if (overlays && image != null && image.getKey() instanceof Integer) {
                int frame = (Integer) image.getKey();
                Integer height = TagD.getTagValue(image, Tag.Rows, Integer.class);
                Integer width = TagD.getTagValue(image, Tag.Columns, Integer.class);
                if (height != null && width != null) {
                    Byte[] color = getShutterColor();

                    Attributes attributes = ((PRSpecialElement) pr).getMediaReader().getDicomObject();
                    Integer shuttOverlayGroup =
                        DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.ShutterOverlayGroup, null);
                    if (shuttOverlayGroup != null) {
                        PlanarImage alpha = PlanarImage.wrapRenderedImage(
                            OverlayUtils.getShutterOverlay(attributes, frame, width, height, shuttOverlayGroup));
                        if (color.length == 1) {
                            int transperency = color[0];
                            imgOverlay = MergeImgOp.combineTwoImages(result, alpha, transperency);
                        } else {
                            imgOverlay = MergeImgOp.combineTwoImages(result,
                                ImageFiler.getEmptyImage(color, width, height), alpha);
                        }
                    }
                }
            }
            result = imgOverlay == null ? result : imgOverlay;
        }

        params.put(Param.OUTPUT_IMG, result);
    }

    private Byte[] getShutterColor() {
        Color color = (Color) params.get(P_RGB_COLOR);
        if (color == null) {
            /*
             * A single gray unsigned value used to replace those parts of the image occluded by the shutter, when
             * rendered on a monochrome display. The units are specified in P-Values, from a minimum of 0000H (black) up
             * to a maximum of FFFFH (white).
             */
            Integer val = (Integer) params.get(P_PS_VALUE);
            return val == null ? new Byte[] { 0 } : new Byte[] { (byte) (val >> 8) };
        } else {
            return new Byte[] { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() };
        }
    }

    private static boolean isBlack(Byte[] color) {
        for (Byte i : color) {
            if (i != 0) {
                return false;
            }
        }
        return true;
    }

    private static PlanarImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        TiledImage ti = new TiledImage(source.getMinX(), source.getMinY(), source.getWidth(), source.getHeight(),
            source.getTileGridXOffset(), source.getTileGridYOffset(), sm, PlanarImage.createColorModel(sm));
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }
}
