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
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderableImage;
import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.ComponentSampleModelJAI;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.RenderableOp;
import javax.media.jai.RenderedOp;
import javax.media.jai.registry.RenderableRegistryMode;
import javax.media.jai.registry.RenderedRegistryMode;

import org.weasis.core.api.image.util.JAIUtil;

public class RectifyUShortToShortDataDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {

    /**
     * The resource strings that provide the general documentation and specify the parameter list for this operation.
     */
    private static final String[][] resources = { { "GlobalName", "RectifyUShortToShortData" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "LocalName", "RectifyUShortToShortData" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Description", //$NON-NLS-1$
            "Rectify image with unsigned short data to signed short data (Workaround for imageio codecs issue" }, //$NON-NLS-1$

        { "DocURL", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Version", "1.0" } //$NON-NLS-1$ //$NON-NLS-2$

    };

    private static final Class[] paramClasses = {};

    /** The parameter name list for this operation. */
    private static final String[] paramNames = {};

    /** The parameter default value list for this operation. */
    private static final Object[] paramDefaults = {};

    private static final String[] supportedModes = { "rendered", "renderable" }; //$NON-NLS-1$ //$NON-NLS-2$

    /** Constructor. */
    public RectifyUShortToShortDataDescriptor() {
        super(resources, supportedModes, 1, paramNames, paramClasses, paramDefaults, null);
    }

    /**
     * Validates the input source and parameter.
     *
     * <p>
     * In addition to the standard checks performed by the superclass method, this method checks that the source image
     * has an integral data type.
     */
    @Override
    public boolean validateArguments(String modeName, ParameterBlock args, StringBuffer message) {
        if (!super.validateArguments(modeName, args, message)) {
            return false;
        }

        if (!modeName.equalsIgnoreCase("rendered")) { //$NON-NLS-1$
            return true;
        }

        RenderedImage src = args.getRenderedSource(0);

        int dtype = src.getSampleModel().getDataType();

        if (dtype != DataBuffer.TYPE_USHORT) {
            return false;
        }
        return true;
    }

    @Override
    public RenderedImage create(ParameterBlock args, RenderingHints renderHints) {
        // Get ImageLayout from renderHints if any.
        RenderedImage src = args.getRenderedSource(0);
        ImageLayout layout = JAIUtil.getImageLayoutHint(renderHints);
        if (layout == null) {
            layout = new ImageLayout(src);
        }
        // Create a sample model with signed short DataBuffer
        SampleModel model = src.getSampleModel();
        if (model instanceof ComponentSampleModel) {
            final ComponentSampleModel cast = (ComponentSampleModel) model;
            final int w = cast.getWidth();
            final int h = cast.getHeight();
            final int pixelStride = cast.getPixelStride();
            final int scanlineStride = cast.getScanlineStride();
            final int[] bankIndices = cast.getBankIndices();
            final int[] bandOffsets = cast.getBandOffsets();
            if (model instanceof BandedSampleModel) {
                model = new BandedSampleModel(DataBuffer.TYPE_SHORT, w, h, scanlineStride, bankIndices, bandOffsets);
            } else if (model instanceof PixelInterleavedSampleModel) {
                model = new PixelInterleavedSampleModel(DataBuffer.TYPE_SHORT, w, h, pixelStride, scanlineStride,
                    bandOffsets);
            } else if (model instanceof ComponentSampleModelJAI) {
                model = new ComponentSampleModelJAI(DataBuffer.TYPE_SHORT, w, h, pixelStride, scanlineStride,
                    bankIndices, bandOffsets);
            } else {
                model = new ComponentSampleModel(DataBuffer.TYPE_SHORT, w, h, pixelStride, scanlineStride, bankIndices,
                    bandOffsets);
            }
        } else if (model instanceof SinglePixelPackedSampleModel) {
            final SinglePixelPackedSampleModel cast = (SinglePixelPackedSampleModel) model;
            final int scanlineStride = cast.getScanlineStride();
            final int[] bitMasks = cast.getBitMasks();
            model = new SinglePixelPackedSampleModel(DataBuffer.TYPE_SHORT, cast.getWidth(), cast.getHeight(),
                scanlineStride, bitMasks);
        }
        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
        layout.setSampleModel(model);
        layout.setColorModel(new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 16 },
            false, false, Transparency.OPAQUE, DataBuffer.TYPE_SHORT));
        renderHints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
        renderHints.add(hints);
        return new RectifyUShortToShortDataOpImage(args.getRenderedSource(0), renderHints, layout);
    }

    public static RenderedOp create(RenderedImage source0, RenderingHints hints) {
        ParameterBlockJAI pb = new ParameterBlockJAI("RectifyUShortToShortData", RenderedRegistryMode.MODE_NAME); //$NON-NLS-1$

        pb.setSource("source0", source0); //$NON-NLS-1$

        return JAI.create("RectifyUShortToShortData", pb, hints); //$NON-NLS-1$
    }

    public static RenderableOp createRenderable(RenderableImage source0, RenderingHints hints) {
        ParameterBlockJAI pb = new ParameterBlockJAI("RectifyUShortToShortData", RenderableRegistryMode.MODE_NAME); //$NON-NLS-1$

        pb.setSource("source0", source0); //$NON-NLS-1$

        return JAI.createRenderable("RectifyUShortToShortData", pb, hints); //$NON-NLS-1$
    }
}
