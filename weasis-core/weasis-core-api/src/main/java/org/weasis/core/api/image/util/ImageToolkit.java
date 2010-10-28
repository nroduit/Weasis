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
package org.weasis.core.api.image.util;

import java.awt.RenderingHints;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.util.Vector;

import javax.media.jai.BorderExtenderConstant;
import javax.media.jai.Interpolation;
import javax.media.jai.JAI;
import javax.media.jai.LookupTableJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.weasis.core.api.Messages;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagElement;

/**
 * An image manipulation toolkit.
 * 
 */
public class ImageToolkit {

    public static final RenderingHints NOCACHE_HINT = new RenderingHints(JAI.KEY_TILE_CACHE, null);

    /**
     * Initialise JAI memory used by the Tile Cache.
     */
    public static void setJaiCacheMemoryCapacity(long tileCacheMB) {
        JAI.getDefaultInstance().getTileCache().setMemoryCapacity(tileCacheMB * 1024L * 1024L);
    }

    /**
     * Load an image.
     * 
     * NOTE: Encapsulate a mechanism to close properly the image (see closeLoadImageStream())
     */
    public static RenderedOp loadImage(File file) {
        return JAI.create("LoadImage", file); //$NON-NLS-1$
    }

    private static PlanarImage getFileloadOp(PlanarImage image) {
        return getImageOp(image, "LoadImage"); //$NON-NLS-1$
    }

    public static PlanarImage getImageOp(PlanarImage image, String opName) {
        if (image instanceof RenderedOp) {
            RenderedOp op = (RenderedOp) image;
            if (op.getOperationName().equalsIgnoreCase(opName)) {
                return image;
            }
            while (image.getNumSources() > 0) {
                try {
                    image = image.getSourceImage(0);
                    op = (RenderedOp) image;
                    if (op.getOperationName().equalsIgnoreCase(opName)) {
                        return image;
                    }
                } catch (Exception ex) {
                    return null;
                }
            }
        }
        return null;
    }

    // private static FileSeekableStream getImageStreamFromLoadImage(PlanarImage
    // image) {
    // image = getFileloadOp(image);
    // FileSeekableStream stream = null;
    // if (image instanceof RenderedOp) {
    // try {
    // RenderedOp img = (RenderedOp) ((RenderedOp) image).getRendering();
    // img = (RenderedOp) getImageOp(img, "stream");
    // stream = (FileSeekableStream)
    // img.getParameterBlock().getParameters().get(0);
    // }
    // catch (Exception ex) {
    // }
    // }
    // return stream;
    // }

    // public static void closeLoadImageStream(PlanarImage image) {
    // try {
    // FileSeekableStream stream = getImageStreamFromLoadImage(image);
    // if (stream != null) {
    // stream.close();
    // }
    // }
    // catch (IOException ex1) {
    // }
    // }

