/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Gabriela Bauermann (gabriela@animati.com.br) - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.media.data.TagW;

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
    
    /**
     * Returns the source image for display. All preprocessing operations has been applied to this image.
     * 
     * @return the source image for display
     */
    RenderedImage getSourceRenderedImage();
    
}
