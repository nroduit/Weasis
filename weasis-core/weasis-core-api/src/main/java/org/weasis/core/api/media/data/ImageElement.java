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
package org.weasis.core.api.media.data;

import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.lang.ref.Reference;
import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageToolkit;
import org.weasis.core.api.image.util.Unit;

public class ImageElement extends MediaElement<PlanarImage> {

    /**
     * Logger for this class
     */
    private static final Logger logger = LoggerFactory.getLogger(ImageElement.class);
    /*
     * Imageio issue with native library in multi-thread environment (to avoid JVM crash let only one simultaneous
     * thread) (https://jai-imageio-core.dev.java.net/issues/show_bug.cgi?id=126)
     */
    public static final ExecutorService IMAGE_LOADER = Executors.newFixedThreadPool(1);

    private static final SoftHashMap<ImageElement, PlanarImage> mCache = new SoftHashMap<ImageElement, PlanarImage>() {

        public Reference<? extends PlanarImage> getReference(ImageElement key) {
            return hash.get(key);
        }

        @Override
        public void removeElement(Reference<? extends PlanarImage> soft) {
            ImageElement key = reverseLookup.remove(soft);
            if (key != null) {
                hash.remove(key);
                MediaReader<PlanarImage> reader = key.getMediaReader();
                key.setTag(TagW.ImageCache, false);
                if (reader != null) {
                    // Close the image stream
                    reader.close();
                }
            }
        }
    };
    protected boolean readable = true;

    protected double pixelSizeX = 1.0;
    protected double pixelSizeY = 1.0;
    protected Unit pixelSpacingUnit = Unit.PIXEL;
    protected String pixelSizeCalibrationDescription = null;
    protected String pixelValueUnit = null;

    protected Float minPixelValue;
    protected Float maxPixelValue;

    public ImageElement(MediaReader<PlanarImage> mediaIO, Object key) {
        super(mediaIO, key);
    }

    protected void findMinMaxValues(RenderedImage img, boolean exclude8bitImage) throws OutOfMemoryError {
        // This function can be called several times from the inner class Load.
        // Do not compute min and max it has already be done

        if (img != null && !isImageAvailable()) {

            int datatype = img.getSampleModel().getDataType();
            if (datatype == DataBuffer.TYPE_BYTE && exclude8bitImage) {
                this.minPixelValue = 0f;
                this.maxPixelValue = 255f;
            } else {

                ParameterBlock pb = new ParameterBlock();
                pb.addSource(img);
                // ImageToolkit.NOCACHE_HINT to ensure this image won't be stored in tile cache
                RenderedOp dst = JAI.create("extrema", pb, ImageToolkit.NOCACHE_HINT); //$NON-NLS-1$

                double[][] extrema = (double[][]) dst.getProperty("extrema"); //$NON-NLS-1$
                double min = Double.MAX_VALUE;
                double max = -Double.MAX_VALUE;
                int numBands = dst.getSampleModel().getNumBands();

                for (int i = 0; i < numBands; i++) {
                    min = Math.min(min, extrema[0][i]);
                    max = Math.max(max, extrema[1][i]);
                }
                this.minPixelValue = Double.valueOf(min).floatValue();
                this.maxPixelValue = Double.valueOf(max).floatValue();
                // Handle special case when min and max are equal, ex. black image
                // + 1 to max enables to display the correct value
                if (this.minPixelValue.equals(this.maxPixelValue)) {
                    this.maxPixelValue += 1.0f;
                }
            }
        }
    }

    public boolean isImageAvailable() {
        return maxPixelValue != null && minPixelValue != null;
    }

    protected boolean isGrayImage(RenderedImage source) {
        // Binary images have indexColorModel
        if (source.getSampleModel().getNumBands() > 1 || source.getColorModel() instanceof IndexColorModel) {
            return false;
        }
        return true;
    }

    public LutShape getDefaultShape(boolean pixelPadding) {
        return LutShape.LINEAR;
    }

    public float getDefaultWindow(boolean pixelPadding) {
        return getMaxValue(pixelPadding) - getMinValue(pixelPadding);
    }

    public float getDefaultLevel(boolean pixelPadding) {
        if (isImageAvailable()) {
            float min = getMinValue(pixelPadding);
            return min + (getMaxValue(pixelPadding) - min) / 2.f;
        }
        return 0.0f;
    }

    public float getMaxValue(boolean pixelPadding) {
        return maxPixelValue == null ? 0.0f : maxPixelValue;
    }

