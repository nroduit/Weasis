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
package org.weasis.core.api.image.util;

import java.awt.Point;
import java.util.List;

public class Statistics {

    private int[] x;
    private int[] y;

    private double mx = 0; // moyenne de valeurs x, moment d'ordre 1
    private double my = 0; // moyenne de valeurs y, moment d'ordre 1
    private double u20 = 0; // moment central bivarié de degré 2 pour x et 0 pour y
    private double u02 = 0; // moment central bivarié de degré 0 pour x et 2 pour y
    private double u11 = 0; // moment central bivarié de degré 1 pour x et 1 pour y

    /**
     * This constructor creates a new <tt>Statistics</tt> object, and initializes the data array with an array of
     * Objects that must be convertable to numeric values.
     */
    public Statistics(List<Point> arrayList) {
        x = new int[arrayList.size()];
        y = new int[arrayList.size()];

        for (int i = 0; i < arrayList.size(); i++) {
            Point p = arrayList.get(i);
            x[i] = p.x;
            y[i] = p.y;
        }
        mx = mean(x);
        my = mean(y);

        if (x.length < 2) {
            return; // variance est égale à 0
        }
        for (int i = 0; i < x.length; i++) {
            // central bivariate moments
            u11 += (x[i] - mx) * (y[i] - my);
            u20 += (x[i] - mx) * (x[i] - mx);
            u02 += (y[i] - my) * (y[i] - my);
        }
    }

    public static double corrCoef(double[] xVal, double[] yVal, double xyStDev, double xMean, double yMean) {
        double r = 0.0D;
        for (int i = 0; i < xVal.length; i++) {
            r += ((xVal[i] - xMean) * (yVal[i] - yMean)) / (xyStDev);
        }
        r /= xVal.length;
        return r;
    }

    public static double corrCoef(double[] xVal, double[] yVal) {
        double r = 0.0D;
        double mX = mean(xVal);
        double mY = mean(yVal);
        double sdX = stDev(xVal, mX);
        double sdY = stDev(yVal, mY);
        for (int i = 0; i < xVal.length; i++) {
            r += ((xVal[i] - mX) * (yVal[i] - mY)) / (sdX * sdY);
        }
        r /= (xVal.length - 1);
        return r;
    }

    public static double stDev(int[] data) {
        double sHat;
        if (data == null || data.length == 0) {
            sHat = Double.NaN;
        } else {
            double mu = mean(data);
            sHat = 0.0D;
            for (int i = 0; i < data.length; i++) {
                sHat += (data[i] - mu) * (data[i] - mu);
            }
            sHat = Math.sqrt(sHat / (data.length - 1.0));
        }
        return sHat;
    }

    public static double stDev(double[] data) {
        double sHat;
        if (data == null || data.length == 0) {
            sHat = Double.NaN;
        } else {
            double mu = mean(data);
            sHat = 0.0D;
            for (int i = 0; i < data.length; i++) {
                sHat += (data[i] - mu) * (data[i] - mu);
            }
            sHat = Math.sqrt(sHat / (data.length - 1.0));
        }
        return sHat;
    }

    public static double stDev(double[] data, double mu) {
        double sHat;
        if (data == null || data.length == 0) {
            sHat = Double.NaN;
        } else {
            sHat = 0.0D;
            for (int i = 0; i < data.length; i++) {
                sHat += (data[i] - mu) * (data[i] - mu);
            }
            sHat = Math.sqrt(sHat / (data.length - 1.0));
        }
        return sHat;
    }

    /**
     * Compute standard deviation in one pass (less accurate for small or large values) Ref.
     * http://www.strchr.com/standard_deviation_in_one_pass
     *
     * @param data
     * @return
     */
    public static double stDevOnePass(double[] data) {
        double sHat;
        if (data == null || data.length == 0) {
            sHat = Double.NaN;
        } else {

            double meanSum = data[0];
            sHat = 0.0;
            for (int i = 1; i < data.length; ++i) {
                double stepSum = data[i] - meanSum;
                double stepMean = ((i - 1) * stepSum) / i;
                meanSum += stepMean;
                sHat += stepMean * stepSum;
            }
            sHat = Math.sqrt(sHat / (data.length - 1.0));
        }
        return sHat;

    }

