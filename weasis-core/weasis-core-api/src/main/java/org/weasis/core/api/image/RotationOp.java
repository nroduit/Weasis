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

import javax.media.jai.InterpolationBilinear;
import javax.media.jai.JAI;
import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageToolkit;

public class RotationOp extends AbstractOp {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotationOp.class);

    public static final String OP_NAME = Messages.getString("RotationOperation.title"); //$NON-NLS-1$

    /**
     * Set the clockwise angle value in degree (Required parameter).
     *
     * Integer value.
     */
    public static final String P_ROTATE = "rotate"; //$NON-NLS-1$

    public RotationOp() {
        setName(OP_NAME);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(INPUT_IMG);
        RenderedImage result = source;
        Integer rotationAngle = (Integer) params.get(P_ROTATE);

        if (rotationAngle == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", OP_NAME); //$NON-NLS-1$
        } else if (rotationAngle != 0 && rotationAngle != 360) {
            // optimize rotation by right angles
            TransposeType rotOp = null;
            if (rotationAngle == 90) {
                rotOp = TransposeDescriptor.ROTATE_90;
            } else if (rotationAngle == 180) {
                rotOp = TransposeDescriptor.ROTATE_180;
            } else if (rotationAngle == 270) {
                rotOp = TransposeDescriptor.ROTATE_270;
            }
            if (rotOp != null) {
                // use Transpose operation
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(rotOp);
                result = JAI.create("transpose", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
                // Handle non square images. Translation is necessary because the transpose operator keeps the same
                // origin (top left not the center of the image)
                float diffw = source.getWidth() / 2.0f - result.getWidth() / 2.0f;
                float diffh = source.getHeight() / 2.0f - result.getHeight() / 2.0f;
                if (MathUtil.isDifferentToZero(diffw) || MathUtil.isDifferentToZero(diffh)) {
                    pb = new ParameterBlock();
                    pb.addSource(result);
                    pb.add(diffw);
                    pb.add(diffh);
                    result = JAI.create("translate", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
                }

            } else {
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(source.getWidth() / 2.0f);
                pb.add(source.getHeight() / 2.0f);
                pb.add((float) (rotationAngle * Math.PI / 180.0));
                pb.add(new InterpolationBilinear());
                result = JAI.create("rotate", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$

                // // Untile this rotate node so that when compute the next node,
                // // no extra memory and time are used in PlanarImage.getExtendedData().
                // ImageLayout il = new ImageLayout();
                // il.setTileWidth(result.getWidth());
                // il.setTileHeight(result.getHeight());
                // il.setTileGridXOffset(result.getMinX());
                // il.setTileGridYOffset(result.getMinY());
                // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, il);
                //
                // result = JAI.create("rotate", pb, hints);
            }
        }

        params.put(OUTPUT_IMG, result);
    }

}
