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
package org.weasis.core.ui.model.layer.imp;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.OpEventListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;

/**
 * The Class RenderedImageLayer.
 *
 * @author Nicolas Roduit
 */
public class RenderedImageLayer<E extends ImageElement> extends DefaultUUID implements Layer, ImageLayer<E> {
    private static final long serialVersionUID = -7071485066284475687L;

    private static final Logger LOGGER = LoggerFactory.getLogger(RenderedImageLayer.class);

    private final SimpleOpManager disOpManager;
    private final List<ImageLayerChangeListener<E>> listenerList;
    private final List<OpEventListener> opListeners;

    private OpManager preprocessing;
    private E sourceImage;
    private RandomIter readIterator;
    private boolean buildIterator = false;
    private RenderedImage displayImage;
    private Boolean visible = true;
    private boolean enableDispOperations = true;
    private Point offset;

    public RenderedImageLayer(boolean buildIterator) {
        this(null, buildIterator);
    }

    public RenderedImageLayer(SimpleOpManager disOpManager, boolean buildIterator) {
        this.disOpManager = Optional.ofNullable(disOpManager).orElseGet(SimpleOpManager::new);
        this.listenerList = new ArrayList<>();
        this.opListeners = new ArrayList<>();
        this.buildIterator = buildIterator;
        addEventListener(this.disOpManager);
    }

    public boolean isBuildIterator() {
        return buildIterator;
    }

    public void setBuildIterator(boolean buildIterator) {
        this.buildIterator = buildIterator;
    }

    @Override
    public RandomIter getReadIterator() {
        return readIterator;
    }

    @Override
    public E getSourceImage() {
        return sourceImage;
    }

    @Override
    public RenderedImage getSourceRenderedImage() {
        if (sourceImage != null) {
            return sourceImage.getImage(preprocessing);
        }
        return null;
    }

    @Override
    public RenderedImage getDisplayImage() {
        return displayImage;
    }

    public OpManager getPreprocessing() {
        return preprocessing;
    }

    public void setPreprocessing(OpManager preprocessing) {
        setImage(sourceImage, preprocessing);
    }

    @Override
    public SimpleOpManager getDisplayOpManager() {
        return disOpManager;
    }

    @Override
    public synchronized boolean isEnableDispOperations() {
        return enableDispOperations;
    }

    @Override
    public synchronized void setEnableDispOperations(boolean enabled) {
        this.enableDispOperations = enabled;
        if (enabled) {
            updateDisplayOperations();
        }
    }

    @Override
    public void setVisible(Boolean visible) {
        this.visible = Optional.ofNullable(visible).orElse(getType().getVisible());
    }

    @Override
    public Boolean getVisible() {
        return visible;
    }

    @Override
    public void setType(LayerType type) {
        // Do nothing
    }

    @Override
    public void setName(String graphicLayerName) {
        // Do nothing
    }

    @Override
    public String getName() {
        return getType().getDefaultName();
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public Integer getLevel() {
        return getType().getLevel();
    }

    @Override
    public void setLevel(Integer i) {
        // Assume to be at the lowest level
    }

    @Override
    public AffineTransform getTransform() {
        return null;
    }

    @Override
    public void setTransform(AffineTransform transform) {
        // Does handle affine transform for image, already in operation manager
    }

    @Override
    public LayerType getType() {
        return LayerType.IMAGE;
    }

    @Override
    public boolean hasContent() {
        return getSourceImage() != null;
    }

    @Override
    public void setImage(E image, OpManager preprocessing) {
        boolean init = (image != null && !image.equals(this.sourceImage)) || (image == null && sourceImage != null);
        this.sourceImage = image;

        this.preprocessing = preprocessing;
        if (preprocessing != null || init) {
            disOpManager.setFirstNode(getSourceRenderedImage());
            updateDisplayOperations();
        }
    }

    public void drawImage(Graphics2D g2d) {
        // Get the clipping rectangle
        if (!visible || displayImage == null) {
            return;
        }

        Shape clip = g2d.getClip();
        if (clip instanceof Rectangle2D) {
            Rectangle2D rect = new Rectangle2D.Double(displayImage.getMinX(), displayImage.getMinY(),
                displayImage.getWidth() - 1, displayImage.getHeight() - 1);
            rect = rect.createIntersection((Rectangle2D) clip);
            if (rect.isEmpty()) {
                return;
            }
            // Avoid to display one pixel outside the border line of a view.
            // rect.setRect(Math.ceil(rect.getX()), Math.ceil(rect.getY()), rect.getWidth() - 1, rect.getHeight() - 1);
            g2d.setClip(rect);
        }

        try {
            g2d.drawRenderedImage(displayImage, AffineTransform.getTranslateInstance(0.0, 0.0));
        } catch (Exception | OutOfMemoryError e) {
            LOGGER.error("Draw rendered image", e);//$NON-NLS-1$
            if ("java.io.IOException: closed".equals(e.getMessage())) { //$NON-NLS-1$
                // Issue when the stream has been closed of a tiled image (problem that readAsRendered do not read data
                // immediately)
                if (sourceImage.isImageInCache()) {
                    sourceImage.removeImageFromCache();
                }
                disOpManager.setFirstNode(getSourceRenderedImage());
                updateDisplayOperations();
            } else if (e instanceof OutOfMemoryError) {
                // When outOfMemory exception or when tiles are not available anymore (file stream closed)
                System.gc();
                try {
                    // Let garbage collection
                    Thread.sleep(100);
                } catch (InterruptedException et) {
                }
            }
        }
        g2d.setClip(clip);

    }

    public void drawImageForPrinter(Graphics2D g2d, double viewScale) {
        // Get the clipping rectangle
        if (!visible || displayImage == null) {
            return;
        }

        Shape clip = g2d.getClip();
        if (clip instanceof Rectangle2D) {
            Rectangle2D rect = new Rectangle2D.Double(displayImage.getMinX(), displayImage.getMinY(),
                displayImage.getWidth() - 1, displayImage.getHeight() - 1);
            rect = rect.createIntersection((Rectangle2D) clip);
            if (rect.isEmpty()) {
                return;
            }
            g2d.setClip(rect);
        }

        double ratioX = (Double) disOpManager.getParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X);
        double ratioY = (Double) disOpManager.getParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y);

