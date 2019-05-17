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
package org.weasis.core.api.media.data;

import java.awt.Point;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import org.opencv.core.Core.MinMaxLocResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.opencv.data.LookupTableCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class ImageElement extends MediaElement {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageElement.class);

    public static final ExecutorService IMAGE_LOADER = ThreadUtil.buildNewSingleThreadExecutor("Image Loader"); //$NON-NLS-1$

    private static final NativeCache<ImageElement, PlanarImage> mCache =
        new NativeCache<ImageElement, PlanarImage>(Runtime.getRuntime().maxMemory() / 2) {

            @Override
            protected void afterEntryRemove(ImageElement key, PlanarImage img) {
                if (key != null) {
                    key.setTag(TagW.ImageCache, false);
                    MediaReader reader = key.getMediaReader();
                    if (reader != null) {
                        // Close the image stream
                        reader.close();
                    }
                }
                if (img != null) {
                    img.release();
                }
            }
        };
 
    protected boolean readable = true;

    protected double pixelSizeX = 1.0;
    protected double pixelSizeY = 1.0;
    protected Unit pixelSpacingUnit = Unit.PIXEL;
    protected String pixelSizeCalibrationDescription = null;
    protected String pixelValueUnit = null;

    protected Double minPixelValue;
    protected Double maxPixelValue;

    public ImageElement(MediaReader mediaIO, Object key) {
        super(mediaIO, key);
    }

    public static int getNumberOfImagesInCache() {
        return mCache.size();
    }

    protected void findMinMaxValues(PlanarImage img, boolean exclude8bitImage) throws OutOfMemoryError {
        // This function can be called several times from the inner class Load.
        // Do not compute min and max it has already be done

        if (img != null && !isImageAvailable()) {

            if (ImageConversion.convertToDataType(img.type()) == DataBuffer.TYPE_BYTE && exclude8bitImage) {
                this.minPixelValue = 0.0;
                this.maxPixelValue = 255.0;
            } else {
                MinMaxLocResult val = ImageProcessor.findMinMaxValues(img.toMat());
                if (val != null) {
                    this.minPixelValue = val.minVal;
                    this.maxPixelValue = val.maxVal;
                }

                // Handle special case when min and max are equal, ex. black image
                // + 1 to max enables to display the correct value
                if (this.minPixelValue.equals(this.maxPixelValue)) {
                    this.maxPixelValue += 1.0;
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

    public double getDefaultWindow(boolean pixelPadding) {
        return getMaxValue(null, pixelPadding) - getMinValue(null, pixelPadding);
    }

    public double getDefaultLevel(boolean pixelPadding) {
        if (isImageAvailable()) {
            double min = getMinValue(null, pixelPadding);
            return min + (getMaxValue(null, pixelPadding) - min) / 2.0;
        }
        return 0.0f;
    }

    public double getMaxValue(TagReadable tagable, boolean pixelPadding) {
        return maxPixelValue == null ? 0.0 : maxPixelValue;
    }

    public double getMinValue(TagReadable tagable, boolean pixelPadding) {
        return minPixelValue == null ? 0.0 : minPixelValue;
    }
    
    public double getPixelMax() {
        return maxPixelValue == null ? 0.0 : maxPixelValue;
    }

    public double getPixelMin() {
        return minPixelValue == null ? 0.0 : minPixelValue;
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
        if (MathUtil.isEqual(pixelSizeX, pixelSizeY)) {
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
    
    public ZoomOp getRectifyAspectRatioZoomOp() {
        // Rectify non square pixel image in the first operation
        if (MathUtil.isDifferent(pixelSizeX, pixelSizeY)) {     
            ZoomOp node = new ZoomOp();
            node.setName("rectifyAspectRatio"); //$NON-NLS-1$
            node.setParam(ZoomOp.P_RATIO_X, getRescaleX());
            node.setParam(ZoomOp.P_RATIO_Y, getRescaleY());
            return node;
        }
        return null;
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
    
    public Number pixelToRealValue(Number pixelValue, TagReadable tagable, boolean pixelPadding) {
        return pixelValue;
    }
    
    public LookupTableCV getVOILookup(TagReadable tagable, Double window, Double level, Double minLevel,
        Double maxLevel, LutShape shape, boolean fillLutOutside, boolean pixelPadding) {
            return null;
    }

    public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit, Point offset) {
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
        int offsetx = offset == null ? 0 : -offset.x;
        int offsety = offset == null ? 0 : -offset.y;
        return new MeasurementsAdapter(unitRatio, offsetx, offsety, false, 0, unit.getAbbreviation());
    }

    public boolean isImageInCache() {
        return mCache.get(this) != null;
    }

    public void removeImageFromCache() {
        mCache.remove(this);
    }

    public boolean hasSameSize(ImageElement image) {
        if (image != null) {
            PlanarImage img = getImage();
            PlanarImage img2 = image.getImage();
            if (img != null && img2 != null && getRescaleWidth(img.width()) == image.getRescaleWidth(img2.width())
                && getRescaleHeight(img.height()) == image.getRescaleHeight(img2.height())) {
                return true;
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
        return mediaIO.getImageFragment(this);
    }

    public PlanarImage getRenderedImage(final PlanarImage imageSource) {
        return getRenderedImage(imageSource, null);
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
    public PlanarImage getRenderedImage(final PlanarImage imageSource, Map<String, Object> params) {
        if (imageSource == null) {
            return null;
        }

        Double window = (params == null) ? null : (Double) params.get(ActionW.WINDOW.cmd());
        Double level = (params == null) ? null : (Double) params.get(ActionW.LEVEL.cmd());
        Boolean pixelPadding = (params == null) ? null : (Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd());

        pixelPadding = (pixelPadding == null) ? Boolean.TRUE : pixelPadding;
        window = (window == null) ? getDefaultWindow(pixelPadding) : window;
        level = (level == null) ? getDefaultLevel(pixelPadding) : level;

        return getDefaultRenderedImage(this, imageSource, window, level, pixelPadding);
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
    public static PlanarImage getDefaultRenderedImage(ImageElement image, PlanarImage source, double window,
        double level, boolean pixelPadding) {
        if (image == null || source == null) {
            return null;
        }

        if (ImageConversion.convertToDataType(source.type()) == DataBuffer.TYPE_BYTE && MathUtil.isEqual(window, 255.0)
            && (MathUtil.isEqual(level, 127.5) || MathUtil.isEqual(level, 127.0))) {
            return source;
        }

        double low = level - window / 2.0;
        double high = level + window / 2.0;
        // use a lookup table for rescaling
        double range = high - low;
        if (range < 1.0) {
            range = 1.0;
        }

        double slope = 255.0 / range;
        double yInt = 255.0 - slope * high;

        return ImageProcessor.rescaleToByte(source.toMat(), slope, yInt);

    }

    public static PlanarImage getDefaultRenderedImage(ImageElement image, PlanarImage source, boolean pixelPadding) {
        return getDefaultRenderedImage(image, source, image.getDefaultWindow(pixelPadding),
            image.getDefaultLevel(pixelPadding), true);
    }

    /**
     * Returns the full size, original image. Returns null if the image is not loaded.
     *
     * @return
     */
    public PlanarImage getImage(OpManager manager) {
        return getImage(manager, true);
    }

    @Override
    public String toString() {
        return getMediaURI().toString();
    }

    public synchronized PlanarImage getImage(OpManager manager, boolean findMinMax) {
        try {
            return getCacheImage(startImageLoading(), manager, findMinMax);
        } catch (OutOfMemoryError e1) {
            LOGGER.warn("Out of MemoryError: {}", this, e1); //$NON-NLS-1$
            
            mCache.expungeStaleEntries();
            CvUtil.runGarbageCollectorAndWait(100);

            return getCacheImage(startImageLoading(), manager, findMinMax);
        }
    }

    private PlanarImage getCacheImage(PlanarImage cacheImage, OpManager manager, boolean findMinMax) {
        if (findMinMax) {
            try {
                findMinMaxValues(cacheImage, true);
            } catch (Exception e) {
                mCache.remove(this);
                readable = false;
                LOGGER.error("Cannot read image: {}", this, e); //$NON-NLS-1$
            }
        }
        if (manager != null && cacheImage != null) {
            PlanarImage img = manager.getLastNodeOutputImage();
            if (manager.getFirstNodeInputImage() != cacheImage || manager.needProcessing()) {
                manager.setFirstNode(cacheImage);
                img = manager.process();
            }

            if (img != null) {
                return img;
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
            LOGGER.debug("Asking for reading image: {}", this); //$NON-NLS-1$
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
                    throw (OutOfMemoryError) e.getCause();
                } else {
                    readable = false;
                    LOGGER.error("Cannot read pixel data!: {}", this, e); //$NON-NLS-1$
                }
            }
            if (img != null) {
                readable = img.width() > 0;
                if (readable) {
                    mCache.put(this, img);
                    cacheImage = img;
                    this.setTag(TagW.ImageCache, true);
                }
            }
            setAsLoaded();
        }
        return cacheImage;
    }

    public boolean isReadable() {
        return readable;
    }

    @Override
    public void dispose() {
        // Let the soft reference mechanism dispose the display image
        super.dispose();
    }

    class Load implements Callable<PlanarImage> {

        @Override
        public PlanarImage call() throws Exception {
            return loadImage();
        }
    }

}
