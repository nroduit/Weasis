/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.img.DicomImageReadParam;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.img.data.OverlayData;
import org.dcm4che3.img.data.PrDicomObject;
import org.dcm4che3.img.stream.ImageDescriptor;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.AbstractOp;
import org.weasis.core.api.image.ImageOpEvent;
import org.weasis.core.api.image.ImageOpEvent.OpEvent;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.util.MathUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageTransformer;

public class OverlayOp extends AbstractOp {
  public static final String OP_NAME = ActionW.IMAGE_OVERLAY.getTitle();

  public static final String P_SHOW = "overlay"; // NON-NLS
  public static final String P_IMAGE_ELEMENT = "img.element";
  public static final String OVERLAY_COLOR_KEY = "overlay.color";

  private OverlayMask overlayMask;

  public OverlayOp() {
    setName(OP_NAME);
  }

  public OverlayOp(OverlayOp op) {
    super(op);
    // Not shared: the cached mask is released by the operation that built it
    this.overlayMask = null;
  }

  @Override
  public OverlayOp copy() {
    return new OverlayOp(this);
  }

  @Override
  public void handleImageOpEvent(ImageOpEvent event) {
    OpEvent type = event.eventType();
    if (OpEvent.IMAGE_CHANGE.equals(type) || OpEvent.RESET_DISPLAY.equals(type)) {
      setParam(WindowAndPresetsOp.P_PR_ELEMENT, null);
      setParam(P_IMAGE_ELEMENT, event.image());
      setParam(ActionW.CROP.cmd(), null);
    } else if (OpEvent.APPLY_PR.equals(type)) {
      Map<String, Object> p = event.params();
      if (p != null) {
        PRSpecialElement pr =
            Optional.ofNullable(p.get(ActionW.PR_STATE.cmd()))
                .filter(PRSpecialElement.class::isInstance)
                .map(PRSpecialElement.class::cast)
                .orElse(null);
        setParam(WindowAndPresetsOp.P_PR_ELEMENT, pr == null ? null : pr.getPrDicomObject());
        setParam(P_IMAGE_ELEMENT, event.image());
        // The displayed area of the presentation state crops the image
        setParam(ActionW.CROP.cmd(), p.get(ActionW.CROP.cmd()));
      }
    }
  }

  @Override
  public void process() throws Exception {
    PlanarImage source = getSourceImage();
    PlanarImage result = source;
    Boolean overlay = (Boolean) params.get(P_SHOW);

    if (overlay != null && overlay) {
      ImageElement image = (ImageElement) params.get(P_IMAGE_ELEMENT);
      if (image != null && image.getMediaReader() instanceof DicomMediaIO reader) {
        DicomMetaData md = reader.getDicomMetaData();
        if (md != null) {
          ImageDescriptor desc = md.getImageDescriptor();
          if (image.getKey() instanceof Integer frame) {
            DicomImageReadParam p = new DicomImageReadParam();
            p.setPresentationState((PrDicomObject) params.get(WindowAndPresetsOp.P_PR_ELEMENT));
            p.setOverlayColor(
                GuiUtils.getUICore()
                    .getSystemPreferences()
                    .getColorProperty(OVERLAY_COLOR_KEY, Color.WHITE));
            ImageCV mask = getOverlayMask(reader, image, frame, source, desc, p);
            if (mask != null) {
              result = OverlayData.applyOverlayMask(source, mask, p);
            }
          }
        }
      }
    }
    params.put(Param.OUTPUT_IMG, result);
  }

  /** The overlays of a frame do not depend on its rendering: the mask is kept across windows. */
  private record OverlayMask(
      ImageElement image,
      int frame,
      PrDicomObject presentationState,
      Rectangle crop,
      ImageCV mask) {
    boolean isFor(ImageElement image, int frame, PrDicomObject presentationState, Rectangle crop) {
      return this.image == image
          && this.frame == frame
          && this.presentationState == presentationState
          && Objects.equals(this.crop, crop);
    }
  }

  private ImageCV getOverlayMask(
      DicomMediaIO reader,
      ImageElement image,
      int frame,
      PlanarImage source,
      ImageDescriptor desc,
      DicomImageReadParam p)
      throws Exception {
    OverlayMask kept = overlayMask;
    PrDicomObject pr = p.getPresentationState().orElse(null);
    Rectangle crop = (Rectangle) params.get(ActionW.CROP.cmd());
    if (kept != null && kept.isFor(image, frame, pr, crop)) {
      return kept.mask();
    }
    // The overlay origins are relative to the stored frame: build the mask at its size, then
    // stretch and crop it like the displayed image
    PlanarImage stored;
    if (desc.getEmbeddedOverlay().isEmpty()) {
      // Only the size is read when no overlay is embedded in the pixel data
      stored = new ImageCV(desc.getRows(), desc.getColumns(), CvType.CV_8UC1);
    } else {
      stored = reader.getImageFragment(image, frame, false);
    }
    ImageCV mask = stored == null ? null : OverlayData.getOverlayMask(stored, desc, p, frame);
    if (stored != null) {
      stored.release();
    }
    mask = toImage(mask, image, crop, source.width(), source.height());
    if (kept != null && kept.mask() != null) {
      kept.mask().release();
    }
    overlayMask = new OverlayMask(image, frame, pr, crop, mask);
    return mask;
  }

  /**
   * Gives the mask of the stored frame the geometry of the image it is applied to: stretched to
   * square pixels when they are not (as {@link ImageElement#getRectifyAspectRatioZoomOp()}) and the
   * image is, then cropped to the displayed area.
   *
   * @param width the width of the image the mask is applied to
   * @param height the height of the image the mask is applied to
   */
  static ImageCV toImage(ImageCV mask, ImageElement image, Rectangle crop, int width, int height) {
    if (mask == null) {
      return null;
    }
    ImageCV result = mask;
    Dimension size =
        new Dimension(
            ZoomOp.scaledLength(mask.width(), image.getRescaleX()),
            ZoomOp.scaledLength(mask.height(), image.getRescaleY()));
    // The export renders the stored pixels and stretches them last: the mask is not stretched then
    Rectangle shown = new Rectangle(size);
    if (crop != null) {
      shown = crop.intersection(shown);
    }
    if (MathUtil.isDifferent(image.getRescaleX(), image.getRescaleY())
        && shown.width == width
        && shown.height == height) {
      // Pixel centers are kept aligned with the bilinear stretch of the image
      result = ImageTransformer.scale(mask, size, Imgproc.INTER_NEAREST_EXACT);
      mask.release();
    }
    if (crop != null) {
      Rectangle area = crop.intersection(new Rectangle(0, 0, result.width(), result.height()));
      if (!area.isEmpty() && (area.width != result.width() || area.height != result.height())) {
        ImageCV cropped = new ImageCV();
        Mat roi = new Mat(result, new Rect(area.x, area.y, area.width, area.height));
        roi.copyTo(cropped);
        roi.release();
        result.release();
        result = cropped;
      }
    }
    return result;
  }
}
