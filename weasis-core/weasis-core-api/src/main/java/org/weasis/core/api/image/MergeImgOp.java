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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

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
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage source2 = (PlanarImage) params.get(INPUT_IMG2);
        PlanarImage result = source;

        if (source2 != null) {
            Integer transparency = (Integer) params.get(P_OPACITY);
            result = ImageProcessor.combineTwoImages(source.toMat(), source2.toMat(), transparency == null ? 255 : transparency);
        }
        params.put(Param.OUTPUT_IMG, result);
    }

}
