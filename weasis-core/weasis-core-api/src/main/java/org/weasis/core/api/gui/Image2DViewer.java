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
package org.weasis.core.api.gui;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.MediaSeries;

public interface Image2DViewer extends ImageOperation {

    MediaSeries getSeries();

    int getFrameIndex();

    void drawLayers(Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform);

    ViewModel getViewModel();

    ImageLayer getImageLayer();

    AffineTransform getAffineTransform();

}
