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
package org.weasis.core.api.image.op;

import java.awt.RenderingHints;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;
import java.util.Arrays;

import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.ROIShape;
import javax.media.jai.RenderedOp;
import javax.media.jai.TiledImage;
import javax.media.jai.registry.RenderedRegistryMode;

import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.LayoutUtil;

import com.sun.media.jai.util.ImageUtil;

public class ShutterDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {
    /**
     * The resource strings that provide the general documentation and specify the parameter list for the "Sample"
     * operation.
     */
    private static final String[][] resources = { { "GlobalName", "Shutter" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "LocalName", "Shutter" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Description", "Apply an shutter to the image" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "DocURL", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Version", "1.0" } }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String[] paramNames = { "roi", "color" }; //$NON-NLS-1$ //$NON-NLS-2$
    private static final Class<?>[] paramClasses = { ROIShape.class, Byte[].class };
    private static final Object[] paramDefaults = { null, null };

    public ShutterDescriptor() {
        super(resources, new String[] { "rendered" }, 1, paramNames, paramClasses, paramDefaults, null); //$NON-NLS-1$
    }

    /**
     * Creates a SampleOpImage with the given ParameterBlock if the SampleOpImage can handle the particular
     * ParameterBlock.
     */
    @Override
    public RenderedImage create(ParameterBlock paramBlock, RenderingHints renderHints) {
        PlanarImage source1 = (PlanarImage) paramBlock.getRenderedSource(0);
        if (source1 == null) {
            return null;
        }
        ROIShape shape = (ROIShape) paramBlock.getObjectParameter(0);
        if (shape == null) {
            return source1;
        }

        TiledImage image;
        if (ImageUtil.isBinary(source1.getSampleModel())) {
            image = new TiledImage(source1.getMinX(), source1.getMinY(), source1.getWidth(), source1.getHeight(),
                source1.getTileGridXOffset(), source1.getTileGridYOffset(), LayoutUtil.createBinarySampelModel(),
                LayoutUtil.createBinaryIndexColorModel());
        } else {
            // rgb cannot be null or have less than one value
            Byte[] rgb = (Byte[]) paramBlock.getObjectParameter(1);
            int nbands = source1.getSampleModel().getNumBands();
            if (rgb.length != nbands) {
                Byte fillVal = rgb[0];
                rgb = new Byte[nbands];
                Arrays.fill(rgb, fillVal);
            }

            image = ImageFiler.getEmptyTiledImage(rgb, source1.getWidth(), source1.getHeight());
        }

        image.set(source1, shape);
        return image;
    }

    /**
     * Checks that all parameters in the ParameterBlock have the correct type before constructing the SampleOpImage
     */
    public boolean validateParameters(ParameterBlock paramBlock) {
        Object arg = paramBlock.getObjectParameter(0);
        if (arg instanceof Byte[] && ((Byte[]) arg).length > 0) {
            return true;
        }
        return false;
    }

    public boolean validateSources(ParameterBlock parameterblock) {
        return parameterblock.getRenderedSource(0) != null;
    }

    public static RenderedOp create(RenderedImage source0, ROIShape roi, Byte[] bandValues, RenderingHints hints) {
        ParameterBlockJAI pb = new ParameterBlockJAI("Shutter", RenderedRegistryMode.MODE_NAME); //$NON-NLS-1$
        pb.setSource("source0", source0); //$NON-NLS-1$
        pb.setParameter("roi", roi); //$NON-NLS-1$
        pb.setParameter("color", bandValues); //$NON-NLS-1$
        return JAI.create("Shutter", pb, hints); //$NON-NLS-1$
    }
}
