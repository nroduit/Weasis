package org.weasis.core.api.image.op;

import java.awt.Component;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.List;

import javax.media.jai.PlanarImage;
import javax.media.jai.RasterAccessor;
import javax.media.jai.RasterFormatTag;
import javax.swing.JProgressBar;

import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.util.LayoutUtil;
import org.weasis.core.api.media.data.ImageElement;

public class MeanCollectionZprojection {

    private final List<ImageElement> sources;
    private final Component view;
    private final JProgressBar progressBar;

    public MeanCollectionZprojection(List<ImageElement> sources, Component view, JProgressBar progressBar) {
        if (sources == null) {
            throw new IllegalArgumentException("Sources cannot be null!"); //$NON-NLS-1$
        }
        this.sources = sources;
        this.view = view;
        this.progressBar = progressBar;
    }

    private void incrementProgressBar() {
        if (progressBar == null) {
            return;
        }
        GuiExecutor.instance().execute(new Runnable() {

            @Override
            public void run() {
                if (progressBar != null) {
                    progressBar.setValue(progressBar.getValue() + 1);
                }
                if (view != null) {
                    view.repaint();
                }
            }
        });
    }

    public PlanarImage computeMeanCollectionOpImage() {
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

        int[][] accu = new int[dstBands][dstWidth * dstHeight];

        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            byte[][] srcData = src.getByteDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                byte[] s = srcData[b];
                int[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += (s[srcPixelOffset] & 0xff);
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            byte[] d = dstData[b];
            int[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = (byte) (a[h * dstWidth + w] / (double) numbSrc + 0.5);
                    dstPixelOffset += dstPixelStride;
                }
            }
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

        float[][] accu = new float[dstBands][dstWidth * dstHeight];
        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            short[][] srcData = src.getShortDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                short[] s = srcData[b];
                float[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += (s[srcPixelOffset] & 0xffff);
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            short[] d = dstData[b];
            float[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = (short) (a[h * dstWidth + w] / numbSrc + 0.5F);
                    dstPixelOffset += dstPixelStride;
                }
            }
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

        float[][] accu = new float[dstBands][dstWidth * dstHeight];
        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            short[][] srcData = src.getShortDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                short[] s = srcData[b];
                float[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += s[srcPixelOffset];
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            short[] d = dstData[b];
            float[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = (short) (a[h * dstWidth + w] / numbSrc + 0.5F);
                    dstPixelOffset += dstPixelStride;
                }
            }
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

        double[][] accu = new double[dstBands][dstWidth * dstHeight];
        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            int[][] srcData = src.getIntDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                int[] s = srcData[b];
                double[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += s[srcPixelOffset];
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            int[] d = dstData[b];
            double[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = (int) (a[h * dstWidth + w] / numbSrc + 0.5);
                    dstPixelOffset += dstPixelStride;
                }
            }
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

        double[][] accu = new double[dstBands][dstWidth * dstHeight];
        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            float[][] srcData = src.getFloatDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                float[] s = srcData[b];
                double[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += s[srcPixelOffset];
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            float[] d = dstData[b];
            double[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = (float) (a[h * dstWidth + w] / numbSrc + 0.5);
                    dstPixelOffset += dstPixelStride;
                }
            }
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

        double[][] accu = new double[dstBands][dstWidth * dstHeight];
        int numbSrc = sources.size();
        for (int i = 0; i < numbSrc; i++) {
            ImageElement imgElement = sources.get(i);
            PlanarImage img = imgElement.getImage(null, false);
            RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
            RasterAccessor src = new RasterAccessor(img.getData(), region, srcTag, img.getColorModel());
            int srcLineStride = src.getScanlineStride();
            int srcPixelStride = src.getPixelStride();
            int[] srcBandOffsets = src.getBandOffsets();
            double[][] srcData = src.getDoubleDataArrays();

            for (int b = 0; b < dstBands; b++) {
                int srcLineOffset = srcBandOffsets[b];
                double[] s = srcData[b];
                double[] a = accu[b];

                for (int h = 0; h < dstHeight; h++) {
                    int srcPixelOffset = srcLineOffset;
                    srcLineOffset += srcLineStride;

                    for (int w = 0; w < dstWidth; w++) {
                        a[h * dstWidth + w] += s[srcPixelOffset];
                        srcPixelOffset += srcPixelStride;
                    }
                }
            }
            incrementProgressBar();
        }

        for (int b = 0; b < dstBands; b++) {
            int dstLineOffset = dstBandOffsets[b];
            double[] d = dstData[b];
            double[] a = accu[b];

            for (int h = 0; h < dstHeight; h++) {
                int dstPixelOffset = dstLineOffset;
                dstLineOffset += dstLineStride;

                for (int w = 0; w < dstWidth; w++) {
                    d[dstPixelOffset] = a[h * dstWidth + w] / numbSrc + 0.5;
                    dstPixelOffset += dstPixelStride;
                }
            }
        }
    }
}
