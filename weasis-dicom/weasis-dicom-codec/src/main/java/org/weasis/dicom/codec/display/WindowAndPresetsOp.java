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

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.DicomImageElement;

public class WindowAndPresetsOp extends WindowOp {

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

                boolean pixelPadding = JMVUtils.getNULLtoTrue(getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
                PresetWindowLevel preset = null;
                if (img instanceof DicomImageElement) {
                    preset = ((DicomImageElement) img).getDefaultPreset(pixelPadding);
                }

                boolean p = preset != null;
                setParam(ActionW.PRESET.cmd(), preset);
                setParam(ActionW.DEFAULT_PRESET.cmd(), true);

                setParam(ActionW.WINDOW.cmd(), p ? preset.getWindow() : img.getDefaultWindow(pixelPadding));
                setParam(ActionW.LEVEL.cmd(), p ? preset.getLevel() : img.getDefaultLevel(pixelPadding));
                setParam(ActionW.LEVEL_MIN.cmd(), p ? preset.getMinBox() : img.getMinValue(pixelPadding));
                setParam(ActionW.LEVEL_MAX.cmd(), p ? preset.getMaxBox() : img.getMaxValue(pixelPadding));
                setParam(ActionW.LUT_SHAPE.cmd(), p ? preset.getLutShape() : img.getDefaultShape(pixelPadding));
                // node.setParam(ActionW.IMAGE_PIX_PADDING.cmd(), pixelPadding);
                // node.setParam(ActionW.INVERSELUT.cmd(), false);
                // node.setParam(WindowOp.P_FILL_OUTSIDE_LUT, false);
            }
        }
    }

}
