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
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Optional;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.weasis.dicom.codec.utils.OverlayUtils;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class OverlayOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayOp.class);

    public static final String OP_NAME = ActionW.IMAGE_OVERLAY.getTitle();

    public static final String P_SHOW = "overlay"; //$NON-NLS-1$
    public static final String P_PR_ELEMENT = "pr.element"; //$NON-NLS-1$
    public static final String P_IMAGE_ELEMENT = "img.element"; //$NON-NLS-1$

    public OverlayOp() {
        setName(OP_NAME);
    }

    public OverlayOp(OverlayOp op) {
        super(op);
    }

    @Override
    public OverlayOp copy() {
        return new OverlayOp(this);
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        OpEvent type = event.getEventType();
        if (OpEvent.ImageChange.equals(type) || OpEvent.ResetDisplay.equals(type)) {
            setParam(P_PR_ELEMENT, null);
            setParam(P_IMAGE_ELEMENT, event.getImage());
        } else if (OpEvent.ApplyPR.equals(type)) {
            HashMap<String, Object> p = event.getParams();
            if (p != null) {
                setParam(P_PR_ELEMENT, Optional.ofNullable(p.get(ActionW.PR_STATE.cmd()))
                    .filter(PRSpecialElement.class::isInstance).orElse(null));
                setParam(P_IMAGE_ELEMENT, event.getImage());
            }
        }
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        Boolean overlay = (Boolean) params.get(P_SHOW);

        if (overlay != null && overlay) {
            RenderedImage imgOverlay = null;
            ImageElement image = (ImageElement) params.get(P_IMAGE_ELEMENT);

            if (image != null) {
                boolean overlays = LangUtil.getNULLtoFalse((Boolean) image.getTagValue(TagW.HasOverlay));

                if (overlays && image.getMediaReader() instanceof DicomMediaIO) {
                    DicomMediaIO reader = (DicomMediaIO) image.getMediaReader();
                    try {
                        if (image.getKey() instanceof Integer) {
                            int frame = (Integer) image.getKey();
                            Integer height = TagD.getTagValue(image, Tag.Rows, Integer.class);
                            Integer width = TagD.getTagValue(image, Tag.Columns, Integer.class);
                            if (height != null && width != null) {
                                imgOverlay = OverlayUtils.getBinaryOverlays(image, reader.getDicomObject(), frame,
                                    width, height, params);
                            }
                        }
                    } catch (IOException e) {
                        LOGGER.error("Applying overlays", e); //$NON-NLS-1$
                    }
                }
            }
            result = imgOverlay == null ? source : ImageProcessor.overlay(source.toMat(), imgOverlay, Color.WHITE);
        }
        params.put(Param.OUTPUT_IMG, result);
    }
}
