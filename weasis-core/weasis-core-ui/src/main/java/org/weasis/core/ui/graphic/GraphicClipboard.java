/*******************************************************************************
 * Copyright (c) 2011 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic;

import java.util.List;

public class GraphicClipboard {
    private List<Graphic> graphics;

    public synchronized List<Graphic> getGraphics() {
        return graphics;
    }

    public synchronized void setGraphics(List<Graphic> graphics) {
        this.graphics = graphics;
    }

}
