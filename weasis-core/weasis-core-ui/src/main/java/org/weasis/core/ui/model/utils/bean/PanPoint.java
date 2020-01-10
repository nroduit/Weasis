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

import java.awt.geom.Point2D;
import java.util.Objects;

public class PanPoint extends Point2D {
    public enum State {
        MOVE, CENTER, DRAGSTART, DRAGGING, DRAGEND;
    }

    private double x;
    private double y;
    private final State state;
    private boolean highlightedPosition;

    public PanPoint(State state) {
        this(state, 0.0, 0.0);
    }

    public PanPoint(State state, double x, double y) {
        this.x = x;
        this.y = y;
        this.state = Objects.requireNonNull(state);
        this.highlightedPosition = false;
    }

    public State getState() {
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

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (highlightedPosition ? 1231 : 1237);
        result = prime * result + state.hashCode();
        long temp;
        temp = java.lang.Double.doubleToLongBits(x);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        temp = java.lang.Double.doubleToLongBits(y);
        result = prime * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (this == obj) {
            return true;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        PanPoint other = (PanPoint) obj;
        if (highlightedPosition != other.highlightedPosition) {
            return false;
        }
        if (state != other.state) {
            return false;
        }
        if (java.lang.Double.doubleToLongBits(x) != java.lang.Double.doubleToLongBits(other.x)) {
            return false;
        }
        if (java.lang.Double.doubleToLongBits(y) != java.lang.Double.doubleToLongBits(other.y)) {
            return false;
        }
        return true;
    }

    public boolean isHighlightedPosition() {
        return highlightedPosition;
    }

    public void setHighlightedPosition(boolean highlightedPosition) {
        this.highlightedPosition = highlightedPosition;
    }

}