    private static File getFilePath(Vector ParameterBlock, String opName) {
        if (ParameterBlock != null && ParameterBlock.size() >= 1 && opName.equalsIgnoreCase("LoadImage")) { //$NON-NLS-1$
            try {
                return (File) (ParameterBlock.get(0));
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    public static File getFileNameOfSource(PlanarImage image) {
        if (image instanceof RenderedOp) {
            RenderedOp img = (RenderedOp) image;
            Vector sources = img.getSources();
            if (sources.size() == 0) {
                return getFilePath(img.getParameterBlock().getParameters(), img.getOperationName());
            } else if (sources.size() >= 1) {
                if (sources.get(0) instanceof PlanarImage) {
                    return getFileNameOfSource((PlanarImage) sources.get(0));
                }

            }
        }
        return null;
    }

    /**
     * Convert index color mapped image content to a full 24-bit 16-million color RGB image.
     * 
     * @param image
     *            the source image to convert.
     * @return a full RGB color image as RenderedOp.
     */
    public static PlanarImage convertIndexColorToRGBColor(PlanarImage image) {
        PlanarImage result = image;

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

        // RendingHints ... ??? scale quality ??? eg. asprin image

        RenderedOp result = JAI.create("scale", params); //$NON-NLS-1$
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

        if (width > toWidth || height > toHeight) {
            image = cropImage(image, Math.min(width, toWidth), Math.min(height, toHeight));
        }

        if (width < toWidth || height < toHeight) {
            int w = Math.max((toWidth - width) / 2, 0);
            int h = Math.max((toHeight - height) / 2, 0);

            image = borderImage(image, w, w, h, h, color);
        }

        return image;
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

        return JAI.create("crop", params); //$NON-NLS-1$
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

        double fill[] = { color };
        params.add(new BorderExtenderConstant(fill));// type
        params.add(color); // fill color

        return JAI.create("border", params); //$NON-NLS-1$
    }

    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source, float window,
        float level) {
        if (image == null || source == null) {
            return null;
        }
        RenderedImage result = null;
        SampleModel sampleModel = source.getSampleModel();
        if (sampleModel == null) {
            return null;
        }
        int datatype = sampleModel.getDataType();
        if (datatype == DataBuffer.TYPE_BYTE && window == 255.0f && level == 127.5f) {
            return source;
        }

        /**
         * In Dicom, Pixel data represent a single monochrome image plane. The minimum sample value is intended to be
         * displayed as white after any VOI gray scale transformations have been performed. See PS 3.4. This value may
         * be used only when Samples per Pixel (0028,0002) has a value of 1.
         */
        boolean monochrome1 =
            "monochrome1".equalsIgnoreCase((String) image.getTagValue(TagElement.PhotometricInterpretation)); //$NON-NLS-1$
        int minValue = (int) image.getPixelLevel(image.getMinValue());
        int maxValue = (int) image.getPixelLevel(image.getMaxValue());
        int tableLength = (maxValue - minValue + 1);

        double low = level - window / 2.0;
        double high = level + window / 2.0;
        // use a lookup table for rescaling
        double range = high - low;
        if (range < 1.0) {
            range = 1.0;
        }

        double slope = 255.0 / range;
        double y_int = 255.0 - slope * high;

        if (datatype >= DataBuffer.TYPE_BYTE && datatype < DataBuffer.TYPE_INT) {
            // Integer paddingValue = (Integer) image.getTagValue(TagElement.PixelPaddingValue);
            // Integer paddingLimit = (Integer) image.getTagValue(TagElement.PixelPaddingRangeLimit);
            byte[][] lut;
            // if (paddingValue != null && paddingValue > 0) {
            // if (paddingLimit == 0) {
            // paddingLimit = paddingValue;
            // }
            // else if (paddingLimit < paddingValue) {
            // int temp = paddingValue;
            // paddingValue = paddingLimit;
            // paddingLimit = temp;
            // }
            // }
            // if (paddingLimit != null && paddingLimit > 0 && paddingValue >= tableLength) {
            // lut = new byte[1][paddingLimit + 1];
            // }
            // else {
            lut = new byte[1][tableLength];
            // }

            for (int i = 0; i < tableLength; i++) {
                int value = (int) (slope * (i + minValue) + y_int);

                if (monochrome1) {
                    value = 255 - value;
                }
                if (value > 255) {
                    value = 255;
                }
                if (value < 0) {
                    value = 0;
                }
                lut[0][i] = (byte) value;
            }

            // if (paddingLimit != null && paddingLimit > 0) {
            // for (int i = paddingValue; i < paddingLimit + 1; i++) {
            // lut[0][paddingValue] = 0;
            // }
            // }

            LookupTableJAI lookup = new LookupTableJAI(lut, minValue);

            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(lookup);
            // Will add tiles in cache tile memory
            result = JAI.create("lookup", pb, null); // hints); //$NON-NLS-1$
        } else if (datatype == DataBuffer.TYPE_INT || datatype == DataBuffer.TYPE_FLOAT
            || datatype == DataBuffer.TYPE_DOUBLE) {
            ParameterBlock pb = new ParameterBlock();
            pb.addSource(source);
            pb.add(slope);
            pb.add(y_int);
            result = JAI.create("rescale", pb, null); //$NON-NLS-1$

            // produce a byte image
            pb = new ParameterBlock();
            pb.addSource(result);
            pb.add(DataBuffer.TYPE_BYTE);
            result = JAI.create("format", pb, null); //$NON-NLS-1$
        }

        return result;
    }

    public static RenderedImage getDefaultRenderedImage(ImageElement image, RenderedImage source) {
        float window = image.getPixelWindow(image.getDefaultWindow());
        float level = image.getPixelLevel(image.getDefaultLevel());
        return getDefaultRenderedImage(image, source, window, level);
    }
}
