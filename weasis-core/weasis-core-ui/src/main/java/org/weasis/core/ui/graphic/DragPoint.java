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
package org.weasis.core.ui.graphic;

import java.awt.geom.Point2D;

public class DragPoint extends Point2D {
    public enum STATE {
        None, Started, Dragged, Ended
    }

    private double x;
    private double y;
    private final STATE state;

    public DragPoint(STATE state) {
        this(state, 0.0, 0.0);
    }

    public DragPoint(STATE state, double x, double y) {
        this.x = x;
        this.y = y;
        this.state = state;
    }

    public STATE getState() {
        return state;
    }

    @Override
    public double getX() {
        return x;
    }

    @Override
    public double getY() {
        return y;
    }

    @Override
    public void setLocation(double x, double y) {
        this.x = x;
        this.y = y;
    }
}
