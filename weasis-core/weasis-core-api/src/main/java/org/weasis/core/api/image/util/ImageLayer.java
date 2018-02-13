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
package org.weasis.core.api.image.util;

import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;

import javax.media.jai.iterator.RandomIter;

import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.media.data.ImageElement;

public interface ImageLayer<E extends ImageElement> extends MeasurableLayer {

    RandomIter getReadIterator();

    E getSourceImage();

    RenderedImage getDisplayImage();

    void setImage(E image, OpManager preprocessing);

    AffineTransform getTransform();

    void setTransform(AffineTransform transform);

    SimpleOpManager getDisplayOpManager();

    void updateDisplayOperations();

    boolean isEnableDispOperations();

    void setEnableDispOperations(boolean enabled);

    // Duplicate of Layer interface
    void setVisible(Boolean visible);

    Boolean getVisible();
}
