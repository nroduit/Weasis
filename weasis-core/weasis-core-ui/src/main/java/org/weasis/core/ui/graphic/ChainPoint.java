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

import java.util.ArrayList;

/**
 * The Class ChainPoint.
 * 
 * @author Nicolas Roduit
 */
public class ChainPoint implements Comparable<ChainPoint> {

    // Fields
    public final int x;
    public final int y;
    private float segLength;

    public float getSegLength() {
        return segLength;
    }

    // get the length of the segment between the current point and the next one.
    public void setSegLength(float segLength) {
        this.segLength = segLength;
    }

    // Constructors
    public ChainPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public int compareTo(ChainPoint anotherPoint) {
        return (this.y < anotherPoint.y ? -1 : (this.y == anotherPoint.y ? (this.x < anotherPoint.x ? -1
            : (this.x == anotherPoint.x ? 0 : 1)) : 1));
    }

    public boolean equals(ChainPoint point) {
        return (this.y == point.y) && (this.x == point.x);
    }

    public boolean equals(int x, int y) {
        return (this.y == y) && (this.x == x);
    }

    public static double[] regression(ArrayList<ChainPoint> list) {
        double mean_x = 0.0;
        double mean_y = 0.0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            mean_x += p.x;
            mean_y += p.y;
        }
        mean_x /= list.size();
        mean_y /= list.size();
        /*
         * We have to solve two equations with two unknows:
         * 
         * 1) mean(y) = b + m*mean(x) 2) mean(xy) = b*mean(x) + m*mean(x²)
         * 
         * Those formulas lead to a quadratic equation. However, the formulas become very simples if we set 'mean(x)=0'.
         * We can achieve this result by computing instead of (2):
         * 
         * 2b) mean(dx y) = m*mean(dx²)
         * 
         * where dx=x-mean(x). In this case mean(dx)==0.
         */
        double mean_x2 = 0;
        double mean_y2 = 0;
        double mean_xy = 0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            double xi = p.x;
            double yi = p.y;
            xi -= mean_x;
            mean_x2 += xi * xi;
            mean_y2 += yi * yi;
            mean_xy += xi * yi;
        }
        mean_x2 /= list.size();
        mean_y2 /= list.size();
        mean_xy /= list.size();
        /*
         * Assuming that 'mean(x)==0', then the correlation coefficient can be approximate by:
         * 
         * R = mean(xy) / sqrt( mean(x²) * (mean(y²) - mean(y)²) )
         */
        double[] val = new double[3];
        val[0] = mean_xy / mean_x2; // slope
        if (Double.isNaN(val[0])) {
            val[0] = 0.0;
        }
        val[1] = mean_y - mean_x * val[0]; // y0 or b
        val[2] = mean_xy / Math.sqrt(mean_x2 * (mean_y2 - mean_y * mean_y)); // R
        if (Double.isInfinite(val[2]) || Double.isNaN(val[2])) {
            val[2] = 1;
        }
        return val;
    }
}
