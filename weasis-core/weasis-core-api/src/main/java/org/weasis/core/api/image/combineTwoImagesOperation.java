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
package org.weasis.core.api.image;

import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;

import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.operator.CompositeDescriptor;
import javax.media.jai.operator.CropDescriptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.image.util.ImageFiler;

import com.sun.media.jai.util.ImageUtil;

/**
 * This operation should
 * 
 * @version $Rev$ $Date$
 */
public class combineTwoImagesOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(combineTwoImagesOperation.class);

    public static final String name = "CombineImage"; //$NON-NLS-1$

    public String getOperationName() {
        return name;
    }

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        RenderedImage source2 = (RenderedImage) imageOperation.getActionValue(name);
        if (source2 == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else {
            result = combineTwoImagesOperation.combineTwoImages(source, source2, 255);
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
                CropDescriptor.create(sourceUp, 0.0f, 0.0f, (float) sourceDown.getWidth(),
                    (float) sourceDown.getHeight(), null);
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
