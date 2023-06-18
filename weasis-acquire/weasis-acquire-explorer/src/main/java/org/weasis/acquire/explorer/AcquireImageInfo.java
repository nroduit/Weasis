/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.explorer;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.core.api.image.AutoLevelsOp;
import org.weasis.core.api.image.BrightnessOp;
import org.weasis.core.api.image.CropOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.image.MaskOp;
import org.weasis.core.api.image.RotationOp;
import org.weasis.core.api.image.SimpleOpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.imp.DefaultViewModel;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;

/**
 * @author Yannick LARVOR
 * @since 2.5.0
 */
public class AcquireImageInfo {
  private static final Logger LOGGER = LoggerFactory.getLogger(AcquireImageInfo.class);

  private final ImageElement image;
  private SeriesGroup seriesGroup;
  private final Attributes attributes;
  private AcquireImageStatus status;

  private final SimpleOpManager postProcessOpManager;

  private final AcquireImageValues defaultValues;
  private AcquireImageValues currentValues;
  private AcquireImageValues nextValues;

  public AcquireImageInfo(ImageElement image) {
    this.image = Objects.requireNonNull(image);
    // Create a SOPInstanceUID if not present
    TagW tagUid = TagD.getUID(Level.INSTANCE);
    String uuid = (String) image.getTagValue(tagUid);
    if (uuid == null) {
      uuid = UIDUtils.createUID();
      image.setTag(tagUid, uuid);
    }
    readTags(image);

    this.setStatus(AcquireImageStatus.TO_PUBLISH);
    this.attributes = new Attributes();

    this.postProcessOpManager = new SimpleOpManager();
    this.postProcessOpManager.addImageOperationAction(new RotationOp());
    this.postProcessOpManager.addImageOperationAction(new MaskOp());
    this.postProcessOpManager.addImageOperationAction(new CropOp());
    this.postProcessOpManager.addImageOperationAction(new BrightnessOp());
    this.postProcessOpManager.addImageOperationAction(new AutoLevelsOp());
    this.postProcessOpManager.addImageOperationAction(new ZoomOp());

    defaultValues = new AcquireImageValues();
    currentValues = defaultValues.copy();
    nextValues = defaultValues.copy();
  }

  public String getUID() {
    return TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
  }

  public ImageElement getImage() {
    return image;
  }

  public Attributes getAttributes() {
    return attributes;
  }

  public SimpleOpManager getPostProcessOpManager() {
    return this.postProcessOpManager;
  }

  public void applyFinalProcessing(ViewCanvas<ImageElement> view) {
    boolean dirty = isDirty();
    if (dirty) {
      reloadFinalProcessing(view);
    }
  }

  public void applyNRotation(ViewCanvas<ImageElement> view) {
    int rotation = (nextValues.getFullRotation() + 720) % 360;
    ImageOpNode node = postProcessOpManager.getNode(RotationOp.OP_NAME);
    if (node != null) {
      node.clearIOCache();
      node.setParam(RotationOp.P_ROTATE, rotation);
      Rectangle area = ImageConversion.getBounds(view.getSourceImage());
      if (area.width > 1 && area.height > 1) {
        ((DefaultViewModel) view.getViewModel())
            .adjustMinViewScaleFromImage(area.width, area.height);
        view.getViewModel().setModelArea(new Rectangle(0, 0, area.width, area.height));
        view.getImageLayer().setOffset(new Point(area.x, area.y));
        if (nextValues.getCropZone() == null) {
          nextValues.setCropZone(area);
        }
      }
    }
  }

  public void reloadFinalProcessing(ViewCanvas<ImageElement> view) {
    postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);
    applyNRotation(view);

