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
import java.util.Optional;

import javax.media.jai.InterpolationBilinear;
import javax.media.jai.JAI;
import javax.media.jai.operator.TransposeDescriptor;
import javax.media.jai.operator.TransposeType;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageToolkit;

public class RotationOp extends AbstractOp {

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

    public RotationOp(RotationOp op) {
        super(op);
    }

    @Override
    public RotationOp copy() {
        return new RotationOp(this);
    }

    @Override
    public void process() throws Exception {
        RenderedImage source = (RenderedImage) params.get(Param.INPUT_IMG);
        RenderedImage result = source;
        Integer rotationAngle = Optional.ofNullable((Integer) params.get(P_ROTATE)).orElse(0);
        rotationAngle = rotationAngle % 360;

        if (rotationAngle != 0) {
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
                if (MathUtil.isDifferentFromZero(diffw) || MathUtil.isDifferentFromZero(diffh)) {
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
        params.put(Param.OUTPUT_IMG, result);
    }

}
