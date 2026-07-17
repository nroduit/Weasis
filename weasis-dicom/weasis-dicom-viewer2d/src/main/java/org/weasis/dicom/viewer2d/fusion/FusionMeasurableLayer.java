/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.awt.Point;
import java.awt.geom.AffineTransform;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.opencv.data.PlanarImage;

/**
 * A {@link MeasurableLayer} exposing the fused PET data so an area measurement drawn on the base
 * (CT/MR) image can additionally report PET SUV statistics. Two modes are supported:
 *
 * <ul>
 *   <li><b>Native slice</b> (preferred): {@code source} is the original PET stored-value image and
 *       {@code shapeTransform} maps the ROI from base-image coordinates into PET pixel coordinates.
 *       The statistics are then computed on the untouched PET voxels — exactly matching a
 *       measurement made directly on the PET image (no resampling, no interpolation loss).
 *   <li><b>Resampled volume</b> (oblique/MPR fallback): {@code source} already holds modality-LUT
 *       (real) values on the base grid with {@code NaN} outside the volume, {@code shapeTransform}
 *       is {@code null} and {@link #pixelToRealValue} is the identity.
 * </ul>
 *
 * <p>In both cases the {@link TagW#SuvFactor} of {@code refOverlay} is applied on top to obtain
 * SUV.
 */
final class FusionMeasurableLayer implements MeasurableLayer {

  private final PlanarImage source;
  private final DicomImageElement refOverlay;
  private final AffineTransform shapeTransform;
  private final boolean valuesAreReal;
  private final String statLabel;
  private Point offset;

  /**
   * @param source the overlay image to measure (native stored image, or resampled real-value image)
   * @param refOverlay the overlay slice providing the rescale factor, pixel unit and modality LUT
   * @param shapeTransform maps base-image ROI coordinates into the source image, or {@code null}
   * @param valuesAreReal {@code true} when {@code source} already holds modality-LUT (real) values
   * @param statLabel the label suffix for the contributed statistics (e.g. {@code "PT"} for PET,
   *     {@code "NM"} for SPECT)
   */
  FusionMeasurableLayer(
      PlanarImage source,
      DicomImageElement refOverlay,
      AffineTransform shapeTransform,
      boolean valuesAreReal,
      String statLabel) {
    this.source = source;
    this.refOverlay = refOverlay;
    this.shapeTransform = shapeTransform;
    this.valuesAreReal = valuesAreReal;
    this.statLabel = statLabel;
  }

  @Override
  public String getStatLabel() {
    return statLabel;
  }

  @Override
  public boolean hasContent() {
    return source != null && refOverlay != null;
  }

  @Override
  public PlanarImage getSourceRenderedImage() {
    return source;
  }

  @Override
  public Object getSourceTagValue(TagW tagW) {
    if (refOverlay == null) {
      return null;
    }
    if (TagW.SuvFactor.equals(tagW)) {
      return refOverlay.getTagValue(tagW);
    }
    // Native path measures the stored image: honor the PET pixel padding (as a native measurement
    // would). The resampled path holds real values with NaN outside, so padding does not apply.
    return valuesAreReal ? null : refOverlay.getTagValue(tagW);
  }

  @Override
  public double pixelToRealValue(Number pixelValue) {
    if (pixelValue == null) {
      return 0;
    }
    if (valuesAreReal) {
      return pixelValue.doubleValue();
    }
    Number val = refOverlay.pixelToRealValue(pixelValue, null);
    return val == null ? pixelValue.doubleValue() : val.doubleValue();
  }

  @Override
  public String getPixelValueUnit() {
    return refOverlay == null ? null : refOverlay.getPixelValueUnit();
  }

  @Override
  public double getPixelMin() {
    return refOverlay == null ? 0 : refOverlay.getPixelMin();
  }

  @Override
  public double getPixelMax() {
    return refOverlay == null ? 0 : refOverlay.getPixelMax();
  }

  @Override
  public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
    return refOverlay == null ? null : refOverlay.getMeasurementAdapter(displayUnit, offset);
  }

  @Override
  public AffineTransform getShapeTransform() {
    return shapeTransform == null ? null : new AffineTransform(shapeTransform);
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