    public float getMinValue(boolean pixelPadding) {
        return minPixelValue == null ? 0.0f : minPixelValue;
    }

    public int getRescaleWidth(int width) {
        return (int) Math.ceil(width * getRescaleX() - 0.5);
    }

    public int getRescaleHeight(int height) {
        return (int) Math.ceil(height * getRescaleY() - 0.5);
    }

    public double getRescaleX() {
        return pixelSizeX <= pixelSizeY ? 1.0 : pixelSizeX / pixelSizeY;
    }

    public double getRescaleY() {
        return pixelSizeY <= pixelSizeX ? 1.0 : pixelSizeY / pixelSizeX;
    }

    public double getPixelSize() {
        return pixelSizeX <= pixelSizeY ? pixelSizeX : pixelSizeY;
    }

    public void setPixelSize(double pixelSize) {
        if (pixelSizeX == pixelSizeY) {
            setPixelSize(pixelSize, pixelSize);
        } else if (pixelSizeX < pixelSizeY) {
            setPixelSize(pixelSize, (pixelSizeY / pixelSizeX) * pixelSize);
        } else {
            setPixelSize((pixelSizeX / pixelSizeY) * pixelSize, pixelSize);
        }
    }

    public void setPixelSize(double pixelSizeX, double pixelSizeY) {
        /*
         * Image is always displayed with a 1/1 aspect ratio, otherwise it becomes very difficult (even impossible) to
         * handle measurement tools. When the ratio is not 1/1, the image is stretched. The smallest ratio keeps the
         * pixel size and the largest one is downscaled.
         */
        this.pixelSizeX = pixelSizeX <= 0.0 ? 1.0 : pixelSizeX;
        this.pixelSizeY = pixelSizeY <= 0.0 ? 1.0 : pixelSizeY;
    }

    public void setPixelValueUnit(String pixelValueUnit) {
        this.pixelValueUnit = pixelValueUnit;
    }

    public Unit getPixelSpacingUnit() {
        return pixelSpacingUnit;
    }

    public void setPixelSpacingUnit(Unit pixelSpacingUnit) {
        this.pixelSpacingUnit = pixelSpacingUnit;
    }

    public String getPixelValueUnit() {
        return pixelValueUnit;
    }

    public String getPixelSizeCalibrationDescription() {
        return pixelSizeCalibrationDescription;
    }

    public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
        Unit unit = displayUnit;
        if (unit == null || pixelSpacingUnit == null || pixelSpacingUnit.equals(Unit.PIXEL)) {
            unit = Unit.PIXEL;
        }