        double imageResX = viewScale * sourceImage.getRescaleX();
        double imageResY = viewScale * sourceImage.getRescaleY();
        // Do not print lower than 72 dpi (drawRenderedImage can only decrease the size for printer not interpolate)
        // TODO add crop image as clip as no effect on the image
        imageResX = imageResX < ratioX ? ratioX : imageResX;
        imageResY = imageResY < ratioY ? ratioY : imageResY;

        disOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, imageResX);
        disOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, imageResY);

        RenderedImage img = disOpManager.process();

        disOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, ratioX);
        disOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, ratioY);

        ratioX /= imageResX;
        ratioY /= imageResY;
        g2d.drawRenderedImage(img, AffineTransform.getScaleInstance(ratioX, ratioY));

        g2d.setClip(clip);
    }

    public void dispose() {
        sourceImage = null;
        displayImage = null;
        listenerList.clear();
        opListeners.clear();
    }

    public void addLayerChangeListener(ImageLayerChangeListener<E> listener) {
        if (listener != null && !listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    /**
     * Removes a layer manager listener from this layer.
     */
    public void removeLayerChangeListener(ImageLayerChangeListener<E> listener) {
        if (listener != null) {
            listenerList.remove(listener);
        }
    }

    public void fireImageChanged() {
        if (displayImage == null) {
            disOpManager.clearNodeIOCache();
        }
        PlanarImage img = null;
        if (buildIterator && sourceImage != null) {
            img = sourceImage.getImage(preprocessing);
        }
        readIterator = (img == null) ? null : RandomIterFactory.create(img, null);
        fireLayerChanged();
    }

    public void fireLayerChanged() {
        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).handleLayerChanged(this);
        }
    }

    public synchronized void addEventListener(OpEventListener listener) {
        if (listener != null && !opListeners.contains(listener)) {
            opListeners.add(listener);
        }
    }

    public synchronized void removeEventListener(OpEventListener listener) {
        if (listener != null) {
            opListeners.remove(listener);
        }
    }

    public synchronized void fireOpEvent(final ImageOpEvent event) {
        Iterator<OpEventListener> i = opListeners.iterator();
        while (i.hasNext()) {
            i.next().handleImageOpEvent(event);
        }
    }

    @Override
    public void updateDisplayOperations() {
        if (isEnableDispOperations()) {
            displayImage = disOpManager.process();
            fireImageChanged();
        }
    }

    @Override
    public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
        if (hasContent()) {
            return getSourceImage().getMeasurementAdapter(displayUnit, offset);
        }
        return null;
    }

    @Override
    public AffineTransform getShapeTransform() {
        E imageElement = getSourceImage();
        if (imageElement != null) {
            double scaleX = imageElement.getRescaleX();
            double scaleY = imageElement.getRescaleY();
            if (MathUtil.isDifferent(scaleX, scaleY)) {
                return AffineTransform.getScaleInstance(1.0 / scaleX, 1.0 / scaleY);
            }
        }
        return null;
    }

    @Override
    public Object getSourceTagValue(TagW tagW) {
        E imageElement = getSourceImage();
        if (imageElement != null) {
            return imageElement.getTagValue(tagW);
        }
        return null;
    }

    @Override
    public String getPixelValueUnit() {
        E imageElement = getSourceImage();
        if (imageElement != null) {
            return imageElement.getPixelValueUnit();
        }
        return null;
    }

    @Override
    public Point getOffset() {
        return offset;
    }

    @Override
    public void setOffset(Point offset) {
        this.offset = offset;
    }

}
