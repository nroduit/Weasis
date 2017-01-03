package org.weasis.core.api.image.cv;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.opencv.core.Core;
import org.opencv.core.Core.MinMaxLocResult;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.util.ImageFiler;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.image.util.KernelData;
import org.weasis.core.api.image.util.LookupTableJAI;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.Thumbnail;

public class ImageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageProcessor.class);

    /**
     * Converts/writes a Mat into a BufferedImage.
     * 
     * @param matrix
     * 
     * @return BufferedImage
     */
    public static BufferedImage toBufferedImage(Mat matrix) {
        if(matrix == null){
            return null;
        }
        
        int cols = matrix.cols();
        int rows = matrix.rows();
        int type = matrix.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;

        ColorSpace cs;
        WritableRaster raster;
        ComponentColorModel colorModel;
        int dataType = convertToDataType(type);

        switch (channels) {
            case 1:
                cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
                colorModel = new ComponentColorModel(cs, new int[] { bpp }, false, true, Transparency.OPAQUE, dataType);
                raster = colorModel.createCompatibleWritableRaster(cols, rows);
                break;
            case 3:
                cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
                colorModel = new ComponentColorModel(cs, new int[] { bpp, bpp, bpp }, false, false, Transparency.OPAQUE,
                    dataType);
                raster = Raster.createInterleavedRaster(dataType, cols, rows, cols * channels, channels,
                    new int[] { 2, 1, 0 }, null);
                break;
            default:
                throw new UnsupportedOperationException(
                    "No implementation to handle " + matrix.channels() + " channels");
        }

        DataBuffer buf = raster.getDataBuffer();

        if (buf instanceof DataBufferByte) {
            matrix.get(0, 0, ((DataBufferByte) buf).getData());
        } else if (buf instanceof DataBufferUShort) {
            matrix.get(0, 0, ((DataBufferUShort) buf).getData());
        } else if (buf instanceof DataBufferShort) {
            matrix.get(0, 0, ((DataBufferShort) buf).getData());
        } else if (buf instanceof DataBufferInt) {
            matrix.get(0, 0, ((DataBufferInt) buf).getData());
        } else if (buf instanceof DataBufferFloat) {
            matrix.get(0, 0, ((DataBufferFloat) buf).getData());
        } else if (buf instanceof DataBufferDouble) {
            matrix.get(0, 0, ((DataBufferDouble) buf).getData());
        }
        return new BufferedImage(colorModel, raster, false, null);

    }

    private static int convertToDataType(int cvType) {
        switch (CvType.depth(cvType)) {
            case CvType.CV_8U:
            case CvType.CV_8S:
                return DataBuffer.TYPE_BYTE;
            case CvType.CV_16U:
                return DataBuffer.TYPE_USHORT;
            case CvType.CV_16S:
                return DataBuffer.TYPE_SHORT;
            case CvType.CV_32S:
                return DataBuffer.TYPE_INT;
            case CvType.CV_32F:
                return DataBuffer.TYPE_FLOAT;
            case CvType.CV_64F:
                return DataBuffer.TYPE_DOUBLE;
            default:
                throw new java.lang.UnsupportedOperationException("Unsupported CvType value: " + cvType);
        }
    }

    public static Mat toMat(RenderedImage img) {
        return toMat(img, null);
    }

    public static Mat toMat(RenderedImage img, Rectangle region) {
        Raster raster = region == null ? img.getData() : img.getData(region);
        DataBuffer buf = raster.getDataBuffer();
        int[] samples = raster.getSampleModel().getSampleSize();
        int[] offsets;
        if (raster.getSampleModel() instanceof ComponentSampleModel) {
            offsets = ((ComponentSampleModel) raster.getSampleModel()).getBandOffsets();
        } else {
            offsets = new int[samples.length];
            for (int i = 0; i < offsets.length; i++) {
                offsets[i] = i;
            }
        }

        if (ImageToolkit.isBinary(raster.getSampleModel())) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
            mat.put(0, 0, ImageToolkit.getUnpackedBinaryData(raster, raster.getBounds()));
            return mat;
        }

        if (buf instanceof DataBufferByte) {
            if (Arrays.equals(offsets, new int[] { 0, 0, 0 })) {
                List<Mat> mv = new ArrayList<>();
                Mat b = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(2));
                mv.add(b);
                Mat g = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(1));
                mv.add(g);
                Mat r = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC1);
                b.put(0, 0, ((DataBufferByte) buf).getData(0));
                mv.add(r);
                Mat dstImg = new Mat();
                Core.merge(mv, dstImg);
                return dstImg;
            }

            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_8UC(samples.length));
            mat.put(0, 0, ((DataBufferByte) buf).getData());
            if (Arrays.equals(offsets, new int[] { 0, 1, 2 })) {
                Mat dstImg = new Mat();
                Imgproc.cvtColor(mat, dstImg, Imgproc.COLOR_RGB2BGR);
                return dstImg;
            }
            return mat;
        } else if (buf instanceof DataBufferUShort) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_16UC(samples.length));
            mat.put(0, 0, ((DataBufferUShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferShort) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_16SC(samples.length));
            mat.put(0, 0, ((DataBufferShort) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferInt) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_32SC(samples.length));
            mat.put(0, 0, ((DataBufferInt) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferFloat) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_32FC(samples.length));
            mat.put(0, 0, ((DataBufferFloat) buf).getData());
            return mat;
        } else if (buf instanceof DataBufferDouble) {
            Mat mat = new Mat(raster.getHeight(), raster.getWidth(), CvType.CV_64FC(samples.length));
            mat.put(0, 0, ((DataBufferDouble) buf).getData());
            return mat;
        }

        return null;
    }

    public static BufferedImage convertTo(RenderedImage src, int imageType) {
        BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), imageType);
        Graphics2D big = dst.createGraphics();
        try {
            big.drawRenderedImage(src, AffineTransform.getTranslateInstance(0.0, 0.0));
        } finally {
            big.dispose();
        }
        return dst;
    }

    public Mat blur(Mat input, int numberOfTimes) {
        Mat sourceImage;
        Mat destImage = input.clone();
        for (int i = 0; i < numberOfTimes; i++) {
            sourceImage = destImage.clone();
            // Imgproc.blur(sourceImage, destImage, new Size(3.0, 3.0));
            process(sourceImage, destImage, 256);
        }
        return destImage;
    }

    public static BufferedImage applyLUT(RenderedImage source, LookupTableJAI modalityLookup) {
        if (source == null || source.getSampleModel().getDataType() != DataBuffer.TYPE_BYTE
            || modalityLookup.getData().getDataType() != DataBuffer.TYPE_BYTE) {
            return ImageToolkit.convertRenderedImage(source);
        }
        Mat srcImg = toMat(source);
        Mat dstImg = new Mat();

        DataBuffer buf = modalityLookup.getData();
        int bands = buf.getNumBanks();

        Mat lut = null;
        if (buf instanceof DataBufferByte) {
            lut = new Mat(source.getHeight(), source.getWidth(), CvType.CV_8UC(bands));
            lut.put(0, 0, ((DataBufferByte) buf).getData());
        } else if (buf instanceof DataBufferUShort) {
            Mat mat = new Mat(source.getHeight(), source.getWidth(), CvType.CV_16UC(bands));
            mat.put(0, 0, ((DataBufferUShort) buf).getData());
        } else if (buf instanceof DataBufferShort) {
            Mat mat = new Mat(source.getHeight(), source.getWidth(), CvType.CV_16SC(bands));
            mat.put(0, 0, ((DataBufferShort) buf).getData());
        } else if (buf instanceof DataBufferInt) {
            Mat mat = new Mat(source.getHeight(), source.getWidth(), CvType.CV_32SC(bands));
            mat.put(0, 0, ((DataBufferInt) buf).getData());
        }
        Core.LUT(srcImg, lut, dstImg);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage rescaleToByte(RenderedImage source, double alpha, double beta) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        srcImg.convertTo(dstImg, CvType.CV_8U, alpha, beta);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage invertLUT(RenderedImage source) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Core.bitwise_not(srcImg, srcImg);
        return toBufferedImage(srcImg);
    }

    public static BufferedImage bitwiseAnd(RenderedImage source, int src2Cst) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat mask = new Mat(srcImg.size(), srcImg.type(), new Scalar(src2Cst));
        Core.bitwise_and(srcImg, mask, srcImg);
        return toBufferedImage(srcImg);
    }

    public static BufferedImage crop(RenderedImage source, Rectangle area) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = srcImg.submat(new Rect(area.x, area.y, area.width, area.height));
        return toBufferedImage(dstImg);
    }

    public static MinMaxLocResult minMaxLoc(RenderedImage source, Rectangle area) {
        Mat srcImg = toMat(Objects.requireNonNull(source), area);
        return Core.minMaxLoc(srcImg);
    }

    public static List<MatOfPoint> transformShapeToContour(Shape shape, boolean keepImageCoordinates) {
        Rectangle b = shape.getBounds();
        if (keepImageCoordinates) {
            b.x = 0;
            b.y = 0;
        }
        List<MatOfPoint> points = new ArrayList<>();
        List<Point> cvPts = new ArrayList<>();

        PathIterator iterator = new FlatteningPathIterator(shape.getPathIterator(null), 2);
        double[] pts = new double[6];
        MatOfPoint p = null;
        while (!iterator.isDone()) {
            int segType = iterator.currentSegment(pts);
            switch (segType) {
                case PathIterator.SEG_MOVETO:
                    if (p != null) {
                        p.fromArray(cvPts.toArray(new Point[cvPts.size()]));
                        points.add(p);
                    }
                    p = new MatOfPoint();
                    cvPts.add(new Point(pts[0] - b.x, pts[1] - b.y));
                    break;
                case PathIterator.SEG_LINETO:
                case PathIterator.SEG_CLOSE:
                    cvPts.add(new Point(pts[0] - b.x, pts[1] - b.y));
                    break;
                default:
                    break; // should never append with FlatteningPathIterator
            }
            iterator.next();
        }

        if (p != null) {
            p.fromArray(cvPts.toArray(new Point[cvPts.size()]));
            points.add(p);
        }
        return points;
    }

    public static double[][] meanStdDev(RenderedImage source, Shape shape) {
        return meanStdDev(source, shape, null, null);
    }

    public static double[][] meanStdDev(RenderedImage source, Shape shape, Integer paddingValue, Integer paddingLimit) {

        Rectangle bounds = shape.getBounds();
        Mat srcImg = toMat(Objects.requireNonNull(source), bounds);
        Mat mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
        List<MatOfPoint> pts = transformShapeToContour(shape, false);
        Imgproc.fillPoly(mask, pts, new Scalar(255));

        if (paddingValue != null) {
            if (paddingLimit == null) {
                paddingLimit = paddingValue;
            } else if (paddingLimit < paddingValue) {
                int temp = paddingValue;
                paddingValue = paddingLimit;
                paddingLimit = temp;
            }
            exludePaddingValue(srcImg, mask, paddingValue, paddingLimit);
        }

        System.out.println(mask.dump());

        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        Core.meanStdDev(srcImg, mean, stddev, mask);

        MinMaxLocResult minMax = Core.minMaxLoc(srcImg, mask);

        double[][] val = new double[4][];
        val[0] = new double[] { minMax.minVal };
        val[1] = new double[] { minMax.maxVal };
        val[2] = mean.toArray();
        val[3] = stddev.toArray();

        return val;
    }

    private static void exludePaddingValue(Mat src, Mat mask, int paddingValue, int paddingLimit) {
        Mat dst = new Mat();
        Core.inRange(src, new Scalar(paddingValue), new Scalar(paddingLimit), dst);
        System.out.println(dst.dump());
        // System.out.println(mask.dump());
        Core.bitwise_not(dst, dst);
        Core.add(dst, mask, mask);
    }

    public static List<MatOfPoint> findContours(RenderedImage source, Rectangle area) {
        Mat srcImg = toMat(Objects.requireNonNull(source), area);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierachy = new Mat();
        Imgproc.findContours(srcImg, contours, hierachy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    public static BufferedImage scale(RenderedImage source, Dimension dim) {
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        Imgproc.resize(srcImg, dstImg, new Size(dim.getWidth(), dim.getHeight()));
        return toBufferedImage(dstImg);
    }

    public static BufferedImage scale(RenderedImage source, Dimension dim, Integer interpolation) {
        if (interpolation == null || interpolation < Imgproc.INTER_NEAREST || interpolation > Imgproc.INTER_LANCZOS4) {
            return scale(source, dim);
        }
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        Imgproc.resize(srcImg, dstImg, new Size(dim.getWidth(), dim.getHeight()), 0, 0, interpolation);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage filter(RenderedImage source, KernelData kernel) {
        Objects.requireNonNull(kernel);
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat k = new Mat(kernel.getHeight(), kernel.getWidth(), CvType.CV_32F);
        k.put(0, 0, kernel.getData());
        Mat dstImg = new Mat();
        Imgproc.filter2D(srcImg, dstImg, -1, k, new Point(-1, -1), 0, Core.BORDER_DEFAULT);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage combineTwoImages(RenderedImage source, RenderedImage imgOverlay, int transparency) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat src2Img = toMat(Objects.requireNonNull(imgOverlay));
        Mat dstImg = new Mat();
        Core.addWeighted(srcImg, 1.0, src2Img, transparency, 0.0, dstImg);
        return toBufferedImage(dstImg);
    }

    private static boolean isGray(Color color) {
        int r = color.getRed();
        return r == color.getGreen() && r == color.getBlue();
    }

    public static BufferedImage overlay(RenderedImage source, RenderedImage imgOverlay, Color color) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat mask = toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color)) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            grayImg.copyTo(srcImg, mask);
            return toBufferedImage(srcImg);
        }
        if (srcImg.channels() < 3) {
            Mat dstImg = new Mat();
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
            srcImg = dstImg;
        }
        Mat colorImg =
            new Mat(srcImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(srcImg, mask);
        return toBufferedImage(srcImg);
    }

    public static BufferedImage drawShape(RenderedImage source, Shape shape, Color color) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(srcImg, pts, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        return toBufferedImage(srcImg);
    }

    public static BufferedImage applyShutter(RenderedImage source, Shape shape, Color color) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(mask, pts, new Scalar(1));
        Mat dstImg =
            new Mat(srcImg.size(), srcImg.type(), new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        srcImg.copyTo(dstImg, mask);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage applyShutter(RenderedImage source, RenderedImage imgOverlay, Color color) {
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat mask = toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color)) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            grayImg.copyTo(srcImg, mask);
            return toBufferedImage(srcImg);
        }

        if (srcImg.channels() < 3) {
            Mat dstImg = new Mat();
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
            srcImg = dstImg;
        }
        Mat colorImg =
            new Mat(srcImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(srcImg, mask);
        return toBufferedImage(srcImg);
    }

    public static BufferedImage getAsImage(Area shape, RenderedImage source) {
        SampleModel sm =
            new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, source.getWidth(), source.getHeight(), 1);
        BufferedImage ti = new BufferedImage(source.getWidth(), source.getHeight(), sm.getDataType());
        Graphics2D g2d = ti.createGraphics();
        // Write the Shape into the TiledImageGraphics.
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.fill(shape);
        g2d.dispose();
        return ti;
    }

    public static BufferedImage getRotatedImage(RenderedImage source, int rotateCvType) {
        if (rotateCvType < 0) {
            return ImageToolkit.convertRenderedImage(source);
        }
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        Core.rotate(srcImg, dstImg, rotateCvType);

        // // Handle non square images. Translation is necessary because the transpose operator keeps the same
        // // origin (top left not the center of the image)
        // float diffw = source.getWidth() / 2.0f - result.getWidth() / 2.0f;
        // float diffh = source.getHeight() / 2.0f - result.getHeight() / 2.0f;
        // if (MathUtil.isDifferentFromZero(diffw) || MathUtil.isDifferentFromZero(diffh)) {
        // result = TranslateDescriptor.create(result, diffw, diffh, null, ImageToolkit.NOCACHE_HINT);
        // }
        return toBufferedImage(dstImg);
    }

    public static BufferedImage flip(RenderedImage source, int flipCvType) {
        if (flipCvType < 0) {
            return ImageToolkit.convertRenderedImage(source);
        }
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        Core.flip(srcImg, dstImg, flipCvType);
        return toBufferedImage(dstImg);
    }

    public static BufferedImage normalizeHisto(RenderedImage source) {
        return ImageToolkit.convertRenderedImage(source);
        // Mat srcImg = fromBufferedImage(source);
        // Mat hist = new Mat();
        //
        // Imgproc.calcHist(srcImg, channels, mask, hist, histSize, ranges);
        // Mat dstImg = new Mat();
        // Core.normalize(dstImg, dstImg, alpha, beta, norm_type);
        // return toBufferedImage(dstImg);
    }

    public static BufferedImage getRotatedImage(RenderedImage source, double angle, double centerx, double centery) {
        if (MathUtil.isEqualToZero(angle)) {
            return ImageToolkit.convertRenderedImage(source);
        }
        Mat srcImg = toMat(Objects.requireNonNull(source));
        Point ptCenter = new Point(centerx, centery);
        Mat rot = Imgproc.getRotationMatrix2D(ptCenter, -angle, 1.0);
        Mat dstImg = new Mat();
        // determine bounding rectangle
        Rect bbox = new RotatedRect(ptCenter, srcImg.size(), -angle).boundingRect();
        // double[] matrix = new double[rot.cols() * rot.rows()];
        // // adjust transformation matrix
        // rot.get(0, 0, matrix);
        // matrix[2] += bbox.width / 2.0 - centerx;
        // matrix[rot.cols() + 2] += bbox.height / 2.0 - centery;
        // rot.put(0, 0, matrix);
        Imgproc.warpAffine(srcImg, dstImg, rot, bbox.size());

        return toBufferedImage(dstImg);
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     *
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     * @return
     */
    public static double[] findMinMaxValues(RenderedImage source) {
        double[] extrema = null;
        if (source != null) {
            Mat srcImg = toMat(Objects.requireNonNull(source));
            MinMaxLocResult minMax = Core.minMaxLoc(srcImg);
            extrema = new double[2];
            extrema[0] = minMax.minVal;
            extrema[1] = minMax.maxVal;
        }
        return extrema;
    }

    public static BufferedImage buildThumbnail(RenderedImage source, Dimension iconDim, boolean keepRatio) {
        Objects.requireNonNull(source);
        if (Objects.requireNonNull(iconDim).width < 1 || iconDim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + iconDim);
        }

        final double scale = Math.min(iconDim.getHeight() / source.getHeight(), iconDim.getWidth() / source.getWidth());
        if (scale >= 1.0) {
            return ImageToolkit.convertRenderedImage(source);
        }
        if (scale < 0.005) {
            return null; // Image is too large to be converted
        }

        Size dim = keepRatio ? new Size((int) (scale * source.getWidth()), (int) (scale * source.getHeight()))
            : new Size(iconDim.width, iconDim.height);

        Mat srcImg = toMat(Objects.requireNonNull(source));
        Mat dstImg = new Mat();
        Imgproc.resize(srcImg, dstImg, dim, 0, 0, Imgproc.INTER_AREA);
        return toBufferedImage(dstImg);
    }

    public static boolean writePNM(RenderedImage source, File file, boolean addThumb) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        Mat srcImg = toMat(source);

        try {
            if (addThumb) {
                final double scale = Math.min(Thumbnail.MAX_SIZE / (double) source.getHeight(),
                    (double) Thumbnail.MAX_SIZE / source.getWidth());
                if (scale < 1.0) {
                    Size dim = new Size((int) (scale * source.getWidth()), (int) (scale * source.getHeight()));
                    Mat thumbnail = new Mat();
                    Imgproc.resize(srcImg, thumbnail, dim, 0, 0, Imgproc.INTER_AREA);
                    Imgcodecs.imwrite(ImageFiler.changeExtension(file.getPath(), ".jpg"), thumbnail);
                }
            }
            return Imgcodecs.imwrite(file.getPath(), srcImg);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }

    public static boolean writePNG(RenderedImage source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        // TOOD handle binary
        Mat srcImg = toMat(source);
        int type = srcImg.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;
        if (bpp > 16 || !CvType.isInteger(type)) {
            Mat dstImg = new Mat();
            srcImg.convertTo(dstImg, CvType.CV_16SC(channels));
            srcImg = dstImg;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), srcImg);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }

    public static boolean writeImage(RenderedImage source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), toMat(source));
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }

    public static boolean writeImage(RenderedImage source, File file, MatOfInt params) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), toMat(source), params);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }
    public static BufferedImage readRenderedImage(File file) {
        return toBufferedImage(readImage(file));
    }
    public static Mat readImage(File file) {
        if (!file.canRead()) {
            return null;
        }

        try {
            return Imgcodecs.imread(file.getPath());
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return null;
        }
    }

    // private static void writeTIFF(OutputStream os, RenderedImage source, boolean tiled, boolean addThumb,
    // boolean jpegCompression) throws IOException {
    //
    // TIFFEncodeParam param = new TIFFEncodeParam();
    // if (tiled) {
    // param.setWriteTiled(true);
    // param.setTileSize(TILESIZE, TILESIZE);
    // }
    // boolean binary = ImageUtil.isBinary(source.getSampleModel());
    // if (binary) {
    // param.setCompression(TIFFEncodeParam.COMPRESSION_GROUP4);
    // } else if (jpegCompression) {
    // param.setCompression(TIFFEncodeParam.COMPRESSION_JPEG_TTN2);
    // JPEGEncodeParam wparam = new JPEGEncodeParam();
    // wparam.setQuality(1.0f);
    // param.setJPEGEncodeParam(wparam);
    // }
    // if (addThumb) {
    // ArrayList<TIFFField> extraFields = new ArrayList<>(6);
    // int fileVal = getResolutionInDpi(source);
    // if (fileVal > 0) {
    // TIFFDirectory dir = (TIFFDirectory) source.getProperty(TIFF_TAG); // $NON-NLS-1$
    // TIFFField f;
    // f = dir.getField(282);
    // long[][] xRes = f.getAsRationals();
    // f = dir.getField(283);
    // long[][] yRes = f.getAsRationals();
    // f = dir.getField(296);
    // char[] resUnit = f.getAsChars();
    // f = dir.getField(271);
    // if (f != null) {
    // extraFields.add(new TIFFField(271, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
    // }
    // f = dir.getField(272);
    // if (f != null) {
    // extraFields.add(new TIFFField(272, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
    // }
    // extraFields.add(new TIFFField(282, TIFFField.TIFF_RATIONAL, xRes.length, xRes));
    // extraFields.add(new TIFFField(283, TIFFField.TIFF_RATIONAL, yRes.length, yRes));
    // extraFields.add(new TIFFField(296, TIFFField.TIFF_SHORT, resUnit.length, resUnit));
    //
    // }
    // extraFields.add(new TIFFField(305, TIFFField.TIFF_ASCII, 1, new String[] { AppProperties.WEASIS_NAME }));
    // param.setExtraFields(extraFields.toArray(new TIFFField[extraFields.size()]));
    //
    // if (!binary) {
    // // Doesn't support bilevel image (or binary to grayscale).
    // ArrayList<RenderedImage> list = new ArrayList<>();
    // list.add(Thumbnail.createThumbnail(source));
    // param.setExtraImages(list.iterator());
    // }
    // }
    //
    // ImageEncoder enc = ImageCodec.createImageEncoder("TIFF", os, param); //$NON-NLS-1$
    // enc.encode(source);
    // }

    public void process(Mat sourceImage, Mat resultImage, int tileSize) {

        if (sourceImage.rows() != resultImage.rows() || sourceImage.cols() != resultImage.cols()) {
            throw new IllegalStateException("");
        }

        final int rowTiles = (sourceImage.rows() / tileSize) + (sourceImage.rows() % tileSize != 0 ? 1 : 0);
        final int colTiles = (sourceImage.cols() / tileSize) + (sourceImage.cols() % tileSize != 0 ? 1 : 0);

        Mat tileInput = new Mat(tileSize, tileSize, sourceImage.type());
        Mat tileOutput = new Mat(tileSize, tileSize, sourceImage.type());

        int boderType = Core.BORDER_DEFAULT;
        int mPadding = 3;

        for (int rowTile = 0; rowTile < rowTiles; rowTile++) {
            for (int colTile = 0; colTile < colTiles; colTile++) {
                Rect srcTile = new Rect(colTile * tileSize - mPadding, rowTile * tileSize - mPadding,
                    tileSize + 2 * mPadding, tileSize + 2 * mPadding);
                Rect dstTile = new Rect(colTile * tileSize, rowTile * tileSize, tileSize, tileSize);
                copyTileFromSource(sourceImage, tileInput, srcTile, boderType);
                processTileImpl(tileInput, tileOutput);
                copyTileToResultImage(tileOutput, resultImage, new Rect(mPadding, mPadding, tileSize, tileSize),
                    dstTile);
            }
        }
    }

    private void copyTileToResultImage(Mat tileOutput, Mat resultImage, Rect srcTile, Rect dstTile) {
        Point br = dstTile.br();

        if (br.x >= resultImage.cols()) {
            dstTile.width -= br.x - resultImage.cols();
            srcTile.width -= br.x - resultImage.cols();
        }

        if (br.y >= resultImage.rows()) {
            dstTile.height -= br.y - resultImage.rows();
            srcTile.height -= br.y - resultImage.rows();
        }

        Mat tileView = tileOutput.submat(srcTile);
        Mat dstView = resultImage.submat(dstTile);

        assert (tileView.rows() == dstView.rows());
        assert (tileView.cols() == dstView.cols());

        tileView.copyTo(dstView);
    }

    private void processTileImpl(Mat tileInput, Mat tileOutput) {
        Imgproc.blur(tileInput, tileOutput, new Size(7.0, 7.0));
    }

    private void copyTileFromSource(Mat sourceImage, Mat tileInput, Rect tile, int mBorderType) {
        Point tl = tile.tl();
        Point br = tile.br();

        Point tloffset = new Point();
        Point broffset = new Point();

        // Take care of border cases
        if (tile.x < 0) {
            tloffset.x = -tile.x;
            tile.x = 0;
        }

        if (tile.y < 0) {
            tloffset.y = -tile.y;
            tile.y = 0;
        }

        if (br.x >= sourceImage.cols()) {
            broffset.x = br.x - sourceImage.cols() + 1;
            tile.width -= broffset.x;
        }

        if (br.y >= sourceImage.rows()) {
            broffset.y = br.y - sourceImage.rows() + 1;
            tile.height -= broffset.y;
        }

        // If any of the tile sides exceed source image boundary we must use copyMakeBorder to make proper paddings
        // for this side
        if (tloffset.x > 0 || tloffset.y > 0 || broffset.x > 0 || broffset.y > 0) {
            Rect paddedTile = new Rect(tile.tl(), tile.br());
            assert (paddedTile.x >= 0);
            assert (paddedTile.y >= 0);
            assert (paddedTile.br().x < sourceImage.cols());
            assert (paddedTile.br().y < sourceImage.rows());

            Core.copyMakeBorder(sourceImage.submat(paddedTile), tileInput, (int) tloffset.y, (int) broffset.y,
                (int) tloffset.x, (int) broffset.x, mBorderType);
        } else {
            // Entire tile (with paddings lies inside image and it's safe to just take a region:
            sourceImage.submat(tile).copyTo(tileInput);
        }

    }

    public static RenderedImage meanStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            RenderedImage img = firstImg.getImage(null, false);
            if (img == null) {
                return null;
            }

            Integer type = null;
            Mat mean = Mat.zeros(img.getWidth(), img.getHeight(), CvType.CV_32F);
            int numbSrc = sources.size();
            for (int i = 0; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                RenderedImage image = imgElement.getImage(null, false);
                Mat mat = toMat(image);
                if (type == null) {
                    type = mat.type();
                }
                Imgproc.accumulate(mat, mean);
            }
            Mat dstImg = new Mat();
            Core.divide(mean, new Scalar(numbSrc), mean);
            mean.convertTo(dstImg, type);
            return toBufferedImage(dstImg);
        }
        return null;
    }

    public static RenderedImage minStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            RenderedImage img = firstImg.getImage(null, false);
            if (img == null) {
                return null;
            }
            Mat dstImg = toMat(img);

            int numbSrc = sources.size();
            for (int i = 1; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                RenderedImage image = imgElement.getImage(null, false);
                Mat mat = toMat(image);
                Core.min(dstImg, mat, dstImg);
            }
            return toBufferedImage(dstImg);
        }
        return null;
    }

    public static RenderedImage maxStack(List<ImageElement> sources) {
        if (sources.size() > 1) {
            ImageElement firstImg = sources.get(0);
            RenderedImage img = firstImg.getImage(null, false);
            if (img == null) {
                return null;
            }
            Mat dstImg = toMat(img);

            int numbSrc = sources.size();
            for (int i = 1; i < numbSrc; i++) {
                ImageElement imgElement = sources.get(i);
                RenderedImage image = imgElement.getImage(null, false);
                Mat mat = toMat(image);
                Core.max(dstImg, mat, dstImg);
            }
            return toBufferedImage(dstImg);
        }
        return null;
    }

}
