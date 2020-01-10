/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.util.WindLevelParameters;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.LangUtil;
import org.weasis.opencv.data.PlanarImage;

public class WindowOp extends AbstractOp {

    public static final String OP_NAME = Messages.getString("WindowLevelOperation.title"); //$NON-NLS-1$

    public static final String P_IMAGE_ELEMENT = "img.element"; //$NON-NLS-1$
    public static final String P_FILL_OUTSIDE_LUT = "fill.outside.lut"; //$NON-NLS-1$
    public static final String P_APPLY_WL_COLOR = "weasis.color.wl.apply"; //$NON-NLS-1$
    public static final String P_INVERSE_LEVEL = "weasis.level.inverse"; //$NON-NLS-1$

    public WindowOp() {
        setName(OP_NAME);
    }

    public WindowOp(WindowOp op) {
        super(op);
    }

    @Override
    public WindowOp copy() {
        return new WindowOp(this);
    }

    @Override
    public void handleImageOpEvent(ImageOpEvent event) {
        OpEvent type = event.getEventType();
        if (OpEvent.ImageChange.equals(type)) {
            setParam(P_IMAGE_ELEMENT, event.getImage());
        } else if (OpEvent.ResetDisplay.equals(type) || OpEvent.SeriesChange.equals(type)) {
            ImageElement img = event.getImage();
            setParam(P_IMAGE_ELEMENT, img);
            if (img != null) {
                if (!img.isImageAvailable()) {
                    // Ensure to load image before calling the default preset that requires pixel min and max
                    img.getImage();
                }

                boolean pixelPadding = LangUtil.getNULLtoTrue((Boolean) getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
                setParam(ActionW.WINDOW.cmd(), img.getDefaultWindow(pixelPadding));
                setParam(ActionW.LEVEL.cmd(), img.getDefaultLevel(pixelPadding));
                setParam(ActionW.LEVEL_MIN.cmd(), img.getMinValue(null, pixelPadding));
                setParam(ActionW.LEVEL_MAX.cmd(), img.getMaxValue(null, pixelPadding));
            }
        }
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        ImageElement imageElement = (ImageElement) params.get(P_IMAGE_ELEMENT);

        if (imageElement != null) {
            result = imageElement.getRenderedImage(source, params);
        }

        params.put(Param.OUTPUT_IMG, result);
    }

    public WindLevelParameters getWindLevelParameters() {
        ImageElement imageElement = (ImageElement) params.get(P_IMAGE_ELEMENT);
        if (imageElement != null) {
            return new WindLevelParameters(imageElement, params);
        }
        return null;
    }

}
