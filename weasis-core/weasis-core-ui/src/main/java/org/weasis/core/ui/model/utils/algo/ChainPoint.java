/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.utils.algo;

import java.util.List;

import org.weasis.core.api.gui.util.MathUtil;

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

    public ChainPoint(int x, int y) {
        this.x = x;
        this.y = y;
    }

    // get the length of the segment between the current point and the next one.
    public float getSegLength() {
        return segLength;
    }

    public void setSegLength(float segLength) {
        this.segLength = segLength;
    }

    @Override
    public int compareTo(ChainPoint anotherPoint) {
        return (this.y < anotherPoint.y ? -1
            : (this.y == anotherPoint.y ? (this.x < anotherPoint.x ? -1 : (this.x == anotherPoint.x ? 0 : 1)) : 1));
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ChainPoint other = (ChainPoint) obj;
        if (x != other.x) {
            return false;
        }
        if (y != other.y) {
            return false;
        }
        return true;
    }

    public static double[] regression(List<ChainPoint> list) {
        double meanx = 0.0;
        double meany = 0.0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            meanx += p.x;
            meany += p.y;
        }
        meanx /= list.size();
        meany /= list.size();
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
        double meanx2 = 0;
        double meany2 = 0;
        double meanxy = 0;
        for (int i = 0; i < list.size(); i++) {
            ChainPoint p = list.get(i);
            double xi = p.x;
            double yi = p.y;
            xi -= meanx;
            meanx2 += xi * xi;
            meany2 += yi * yi;
            meanxy += xi * yi;
        }
        meanx2 /= list.size();
        meany2 /= list.size();
        meanxy /= list.size();
        /*
         * Assuming that 'mean(x)==0', then the correlation coefficient can be approximate by:
         *
         * R = mean(xy) / sqrt( mean(x²) * (mean(y²) - mean(y)²) )
         */
        double[] val = new double[3];
        if (meanx2 == 0.0 || MathUtil.isEqualToZero(meanx2)) {
            val[0] = 0.0;
        }
        else {
            val[0] = meanxy / meanx2; // slope
        }
        val[1] = meany - meanx * val[0]; // y0 or b
        val[2] = meanxy / Math.sqrt(meanx2 * (meany2 - meany * meany)); // R
        if (Double.isInfinite(val[2]) || Double.isNaN(val[2])) {
            val[2] = 1;
        }
        return val;
    }
}
