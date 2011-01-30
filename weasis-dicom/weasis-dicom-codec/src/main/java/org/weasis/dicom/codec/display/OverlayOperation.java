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

import javax.media.jai.PlanarImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOperation;
import org.weasis.core.api.image.combineTwoImagesOperation;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.OverlayUtils;

public class OverlayOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayOperation.class);

    public final static String name = Messages.getString("OverlayOperation.title"); //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean overlay = (Boolean) imageOperation.getActionValue(ActionW.IMAGE_OVERLAY.cmd());
        if (overlay == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (overlay) {
            RenderedImage imgOverlay = null;
            ImageElement image = imageOperation.getImage();
            Integer row = (Integer) image.getTagValue(TagW.OverlayRows);
            if (row != null && row != 0 && image.getMediaReader() instanceof DicomMediaIO) {
                DicomMediaIO reader = (DicomMediaIO) image.getMediaReader();
                try {
                    if (image.getKey() instanceof Integer) {
                        int frame = (Integer) image.getKey();
                        Integer height = (Integer) reader.getTagValue(TagW.Rows);
                        Integer width = (Integer) reader.getTagValue(TagW.Columns);
                        if (height != null && width != null) {
                            imgOverlay =
                                PlanarImage.wrapRenderedImage(OverlayUtils.getOverlays(imageOperation.getImage(),
                                    reader, frame, width, height));
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            result = imgOverlay == null ? source : combineTwoImagesOperation.combineTwoImages(source, imgOverlay, 255);
        } else {
            result = source;
        }
        return result;
    }

}
