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
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.PlanarImage;

import org.weasis.core.api.image.util.ImageFiler;

import com.sun.media.jai.util.ImageUtil;

/**
 * The Class FormatBinaryDescriptor.
 *
 * @author Nicolas Roduit
 */
public class FormatBinaryDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {

    /**
     * The resource strings that provide the general documentation and specify the parameter list for the "Sample"
     * operation.
     */
    private static final String[][] resources = { { "GlobalName", "FormatBinary" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "LocalName", "FormatBinary" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Description", "format bilevel to be displayed correctly" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "DocURL", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Version", "1.0" } }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String supportedModes[] = { "rendered" }; //$NON-NLS-1$

    /** Constructor. */
    public FormatBinaryDescriptor() {
        super(resources, supportedModes, 1, null, null, null, null);
    }

    /**
     * Creates a SampleOpImage with the given ParameterBlock if the SampleOpImage can handle the particular
     * ParameterBlock.
     */
    @Override
    public RenderedImage create(ParameterBlock paramBlock, RenderingHints renderHints) {
        if (!validateSources(paramBlock)) {
            return null;
        }
        RenderedImage imgSource = paramBlock.getRenderedSource(0);
        ColorModel cm = new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false,
            false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        byte[] table_data = new byte[] { (byte) 0x00, (byte) 0xff };
        if (imgSource.getColorModel() instanceof IndexColorModel) {
            IndexColorModel icm = (IndexColorModel) imgSource.getColorModel();
            int size = icm.getMapSize();
            table_data = new byte[size];
            icm.getReds(table_data);
        }
        LookupTableJAI lut = new LookupTableJAI(table_data);
        ImageLayout layout = new ImageLayout();
        layout.setTileWidth(ImageFiler.TILESIZE);
        layout.setTileHeight(ImageFiler.TILESIZE);
        layout.setColorModel(cm);
        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
        hints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(imgSource);
        pb.add(lut);
        PlanarImage dst = JAI.create("lookup", pb, hints); //$NON-NLS-1$
        return dst;
    }

    public boolean validateSources(ParameterBlock parameterblock) {
        RenderedImage img = parameterblock.getRenderedSource(0);
        return (img != null && ImageUtil.isBinary(img.getSampleModel()));
    }
}