    Rectangle area = nextValues.getCropZone();
    PlanarImage source = view.getSourceImage();
    if (source != null && area != null && !area.equals(view.getViewModel().getModelArea())) {
      Rectangle imgBounds = ImageConversion.getBounds(source);
      area = area.intersection(imgBounds);
      if (area.width > 1 && area.height > 1 && !area.equals(imgBounds)) {
        ((DefaultViewModel) view.getViewModel())
            .adjustMinViewScaleFromImage(area.width, area.height);
        view.getViewModel().setModelArea(new Rectangle(0, 0, area.width, area.height));
        view.getImageLayer().setOffset(new Point(area.x, area.y));

        postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_AREA, area);
      }
    }
    view.resetZoom();

    if (nextValues.getBrightness() != currentValues.getBrightness()
        || nextValues.getContrast() != currentValues.getContrast()) {
      postProcessOpManager.setParamValue(
          BrightnessOp.OP_NAME,
          BrightnessOp.P_BRIGHTNESS_VALUE,
          (double) nextValues.getBrightness());
      postProcessOpManager.setParamValue(
          BrightnessOp.OP_NAME, BrightnessOp.P_CONTRAST_VALUE, (double) nextValues.getContrast());
    }

    postProcessOpManager.setParamValue(
        AutoLevelsOp.OP_NAME, AutoLevelsOp.P_AUTO_LEVEL, nextValues.isAutoLevel());

    if (!Objects.equals(nextValues.getRatio(), currentValues.getRatio())) {
      postProcessOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, nextValues.getRatio());
      postProcessOpManager.setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, nextValues.getRatio());
      postProcessOpManager.setParamValue(
          ZoomOp.OP_NAME, ZoomOp.P_INTERPOLATION, ZoomOp.Interpolation.BICUBIC);
    }

    // Reset preprocess cache
    postProcessOpManager.clearNodeIOCache();
    view.getImageLayer().setImage(image, postProcessOpManager);
    updateTags(view.getImage());
    updateImageGeometry(view);

    // Next value become the current value. Register the step.
    currentValues = nextValues;
    nextValues = currentValues.copy();
  }

  public void updateImageGeometry(ViewCanvas<ImageElement> view) {
    if (view != null) {
      Panner<?> panner = view.getPanner();
      if (panner != null) {
        panner.updateImage();
      }
    }
  }

  public void applyCurrentProcessing(ViewCanvas<ImageElement> view) {
    if (view != null) {
      ImageLayer<ImageElement> imageLayer = view.getImageLayer();
      ImageOpNode node = imageLayer.getDisplayOpManager().getNode(WindowOp.OP_NAME);
      if (node != null) {
        node.setEnabled(false);
      }
      imageLayer.setImage(image, postProcessOpManager);
    }
  }

  public void removeLayer(ViewCanvas<ImageElement> view) {
    if (view != null) {
      GraphicModel gm = view.getGraphicManager();
      gm.deleteByLayerType(LayerType.ACQUIRE);
      view.getJComponent().repaint();
    }
  }

  private void updateTags(ImageElement image) {
    this.image.setTag(TagW.ImageWidth, image.getTagValue(TagW.ImageWidth));
    this.image.setTag(TagW.ImageHeight, image.getTagValue(TagW.ImageHeight));
  }

  public AcquireImageValues getNextValues() {
    return nextValues;
  }

  public AcquireImageValues getCurrentValues() {
    return currentValues;
  }

  public AcquireImageValues getDefaultValues() {
    return defaultValues;
  }

  public boolean isDirtyFromDefault() {
    return !defaultValues.equals(nextValues);
  }

  public boolean isDirty() {
    return !currentValues.equals(nextValues);
  }

  public AcquireImageValues restore(ViewCanvas<ImageElement> view) {
    image.setPixelSpacingUnit(defaultValues.getCalibrationUnit());
    image.setPixelSize(defaultValues.getCalibrationRatio());

    postProcessOpManager.setParamValue(
        RotationOp.OP_NAME, RotationOp.P_ROTATE, defaultValues.getOrientation());
    postProcessOpManager.setParamValue(CropOp.OP_NAME, CropOp.P_AREA, null);

    postProcessOpManager.setParamValue(
        BrightnessOp.OP_NAME,
        BrightnessOp.P_BRIGHTNESS_VALUE,
        (double) defaultValues.getBrightness());
    postProcessOpManager.setParamValue(
        BrightnessOp.OP_NAME, BrightnessOp.P_CONTRAST_VALUE, (double) defaultValues.getContrast());
    postProcessOpManager.setParamValue(
        AutoLevelsOp.OP_NAME, AutoLevelsOp.P_AUTO_LEVEL, defaultValues.isAutoLevel());

    if (view != null) {
      view.getImageLayer().setImage(image, postProcessOpManager);
    }

    currentValues = defaultValues.copy();
    nextValues = defaultValues.copy();

    return defaultValues;
  }

  public SeriesGroup getSeries() {
    return seriesGroup;
  }

  public void setSeries(SeriesGroup seriesGroup) {
    this.seriesGroup = seriesGroup;
    if (seriesGroup != null) {
      image.setTag(TagD.get(Tag.SeriesInstanceUID), seriesGroup.getUID());

      String seriesDescription = TagD.getTagValue(seriesGroup, Tag.SeriesDescription, String.class);
      if (!StringUtil.hasText(seriesDescription)
          && seriesGroup.getType() != SeriesGroup.Type.NONE) {
        seriesGroup.setTag(TagD.get(Tag.SeriesDescription), seriesGroup.getDisplayName());
      }
    }
  }

  @Override
  public String toString() {
    return Optional.ofNullable(image).map(ImageElement::getName).orElse("");
  }

  public AcquireImageStatus getStatus() {
    return status;
  }

  public void setStatus(AcquireImageStatus status) {
    this.status = Objects.requireNonNull(status);
  }

  public static Consumer<AcquireImageInfo> changeStatus(AcquireImageStatus status) {
    return imgInfo -> imgInfo.setStatus(status);
  }

  public AffineTransform getAffineTransform(int rotation, boolean inverse) {
    AffineTransform transform = new AffineTransform();
    if (rotation != 0) {
      PlanarImage img = image.getImage();
      Rectangle2D modelArea = new Rectangle2D.Double(0.0, 0.0, img.width(), img.height());
      double w = modelArea.getWidth();
      double h = modelArea.getHeight();
      org.opencv.core.Point ptCenter = new org.opencv.core.Point(w / 2.0, h / 2.0);
      Mat rot = Imgproc.getRotationMatrix2D(ptCenter, -rotation, 1.0);

      Rect bbox = new RotatedRect(ptCenter, new Size(w, h), -rotation).boundingRect();
      double[] m = new double[rot.cols() * rot.rows()];
      // adjust transformation matrix
      rot.get(0, 0, m);
      m[2] += bbox.width / 2.0 - ptCenter.x;
      m[rot.cols() + 2] += bbox.height / 2.0 - ptCenter.y;

      transform.setTransform(m[0], m[3], m[1], m[4], m[2], m[5]);
      if (inverse) {
        try {
          return transform.createInverse();
        } catch (NoninvertibleTransformException e) {
          LOGGER.error("Cannot create inverse transform for graphics");
        }
      }
    }
    return transform;
  }

  /**
   * Check if ImageElement has a SOPInstanceUID TAG value and if not create a new UUID. Read Exif
   * metaData from original file and populate relevant ImageElement TAGS. <br>
   *
   * @param imageElement the ImageElement value
   */
  private static void readTags(ImageElement imageElement) {
    // Convert Exif TAG to DICOM attributes
    Optional<File> file = imageElement.getFileCache().getOriginalFile();

    if (file.isPresent()) {
      imageElement.setTagNoNull(
          TagD.get(Tag.Manufacturer), imageElement.getTagValue(TagW.ExifMake));
      imageElement.setTagNoNull(
          TagD.get(Tag.ManufacturerModelName), imageElement.getTagValue(TagW.ExifModel));

      String date = (String) TagUtil.getTagValue(TagW.ExifDateTime, imageElement);
      LocalDateTime dateTime = null;
      if (StringUtil.hasText(date)) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"); // NON-NLS
        try {
          dateTime = LocalDateTime.parse(date, formatter);
        } catch (DateTimeParseException ex) {
          // Do nothing
        }
      }
      if (dateTime == null) {
        dateTime =
            LocalDateTime.from(
                Instant.ofEpochMilli(imageElement.getLastModified())
                    .atZone(ZoneId.systemDefault()));
      }
      imageElement.setTagNoNull(TagD.get(Tag.ContentDate), dateTime.toLocalDate());
      imageElement.setTagNoNull(TagD.get(Tag.ContentTime), dateTime.toLocalTime());

      String imgDescription = (String) imageElement.getTagValue(TagW.ExifImageDescription);
      if (!StringUtil.hasText(imgDescription)) {
        imgDescription = file.get().getName();
      }
      imageElement.setTagNoNull(TagD.get(Tag.ImageComments), imgDescription);
    }
  }
}
