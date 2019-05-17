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

import java.awt.Point;
import java.awt.geom.AffineTransform;

import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.opencv.data.PlanarImage;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 */
public interface MeasurableLayer {

    boolean hasContent();

    MeasurementsAdapter getMeasurementAdapter(Unit displayUnit);

    // Only for statistics:
    AffineTransform getShapeTransform();

    Object getSourceTagValue(TagW tagW);

    String getPixelValueUnit();

    Point getOffset();

    void setOffset(Point p);

    /**
     * Returns the source image for display. All preprocessing operations has been applied to this image.
     *
     * @return the source image for display
     */
    PlanarImage getSourceRenderedImage();

    double pixelToRealValue(Number pixelValue);

    double getPixelMin();

    double getPixelMax();
}
