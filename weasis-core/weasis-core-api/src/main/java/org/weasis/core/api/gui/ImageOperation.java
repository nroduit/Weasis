/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.gui;

import java.awt.image.RenderedImage;

import org.weasis.core.api.media.data.ImageElement;

public interface ImageOperation {

    Object getActionValue(String action);

    ImageElement getImage();

    // Return the source image for the first operation in the list
    RenderedImage getSourceImage();
}
