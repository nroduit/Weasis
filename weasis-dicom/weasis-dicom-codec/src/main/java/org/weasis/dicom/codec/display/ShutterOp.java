/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.display;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.RenderedImage;
import java.util.HashMap;
import java.util.Optional;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.OverlayUtils;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

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
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;

        boolean shutter = LangUtil.getNULLtoFalse((Boolean) params.get(P_SHOW));
        Area area = (Area) params.get(P_SHAPE);
        Object pr = params.get(P_PR_ELEMENT);

        if (shutter && area != null) {
            result = ImageProcessor.applyShutter(source.toMat(), area, getShutterColor());
        }

        // Potentially override the shutter in the original dicom
        if (shutter && params.get(P_PS_VALUE) != null && (pr instanceof PRSpecialElement)) {
            DicomMediaIO prReader = ((PRSpecialElement) pr).getMediaReader();
            ImageCV imgOverlay = null;
            ImageElement image = (ImageElement) params.get(P_IMAGE_ELEMENT);
            boolean overlays = LangUtil.getNULLtoFalse((Boolean) prReader.getTagValue(TagW.HasOverlay));

            if (overlays && image != null && image.getKey() instanceof Integer) {
                int frame = (Integer) image.getKey();
                Integer height = TagD.getTagValue(image, Tag.Rows, Integer.class);
                Integer width = TagD.getTagValue(image, Tag.Columns, Integer.class);
                if (height != null && width != null) {
                    Attributes attributes = ((PRSpecialElement) pr).getMediaReader().getDicomObject();
                    Integer shuttOverlayGroup =
                        DicomMediaUtils.getIntegerFromDicomElement(attributes, Tag.ShutterOverlayGroup, null);
                    if (shuttOverlayGroup != null) {
                        RenderedImage overlayImg =
                            OverlayUtils.getShutterOverlay(attributes, frame, width, height, shuttOverlayGroup);
                        imgOverlay = ImageProcessor.applyShutter(result.toMat(), overlayImg, getShutterColor());
                    }
                }
            }
            result = imgOverlay == null ? result : imgOverlay;
        }

        params.put(Param.OUTPUT_IMG, result);
    }

    private Color getShutterColor() {
        Color color = (Color) params.get(P_RGB_COLOR);
        if (color == null) {
            /*
             * A single gray unsigned value used to replace those parts of the image occluded by the shutter, when
             * rendered on a monochrome display. The units are specified in P-Values, from a minimum of 0000H (black) up
             * to a maximum of FFFFH (white).
             */
            Integer val = (Integer) params.get(P_PS_VALUE);
            return val == null ? Color.BLACK : new Color(val >> 8, val >> 8,  val >> 8);
        } else {
            return color;
        }
    }

}
