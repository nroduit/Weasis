/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils.imp;

import java.awt.geom.Point2D;
import java.util.Objects;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.ui.model.graphic.DragGraphic;
import org.weasis.core.ui.model.utils.Draggable;
import org.weasis.core.ui.util.MouseEventDouble;

public class DragLabelSequence implements Draggable {
    private final Point2D lastPoint;
    private final DragGraphic graphic;

    public DragLabelSequence(DragGraphic graphic) {
        this.graphic = Objects.requireNonNull(graphic);
        this.lastPoint = new Point2D.Double();
    }

    @Override
    public void startDrag(MouseEventDouble evt) {
        lastPoint.setLocation(evt.getImageX(), evt.getImageY());
    }

    @Override
    public void drag(MouseEventDouble evt) {
        double deltaX = evt.getImageX() - lastPoint.getX();
        double deltaY = evt.getImageY() - lastPoint.getY();

        if (MathUtil.isDifferentFromZero(deltaX) || MathUtil.isDifferentFromZero(deltaY)) {
            lastPoint.setLocation(evt.getImageX(), evt.getImageY());
            graphic.moveLabel(deltaX, deltaY);
        }
    }

    @Override
    public Boolean completeDrag(MouseEventDouble mouseevent) {
        return Boolean.TRUE;
    }

}