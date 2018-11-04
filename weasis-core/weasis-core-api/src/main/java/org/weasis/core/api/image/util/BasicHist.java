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

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.MathUtil;

// A simple histogram class
public class BasicHist {

    private static final String[] STATISTICS_LIST =
        { Messages.getString("BasicHist.pix"), Messages.getString("BasicHist.min"), Messages.getString("BasicHist.max"), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
            Messages.getString("BasicHist.mean"), Messages.getString("BasicHist.median"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("BasicHist.thresh"), Messages.getString("BasicHist.std"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("BasicHist.skew"), Messages.getString("BasicHist.kurtosis"), //$NON-NLS-1$ //$NON-NLS-2$
            Messages.getString("BasicHist.entropy") }; //$NON-NLS-1$
    private int[] bins;
    private int numBins;

    private double lo;
    private double hi;
    private double range;

    // The constructor will create an array of a given
    // number of bins. The range of the histogram given
    // by the upper and lower limit values.
    public BasicHist(int numBins, double lo, double hi) {
        this.numBins = numBins;
        bins = new int[numBins];
        this.lo = lo;
        this.hi = hi;
        range = hi - lo;
    }

    // Add an entry to a bin.
    // Include if value is in the range
    public boolean add(double x) {
        if (x >= lo && x <= hi) {
            double val = x - lo;
            // Casting to int will round off to lower
            // integer value.
            int bin = (int) (numBins * (val / range - 0.00000001));

            // Increment the corresponding bin.
            bins[bin]++;
            return true;
        }
        return false;
    }

    public void add(double[] x) {
        for (int i = 0; i < x.length; i++) {
            if (x[i] >= lo && x[i] <= hi) {
                double val = x[i] - lo;
                // Casting to int will round off to lower
                // integer value.
                int bin = (int) (numBins * (val / range));

                // Increment the corresponding bin.
                bins[bin]++;
            }
        }

    }

    /**
     * add
     *
     * @param obj
     *            Object[]
     */
    public void add(Object[] obj) {
        if (obj != null && obj.length > 1 && obj[0] instanceof Number) {
            for (int i = 0; i < obj.length; i++) {
                double val = ((Number) obj[i]).doubleValue();
                if (val >= lo && val <= hi) {
                    val = val - lo;
                    // Casting to int will round off to lower
                    // integer value.
                    int bin = (int) ((numBins - 1) * (val / range));

                    // Increment the corresponding bin.
                    bins[bin]++;
                }
            }
        }
    }

    // Clear the histogram bins.
    public void clear() {
        for (int i = 0; i < numBins; i++) {
            bins[i] = 0;
        }
    }

    public int[] getBins() {
        return bins;
    }

    public Integer[] getIntegerBins() {
        Integer[] vals = new Integer[numBins];
        for (int i = 0; i < numBins; i++) {
            vals[i] = bins[i];
        }
        return vals;
    }

    public double getRange() {
        return range;
    }

    public double getHi() {
        return hi;
    }

    public double getLo() {
        return lo;
    }

    public int[] getCummulative() {
        int[] cml = new int[numBins];
        int sum = 0;
        for (int j = 0; j < numBins; j++) {
            sum += bins[j];
            cml[j] = sum;
        }
        return cml;
    }

    public static double[] getStatistics(int[] bins, double treshRatio, int offset) {
        double[] stat = new double[STATISTICS_LIST.length];

        if (bins != null && bins.length > 1) {
            stat[1] = Double.MAX_VALUE;
            stat[2] = -Double.MAX_VALUE;
            for (int i = 0; i < bins.length; i++) {
                double val = bins[i];
                if (MathUtil.isDifferentFromZero(val) && i < stat[1]) {
                    stat[1] = i;
                }
                if (MathUtil.isDifferentFromZero(val) && i > stat[2]) {
                    stat[2] = i;
                }
                stat[0] += val;
                stat[3] += val * (i + offset);
            }
            stat[1] += offset;
            stat[2] += offset;
            stat[3] /= stat[0];
            stat[4] = medianBin(bins, (int) stat[0] / 2) + offset;
            stat[5] = treshRatio;

            double m2 = 0.0;
            for (int i = 0; i < bins.length; i++) {
                double factor = bins[i];
                double val = (i + offset) - stat[3];
                m2 += factor * Math.pow(val, 2); // variance
                stat[7] += factor * Math.pow(val, 3); // skewness
                stat[8] += factor * Math.pow(val, 4); // kurtosis
            }
            double variance = m2 / (stat[0] - 1); // variance
            stat[6] = Math.sqrt(variance);
            if (bins.length > 3 && variance > 10E-20) {
                double val = ((stat[0] - 1) * (stat[0] - 2) * stat[6] * variance);
                if (val == 0.0) {
                    stat[7] = 0.0;
                    stat[8] = 0.0;
                } else {
                    stat[7] = (stat[0] * stat[7]) / val;
                    stat[8] = (stat[0] * (stat[0] + 1) * stat[8] - 3 * m2 * m2 * (stat[0] - 1))
                        / ((stat[0] - 1) * (stat[0] - 2) * (stat[0] - 3) * variance * variance);
                }
            } else {
                stat[7] = 0.0;
                stat[8] = 0.0;
            }
            stat[9] = getEntropy(bins, stat[0]);
        }
        return stat;
    }

    public static double getEntropy(int[] data, double nbPixels) {
        double entropy = 0.0;
        if (data == null || data.length < 1) {
            return 0.0;
        } else {
            double log2 = Math.log(2.0);
            for (int b = 0; b < data.length; b++) {
                double p = data[b] / nbPixels;
                if (MathUtil.isDifferentFromZero(p)) {
                    entropy -= p * (Math.log(p) / log2);
                }
            }
        }
        return entropy;
    }

    public static double medianBin(final int[] bin, int halfEntries) {
        if (bin == null || bin.length < 1) {
            return 0.0;
        } else {
            int sumBinEntries = 0;
            int sum;
            for (int i = 0; i < bin.length; i++) {
                sum = sumBinEntries + bin[i];
                // Check if bin crosses halfTotal point
                if (sum >= halfEntries) {
                    // Scale linearly across the bin
                    int dif = halfEntries - sumBinEntries;
                    double frac = 0.0;
                    if (bin[i] > 0) {
                        frac = ((double) dif) / (double) bin[i];
                    }
                    return i + frac;
                }
                sumBinEntries = sum;
            }
        }
        return 0.0;
    }

}
