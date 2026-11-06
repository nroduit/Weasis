/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.model.layer.imp;

import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.opencv.core.CvType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.OpEventListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.cv.CvUtil;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.measure.PlaneGeometry;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.layer.Layer;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.ImageLayerChangeListener;
import org.weasis.core.ui.model.utils.imp.DefaultUUID;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.lut.WlPresentation;

/**
 * The Class RenderedImageLayer.
 *
 * @author Nicolas Roduit
 */
public class RenderedImageLayer<E extends ImageElement> extends DefaultUUID
    implements Layer, ImageLayer<E> {

  private static final Logger LOGGER = LoggerFactory.getLogger(RenderedImageLayer.class);

  private final SimpleOpManager disOpManager;
  private final List<ImageLayerChangeListener<E>> listenerList;
  private final List<OpEventListener> opListeners;

  private OpManager preprocessing;
  private E sourceImage;
  private Optional<PlanarImage> displayImage;
  private transient PaintBuffer paintBuffer;
  private Boolean visible = true;
  private boolean enableDispOperations = true;
  private Point offset;
  private transient Supplier<List<MeasurableLayer>> secondaryLayersSupplier;

  public RenderedImageLayer() {
    this(null);
  }

  public RenderedImageLayer(SimpleOpManager disOpManager) {
    this.disOpManager = Optional.ofNullable(disOpManager).orElseGet(SimpleOpManager::new);
    this.listenerList = new ArrayList<>();
    this.opListeners = new ArrayList<>();
    this.displayImage = Optional.empty();
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
    return displayImage.orElse(null);
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
    boolean init =
        (image != null && !image.equals(this.sourceImage))
            || (image == null && sourceImage != null);
    this.sourceImage = image;
    this.preprocessing = preprocessing;
    // Rectify non-square pixel image in the first operation
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
    if (!visible || displayImage.isEmpty()) {
      return;
    }

    PlanarImage currentImage = this.displayImage.get();
    Shape clip = g2d.getClip();
    if (clip instanceof Rectangle2D) {
      Rectangle2D rect =
          new Rectangle2D.Double(0, 0, currentImage.width() - 1.0, currentImage.height() - 1.0);
      rect = rect.createIntersection((Rectangle2D) clip);
      if (rect.isEmpty()) {
        return;
      }
      // Avoid to display one pixel outside the border of a view.
      // rect.setRect(Math.ceil(rect.getX()), Math.ceil(rect.getY()), rect.getWidth() - 1,
      // rect.getHeight() - 1);
      g2d.setClip(rect);
    }

    try {
      if (g2d.getDeviceConfiguration().getDefaultTransform().getScaleX() > 1.0) {
        g2d.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      }
      g2d.drawImage(getPaintImage(currentImage), 0, 0, null);
    } catch (Exception e) {
      LOGGER.error("Cannot draw the image", e);
      if ("java.io.IOException: closed".equals(e.getMessage())) { // NON-NLS
        // Issue when the stream has been closed of a tiled image (problem that readAsRendered do
        // not read data immediately)
        if (sourceImage.isImageInCache()) {
          sourceImage.removeImageFromCache();
        }
        disOpManager.setFirstNode(getSourceRenderedImage());
        updateDisplayOperations();
      }
    } catch (OutOfMemoryError e) {
      LOGGER.error("Cannot draw the image", e);
      CvUtil.runGarbageCollectorAndWait(200);
    }
    g2d.setClip(clip);
  }

  /** The Java2D image of a display image, kept so that a repaint converts nothing. */
  private record PaintBuffer(PlanarImage source, BufferedImage image) {}

  private BufferedImage getPaintImage(PlanarImage image) {
    PaintBuffer buffer = paintBuffer;
    if (buffer == null || buffer.source() != image) {
      buffer = new PaintBuffer(image, toPaintImage(image, buffer == null ? null : buffer.image()));
      paintBuffer = buffer;
    }
    return buffer.image();
  }

  // 8-bit gray and BGR have a standard Java2D type with the memory layout of the Mat: the pixels
  // are copied as they are, into the previous image when it still fits.
  private static BufferedImage toPaintImage(PlanarImage image, BufferedImage previous) {
    int type;
    if (image.type() == CvType.CV_8UC1) {
      type = BufferedImage.TYPE_BYTE_GRAY;
    } else if (image.type() == CvType.CV_8UC3) {
      type = BufferedImage.TYPE_3BYTE_BGR;
    } else {
      return ImageConversion.toBufferedImage(image);
    }
    boolean reusable =
        previous != null
            && previous.getType() == type
            && previous.getWidth() == image.width()
            && previous.getHeight() == image.height();
    BufferedImage result =
        reusable ? previous : new BufferedImage(image.width(), image.height(), type);
    image.get(0, 0, ((DataBufferByte) result.getRaster().getDataBuffer()).getData());
    return result;
  }

  public void dispose() {
    sourceImage = null;
    displayImage = Optional.empty();
    paintBuffer = null;
    listenerList.clear();
    opListeners.clear();
  }

  public void addLayerChangeListener(ImageLayerChangeListener<E> listener) {
    if (listener != null && !listenerList.contains(listener)) {
      listenerList.add(listener);
    }
  }

  /** Removes a layer manager listener from this layer. */
  public void removeLayerChangeListener(ImageLayerChangeListener<E> listener) {
    if (listener != null) {
      listenerList.remove(listener);
    }
  }

  public void fireImageChanged() {
    if (displayImage.isEmpty()) {
      Optional<PlanarImage> imgSource = disOpManager.getFirstNodeInputImage();
      disOpManager.clearNodeIOCache();
      disOpManager.setFirstNode(imgSource.orElse(null));
    }
    fireLayerChanged();
  }

  public void fireLayerChanged() {
    for (ImageLayerChangeListener<E> eImageLayerChangeListener : listenerList) {
      eImageLayerChangeListener.handleLayerChanged(this);
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
    for (OpEventListener opListener : opListeners) {
      opListener.handleImageOpEvent(event);
    }
  }

  @Override
  public void updateDisplayOperations() {
    if (isEnableDispOperations()) {
      Optional<PlanarImage> source = disOpManager.getFirstNodeInputImage();
      if (source.isPresent() && source.get().toMat().empty()) {
        disOpManager.setFirstNode(getSourceRenderedImage());
      }
      displayImage = disOpManager.process();
      // A pass that ran nothing gives the same image: the converted one is still right
      if (!disOpManager.isLastPassReused()) {
        invalidatePaintBuffer();
      }
      fireImageChanged();
    }
  }

  // A node passing through an image changed in place returns the same object with new content:
  // the identity check is not enough
  private void invalidatePaintBuffer() {
    PaintBuffer buffer = paintBuffer;
    if (buffer != null) {
      paintBuffer = new PaintBuffer(null, buffer.image());
    }
  }

  @Override
  public PlaneGeometry getPlaneGeometry() {
    E image = getSourceImage();
    return image == null ? null : image.getPlaneGeometry();
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
      WlPresentation wlp = null;
      Optional<ImageOpNode> wlOp = disOpManager.getNode(WindowOp.OP_NAME);
      if (wlOp.isPresent() && wlOp.get() instanceof WindowOp windowOp) {
        wlp = windowOp.getWlPresentation();
      }
      val = imageElement.pixelToRealValue(pixelValue, wlp);
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

  /**
   * Registers a lazy provider of secondary layers (e.g. a fused PET overlay for SUV statistics).
   */
  public void setSecondaryLayersSupplier(Supplier<List<MeasurableLayer>> supplier) {
    this.secondaryLayersSupplier = supplier;
  }

  @Override
  public List<MeasurableLayer> getSecondaryLayers() {
    return secondaryLayersSupplier == null ? List.of() : secondaryLayersSupplier.get();
  }
}
