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
package org.weasis.core.ui.model.utils.imp;

import java.awt.event.MouseEvent;

import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.util.MouseEventDouble;

public class SelectedDragSequence extends DefaultDragSequence {
    public SelectedDragSequence(DragGraphic graphic) {
        super(graphic);
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseEvent) {
        if (mouseEvent != null && mouseEvent.getID() == MouseEvent.MOUSE_RELEASED) {
            graphic.fireRemoveAndRepaintAction();
        }
        return Boolean.TRUE;
    }
}
