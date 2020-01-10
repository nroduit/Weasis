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

import java.awt.geom.Rectangle2D;
import java.util.Arrays;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.weasis.core.api.Messages;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageProcessor;

public class AffineTransformOp extends AbstractOp {

    public static final String OP_NAME = Messages.getString("AffineTransformOp.affine_op"); //$NON-NLS-1$

    public static final String[] INTERPOLATIONS =
        { Messages.getString("ZoomOperation.nearest"), Messages.getString("ZoomOperation.bilinear"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("ZoomOperation.bicubic"), Messages.getString("ZoomOperation.lanczos") }; //$NON-NLS-1$ //$NON-NLS-2$

    public static final double[] identityMatrix = new double[] { 1.0, 0.0, 0.0, 0.0, 1.0, 0.0 };
    /**
     * Set a affine transformation (Required parameter).
     *
     * Double array (length of 6).
     */
    public static final String P_AFFINE_MATRIX = "affine.matrix"; //$NON-NLS-1$

    /**
     * Set the interpolation type (Optional parameter).
     *
     * Integer value. Default value is bilinear interpolation. See javax.media.jai.Interpolation.
     */
    public static final String P_INTERPOLATION = "interpolation"; //$NON-NLS-1$

    public static final String P_DST_BOUNDS = "dest.bounds"; //$NON-NLS-1$

    public AffineTransformOp() {
        setName(OP_NAME);
    }

    public AffineTransformOp(AffineTransformOp op) {
        super(op);
    }

    @Override
    public AffineTransformOp copy() {
        return new AffineTransformOp(this);
    }

    @Override
    public void process() throws Exception {
        PlanarImage source = (PlanarImage) params.get(Param.INPUT_IMG);
        PlanarImage result = source;
        double[] matrix = (double[]) params.get(P_AFFINE_MATRIX);
        Rectangle2D bound = (Rectangle2D) params.get(P_DST_BOUNDS);

        if (bound != null && matrix != null && !Arrays.equals(identityMatrix, matrix)) {
            if (bound.getWidth() > 0 && bound.getHeight() > 0) {
                Mat mat = new Mat(2, 3, CvType.CV_64FC1);
                mat.put(0, 0, matrix);
                Integer interpolation = (Integer) params.get(P_INTERPOLATION);
                if (interpolation != null && interpolation == 3) {
                    interpolation = 4;
                }
                result = ImageProcessor.warpAffine(source.toMat(), mat, new Size(bound.getWidth(), bound.getHeight()),
                    interpolation);
            } else {
                result = null;
            }
        }

        params.put(Param.OUTPUT_IMG, result);
    }

}