    public static double stDev(int[] data, double mu) {
        double sHat;
        if (data == null || data.length == 0) {
            sHat = Double.NaN;
        } else {
            sHat = 0.0D;
            for (int i = 0; i < data.length; i++) {
                sHat += (data[i] - mu) * (data[i] - mu);
            }
            sHat = Math.sqrt(sHat / (data.length - 1.0));

        }
        return sHat;
    }

    public static double mean(double[] data) {
        double mu;
        if (data == null || data.length == 0) {
            mu = Double.NaN;
        } else {
            mu = 0.0D;
            for (int i = 0; i < data.length; i++) {
                mu += data[i];
            }
            mu /= data.length;
        }
        return mu;
    }

    public static double mean(int[] data) {
        if (data == null || data.length < 1) {
            return 0;
        } else {
            double sum = 0;
            for (int i = 0; i < data.length; i++) {
                sum += data[i];
            }
            return sum / data.length;
        }
    }

    public static double[] normalizeData(double[] data) {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        double[] out = new double[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] < min) {
                min = data[i];
            }
            if (data[i] > max) {
                max = data[i];
            }
        }
        double range = max - min;
        for (int i = 0; i < out.length; i++) {
            out[i] = (data[i] - min) / range;
        }
        return out;
    }

    /**
     * This method calculates the median of a data set.
     *
     * @param data
     *            The input data set
     *
     * @return the median of <tt>data</tt>.
     */
    public static double median(double[] data) {

        double median;
        if (data == null || data.length < 1) {
            return Double.NaN;
        } else {
            // Get local copy of data
            double[] out = new double[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            // Sort data
            java.util.Arrays.sort(out);

            // Get median
            if (out.length % 2 == 0) {
                median = (out[out.length / 2 - 1] + out[out.length / 2]) / 2.0;
            } else {
                median = out[out.length / 2];

            }
            return median;
        }
    }

    public static double median(int[] data) {
        double median;
        if (data == null || data.length < 1) {
            return Double.NaN;
        } else {
            // Get local copy of data
            int[] out = new int[data.length];
            for (int i = 0; i < data.length; i++) {
                out[i] = data[i];
            }
            java.util.Arrays.sort(out);

            // Get median
            if (out.length % 2 == 0) {
                median = (out[out.length / 2 - 1] + out[out.length / 2]) / 2.0;
            } else {
                median = out[out.length / 2];
            }
            return median;
        }
    }

    /**
     * This method calculates the skewness of a data set. Skewness is the third central moment divided by the third
     * power of the standard deviation.
     *
     * @param data
     *            The input data set
     *
     * @return the skewness of <tt>data</tt>.
     */
    public static double skewness(double[] data) {

        if (data == null || data.length < 2) {
            return Double.NaN;
        } else {
            double m3 = moment(data, 3);
            double sm2 = Math.sqrt(moment(data, 2));
            return m3 / Math.pow(sm2, 3);
        }
    }

    /**
     * This method calculates the kurtosis of a data set. Kurtosis is the fourth central moment divided by the fourth
     * power of the standard deviation.
     *
     * @param data
     *            The input data set
     *
     * @return the kurtosis of <tt>data</tt>.
     */
    public static double kurtosis(double[] data) {

        if (data == null || data.length < 2) {
            return Double.NaN;
        } else {
            double m4 = moment(data, 4);
            double sm2 = Math.sqrt(moment(data, 2));
            return m4 / Math.pow(sm2, 4);
        }
    }

    private static double moment(double[] x, int order) {
        if (x == null || order == 1) {
            return Double.NaN;
        } else {
            double mu = mean(x);
            double sum = 0;
            for (int i = 0; i < x.length; i++) {
                sum += Math.pow(x[i] - mu, order);
            }
            return sum / (x.length - 1);
        }
    }

    public double orientationInRadian() {
        // utilise arctan2 pour lever l'ambiguité 180 - degrés
        // Converts rectangular coordinates (x, y) to polar (r, theta).
        // This method computes the phase theta by computing an arc tangent of y/x in the range of -pi to pi.
        return 0.5d * Math.atan2(2d * u11, u20 - u02);
    }

    public double eccentricity() {
        double sum;
        sum = (u20 + u02 + Math.sqrt(((u20 - u02) * (u20 - u02)) + (4d * u11 * u11)))
            / (u20 + u02 - Math.sqrt(((u20 - u02) * (u20 - u02)) + (4d * u11 * u11)));
        return sum;
    }

    public double getBarycenterX() {
        return mx;
    }

    public double getBarycentery() {
        return my;
    }

    public void dispose() {
        x = null;
        y = null;
    }

    public static final double[] averageSmooth(double[] img, int rad) {
        int h = img.length;
        double[] result = new double[h];

        for (int i = 0; i < h; i++) {
            int by = i - rad;
            int ey = i + rad;
            if (by < 0) {
                by = 0;
            }
            if (ey >= h) {
                ey = h - 1;

            }
            double tmp = 0;
            int k = 0;
            for (int y = by; y <= ey; y++, k++) {
                tmp += img[y];
            }
            if(k == 0) {
                k = 1;
            }
            result[i] = tmp / k;
        }
        return result;
    }

    /**
     * Apply least squares to raw data to determine the coefficients an n-order equation: y = an*X^n+... + a1*X^1 +
     * a0*X^0.
     *
     * @param y
     *            the x coordinates of data points
     * @param x
     *            the y coordinates of data points
     * @param norder
     * @return the coefficients for the solved equation in the form {a0, a1,...,an}
     */
    public static double[] regression(double[] x, double[] y, int norder) {
        double[][] a = new double[norder + 1][norder + 1];
        double[] b = new double[norder + 1];
        double[] term = new double[norder + 1];
        double ysquare = 0;
        // step through each raw data entries
        for (int i = 0; i < y.length; i++) {
            // sum the y values
            b[0] += y[i];
            ysquare += y[i] * y[i];
            // sum the x power values
            double xpower = 1;
            for (int j = 0; j < norder + 1; j++) {
                term[j] = xpower;
                a[0][j] += xpower;
                xpower = xpower * x[i];
            }
            // now set up the rest of rows in the matrix - multiplying each row
            // by each term
            for (int j = 1; j < norder + 1; j++) {
                b[j] += y[i] * term[j];
                for (int k = 0; k < b.length; k++) {
                    a[j][k] += term[j] * term[k];
                }
            }
        }
        // solve for the coefficients
        double[] coef = gauss(a, b);
        // calculate the r-squared statistic
        double ss = 0;
        double yaverage = b[0] / y.length;
        for (int i = 0; i < norder + 1; i++) {
            double xaverage = a[0][i] / y.length;
            ss += coef[i] * (b[i] - (y.length * xaverage * yaverage));
        }
        double rsquared = ss / (ysquare - (y.length * yaverage * yaverage));
        // solve the simultaneous equations via gauss
        int size = coef.length + 1;
        double[] out = new double[size];
        for (int i = 0; i < coef.length; i++) {
            out[i] = coef[i];
        }
        // set rsquared
        out[coef.length] = rsquared;
        return out;
    }

    /**
     * IIRC, standard gaussian technique for solving simultaneous eq. of the form: |A| = |B| * |C| where we know the
     * values of |A| and |B|, and we are solving for the coefficients in |C|
     *
     * @param ax
     * @param bx
     * @return
     */
    private static double[] gauss(double[][] ax, double[] bx) {
        double[][] a = new double[ax.length][ax[0].length];
        double[] b = new double[bx.length];
        double pivot;
        double mult;
        double top;
        int n = b.length;
        double[] coef = new double[n];
        // copy over the array values - inplace solution changes values
        for (int i = 0; i < ax.length; i++) {
            for (int j = 0; j < ax[i].length; j++) {
                a[i][j] = ax[i][j];
            }
            b[i] = bx[i];
        }
        for (int j = 0; j < (n - 1); j++) {
            pivot = a[j][j];
            for (int i = j + 1; i < n; i++) {
                mult = a[i][j] / pivot;
                for (int k = j + 1; k < n; k++) {
                    a[i][k] = a[i][k] - mult * a[j][k];
                }
                b[i] = b[i] - mult * b[j];
            }
        }
        coef[n - 1] = b[n - 1] / a[n - 1][n - 1];
        for (int i = n - 2; i >= 0; i--) {
            top = b[i];
            for (int k = i + 1; k < n; k++) {
                top = top - a[i][k] * coef[k];
            }
            coef[i] = top / a[i][i];
        }
        return coef;
    }
}
