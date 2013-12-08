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
package org.weasis.dicom.codec.display;

import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.HashMap;

import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.MergeImgOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.codec.utils.OverlayUtils;

public class OverlayOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayOp.class);

    public static final String OP_NAME = ActionW.IMAGE_OVERLAY.getTitle();

    public static final String P_SHOW = "overlay";
    public static final String P_PR_ELEMENT = "pr.element";
    public static final String P_IMAGE_ELEMENT = "img.element";

    public OverlayOp() {
        setName(OP_NAME);
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        OpEvent type = event.getEventType();
        if (OpEvent.ImageChange.equals(type)) {
            ImageElement img = event.getImage();
            boolean noMedia = img == null;
            setParam(P_IMAGE_ELEMENT, noMedia ? null : img);
        } else if (OpEvent.ApplyPR.equals(type)) {
            HashMap<String, Object> p = event.getParams();
            if (p != null) {
                PRSpecialElement pr = (PRSpecialElement) p.get(ActionW.PR_STATE.cmd());
                setParam(P_IMAGE_ELEMENT, event.getImage());
                setParam(P_PR_ELEMENT, pr);
            }
        }
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;
        Boolean overlay = (Boolean) params.get(P_SHOW);

        if (overlay == null) {
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else if (overlay) {
            RenderedImage imgOverlay = null;

            ImageElement image = (ImageElement) params.get(P_IMAGE_ELEMENT);
            if (image != null) {
                boolean overlays = JMVUtils.getNULLtoFalse(image.getTagValue(TagW.HasOverlay));
                if (!overlays) {
                    Object pr = params.get(P_PR_ELEMENT);
                    if (pr instanceof PRSpecialElement) {
                        overlays =
                            DicomMediaUtils.hasOverlay(((PRSpecialElement) pr).getMediaReader().getDicomObject());
                    }
                }
                if (overlays && image.getMediaReader() instanceof DicomMediaIO) {
                    DicomMediaIO reader = (DicomMediaIO) image.getMediaReader();
                    try {
                        if (image.getKey() instanceof Integer) {
                            int frame = (Integer) image.getKey();
                            Integer height = (Integer) reader.getTagValue(TagW.Rows);
                            Integer width = (Integer) reader.getTagValue(TagW.Columns);
                            if (height != null && width != null) {
                                imgOverlay =
                                    PlanarImage.wrapRenderedImage(OverlayUtils.getOverlays(image, reader, frame, width,
                                        height, params));
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            result = imgOverlay == null ? source : MergeImgOp.combineTwoImages(source, imgOverlay, 255);
        }

        params.put(OUTPUT_IMG, result);
    }

}
