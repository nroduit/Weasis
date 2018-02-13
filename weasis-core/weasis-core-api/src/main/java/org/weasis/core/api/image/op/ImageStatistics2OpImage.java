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

    private double[][] results;
    private final Double excludedMin;
    private final Double excludedMax;
    private final Double mean;
    private final Double slope;
    private final Double intercept;
    private boolean isInitialized = false;
    private PixelAccessor srcPA;
    private int srcSampleType;
    private long[] totalPixelCount;
    private double[] totalPixelValue;

    public ImageStatistics2OpImage(RenderedImage source, ROI roi, int xStart, int yStart, int xPeriod, int yPeriod,
        Double mean, Double excludedMin, Double excludedMax, Double slope, Double intercept) {
        super(source, roi, xStart, yStart, xPeriod, yPeriod);

        results = null;
        if (excludedMin == null && excludedMax != null) {
            this.excludedMin = excludedMax;
            this.excludedMax = excludedMax;
        } else if (excludedMax == null && excludedMin != null) {
            this.excludedMin = excludedMin;
            this.excludedMax = excludedMin;
        } else {
            this.excludedMin = excludedMin;
            this.excludedMax = excludedMax;
        }
        if (slope != null || intercept != null) {
            this.slope = slope == null ? 1.0f : slope;
            this.intercept = intercept == null ? 0.0f : intercept;
            this.mean = mean * this.slope + this.intercept;
        } else {
            this.slope = slope;
            this.intercept = intercept;
            this.mean = mean;
        }
    }

    private final boolean tileIntersectsROI(int tileX, int tileY) {
        if (roi == null) { // ROI is entire tile
            return true;
        } else {
            return roi.intersects(tileXToX(tileX), tileYToY(tileY), tileWidth, tileHeight);
        }
    }

    private final int startPosition(int pos, int start, int period) {
        int t = (pos - start) % period;
        if (t == 0) {
            return pos;
        } else {
            return pos + (period - t);
        }
    }

    @Override
    protected String[] getStatisticsNames() {
        return new String[] { "statistics" }; //$NON-NLS-1$
    }

    @Override
    protected Object createStatistics(String name) {
        int numBands = sampleModel.getNumBands();
        Object stats;

        if (name.equalsIgnoreCase("statistics")) { //$NON-NLS-1$
            stats = new double[3][numBands];
        } else {
            stats = java.awt.Image.UndefinedProperty;
        }
        return stats;
    }

    @Override
    protected void accumulateStatistics(String name, Raster source, Object stats) {
        if (!isInitialized) {
            srcPA = new PixelAccessor(getSourceImage(0));
            srcSampleType = srcPA.sampleType == PixelAccessor.TYPE_BIT ? DataBuffer.TYPE_BYTE : srcPA.sampleType;
            results = new double[3][srcPA.numBands];

            for (int i = 0; i < results[0].length; i++) {
                results[0][i] = 0.0;
                results[1][i] = 0.0;
                results[2][i] = 0.0;
            }
            totalPixelCount = new long[srcPA.numBands];
            totalPixelValue = new double[srcPA.numBands];
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
            double[][] ext = (double[][]) stats;
            for (int i = 0; i < srcPA.numBands; i++) {
                if (totalPixelCount[i] < 4) {
                    ext[0][i] = Double.NaN;
                    ext[1][i] = Double.NaN;
                    ext[2][i] = Double.NaN;
                } else {
                    double pix = totalPixelCount[i];
                    double variance = (results[0][i] - (totalPixelValue[i] * totalPixelValue[i] / pix)) / (pix - 1.0);
                    // Standard Deviation
                    ext[0][i] = Math.sqrt(variance);
                    // Skewness
                    ext[1][i] = (pix / ((pix - 1) * (pix - 2))) * (results[1][i] / (variance * ext[0][i]));

                    double c1 = (pix * (pix + 1)) / ((pix - 1) * (pix - 2) * (pix - 3));
                    double c2 = (3 * Math.pow(pix - 1, 2.0)) / ((pix - 2) * (pix - 3));
                    // Kurtosis
                    ext[2][i] = (c1 * results[2][i] / Math.pow(ext[0][i], 4.0d)) - c2;
                }
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
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            byte[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po] & 0xff;
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
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
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            short[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po] & 0xffff;
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
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
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            short[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po];
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
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
        float exMin = noBound ? 0 : excludedMin.floatValue();
        float exMax = noBound ? 0 : excludedMax.floatValue();

        for (int b = 0; b < srcPA.numBands; b++) {
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            int[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    int p = d[po];
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
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
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            float[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    float p = d[po];
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
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
            int outRange = 0;
            double totalValues = 0.0;
            double accum2 = 0.0;
            double accum3 = 0.0;
            double accum4 = 0.0;

            double[] d = data[b];
            int lastLine = uid.bandOffsets[b] + rect.height * lineStride;

            for (int lo = uid.bandOffsets[b]; lo < lastLine; lo += lineInc) {
                int lastPixel = lo + rect.width * pixelStride;

                for (int po = lo; po < lastPixel; po += pixelInc) {
                    double p = d[po];
                    boolean inRange = noBound || (p < exMin || p > exMax);
                    if (inRange) {
                        final double val = slope == null ? p - mean : p * slope + intercept - mean;
                        totalValues += val;
                        accum2 += val * val;
                        accum3 += val * val * val;
                        accum4 += val * val * val * val;
                    } else {
                        outRange++;
                    }
                }
            }
            totalPixelCount[b] -= outRange;
            totalPixelValue[b] += totalValues;
            results[0][b] += accum2;
            results[1][b] += accum3;
            results[2][b] += accum4;
        }
        long nbpix = (long) Math.ceil((double) rect.height / yPeriod) * (int) Math.ceil((double) rect.width / xPeriod);
        for (int b = 0; b < srcPA.numBands; b++) {
            totalPixelCount[b] += nbpix;
        }
    }

}
