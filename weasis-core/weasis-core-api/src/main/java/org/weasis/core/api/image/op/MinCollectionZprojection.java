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
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.task.TaskInterruptionException;
import org.weasis.core.api.gui.task.TaskMonitor;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.StringUtil;

public class MinCollectionZprojection {

    private final List<ImageElement> sources;
    private final TaskMonitor taskMonitor;

    public MinCollectionZprojection(List<ImageElement> sources, final TaskMonitor taskMonitor) {
        if (sources == null) {
            throw new IllegalArgumentException("Sources cannot be null!"); //$NON-NLS-1$
        }
        this.sources = sources;
        this.taskMonitor = taskMonitor;
    }

    private void incrementProgressBar(final int progress) {
        if (taskMonitor == null) {
            return;
        }
        if (taskMonitor.isCanceled()) {
            throw new TaskInterruptionException("Operation from " + this.getClass().getName() + " has been canceled"); //$NON-NLS-1$ //$NON-NLS-2$
        }
        if (taskMonitor.isShowProgression()) {
            GuiExecutor.instance().execute(new Runnable() {

                @Override
                public void run() {
                    taskMonitor.setProgress(progress);
                    StringBuilder buf = new StringBuilder(Messages.getString("MinCollectionZprojection.operation")); //$NON-NLS-1$
                    buf.append(StringUtil.COLON_AND_SPACE);
                    buf.append(progress);
                    buf.append("/"); //$NON-NLS-1$
                    buf.append(taskMonitor.getMaximum());
                    taskMonitor.setNote(buf.toString());
                }
            });
        }
    }

    public PlanarImage computeMinCollectionOpImage() {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            PlanarImage img = firstImg.getImage(null, false);
            if (img == null) {
                return null;
            }

            Rectangle region = img.getBounds();
            WritableRaster raster = LayoutUtil.createCompatibleRaster(img, region);

            SampleModel[] sampleModels = { img.getSampleModel() };
            int tagID = RasterAccessor.findCompatibleTag(sampleModels, raster.getSampleModel());

            RasterFormatTag dstTag = new RasterFormatTag(raster.getSampleModel(), tagID);
            RasterAccessor dst = new RasterAccessor(raster, region, dstTag, null);

            switch (dst.getDataType()) {
                case DataBuffer.TYPE_BYTE:
                    computeRectByte(dst, region, tagID);
                    break;
                case DataBuffer.TYPE_USHORT:
                    computeRectUShort(dst, region, tagID);
                    break;
                case DataBuffer.TYPE_SHORT:
                    computeRectShort(dst, region, tagID);
                    break;
                case DataBuffer.TYPE_INT:
                    computeRectInt(dst, region, tagID);
                    break;
                case DataBuffer.TYPE_FLOAT:
                    computeRectFloat(dst, region, tagID);
                    break;
                case DataBuffer.TYPE_DOUBLE:
                    computeRectDouble(dst, region, tagID);
                    break;
            }

            dst.copyDataToRaster();
            BufferedImage buffer = new BufferedImage(img.getColorModel(), raster, false, null);
            return PlanarImage.wrapRenderedImage(buffer);
        }
        return null;
    }

    private void computeRectByte(RasterAccessor dst, Rectangle region, int tagID) {

        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        byte[][] dstData = dst.getByteDataArrays();

        final byte maxVal = (byte) 255;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            byte[][] srcData = src.getByteDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                byte[] d = dstData[b];
                byte[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        if ((s[srcPixelOffset] & 0xff) < (d[dstPixelOffset] & 0xff)) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }

    private void computeRectUShort(RasterAccessor dst, Rectangle region, int tagID) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        short[][] dstData = dst.getShortDataArrays();

        final short maxVal = (short) 65535;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }
        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            short[][] srcData = src.getShortDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                short[] d = dstData[b];
                short[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        // Get unsigned value to compare
                        if ((s[srcPixelOffset] & 0xffff) < (d[dstPixelOffset] & 0xffff)) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }

                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }

    private void computeRectShort(RasterAccessor dst, Rectangle region, int tagID) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        short[][] dstData = dst.getShortDataArrays();

        final short maxVal = Short.MAX_VALUE;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            short[][] srcData = src.getShortDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                short[] d = dstData[b];
                short[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        if (s[srcPixelOffset] < d[dstPixelOffset]) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }

    private void computeRectInt(RasterAccessor dst, Rectangle region, int tagID) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        int[][] dstData = dst.getIntDataArrays();

        final int maxVal = Integer.MAX_VALUE;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            int[][] srcData = src.getIntDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                int[] d = dstData[b];
                int[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        if (s[srcPixelOffset] < d[dstPixelOffset]) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }

    private void computeRectFloat(RasterAccessor dst, Rectangle region, int tagID) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        float[][] dstData = dst.getFloatDataArrays();

        final float maxVal = Float.MAX_VALUE;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            float[][] srcData = src.getFloatDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                float[] d = dstData[b];
                float[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        if (s[srcPixelOffset] < d[dstPixelOffset]) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }

    private void computeRectDouble(RasterAccessor dst, Rectangle region, int tagID) {
        int dstWidth = dst.getWidth();
        int dstHeight = dst.getHeight();
        int dstBands = dst.getNumBands();

        int dstLineStride = dst.getScanlineStride();
        int dstPixelStride = dst.getPixelStride();
        int[] dstBandOffsets = dst.getBandOffsets();
        double[][] dstData = dst.getDoubleDataArrays();

        final double maxVal = Double.MAX_VALUE;
        for (int i = 0; i < dstData.length; i++) {
            for (int j = 0; j < dstData[i].length; j++) {
                dstData[i][j] = maxVal;
            }
        }

        for (int i = 0; i < sources.size(); i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            double[][] srcData = src.getDoubleDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int dstLineOffset = dstBandOffsets[b];
                int srcLineOffset = srcBandOffsets[b];

                double[] d = dstData[b];
                double[] s = srcData[b];

                for (int h = 0; h < dstHeight; h++) {
                    int dstPixelOffset = dstLineOffset;
                    int srcPixelOffset = srcLineOffset;

                    dstLineOffset += dstLineStride;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        if (s[srcPixelOffset] < d[dstPixelOffset]) {
                            d[dstPixelOffset] = s[srcPixelOffset];
                        }
                        dstPixelOffset += dstPixelStride;
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar(i);
        }
    }
}
