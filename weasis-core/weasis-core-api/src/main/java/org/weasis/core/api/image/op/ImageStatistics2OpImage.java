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
package org.weasis.core.api.image.op;

import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.util.LinkedList;
import java.util.ListIterator;

import javax.media.jai.PixelAccessor;
import javax.media.jai.ROI;
import javax.media.jai.StatisticsOpImage;
import javax.media.jai.UnpackedImageData;

public class ImageStatistics2OpImage extends StatisticsOpImage {

    private double[][] stats;
    private final Double excludedMin;
    private final Double excludedMax;
    private final Double slope;
    private final Double intercept;
    private boolean isInitialized = false;
    private PixelAccessor srcPA;
    private int srcSampleType;

    private boolean tileIntersectsROI(int tileX, int tileY) {
        if (roi == null) {
            return true;
        } else {
            return roi.intersects(tileXToX(tileX), tileYToY(tileY), tileWidth, tileHeight);
        }
    }

    /**
     * Constructs an <code>ExtremaOpImage</code>.
     * 
     * @param source
     *            The source image.
     */
    public ImageStatistics2OpImage(RenderedImage source, ROI roi, int xStart, int yStart, int xPeriod, int yPeriod,
        Double excludedMin, Double excludedMax, Double slope, Double intercept) {
        super(source, roi, xStart, yStart, xPeriod, yPeriod);

        stats = null;
        this.excludedMin = excludedMin;
        this.excludedMax = excludedMax;
        this.slope = slope;
        this.intercept = intercept;
    }

    /** Returns one of the available statistics as a property. */
    @Override
    public Object getProperty(String name) {
        if (stats == null) {
            // Statistics have not been accumulated: call superclass
            // method to do so.
            return super.getProperty(name);
        } else if (name.equalsIgnoreCase("statistics")) { //$NON-NLS-1$
            return stats.clone();
        }
        return java.awt.Image.UndefinedProperty;
    }

    @Override
    protected String[] getStatisticsNames() {
        return new String[] { "statistics" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    }

    @Override
    protected Object createStatistics(String name) {
        int numBands = sampleModel.getNumBands();
        Object statistics = null;

        if (name.equalsIgnoreCase("statistics")) { //$NON-NLS-1$
            statistics = new double[3][numBands];
        } else {
            statistics = java.awt.Image.UndefinedProperty;
        }
        return statistics;
    }

    private final int startPosition(int pos, int start, int period) {
        int t = (pos - start) % period;
        return t == 0 ? pos : pos + (period - t);
    }

    @Override
    protected void accumulateStatistics(String name, Raster source, Object statistics) {
        if (!isInitialized) {
            srcPA = new PixelAccessor(getSourceImage(0));
            srcSampleType = srcPA.sampleType == PixelAccessor.TYPE_BIT ? DataBuffer.TYPE_BYTE : srcPA.sampleType;
            isInitialized = true;
        }

        Rectangle srcBounds = getSourceImage(0).getBounds().intersection(source.getBounds());

        LinkedList rectList;
        if (roi == null) { // ROI is the whole Raster
            rectList = new LinkedList();
            rectList.addLast(srcBounds);
        } else {
            rectList = roi.getAsRectangleList(srcBounds.x, srcBounds.y, srcBounds.width, srcBounds.height);
            if (rectList == null) {
                return; // ROI does not intersect with Raster boundary.
            }
        }
        ListIterator iterator = rectList.listIterator(0);

        while (iterator.hasNext()) {
            Rectangle rect = srcBounds.intersection((Rectangle) iterator.next());
            int tx = rect.x;
            int ty = rect.y;

            // Find the actual ROI based on start and period.
            rect.x = startPosition(tx, xStart, xPeriod);
            rect.y = startPosition(ty, yStart, yPeriod);
            rect.width = tx + rect.width - rect.x;
            rect.height = ty + rect.height - rect.y;

            if (rect.isEmpty()) {
                continue; // no pixel to count in this rectangle
            }

            initializeState(source);

            UnpackedImageData uid = srcPA.getPixels(source, rect, srcSampleType, false);
            switch (uid.type) {
                case DataBuffer.TYPE_BYTE:
                    accumulateStatisticsByte(uid);
                    break;
                case DataBuffer.TYPE_USHORT:
                    accumulateStatisticsUShort(uid);
                    break;
                case DataBuffer.TYPE_SHORT:
                    accumulateStatisticsShort(uid);
                    break;
                case DataBuffer.TYPE_INT:
                    accumulateStatisticsInt(uid);
                    break;
                case DataBuffer.TYPE_FLOAT:
                    accumulateStatisticsFloat(uid);
                    break;
                case DataBuffer.TYPE_DOUBLE:
                    accumulateStatisticsDouble(uid);
                    break;
            }
        }

        if (name.equalsIgnoreCase("statistics")) { //$NON-NLS-1$
            double[][] ext = (double[][]) statistics;
            for (int i = 0; i < srcPA.numBands; i++) {
                ext[0][i] = stats[0][i];
                ext[1][i] = stats[1][i];
                ext[2][i] = stats[2][i];
            }
        }
    }

    private void accumulateStatisticsByte(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        byte[][] data = uid.getByteData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        int exMin = noBound ? 0 : excludedMin.intValue();
        int exMax = noBound ? 0 : excludedMax.intValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            int min = (int) stats[0][b]; // minimum
            int max = (int) stats[1][b]; // maximum

            byte[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po] & 0xff;
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }

    }

