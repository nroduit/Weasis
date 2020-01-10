/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
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
