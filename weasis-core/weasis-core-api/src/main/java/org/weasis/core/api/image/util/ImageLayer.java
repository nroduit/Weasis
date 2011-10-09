/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse  License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;

import javax.media.jai.iterator.RandomIter;

import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.media.data.ImageElement;

public interface ImageLayer<E extends ImageElement> {

    RandomIter getReadIterator();

    E getSourceImage();

    /**
     * Returns the source image for display. All preprocessing operations has been applied to this image.
     * 
     * @return the source image for display
     */
    RenderedImage getSourceRenderedImage();

    RenderedImage getDisplayImage();

    void setImage(E image, OperationsManager preprocessing);

    AffineTransform getTransform();

    void setTransform(AffineTransform transform);

    void updateImageOperation(String operation);

}
