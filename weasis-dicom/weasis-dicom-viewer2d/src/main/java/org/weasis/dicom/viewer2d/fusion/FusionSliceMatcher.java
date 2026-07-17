/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.awt.geom.Point2D;
import java.util.Objects;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;

/**
 * Finds the best-matching PET slice for a given CT/MR slice based on 3D spatial position.
 *
 * <p>Uses the normal-vector dot product to compute the slice location in the PET series' coordinate
 * system, then selects the nearest PET image.
 */
public final class FusionSliceMatcher {

  private FusionSliceMatcher() {}

  /**
   * Finds the PET image that best matches the given CT/MR image's spatial position.
   *
   * @param baseImage the reference CT/MR image
   * @param overlaySeries the PET series to search
   * @param filter optional filter for the PET series
   * @param comparator sort comparator for the PET series
   * @return the best-matching PET image, or null if no match is found
   */
  public static DicomImageElement findMatchingSlice(
      DicomImageElement baseImage,
      MediaSeries<DicomImageElement> overlaySeries,
      Filter<DicomImageElement> filter,
      java.util.Comparator<DicomImageElement> comparator) {
    Objects.requireNonNull(baseImage, "Base image cannot be null");
    Objects.requireNonNull(overlaySeries, "Overlay series cannot be null");

    GeometryOfSlice baseGeometry = baseImage.getSliceGeometry();
    if (baseGeometry == null) {
      return null;
    }

    // Compute the 3D center position of the CT slice
    Vector3d baseCenter = computeSliceCenter(baseGeometry);
    if (baseCenter == null) {
      return null;
    }

    // Find the PET image nearest to this 3D position
    return findNearestOverlaySlice(baseCenter, overlaySeries, filter, comparator);
  }

  private static Vector3d computeSliceCenter(GeometryOfSlice geometry) {
    Vector3d dimensions = geometry.getDimensions();
    if (geometry.getTLHC() == null
        || geometry.getRow() == null
        || geometry.getColumn() == null
        || geometry.getVoxelSpacing() == null
        || dimensions == null) {
      return null;
    }
    // Center pixel position (half of rows and columns)
    return geometry.getPosition(new Point2D.Double(dimensions.y * 0.5, dimensions.x * 0.5));
  }

  private static DicomImageElement findNearestOverlaySlice(
      Vector3d position3D,
      MediaSeries<DicomImageElement> overlaySeries,
      Filter<DicomImageElement> filter,
      java.util.Comparator<DicomImageElement> comparator) {

    // Get a reference PET image to determine the normal vector
    DicomImageElement refOverlayImage = overlaySeries.getMedia(0, filter, comparator);
    if (refOverlayImage == null) {
      return null;
    }

    GeometryOfSlice overlayGeometry = refOverlayImage.getSliceGeometry();
    if (overlayGeometry == null) {
      return null;
    }

    Vector3d normal = overlayGeometry.getNormal();
    if (normal == null) {
      return null;
    }

    // Project the 3D position onto the PET series' normal axis
    double location = position3D.x * normal.x + position3D.y * normal.y + position3D.z * normal.z;

    return overlaySeries.getNearestImage(location, 0, filter, comparator);
  }
}
