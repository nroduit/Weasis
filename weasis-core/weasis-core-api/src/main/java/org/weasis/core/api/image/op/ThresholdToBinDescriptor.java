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

import javax.media.jai.ImageLayout;
import javax.media.jai.OperationDescriptorImpl;

import org.weasis.core.api.image.util.LayoutUtil;

public class ThresholdToBinDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {

    /**
     * The resource strings that provide the general documentation and specify the parameter list for the "Sample"
     * operation.
     */
    private static final String[][] resources = { { "GlobalName", "ThresholdToBin" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "LocalName", "ThresholdToBin" }, //$NON-NLS-1$ //$NON-NLS-2$
        { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Description", "A sample operation that thresholds source pixels" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "DocURL", "http://www.mycompany.com/SampleDescriptor.html" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Version", "1.0" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "arg0Desc", "min" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "arg1Desc", "max" } }; //$NON-NLS-1$ //$NON-NLS-2$

    private static final String[] paramNames = { "min", "max" }; //$NON-NLS-1$ //$NON-NLS-2$
    /**
     * The class types for the parameters of the "Sample" operation. User defined classes can be used here as long as
     * the fully qualified name is used and the classes can be loaded.
     */
    private static final Class[] paramClasses = { java.lang.Double.class, java.lang.Double.class };
    /**
     * The default parameter values for the "Sample" operation when using a ParameterBlockJAI.
     */
    private static final Object[] paramDefaults = { 0.0, 128.0 };
    private static final String supportedModes[] = { "rendered" }; //$NON-NLS-1$

    /** Constructor. */
    public ThresholdToBinDescriptor() {
        super(resources, supportedModes, 1, paramNames, paramClasses, paramDefaults, null);
    }

    /**
     * Creates a SampleOpImage with the given ParameterBlock if the SampleOpImage can handle the particular
     * ParameterBlock.
     */
    @Override
    public RenderedImage create(ParameterBlock parameterblock, RenderingHints renderHints) {
        if (!validateParameters(parameterblock)) {
            return null;
        }
        if (!validateSources(parameterblock)) {
            return null;
        }
        renderHints = LayoutUtil.createBinaryRenderedImage();
        ImageLayout imagelayout = LayoutUtil.getImageLayoutHint(renderHints);
        return new ThresholdToBinOpImage(parameterblock.getRenderedSource(0), renderHints, imagelayout,
            (Double) parameterblock.getObjectParameter(0), (Double) parameterblock.getObjectParameter(1));
    }

    /**
     * Checks that all parameters in the ParameterBlock have the correct type before constructing the SampleOpImage
     */
    public boolean validateParameters(ParameterBlock paramBlock) {
        for (int i = 0; i < 2; i++) {
            Object arg = paramBlock.getObjectParameter(i);
            if (arg == null) {
                return false;
            }
            if (!(arg instanceof Double)) {
                return false;
            }
        }
        return true;
    }

    public boolean validateSources(ParameterBlock parameterblock) {
        return (parameterblock.getRenderedSource(0) != null
            && parameterblock.getRenderedSource(0).getSampleModel().getNumBands() == 1);
    }
}
