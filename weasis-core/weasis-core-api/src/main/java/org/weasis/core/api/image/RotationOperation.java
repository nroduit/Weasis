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
import org.weasis.core.api.gui.ImageOperation;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.util.ImageToolkit;

public class RotationOperation extends AbstractOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(RotationOperation.class);

    public static final String name = Messages.getString("RotationOperation.title"); //$NON-NLS-1$
    public static final double epsilon = 1e-5;

    public RenderedImage getRenderedImage(RenderedImage source, ImageOperation imageOperation) {
        Integer rotationAngle = (Integer) imageOperation.getActionValue(ActionW.ROTATION.cmd());
        if (rotationAngle == null) {
            result = source;
            LOGGER.warn("Cannot apply \"{}\" because a parameter is null", name); //$NON-NLS-1$
        } else if (rotationAngle == 0 || rotationAngle == 360) {
            result = source;
        } else {
            // optimize rotation by right angles
            TransposeType rotOp = null;

            if (Math.abs(rotationAngle - 90) < epsilon) {
                // 90 degree
                rotOp = TransposeDescriptor.ROTATE_90;
            } else if (Math.abs(rotationAngle - 180) < epsilon) {
                // 180 degree
                rotOp = TransposeDescriptor.ROTATE_180;
            } else if (Math.abs(rotationAngle - 270) < epsilon) {
                // 270 degree
                rotOp = TransposeDescriptor.ROTATE_270;
            }
            if (rotOp != null) {
                // use Transpose operation
                ParameterBlock pb = new ParameterBlock();
                pb.addSource(source);
                pb.add(rotOp);
                result = JAI.create("transpose", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
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
        return result;
    }

    public String getOperationName() {
        return name;
    }
}
