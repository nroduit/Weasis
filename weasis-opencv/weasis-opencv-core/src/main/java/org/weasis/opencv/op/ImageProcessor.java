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
package org.weasis.opencv.op;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.FlatteningPathIterator;
import java.awt.geom.PathIterator;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;

public class ImageProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageProcessor.class);

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

    public static ImageCV applyLUT(Mat source, byte[][] lut) {
        Mat srcImg = Objects.requireNonNull(source);
        int lutCh = Objects.requireNonNull(lut).length;
        Mat lutMat;

        if (lutCh > 1) {
            lutMat = new Mat();
            List<Mat> luts = new ArrayList<>(lutCh);
            for (int i = 0; i < lutCh; i++) {
                Mat l = new Mat(1, 256, CvType.CV_8U);
                l.put(0, 0, lut[i]);
                luts.add(l);
            }
            Core.merge(luts, lutMat);
            if (srcImg.channels() < lut.length) {
                Imgproc.cvtColor(srcImg.clone(), srcImg, Imgproc.COLOR_GRAY2BGR);
            }
        } else {
            lutMat = new Mat(1, 256, CvType.CV_8UC1);
            lutMat.put(0, 0, lut[0]);
        }

        ImageCV dstImg = new ImageCV();
        Core.LUT(srcImg, lutMat, dstImg);
        return dstImg;
    }

    public static ImageCV rescaleToByte(Mat source, double alpha, double beta) {
        ImageCV dstImg = new ImageCV();
        Objects.requireNonNull(source).convertTo(dstImg, CvType.CV_8U, alpha, beta);
        return dstImg;
    }

    public static ImageCV invertLUT(ImageCV source) {
        Objects.requireNonNull(source);
        Core.bitwise_not(source, source);
        return source;
    }

    public static ImageCV bitwiseAnd(Mat source, int src2Cst) {
        Objects.requireNonNull(source);
        ImageCV mask = new ImageCV(source.size(), source.type(), new Scalar(src2Cst));
        Core.bitwise_and(source, mask, mask);
        return mask;
    }

    public static ImageCV crop(Mat source, Rectangle area) {
        return ImageCV
            .toImageCV(Objects.requireNonNull(source).submat(new Rect(area.x, area.y, area.width, area.height)));
    }

    public static MinMaxLocResult minMaxLoc(RenderedImage source, Rectangle area) {
        Mat srcImg = ImageConversion.toMat(Objects.requireNonNull(source), area);
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

    public static double[][] meanStdDev(Mat source) {
        return meanStdDev(source, null, null, null);
    }

    public static double[][] meanStdDev(Mat source, Shape shape) {
        return meanStdDev(source, shape, null, null);
    }

    public static double[][] meanStdDev(Mat source, Shape shape, Integer paddingValue, Integer paddingLimit) {
        List<Mat> list = getMaskImage(source, shape, paddingValue, paddingLimit);
        if(list.size() < 2) {
            return null;
        }
        Mat srcImg = list.get(0);
        Mat mask = list.get(1);
        
        MatOfDouble mean = new MatOfDouble();
        MatOfDouble stddev = new MatOfDouble();
        if (mask == null) {
            Core.meanStdDev(srcImg, mean, stddev);
        } else {
            Core.meanStdDev(srcImg, mean, stddev, mask);
        }

        List<Mat> channels = new ArrayList<>();
        if (srcImg.channels() > 1) {
            Core.split(srcImg, channels);
        } else {
            channels.add(srcImg);
        }

        double[][] val = new double[5][channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            MinMaxLocResult minMax;
            if (mask == null) {
                minMax = Core.minMaxLoc(channels.get(i));
            } else {
                minMax = Core.minMaxLoc(channels.get(i), mask);
            }
            val[0][i] = minMax.minVal;
            val[1][i] = minMax.maxVal;
        }

        val[2] = mean.toArray();
        val[3] = stddev.toArray();
        if(mask == null) {
            val[4][0] = srcImg.width() * (double) srcImg.height();  
        }
        else {
            val[4][0] = Core.countNonZero(mask);  
        }

        return val;
    }
    public static List<Mat> getMaskImage(Mat source, Shape shape, Integer paddingValue, Integer paddingLimit) {
        Objects.requireNonNull(source);
        Mat srcImg;
        Mat mask = null;
        if (shape == null) {
            srcImg = source;
        } else {
            Rectangle b = new Rectangle(0, 0, source.width(), source.height()).intersection(shape.getBounds());
            if (b.getWidth() < 1 || b.getHeight() < 1) {
                return Collections.emptyList();
            }

            srcImg = source.submat(new Rect(b.x, b.y, b.width, b.height));
            mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
            List<MatOfPoint> pts = transformShapeToContour(shape, false);
            Imgproc.fillPoly(mask, pts, new Scalar(255));
        }

        if (paddingValue != null) {
            if (paddingLimit == null) {
                paddingLimit = paddingValue;
            } else if (paddingLimit < paddingValue) {
                int temp = paddingValue;
                paddingValue = paddingLimit;
                paddingLimit = temp;
            }
            Mat maskPix = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(0));
            exludePaddingValue(srcImg, maskPix, paddingValue, paddingLimit);
            if (mask == null) {
                mask = maskPix;
            } else {
                Core.bitwise_and(mask, maskPix, mask);
            }
        }
        return Arrays.asList(srcImg, mask);
    }

    public static MinMaxLocResult minMaxLoc(Mat srcImg, Mat mask) {
        List<Mat> channels = new ArrayList<>(Objects.requireNonNull(srcImg).channels());
        if (srcImg.channels() > 1) {
            Core.split(srcImg, channels);
        } else {
            channels.add(srcImg);
        }

        MinMaxLocResult result = new MinMaxLocResult();
        result.minVal = Double.MAX_VALUE;
        result.maxVal = -Double.MAX_VALUE;

        for (int i = 0; i < channels.size(); i++) {
            MinMaxLocResult minMax = Core.minMaxLoc(channels.get(i), mask);
            result.minVal = Math.min(result.minVal, minMax.minVal);
            if (result.minVal == minMax.minVal) {
                result.minLoc = minMax.minLoc;
            }
            result.maxVal = Math.max(result.maxVal, minMax.maxVal);
            if (result.maxVal == minMax.maxVal) {
                result.maxLoc = minMax.maxLoc;
            }
        }
        return result;
    }

    private static void exludePaddingValue(Mat src, Mat mask, int paddingValue, int paddingLimit) {
        Mat dst = new Mat();
        Core.inRange(src, new Scalar(paddingValue), new Scalar(paddingLimit), dst);
        Core.bitwise_not(dst, dst);
        Core.add(dst, mask, mask);
    }

    public static List<MatOfPoint> findContours(RenderedImage source, Rectangle area) {
        Mat srcImg = ImageConversion.toMat(Objects.requireNonNull(source), area);
        List<MatOfPoint> contours = new ArrayList<>();
        Mat hierachy = new Mat();
        Imgproc.findContours(srcImg, contours, hierachy, Imgproc.RETR_CCOMP, Imgproc.CHAIN_APPROX_SIMPLE);
        return contours;
    }

    public static ImageCV scale(Mat source, Dimension dim) {
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(Objects.requireNonNull(source), dstImg, new Size(dim.getWidth(), dim.getHeight()));
        return dstImg;
    }

    public static ImageCV scale(Mat source, Dimension dim, Integer interpolation) {
        if (interpolation == null || interpolation < Imgproc.INTER_NEAREST || interpolation > Imgproc.INTER_LANCZOS4) {
            return scale(source, dim);
        }
        if (Objects.requireNonNull(dim).width < 1 || dim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + dim);
        }
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(Objects.requireNonNull(source), dstImg, new Size(dim.getWidth(), dim.getHeight()), 0, 0,
            interpolation);
        return dstImg;
    }

    public static ImageCV combineTwoImages(Mat source, Mat imgOverlay, int transparency) {
        Mat srcImg = Objects.requireNonNull(source);
        Mat src2Img = Objects.requireNonNull(imgOverlay);
        ImageCV dstImg = new ImageCV();
        Core.addWeighted(srcImg, 1.0, src2Img, transparency, 0.0, dstImg);
        return dstImg;
    }

    private static boolean isGray(Color color) {
        int r = color.getRed();
        return r == color.getGreen() && r == color.getBlue();
    }

    public static ImageCV overlay(Mat source, RenderedImage imgOverlay, Color color) {
        ImageCV srcImg = ImageCV.toImageCV(Objects.requireNonNull(source));
        Mat mask = ImageConversion.toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color) && srcImg.channels() == 1) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            ImageCV dstImg = new ImageCV();
            srcImg.copyTo(dstImg);
            grayImg.copyTo(dstImg, mask);
            return dstImg;
        }

        ImageCV dstImg = new ImageCV();
        if (srcImg.channels() < 3) {
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
        } else {
            srcImg.copyTo(dstImg);
        }

        Mat colorImg =
            new Mat(dstImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(dstImg, mask);
        return dstImg;
    }

    public static BufferedImage drawShape(RenderedImage source, Shape shape, Color color) {
        Mat srcImg = ImageConversion.toMat(Objects.requireNonNull(source));
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(srcImg, pts, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        return ImageConversion.toBufferedImage(srcImg);
    }

    public static ImageCV applyCropMask(Mat source, Rectangle b, double alpha) {
        Mat srcImg = Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();
        source.copyTo(dstImg);
        if (b.getY() > 0) {
            Imgproc.rectangle(dstImg, new Point(0.0, 0.0), new Point(dstImg.width(), b.getMinY()), new Scalar(0), -1);
        }
        if (b.getX() > 0) {
            Imgproc.rectangle(dstImg, new Point(0.0, b.getMinY()), new Point(b.getMinX(), b.getMaxY()), new Scalar(0),
                -1);
        }
        if (b.getX() < dstImg.width()) {
            Imgproc.rectangle(dstImg, new Point(b.getMaxX(), b.getMinY()), new Point(dstImg.width(), b.getMaxY()),
                new Scalar(0), -1);
        }
        if (b.getY() < dstImg.height()) {
            Imgproc.rectangle(dstImg, new Point(0.0, b.getMaxY()), new Point(dstImg.width(), dstImg.height()),
                new Scalar(0), -1);
        }
        Core.addWeighted(dstImg, alpha, srcImg, 1 - alpha, 0.0, dstImg);
        return dstImg;
    }

    public static ImageCV applyShutter(Mat source, Shape shape, Color color) {
        Mat srcImg = Objects.requireNonNull(source);
        Mat mask = Mat.zeros(srcImg.size(), CvType.CV_8UC1);
        List<MatOfPoint> pts = transformShapeToContour(shape, true);
        Imgproc.fillPoly(mask, pts, new Scalar(1));
        ImageCV dstImg =
            new ImageCV(srcImg.size(), srcImg.type(), new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        srcImg.copyTo(dstImg, mask);
        return dstImg;
    }

    public static ImageCV applyShutter(Mat source, RenderedImage imgOverlay, Color color) {
        ImageCV srcImg = ImageCV.toImageCV(Objects.requireNonNull(source));
        Mat mask = ImageConversion.toMat(Objects.requireNonNull(imgOverlay));
        if (isGray(color) && srcImg.channels() == 1) {
            Mat grayImg = new Mat(srcImg.size(), CvType.CV_8UC1, new Scalar(color.getRed()));
            ImageCV dstImg = new ImageCV();
            srcImg.copyTo(dstImg);
            grayImg.copyTo(dstImg, mask);
            return dstImg;
        }

        ImageCV dstImg = new ImageCV();
        if (srcImg.channels() < 3) {
            Imgproc.cvtColor(srcImg, dstImg, Imgproc.COLOR_GRAY2BGR);
        } else {
            srcImg.copyTo(dstImg);
        }

        Mat colorImg =
            new Mat(dstImg.size(), CvType.CV_8UC3, new Scalar(color.getBlue(), color.getGreen(), color.getRed()));
        colorImg.copyTo(dstImg, mask);
        return dstImg;
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

    public static ImageCV getRotatedImage(Mat source, int rotateCvType) {
        if (rotateCvType < 0) {
            return ImageCV.toImageCV(source);
        }
        Mat srcImg = Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();
        Core.rotate(srcImg, dstImg, rotateCvType);
        return dstImg;
    }

    public static ImageCV flip(Mat source, int flipCvType) {
        if (flipCvType < 0) {
            return ImageCV.toImageCV(source);
        }
        Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();
        Core.flip(source, dstImg, flipCvType);
        return dstImg;
    }

    private static boolean isEqualToZero(double val) {
        return Math.copySign(val, 1.0) < 1e-6;
    }

    public static ImageCV getRotatedImage(Mat source, double angle, double centerx, double centery) {
        if (isEqualToZero(angle)) {
            return ImageCV.toImageCV(source);
        }
        Mat srcImg = Objects.requireNonNull(source);
        Point ptCenter = new Point(centerx, centery);
        Mat rot = Imgproc.getRotationMatrix2D(ptCenter, -angle, 1.0);
        ImageCV dstImg = new ImageCV();
        // determine bounding rectangle
        Rect bbox = new RotatedRect(ptCenter, srcImg.size(), -angle).boundingRect();
        // double[] matrix = new double[rot.cols() * rot.rows()];
        // // adjust transformation matrix
        // rot.get(0, 0, matrix);
        // matrix[2] += bbox.width / 2.0 - centerx;
        // matrix[rot.cols() + 2] += bbox.height / 2.0 - centery;
        // rot.put(0, 0, matrix);
        Imgproc.warpAffine(srcImg, dstImg, rot, bbox.size());

        return dstImg;
    }

    public static ImageCV warpAffine(Mat source, Mat matrix, Size boxSize, Integer interpolation) {
        if (matrix == null) {
            return (ImageCV) source;
        }
        // System.out.println(matrix.dump());
        Mat srcImg = Objects.requireNonNull(source);
        ImageCV dstImg = new ImageCV();

        if (interpolation == null) {
            interpolation = Imgproc.INTER_LINEAR;
        }
        Imgproc.warpAffine(srcImg, dstImg, matrix, boxSize, interpolation);

        return dstImg;
    }

    /**
     * Computes Min/Max values from Image excluding range of values provided
     *
     * @param img
     * @param paddingValueMin
     * @param paddingValueMax
     * @return
     */
    public static MinMaxLocResult findMinMaxValues(Mat source) {
        if (source != null) {
            return minMaxLoc(source, null);
        }
        return null;
    }

    public static MinMaxLocResult findMinMaxValues(Mat source, Integer paddingValue, Integer paddingLimit) {
        if (source != null) {
            Mat mask = new Mat(source.size(), CvType.CV_8UC1, new Scalar(0));
            if (paddingValue != null) {
                if (paddingLimit == null) {
                    paddingLimit = paddingValue;
                } else if (paddingLimit < paddingValue) {
                    int temp = paddingValue;
                    paddingValue = paddingLimit;
                    paddingLimit = temp;
                }
                exludePaddingValue(source, mask, paddingValue, paddingLimit);
            }
            return minMaxLoc(source, mask);
        }
        return null;
    }

    public static ImageCV buildThumbnail(PlanarImage source, Dimension iconDim, boolean keepRatio) {
        Objects.requireNonNull(source);
        if (Objects.requireNonNull(iconDim).width < 1 || iconDim.height < 1) {
            throw new IllegalArgumentException("Unsupported size: " + iconDim);
        }

        final double scale = Math.min(iconDim.getHeight() / source.height(), iconDim.getWidth() / source.width());
        if (scale >= 1.0) {
            return source.toImageCV();
        }
        if (scale < 0.005) {
            return null; // Image is too large to be converted
        }

        Size dim = keepRatio ? new Size((int) (scale * source.width()), (int) (scale * source.height()))
            : new Size(iconDim.width, iconDim.height);

        Mat srcImg = Objects.requireNonNull(source).toMat();
        ImageCV dstImg = new ImageCV();
        Imgproc.resize(srcImg, dstImg, dim, 0, 0, Imgproc.INTER_AREA);
        return dstImg;
    }

    public static boolean writeImage(Mat source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), source);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing Image", e); //$NON-NLS-1$
            delete(file);
            return false;
        }
    }

    public static boolean writeThumbnail(Mat source, File file, int maxSize) {
        try {
            final double scale = Math.min(maxSize / (double) source.height(), (double) maxSize / source.width());
            if (scale < 1.0) {
                Size dim = new Size((int) (scale * source.width()), (int) (scale * source.height()));
                try (ImageCV thumbnail = new ImageCV()) {
                    Imgproc.resize(source, thumbnail, dim, 0, 0, Imgproc.INTER_AREA);
                    MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80);
                    return Imgcodecs.imwrite(file.getPath(), thumbnail, map);
                }
            }
            return false;
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing thumbnail", e); //$NON-NLS-1$
            delete(file);
            return false;
        }
    }

    public static boolean writePNG(Mat source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        // TOOD handle binary
        Mat srcImg = Objects.requireNonNull(source);
        Mat dstImg = null;
        int type = srcImg.type();
        int elemSize = CvType.ELEM_SIZE(type);
        int channels = CvType.channels(type);
        int bpp = (elemSize * 8) / channels;
        if (bpp > 16 || !CvType.isInteger(type)) {
            dstImg = new Mat();
            srcImg.convertTo(dstImg, CvType.CV_16SC(channels));
            srcImg = dstImg;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), srcImg);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            delete(file);
            return false;
        } finally {
            ImageConversion.releaseMat(dstImg);
        }
    }

    public static boolean writeImage(RenderedImage source, File file) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try (ImageCV dstImg = ImageConversion.toMat(source)) {
            return Imgcodecs.imwrite(file.getPath(), dstImg);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
    }

    public static boolean writeImage(Mat source, File file, MatOfInt params) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try {
            return Imgcodecs.imwrite(file.getPath(), source, params);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Writing image", e); //$NON-NLS-1$
            delete(file);
            return false;
        }
    }

    public static ImageCV readImage(File file) {
        try {
            return readImageWithCvException(file);
        } catch (OutOfMemoryError | CvException e) {
            LOGGER.error("Reading image", e); //$NON-NLS-1$
            return null;
        }
    }

    public static ImageCV readImageWithCvException(File file) {
        if (!file.canRead()) {
            return null;
        }

        Mat img = Imgcodecs.imread(file.getPath());
        if (img.width() < 1 || img.height() < 1) {
            throw new CvException("OpenCV cannot read " + file.getPath());
        }
        return ImageCV.toImageCV(img);
    }

    private static boolean deleteFile(File fileOrDirectory) {
        try {
            Files.delete(fileOrDirectory.toPath());
        } catch (Exception e) {
            LOGGER.error("Cannot delete", e); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    private static boolean delete(File fileOrDirectory) {
        if (fileOrDirectory == null || !fileOrDirectory.exists()) {
            return false;
        }

        if (fileOrDirectory.isDirectory()) {
            final File[] files = fileOrDirectory.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }
        return deleteFile(fileOrDirectory);
    }

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
}
