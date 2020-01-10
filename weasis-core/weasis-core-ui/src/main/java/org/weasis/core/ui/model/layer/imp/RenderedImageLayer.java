/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.ui.model.layer.imp;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.AffineTransformOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpEventListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LangUtil;
import org.weasis.core.ui.editor.image.Canvas;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

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
    private PlanarImage displayImage;
    private Boolean visible = true;
    private boolean enableDispOperations = true;
    private Point offset;

    public RenderedImageLayer() {
        this(null);
    }

    public RenderedImageLayer(SimpleOpManager disOpManager) {
        this.disOpManager = Optional.ofNullable(disOpManager).orElseGet(SimpleOpManager::new);
        this.listenerList = new ArrayList<>();
        this.opListeners = new ArrayList<>();
        addEventListener(this.disOpManager);
    }

    @Override
    public E getSourceImage() {
        return sourceImage;
    }

    @Override
    public PlanarImage getSourceRenderedImage() {
        if (sourceImage != null) {
            return sourceImage.getImage(preprocessing);
        }
        return null;
    }

    @Override
    public PlanarImage getDisplayImage() {
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
        // Rectify non square pixel image in the first operation
        if (sourceImage != null) {
            ZoomOp node = sourceImage.getRectifyAspectRatioZoomOp();
            if (node != null) {
                SimpleOpManager process = new SimpleOpManager();
                process.addImageOperationAction(node);
                if (preprocessing != null) {
                    for (ImageOpNode op : preprocessing.getOperations()) {
                        if (!node.getName().equals(op.getName())) {
                            process.addImageOperationAction(op);
                        }
                    }
                }
                this.preprocessing = process;
            }
        }

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
            Rectangle2D rect = new Rectangle2D.Double(0, 0, displayImage.width() - 1.0, displayImage.height() - 1.0);
            rect = rect.createIntersection((Rectangle2D) clip);
            if (rect.isEmpty()) {
                return;
            }
            // Avoid to display one pixel outside the border line of a view.
            // rect.setRect(Math.ceil(rect.getX()), Math.ceil(rect.getY()), rect.getWidth() - 1, rect.getHeight() - 1);
            g2d.setClip(rect);
        }

        try {
            g2d.drawRenderedImage(ImageConversion.toBufferedImage(displayImage),
                AffineTransform.getTranslateInstance(0.0, 0.0));
        } catch (Exception e) {
            LOGGER.error("Cannot draw the image", e);//$NON-NLS-1$
            if ("java.io.IOException: closed".equals(e.getMessage())) { //$NON-NLS-1$
                // Issue when the stream has been closed of a tiled image (problem that readAsRendered do not read data
                // immediately)
                if (sourceImage.isImageInCache()) {
                    sourceImage.removeImageFromCache();
                }
                disOpManager.setFirstNode(getSourceRenderedImage());
                updateDisplayOperations();
            }
        } catch (OutOfMemoryError e) {
            LOGGER.error("Cannot draw the image", e);//$NON-NLS-1$
            CvUtil.runGarbageCollectorAndWait(100);
        }
        g2d.setClip(clip);

    }

    public void drawImageForPrinter(Graphics2D g2d, double viewScale, Canvas canvas) {
        // Get the clipping rectangle
        if (!visible || displayImage == null) {
            return;
        }

        Shape clip = g2d.getClip();
        if (clip instanceof Rectangle2D) {
            Rectangle2D rect = new Rectangle2D.Double(0, 0, displayImage.width() - 1.0, displayImage.height() - 1.0);
            rect = rect.createIntersection((Rectangle2D) clip);
            if (rect.isEmpty()) {
                return;
            }
            g2d.setClip(rect);
        }

        // Rectangle2D modelArea = canvas.getViewModel().getModelArea();
        // double rWidth = modelArea.getWidth();
        // double rHeight = modelArea.getHeight();
        //
        // OpManager dispOp = getDisplayOpManager();
        // boolean flip = LangUtil.getNULLtoFalse((Boolean) canvas.getActionValue(ActionW.FLIP.cmd()));
        // Integer rotationAngle = (Integer) canvas.getActionValue(ActionW.ROTATION.cmd());
        //
        // double curScale = canvas.getViewModel().getViewScale();
        // // Do not print lower than 72 dpi (drawRenderedImage can only decrease the size for printer not interpolate)
        // double imageRes = viewScale < curScale ? curScale : viewScale;
        //
        // AffineTransform affineTransform = AffineTransform.getScaleInstance(flip ? -imageRes : imageRes, imageRes);
        // if (rotationAngle != null && rotationAngle > 0) {
        // affineTransform.rotate(Math.toRadians(rotationAngle), rWidth / 2.0, rHeight / 2.0);
        // }
        // if (flip) {
        // affineTransform.translate(-rWidth, 0.0);
        // }
        //
        // ImageOpNode node = dispOp.getNode(AffineTransformOp.OP_NAME);
        // if (node != null) {
        // double diffRatio = curScale / imageRes;
        // Rectangle2D imgBounds = affineTransform.createTransformedShape(modelArea).getBounds2D();
        //
        // double diffx = 0.0;
        // double diffy = 0.0;
        // Rectangle2D viewBounds = new Rectangle2D.Double(0, 0, canvas.getJComponent().getWidth(),
        // canvas.getJComponent().getHeight());
        // Rectangle2D srcBounds = canvas.getImageViewBounds(viewBounds.getWidth(), viewBounds.getHeight());
        //
        // Rectangle2D dstBounds;
        // if (viewBounds.contains(srcBounds)) {
        // dstBounds = srcBounds;
        // } else {
        // dstBounds = viewBounds.createIntersection(srcBounds);
        //
        // if (srcBounds.getX() < 0.0) {
        // diffx += srcBounds.getX();
        // }
        // if (srcBounds.getY() < 0.0) {
        // diffy += srcBounds.getY();
        // }
        // }
        //
        // double[] fmx = new double[6];
        // affineTransform.getMatrix(fmx);
        // // adjust transformation matrix => move the center to keep all the image
        // fmx[4] -= imgBounds.getX() - diffx;
        // fmx[5] -= imgBounds.getY() - diffy;
        // affineTransform.setTransform(fmx[0], fmx[1], fmx[2], fmx[3], fmx[4], fmx[5]);
        //
        // // Convert to openCV affine matrix
        // double[] m = new double[] { fmx[0], fmx[2], fmx[4], fmx[1], fmx[3], fmx[5] };
        // Object oldMatrix = node.getParam(AffineTransformOp.P_AFFINE_MATRIX);
        // Object oldBounds = node.getParam(AffineTransformOp.P_DST_BOUNDS);
        // node.setParam(AffineTransformOp.P_AFFINE_MATRIX, m);
        // node.setParam(AffineTransformOp.P_DST_BOUNDS, dstBounds);
        // PlanarImage img = disOpManager.process();
        // node.setParam(AffineTransformOp.P_AFFINE_MATRIX, oldMatrix);
        // node.setParam(AffineTransformOp.P_DST_BOUNDS, oldBounds);
        //
        //
        // g2d.drawRenderedImage(ImageProcessor.toBufferedImage(img), AffineTransform.getScaleInstance(diffRatio,
        // diffRatio));
        // }

        double[] matrix =
            (double[]) disOpManager.getParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX);
        Rectangle2D bound =
            (Rectangle2D) disOpManager.getParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_DST_BOUNDS);
        double ratioX = matrix[0];
        double ratioY = matrix[4];
        double offsetX = matrix[2];
        double offsetY = matrix[5];

        double imageResX = viewScale;
        double imageResY = viewScale;
        // Do not print lower than 72 dpi (drawRenderedImage can only decrease the size for printer not interpolate)
        imageResX = imageResX < ratioX ? ratioX : imageResX;
        imageResY = imageResY < ratioY ? ratioY : imageResY;
        matrix[0] = imageResX;
        matrix[4] = imageResY;

        double rx = ratioX / imageResX;
        double ry = ratioY / imageResY;
        Rectangle2D b =
            new Rectangle2D.Double(bound.getX() / rx, bound.getY() / ry, bound.getWidth() / rx, bound.getHeight() / ry);
        matrix[2] = offsetX / rx;
        matrix[5] = offsetY / ry;
        disOpManager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX, matrix);
        disOpManager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_DST_BOUNDS, b);
        PlanarImage img = bound.equals(b) ? displayImage : disOpManager.process();

        matrix[0] = ratioX;
        matrix[4] = ratioY;
        matrix[2] = offsetX;
        matrix[5] = offsetY;
        disOpManager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_AFFINE_MATRIX, matrix);
        disOpManager.setParamValue(AffineTransformOp.OP_NAME, AffineTransformOp.P_DST_BOUNDS, bound);

        g2d.drawRenderedImage(ImageConversion.toBufferedImage(img), AffineTransform.getScaleInstance(rx, ry));

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
            PlanarImage imgSource = disOpManager.getFirstNodeInputImage();
            disOpManager.clearNodeIOCache();
            disOpManager.setFirstNode(imgSource);
        }
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
    public double pixelToRealValue(Number pixelValue) {
        Number val = pixelValue;
        E imageElement = getSourceImage();
        if (imageElement != null) {
            TagReadable tagable = null;
            boolean pixelPadding = false;
            WindowOp wlOp = (WindowOp) disOpManager.getNode(WindowOp.OP_NAME);
            if (wlOp != null) {
                pixelPadding = LangUtil.getNULLtoTrue((Boolean) wlOp.getParam(ActionW.IMAGE_PIX_PADDING.cmd()));
                tagable = (TagReadable) wlOp.getParam("pr.element"); //$NON-NLS-1$
            }
            val = imageElement.pixelToRealValue(pixelValue, tagable, pixelPadding);
        }
        
        if (val != null) {
            return val.doubleValue();
        }
        return 0;
    }
    
    @Override
    public Point getOffset() {
        return offset;
    }

    @Override
    public void setOffset(Point offset) {
        this.offset = offset;
    }

    @Override
    public double getPixelMin() {
        E imageElement = getSourceImage();
        if (imageElement != null) {
            return imageElement.getPixelMin();
        }
        return 0;
    }

    @Override
    public double getPixelMax() {
        E imageElement = getSourceImage();
        if (imageElement != null) {
            return imageElement.getPixelMax();
        }
        return 0;
    }
}