    private void accumulateStatisticsUShort(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        short[][] data = uid.getShortData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        int exMin = noBound ? 0 : excludedMin.intValue();
        int exMax = noBound ? 0 : excludedMax.intValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            int min = (int) stats[0][b]; // minimum
            int max = (int) stats[1][b]; // maximum

            short[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po] & 0xffff;
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }

    }

    private void accumulateStatisticsShort(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        short[][] data = uid.getShortData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        int exMin = noBound ? 0 : excludedMin.intValue();
        int exMax = noBound ? 0 : excludedMax.intValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            int min = (int) stats[0][b]; // minimum
            int max = (int) stats[1][b]; // maximum

            short[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po];
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }
    }

    private void accumulateStatisticsInt(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        int[][] data = uid.getIntData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        int exMin = noBound ? 0 : excludedMin.intValue();
        int exMax = noBound ? 0 : excludedMax.intValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            int min = (int) stats[0][b]; // minimum
            int max = (int) stats[1][b]; // maximum

            int[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po];
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }

    }

    private void accumulateStatisticsFloat(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        float[][] data = uid.getFloatData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        float exMin = noBound ? 0 : excludedMin.floatValue();
        float exMax = noBound ? 0 : excludedMax.floatValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            float min = (float) stats[0][b]; // minimum
            float max = (float) stats[1][b]; // maximum

            float[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    float p = d[po];
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }

    }

    private void accumulateStatisticsDouble(UnpackedImageData uid) {
        Rectangle rect = uid.rect;
        double[][] data = uid.getDoubleData();
        int lineStride = uid.lineStride;
        int pixelStride = uid.pixelStride;

        int lineInc = lineStride * yPeriod;
        int pixelInc = pixelStride * xPeriod;

        boolean noBound = excludedMin == null || excludedMax == null;
        double exMin = noBound ? 0 : excludedMin;
        double exMax = noBound ? 0 : excludedMax;

        for (int b = 0; b < srcPA.numBands; b++) {
            double min = stats[0][b]; // minimum
            double max = stats[1][b]; // maximum

            double[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    double p = d[po];
                    if (p < min && (noBound || (p < exMin || p > exMax))) {
                        min = p;
                    }
                    if (p > max && (noBound || (p < exMin || p > exMax))) {
                        max = p;
                    }
                }
            }
            stats[0][b] = min;
            stats[1][b] = max;
        }

    }

    protected void initializeState(Raster source) {
        if (stats == null) {
            int numBands = sampleModel.getNumBands();
            stats = new double[3][numBands];

            for (int i = 0; i < numBands; i++) {
                stats[0][i] = Double.MAX_VALUE;
                stats[1][i] = -Double.MAX_VALUE;
                stats[2][i] = 0.0;
            }
        }
    }

}