        double unitRatio;
        if (unit.equals(Unit.PIXEL)) {
            unitRatio = 1.0;
        } else {
            unitRatio = getPixelSize() * unit.getConversionRatio(pixelSpacingUnit.getConvFactor());
        }
        return new MeasurementsAdapter(unitRatio, 0, 0, false, 0, unit.getAbbreviation());
    }

    public boolean isImageInCache() {
        return mCache.get(this) != null;
    }

    public void removeImageFromCache() {
        mCache.remove(this);
        MediaReader<PlanarImage> reader = this.getMediaReader();
        this.setTag(TagW.ImageCache, false);
        if (reader != null) {
            // Close the image stream
            reader.close();
        }
    }

    public boolean hasSameSize(ImageElement image) {
        if (image != null) {
            PlanarImage img = getImage();
            PlanarImage img2 = image.getImage();
            if (img != null && img2 != null) {
                if (getRescaleWidth(img.getWidth()) == image.getRescaleWidth(img2.getWidth())
                    && getRescaleHeight(img.getHeight()) == image.getRescaleHeight(img2.getHeight())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Loads the original image. Must load and return the original image.
     *
     * @throws Exception
     *
     * @throws IOException
     */

    protected PlanarImage loadImage() throws Exception {
        return mediaIO.getMediaFragment(this);
    }

    /**
     * @param imageSource
     *            is the RenderedImage upon which transformation is done
     * @param window
     *            is width from low to high input values around level. If null, getDefaultWindow() value is used
     * @param level
     *            is center of window values. If null, getDefaultLevel() value is used
     * @param pixelPadding
     *            indicates if some padding values defined in ImageElement should be applied or not. If null, TRUE is
     *            considered
     * @return
     */

    protected RenderedImage getRenderedImage(final RenderedImage imageSource, Float window, Float level,
        Boolean pixelPadding) {

        if (imageSource == null) {
            return null;
        }

        pixelPadding = (pixelPadding == null) ? true : pixelPadding;
        window = (window == null) ? getDefaultWindow(pixelPadding) : window;
        level = (level == null) ? getDefaultLevel(pixelPadding) : level;

        return ImageToolkit.getDefaultRenderedImage(this, imageSource, window, level, pixelPadding);
    }

    public RenderedImage getRenderedImage(final RenderedImage imageSource) {
        return getRenderedImage(imageSource, null);
    }

    public RenderedImage getRenderedImage(final RenderedImage imageSource, HashMap<String, Object> params) {

        Float window = (params == null) ? null : (Float) params.get(ActionW.WINDOW.cmd());
        Float level = (params == null) ? null : (Float) params.get(ActionW.LEVEL.cmd());
        Boolean pixelPadding = (params == null) ? null : (Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd());

        return getRenderedImage(imageSource, window, level, pixelPadding);
    }

    /**
     * Returns the full size, original image. Returns null if the image is not loaded.
     *
     * @return
     */
    public PlanarImage getImage(OpManager manager) {
        return getImage(manager, true);
    }

    public synchronized PlanarImage getImage(OpManager manager, boolean findMinMax) {
        PlanarImage cacheImage;
        try {
            cacheImage = startImageLoading();
            if (findMinMax) {
                findMinMaxValues(cacheImage, true);
            }
        } catch (OutOfMemoryError e1) {
            /*
             * Appends when loading a big image without tiling, the memory left is not enough for the renderedop (like
             * Extrema)
             */
            logger.warn("Out of MemoryError: {}", getMediaURI()); //$NON-NLS-1$
            mCache.expungeStaleEntries();
            System.gc();
            try {
                Thread.sleep(100);
            } catch (InterruptedException et) {
            }
            cacheImage = startImageLoading();
            if (findMinMax) {
                findMinMaxValues(cacheImage, true);
            }
        }
        if (manager != null && cacheImage != null) {
            manager.setFirstNode(cacheImage);
            RenderedImage img = manager.process();
            if (img != null) {
                cacheImage = PlanarImage.wrapRenderedImage(img);
            }
        }
        return cacheImage;
    }

    public PlanarImage getImage() {
        return getImage(null);
    }

    private PlanarImage startImageLoading() throws OutOfMemoryError {
        PlanarImage cacheImage;
        if ((cacheImage = mCache.get(this)) == null && readable && setAsLoading()) {
            logger.debug("Asking for reading image: {}", this); //$NON-NLS-1$
            Load ref = new Load();
            Future<PlanarImage> future = IMAGE_LOADER.submit(ref);
            PlanarImage img = null;
            try {
                img = future.get();

            } catch (InterruptedException e) {
                // Re-assert the thread's interrupted status
                Thread.currentThread().interrupt();
                // We don't need the result, so cancel the task too
                future.cancel(true);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof OutOfMemoryError) {
                    setAsLoaded();
                    throw new OutOfMemoryError();
                } else {
                    readable = false;
                    logger.error("Cannot read pixel data!: {}", getMediaURI()); //$NON-NLS-1$
                    e.printStackTrace();
                }
            }
            if (img != null) {
                readable = true;
                mCache.put(this, img);
                cacheImage = img;
                this.setTag(TagW.ImageCache, true);
            }
            setAsLoaded();
        }
        return cacheImage;
    }

    // private void freeMemory() {
    // Entry<K, V> eldest = mCache..after;
    // if (removeEldestEntry(eldest)) {
    // removeEntryForKey(eldest.key);
    // }
    // }

    public boolean isReadable() {
        return readable;
    }

    @Override
    public void dispose() {
        // TODO find a clue to not dispose the display image
        // Or do nothing, let the soft reference mechanism do its job

        // Close image reader and image stream, but it should be already closed
        if (mediaIO != null) {
            mediaIO.close();
        }

        // // Unload image from memory
        // PlanarImage temp = mCache.remove(this);
        // if (temp != null) {
        // temp.dispose();
        //
        // }
        // if (image != null) {
        // PlanarImage temp = image.get();
        // if (temp != null) {
        // temp.dispose();
        // }
        // // image = null;
        // }
    }

    class Load implements Callable<PlanarImage> {

        @Override
        public PlanarImage call() throws Exception {
            return loadImage();
        }
    }

}
