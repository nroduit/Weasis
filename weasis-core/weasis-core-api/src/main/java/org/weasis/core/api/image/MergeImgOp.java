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
import org.weasis.core.api.image.util.ImageFiler;

import com.sun.media.jai.util.ImageUtil;

public class MergeImgOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(MergeImgOp.class);

    public static final String OP_NAME = "merge.img"; //$NON-NLS-1$

    /**
     * The second image for merging operation (Required parameter). Note: calling clearIOCache will remove the parameter
     * value.
     *
     * java.awt.image.RenderedImage value.
     */
    public static final String INPUT_IMG2 = "op.input.img.2"; //$NON-NLS-1$

    /**
     * Opacity of the top image (Optional parameter).
     *
     * Integer value. Default value is 255 (highest value => no transparency).
     */
    public static final String P_OPACITY = "opacity"; //$NON-NLS-1$

    public MergeImgOp() {
        setName(OP_NAME);
    }

    public MergeImgOp(MergeImgOp op) {
        super(op);
    }

    @Override
    public MergeImgOp copy() {
        return new MergeImgOp(this);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage source2 = (RenderedImage) params.get(INPUT_IMG2);
        RenderedImage result = source;

        if (source2 != null) {
            Integer transparency = (Integer) params.get(P_OPACITY);
            result = MergeImgOp.combineTwoImages(source, source2, transparency == null ? 255 : transparency);
        }
        params.put(Param.OUTPUT_IMG, result);
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
            sourceUp = CropDescriptor.create(sourceUp, 0.0f, 0.0f, (float) sourceDown.getWidth(),
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

    public static PlanarImage combineTwoImages(RenderedImage sourceDown, RenderedImage sourceUp, PlanarImage alpha1) {
        ParameterBlock pb = new ParameterBlock();
        if (sourceDown.getSampleModel().getNumBands() < sourceUp.getSampleModel().getNumBands()) {
            sourceDown = convertBinaryToColor(sourceDown);
        } else if (sourceUp.getSampleModel().getNumBands() < sourceDown.getSampleModel().getNumBands()) {
            sourceUp = convertBinaryToColor(sourceUp);
        }
        if (sourceDown.getWidth() < sourceUp.getWidth() || sourceDown.getHeight() < sourceUp.getHeight()) {
            sourceUp = CropDescriptor.create(sourceUp, 0.0f, 0.0f, (float) sourceDown.getWidth(),
                (float) sourceDown.getHeight(), null);
        }

        pb.addSource(formatIfBinary(sourceDown));
        pb.addSource(formatIfBinary(sourceUp));
        pb.add(formatIfBinary(alpha1));
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
        return JAI.create("bandMerge", pb2, null); //$NON-NLS-1$
    }

}
