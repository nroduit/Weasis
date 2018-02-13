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
import javax.media.jai.PlanarImage;

import org.weasis.core.api.image.util.LayoutUtil;

import com.sun.media.jai.util.ImageUtil;

public class NotBinaryDescriptor extends OperationDescriptorImpl implements RenderedImageFactory {

    private static final String resources[][] = { { "GlobalName", "NotBinary" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "LocalName", "NotBinary" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Vendor", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Description", "Not for binary image (better than use transform colormodel)" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "DocURL", "" }, //$NON-NLS-1$ //$NON-NLS-2$

        { "Version", "1.0" } //$NON-NLS-1$ //$NON-NLS-2$

    };

    private static final String[] paramNames = {};
    public static final String[] supportedModes = { "rendered" }; //$NON-NLS-1$
    private static final Class[] paramClasses = {};
    private static final Object[] paramDefaults = {};

    public NotBinaryDescriptor() {
        super(resources, supportedModes, 1, paramNames, paramClasses, paramDefaults, null);
    }

    @Override
    public RenderedImage create(ParameterBlock parameterblock, RenderingHints renderHints) {
        if (!validateSources(parameterblock)) {
            return null;
        }

        PlanarImage source = (PlanarImage) parameterblock.getRenderedSource(0);
        if (renderHints == null) {
            renderHints = LayoutUtil.createBinaryRenderedImage();
        }
        ImageLayout imagelayout = LayoutUtil.getImageLayoutHint(renderHints);
        return new NotBinaryOpImage(source, renderHints, imagelayout);
    }

    public boolean validateSources(ParameterBlock parameterblock) {
        RenderedImage img = parameterblock.getRenderedSource(0);
        return (img != null && ImageUtil.isBinary(img.getSampleModel()));
    }
}
