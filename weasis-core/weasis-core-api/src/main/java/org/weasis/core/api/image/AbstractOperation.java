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
package org.weasis.core.api.image;

import java.awt.image.RenderedImage;

public abstract class AbstractOperation implements ImageOperationAction {

    protected RenderedImage result;

    public RenderedImage getRenderedImageNode() {
        return result;
    }

    public void clearNode() {
        result = null;
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        Object obj = super.clone();
        result = null;
        return obj;
    }
}
