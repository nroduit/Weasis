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
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.CompositeDescriptor;
import javax.media.jai.operator.CropDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AbstractOperation;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.Messages;
import org.weasis.dicom.codec.OverlayUtils;

import com.sun.media.jai.util.ImageUtil;

public class OverlayOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(OverlayOperation.class);

    public final static String name = Messages.getString("OverlayOperation.title"); //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Boolean overlay = (Boolean) imageOperation.getActionValue(ActionW.IMAGE_OVERLAY);
        if (overlay == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (overlay) {
            RenderedImage imgOverlay = null;
            ImageElement image = imageOperation.getImage();
            Integer row = (Integer) image.getTagValue(TagElement.OverlayRows);
            if (row != null && row != 0 && image.getMediaReader() instanceof DicomMediaIO) {
                DicomMediaIO reader = (DicomMediaIO) image.getMediaReader();
                try {
                    if (image.getKey() instanceof Integer) {
                        int frame = (Integer) image.getKey();
                        int height = (Integer) reader.getTagValue(TagElement.Rows);
                        int width = (Integer) reader.getTagValue(TagElement.Columns);
                        imgOverlay =
                            PlanarImage.wrapRenderedImage(OverlayUtils.getOverlays(imageOperation.getImage(), reader,
                                frame, width, height));
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            result = imgOverlay == null ? source : combineTwoImages(source, imgOverlay, 255);
        } else {
            result = source;
        }
        return result;
    }

    public static PlanarImage combineTwoImages(RenderedImage sourceDown, RenderedImage sourceUp, int transparency) {
        Byte[] bandValues = new Byte[sourceDown.getSampleModel().getNumBands()];
        for (int i = 0; i < bandValues.length; i++) {
            bandValues[i] = (byte) transparency;
        }
        PlanarImage alpha1 = ImageFiler.getEmptyImage(bandValues, sourceDown.getWidth(), sourceDown.getHeight());
        ParameterBlock pb = new ParameterBlock();
        if (sourceDown.getSampleModel().getNumBands() < sourceUp.getSampleModel().getNumBands()) {
            sourceDown = convertBinaryToColor(sourceDown);
        } else if (sourceUp.getSampleModel().getNumBands() < sourceDown.getSampleModel().getNumBands()) {
            sourceUp = convertBinaryToColor(sourceUp);
        }
        if (sourceDown.getWidth() < sourceUp.getWidth() || sourceDown.getHeight() < sourceUp.getHeight()) {
            sourceUp =
                CropDescriptor.create(sourceUp, 0.0f, 0.0f, (float) sourceDown.getWidth(), (float) sourceDown
                    .getHeight(), null);
        }

        pb.addSource(formatIfBinary(sourceDown));
        pb.addSource(alpha1);
        pb.add(formatIfBinary(sourceUp));
        pb.add(null);
        pb.add(false);
        pb.add(CompositeDescriptor.NO_DESTINATION_ALPHA);

        return JAI.create("composite", pb, null); //$NON-NLS-1$
    }

    public static RenderedImage formatIfBinary(RenderedImage src) {
        if (src != null && ImageUtil.isBinary(src.getSampleModel())) {
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(src);
            return JAI.create("formatbinary", pb, null); //$NON-NLS-1$
        }
        return src;
    }

    public static PlanarImage convertBinaryToColor(RenderedImage src) {
        RenderedImage img = formatIfBinary(src);
        ParameterBlockJAI pb2 = new ParameterBlockJAI("bandMerge"); //$NON-NLS-1$
        pb2.addSource(img);
        pb2.addSource(img);
        pb2.addSource(img);
        return JAI.create("bandMerge", pb2); //$NON-NLS-1$
    }

}
