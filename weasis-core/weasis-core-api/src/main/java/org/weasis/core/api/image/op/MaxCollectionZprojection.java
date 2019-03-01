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

import java.util.List;

import org.weasis.core.api.image.CvUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.opencv.data.PlanarImage;

public class MaxCollectionZprojection {

    private final List<ImageElement> sources;

    public MaxCollectionZprojection(List<ImageElement> sources) {
        if (sources == null) {
            throw new IllegalArgumentException("Sources cannot be null!"); //$NON-NLS-1$
        }
        this.sources = sources; 
    }

    public PlanarImage computeMaxCollectionOpImage() {
        return CvUtil.maxStack(sources);
    }
}
