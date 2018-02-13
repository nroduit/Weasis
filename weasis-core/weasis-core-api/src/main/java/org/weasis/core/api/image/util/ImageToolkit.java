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

import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.util.Hashtable;

import javax.media.jai.BorderExtenderConstant;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.media.data.ImageElement;

/**
 * An image manipulation toolkit.
 *
 */
public class ImageToolkit {
    public static final RenderingHints NOCACHE_HINT = new RenderingHints(JAI.KEY_TILE_CACHE, null);

    private ImageToolkit() {
    }

    /**
     * Load an image.
     *
     * NOTE: Encapsulate a mechanism to close properly the image (see closeLoadImageStream())
     */
    public static RenderedOp loadImage(File file) {
        return JAI.create("LoadImage", file); //$NON-NLS-1$
    }

    public static RenderedImage getImageOp(RenderedImage image, String opName) {
        if (image instanceof RenderedOp) {
            RenderedOp op = (RenderedOp) image;
            if (op.getOperationName().equalsIgnoreCase(opName)) {
                return image;
            }
            while (op.getNumSources() > 0) {
                try {
                    PlanarImage img = op.getSourceImage(0);
                    if (image instanceof RenderedOp) {
                        RenderedOp op2 = (RenderedOp) img;
                        if (op2.getOperationName().equalsIgnoreCase(opName)) {
                            return img;
                        }
                    }
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    public static BufferedImage convertRenderedImage(RenderedImage img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        ColorModel cm = img.getColorModel();
        int width = img.getWidth();
        int height = img.getHeight();
        WritableRaster raster = cm.createCompatibleWritableRaster(width, height);
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        Hashtable<String, Object> properties = new Hashtable<>();
        String[] keys = img.getPropertyNames();
        if (keys != null) {
            for (int i = 0; i < keys.length; i++) {
                properties.put(keys[i], img.getProperty(keys[i]));
            }
        }
        BufferedImage result = new BufferedImage(cm, raster, isAlphaPremultiplied, properties);
        img.copyData(raster);
        return result;
    }

    /**
     * Convert index color mapped image content to a full 24-bit 16-million color RGB image.
     *
     * @param image
     *            the source image to convert.
     * @return a full RGB color image as RenderedOp.
     */
    public static RenderedImage convertIndexColorToRGBColor(RenderedImage image) {
        RenderedImage result = image;

        // If the source image is color mapped, convert it to 3-band RGB.
        // Note that GIF and PNG files fall into this category.
        if (image.getColorModel() instanceof IndexColorModel) {
            // Retrieve the IndexColorModel
            IndexColorModel icm = (IndexColorModel) image.getColorModel();

            // Cache the number of elements in each band of the colormap.
            int mapSize = icm.getMapSize();

            // Allocate an array for the lookup table data.
            byte[][] lutData = new byte[3][mapSize];

            // Load the lookup table data from the IndexColorModel.
            icm.getReds(lutData[0]);
            icm.getGreens(lutData[1]);
            icm.getBlues(lutData[2]);

            // Create the lookup table object.
            LookupTableJAI lut = new LookupTableJAI(lutData);

            // Replace the original image with the 3-band RGB image.
            result = JAI.create("lookup", image, lut); //$NON-NLS-1$
        }

        return result;
    }

    /**
     * Scale an image up/down to the desired width and height. The aspect ratio of the image will not be maintained.
     *
     * @param image
     *            the source image to scale
     * @param scaleWidth
     *            the new width to scale to
     * @param scaleHeight
     *            the new height to scale to
     * @return a scaled image as RenderedOp
     * @see #scaleImage(RenderedOp, int, int, boolean, double)
     */
    public static RenderedOp scaleImage(RenderedOp image, int scaleWidth, int scaleHeight) {
        return scaleImage(image, scaleWidth, scaleHeight, false, 0);
    }

    /**
     * Scale an image up/down to the desired width and height, while maintaining the image's aspect ratio (if
     * requested).
     *
     * @param image
     *            the source image to scale
     * @param scaleWidth
     *            the new width to scale to
     * @param scaleHeight
     *            the new height to scale to
     * @param keepAspect
     *            true if the aspect ratio should be maintained.
     * @param color
     *            the color to fill borders when maintaining aspect ratio (0 = black, 255 = white).
     * @return a scaled image as RenderedOp
     * @see #scaleImage(RenderedOp, int, int)
     */
    public static RenderedOp scaleImage(RenderedOp image, int scaleWidth, int scaleHeight, boolean keepAspect,
        double color) {
        float xScale = (float) scaleWidth / (float) image.getWidth();
        float yScale = (float) scaleHeight / (float) image.getHeight();
        boolean resize = false;

        if (keepAspect) {
            resize = Math.abs(xScale - yScale) < .0000001;
            xScale = Math.min(xScale, yScale);
            yScale = xScale;
        }

        ParameterBlock params = new ParameterBlock();
        params.addSource(image);

        params.add(xScale); // x scale factor
        params.add(yScale); // y scale factor
        params.add(0.0F); // x translate
        params.add(0.0F); // y translate
        params.add(Interpolation.getInstance(Interpolation.INTERP_BICUBIC));

        RenderedOp result = JAI.create("scale", params, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$
        if (resize) {
            result = resizeImage(result, scaleWidth, scaleHeight, color);
        }

        return result;
    }

    /**
     * Resize an image to the new dimensions - no scaling is performed on the image, but the canvas size is changed. Any
     * empty areas are filled with white.
     *
     * @param image
     *            the source image to resize
     * @param toWidth
     *            the new width to resize to
     * @param toHeight
     *            the new height to resize to
     * @param color
     *            the color to fill borders when resizing up.
     * @return the resized image as RenderedOp
     */
    public static RenderedOp resizeImage(RenderedOp image, int toWidth, int toHeight, double color) {
        int width = image.getWidth();
        int height = image.getHeight();

        RenderedOp resImage = image;
        if (width > toWidth || height > toHeight) {
            resImage = cropImage(resImage, Math.min(width, toWidth), Math.min(height, toHeight));
        }

        if (width < toWidth || height < toHeight) {
            int w = Math.max((toWidth - width) / 2, 0);
            int h = Math.max((toHeight - height) / 2, 0);

            resImage = borderImage(resImage, w, w, h, h, color);
        }

        return resImage;
    }

    /**
     * Crop down an image to smaller dimensions. Used by resizeImage() when an image dimension is smaller.
     *
     * @param image
     *            the source image to crop
     * @param toWidth
     *            the new width to crop to
     * @param toHeight
     *            the new height to crop to
     * @return a cropped image as RenderedOp
     * @see #resizeImage(RenderedOp, int, int)
     */
    public static RenderedOp cropImage(RenderedOp image, int toWidth, int toHeight) {
        int width = image.getWidth();
        int height = image.getHeight();
        int xOffset = (width - toWidth) / 2;
        int yOffset = (height - toHeight) / 2;
        ParameterBlock params = new ParameterBlock();
        params.addSource(image);

        params.add((float) xOffset); // x origin
        params.add((float) yOffset); // y origin
        params.add((float) toWidth); // width
        params.add((float) toHeight); // height

        return JAI.create("crop", params, null); //$NON-NLS-1$
    }

    /**
     * Add a colored border edge around an image, with the margin defined as left, right, top and bottom. Used by
     * resizeImage() when an image dimension is larger.
     *
     * @param image
     *            the source image to add borders to
     * @param left
     *            the left edge border width
     * @param right
     *            the right edge border width
     * @param top
     *            the top edge border height
     * @param btm
     *            the bottom edge border height
     * @param color
     *            the color to use for the border (0 = black, 255 = white)
     * @return a bordered image as RenderedOp
     * @see #resizeImage(RenderedOp, int, int)
     */
    public static RenderedOp borderImage(RenderedOp image, int left, int right, int top, int btm, double color) {
        ParameterBlock params = new ParameterBlock();
        params.addSource(image);

        params.add(left); // left pad
        params.add(right); // right pad
        params.add(top); // top pad
        params.add(btm); // bottom pad

        double[] fill = { color };
        params.add(new BorderExtenderConstant(fill));// type
        params.add(color); // fill color

        return JAI.create("border", params, null); //$NON-NLS-1$
    }

    /**
     * Apply window/level to the image source. Note: this method cannot be used with a DicomImageElement as image
     * parameter.
     *
     * @param image
     * @param source
     * @param window
     * @param level
     * @param pixelPadding
     * @return
     */
    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source, double window,
        double level, boolean pixelPadding) {
        if (image == null || source == null) {
            return null;
        }
        RenderedImage result = null;
        SampleModel sampleModel = source.getSampleModel();
        if (sampleModel == null) {
            return null;
        }
        int datatype = sampleModel.getDataType();
        if (datatype == DataBuffer.TYPE_BYTE && MathUtil.isEqual(window, 255.0)
            && (MathUtil.isEqual(level, 127.5) || MathUtil.isEqual(level, 127.0))) {
            return source;
        }

        // Get pixel values of Min and Max (values must not be rescaled rescaled, works only for ImageElement not for
        // DicomImageElement class)
        int minValue = (int) image.getMinValue(null, pixelPadding);
        int maxValue = (int) image.getMaxValue(null, pixelPadding);
        int tableLength = maxValue - minValue + 1;

        double low = level - window / 2.0;
        double high = level + window / 2.0;
        // use a lookup table for rescaling
        double range = high - low;
        if (range < 1.0) {
            range = 1.0;
        }

        double slope = 255.0 / range;
        double yInt = 255.0 - slope * high;

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            byte[][] lut = new byte[1][tableLength];

            for (int i = 0; i < tableLength; i++) {
                int value = (int) (slope * (i + minValue) + yInt);

                if (value > 255) {
                    value = 255;
                }
                if (value < 0) {
                    value = 0;
                }
                lut[0][i] = (byte) value;
            }

            LookupTableJAI lookup = new LookupTableJAI(lut, minValue);

            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(lookup);
            result = JAI.create("lookup", pb, null); // hints); //$NON-NLS-1$
        } else if (datatype == DataBuffer.TYPE_INT || datatype == DataBuffer.TYPE_FLOAT
            || datatype == DataBuffer.TYPE_DOUBLE) {
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(new double[] { slope });
            pb.add(new double[] { yInt });
            result = JAI.create("rescale", pb, null); //$NON-NLS-1$

            // produce a byte image
            pb = new ParameterBlock();
            pb.addSource(result);
            pb.add(DataBuffer.TYPE_BYTE);
            result = JAI.create("format", pb, null); //$NON-NLS-1$
        }

        return result;
    }

    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source,
        boolean pixelPadding) {
        return getDefaultRenderedImage(image, source, image.getDefaultWindow(pixelPadding),
            image.getDefaultLevel(pixelPadding), true);
    }
}
