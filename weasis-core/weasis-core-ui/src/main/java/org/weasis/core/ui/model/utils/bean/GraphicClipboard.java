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
package org.weasis.core.ui.model.utils.bean;

import java.util.List;

import org.weasis.core.ui.model.graphic.Graphic;

public class GraphicClipboard {
    private List<Graphic> graphics;

    public synchronized List<Graphic> getGraphics() {
        return graphics;
    }

    public synchronized void setGraphics(List<Graphic> graphics) {
        this.graphics = graphics;
    }

    public boolean hasGraphics() {
        return graphics != null && !graphics.isEmpty();
    }

}
