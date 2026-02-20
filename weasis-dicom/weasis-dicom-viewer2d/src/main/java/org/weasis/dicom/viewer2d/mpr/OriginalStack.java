/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.weasis.dicom.viewer2d.mpr.MprView.Plane.AXIAL;
import static org.weasis.dicom.viewer2d.mpr.VolumeBounds.EPSILON;
import static org.weasis.dicom.viewer2d.mpr.VolumeBounds.needsRectification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.joml.Vector3d;
import org.joml.Vector3i;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mip.SeriesBuilder;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public abstract class OriginalStack extends AbstractStack {
  static TagW seriesReferences = new TagW("series.builder.refs", TagType.STRING, 2, 2);

  protected final List<DicomImageElement> sourceStack;
  private final GeometryOfSlice fistSliceGeometry;
  protected double sliceSpace;
  protected boolean variableSliceSpacing;
  protected boolean nonParallelSlices;

  public OriginalStack(
      Plane plane, MediaSeries<DicomImageElement> series, Filter<DicomImageElement> filter) {
    super(plane, series);
    this.sourceStack = series.copyOfMedias(filter, SortSeriesStack.slicePosition);
    this.fistSliceGeometry = new GeometryOfSlice(getStartingImage().getSliceGeometry());
    this.sliceSpace = initSliceSpace();
  }

  public DicomImageElement getMiddleImage() {
    return sourceStack.get(sourceStack.size() / 2);
  }

  public DicomImageElement getFirstImage() {
    return sourceStack.getFirst();
  }

  public DicomImageElement getLastImage() {
    return sourceStack.getLast();
  }

  public List<DicomImageElement> getSourceStack() {
    return sourceStack;
  }

  protected DicomImageElement getStartingImage() {
    return plane == AXIAL ? getLastImage() : getFirstImage();
  }

  public GeometryOfSlice getFistSliceGeometry() {
    return fistSliceGeometry;
  }

  protected Attributes getCommonAttributes(String frameOfReferenceUID, String seriesDescription) {
    Attributes attributes = getMiddleImage().getMediaReader().getDicomObject();
    Attributes cpTags = SeriesBuilder.getBaseAttributes(attributes);
    cpTags.setString(Tag.SeriesDescription, VR.LO, seriesDescription);
    cpTags.setString(Tag.ImageType, VR.CS, ObliqueMpr.imageTypes);
    cpTags.setString(Tag.FrameOfReferenceUID, VR.UI, frameOfReferenceUID);
    return cpTags;
  }

  /**
   * Initializes the slice spacing by measuring distances between consecutive slices. Applies
   * correction for non-orthogonal orientations (column/row shear).
   */
  protected double initSliceSpace() {
    if (sourceStack == null || sourceStack.size() < 2) {
      return 0.0;
    }

    double[] firstPos = (double[]) sourceStack.getFirst().getTagValue(TagW.SlicePosition);
    if (firstPos == null || firstPos.length != 3) {
      return 0.0;
    }

    // Check for slice parallelism
    checkSliceParallelism();

    // Collect all spacing measurements
    List<Double> spacings = new ArrayList<>(sourceStack.size() - 1);
    Vector3d lastPosVector = new Vector3d(firstPos[0], firstPos[1], firstPos[2]);
    for (int i = 1; i < sourceStack.size(); i++) {
      double[] sp = (double[]) sourceStack.get(i).getTagValue(TagW.SlicePosition);
      if (sp == null || sp.length != 3) {
        continue;
      }

      Vector3d currentPosVector = new Vector3d(sp[0], sp[1], sp[2]);
      double space = lastPosVector.distance(currentPosVector);
      spacings.add(space);
      lastPosVector.set(currentPosVector);
    }

    // Check for variable spacing (using median as reference)
    spacings.sort(Double::compareTo);
    double medianSpace = spacings.get(spacings.size() / 2);

    for (double space : spacings) {
      if (Math.abs(space - medianSpace) > EPSILON) {
        this.variableSliceSpacing = true;
        break;
      }
    }

    return medianSpace;
  }

  /**
   * Checks if slices are parallel within tolerance. Compares normal vectors of consecutive slices.
   */
  private void checkSliceParallelism() {
    if (sourceStack.size() < 2) {
      return;
    }

    Vector3d firstNormal = sourceStack.getFirst().getSliceGeometry().getNormal();
    if (firstNormal == null) {
      return;
    }

    for (int i = 1; i < sourceStack.size(); i++) {
      GeometryOfSlice geom = sourceStack.get(i).getSliceGeometry();
      if (geom == null) {
        continue;
      }

      Vector3d currentNormal = geom.getNormal();
      if (currentNormal == null) {
        continue;
      }

      // Calculate the angle between normals using dot product
      // For parallel slices, |dot product| should be very close to 1
      double dotProduct = firstNormal.dot(currentNormal);

      // Allow small deviation from perfect parallelism (EPSILON tolerance)
      if (Math.abs(Math.abs(dotProduct) - 1.0) > EPSILON) {
        this.nonParallelSlices = true;
        break;
      }
    }
  }

  /**
   * Computes the spacing correction factor from the image orientation geometry. This generalizes
   * the gantry tilt correction to handle both column and row shear.
   *
   * @return correction factor to apply to measured slice spacing
   */
  double computeSpacingCorrectionFromGeometry() {
    GeometryOfSlice geometry = fistSliceGeometry;
    if (geometry == null) {
      return 1.0;
    }

    // Get shear components based on plane orientation
    double colShear = getColumnShearComponent(geometry.getColumn());
    double rowShear = getRowShearComponent(geometry.getRow());

    // If no significant shear, no correction needed
    if (!needsRectification(colShear) && !needsRectification(rowShear)) {
      return 1.0;
    }

    // Combined shear magnitude
    double combinedShear = Math.sqrt(colShear * colShear + rowShear * rowShear);

    // The correction factor compensates for the apparent shortening of spacing
    // when slices are acquired at an angle
    double shearAngle = Math.atan(combinedShear);
    return 1.0 / Math.cos(shearAngle);
  }

  /**
   * Gets the column shear component based on the acquisition plane. Column shear represents
   * deviation in the slice normal direction.
   */
  private double getColumnShearComponent(Vector3d col) {
    return switch (plane) {
      case AXIAL -> col.z;
      case CORONAL -> col.y;
      case SAGITTAL -> col.x;
    };
  }

  /**
   * Gets the row shear component based on the acquisition plane. Row shear represents deviation
   * perpendicular to column shear.
   */
  private double getRowShearComponent(Vector3d row) {
    return switch (plane) {
      case AXIAL -> row.z;
      case CORONAL -> row.y;
      case SAGITTAL -> row.x;
    };
  }

  private double getPlanRotationComponent(Vector3d row) {
    return switch (plane) {
      case AXIAL -> row.y();
      case CORONAL -> row.z();
      case SAGITTAL -> -row.z();
    };
  }

  public double getSliceSpace() {
    return sliceSpace;
  }

  public boolean isVariableSliceSpacing() {
    return variableSliceSpacing;
  }

  public boolean isNonParallelSlices() {
    return nonParallelSlices;
  }

  public abstract void generate(BuildContext context);

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof OriginalStack that)) {
      return false;
    }
    return Double.compare(getSliceSpace(), that.getSliceSpace()) == 0
        && isVariableSliceSpacing() == that.isVariableSliceSpacing()
        && Objects.equals(getSourceStack(), that.getSourceStack())
        && Objects.equals(getFistSliceGeometry(), that.getFistSliceGeometry());
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        getSourceStack(), getFistSliceGeometry(), getSliceSpace(), isVariableSliceSpacing());
  }

  /**
   * Computes the volume bounds from all slice geometries. This method calculates the bounding box
   * in patient coordinate space and determines the optimal voxel spacing, including shear factors
   * for rectification.
   *
   * @return VolumeBounds containing size, spacing, orientation, and shear information
   */
  public VolumeBounds computeVolumeBounds() {
    if (sourceStack == null || sourceStack.isEmpty()) {
      return null;
    }

    DicomImageElement firstImg = getStartingImage();
    GeometryOfSlice firstGeom = firstImg.getSliceGeometry();

    Vector3d row = new Vector3d(firstGeom.getRow());
    Vector3d col = new Vector3d(firstGeom.getColumn());
    Vector3d normal = firstGeom.getNormal();

    // Normalize directions to ensure consistent orientation
    normalizeToPositiveDirection(row);
    normalizeToPositiveDirection(col);

    // Compute shear factors for potential rectification
    double columnShear = getColumnShearComponent(firstGeom.getColumn());
    double rowShear = getRowShearComponent(firstGeom.getRow());
    double planRotation = getPlanRotationComponent(firstGeom.getRow());

    // Get pixel spacing from first image
    double pixelSpacing = firstImg.getPixelSize();
    double sliceSpacing = getSliceSpace();

    // Get the origin (TLHC of first slice)
    Vector3d origin = new Vector3d(firstGeom.getTLHC());
    int numSlices = sourceStack.size();

    return switch (plane) {
      case AXIAL ->
          new VolumeBounds(
              new Vector3i(getWidth(), getHeight(), numSlices),
              new Vector3d(pixelSpacing, pixelSpacing, sliceSpacing),
              origin,
              row,
              col,
              normal,
              columnShear,
              rowShear,
              planRotation);
      case CORONAL ->
          new VolumeBounds(
              new Vector3i(getWidth(), numSlices, getHeight()),
              new Vector3d(pixelSpacing, sliceSpacing, pixelSpacing),
              origin,
              row,
              col,
              normal,
              columnShear,
              rowShear,
              planRotation);
      case SAGITTAL ->
          new VolumeBounds(
              new Vector3i(numSlices, getWidth(), getHeight()),
              new Vector3d(sliceSpacing, pixelSpacing, pixelSpacing),
              origin,
              row,
              col,
              normal,
              columnShear,
              rowShear,
              planRotation);
    };
  }

  /** Normalizes a direction vector to point in the positive direction for the dominant axis. */
  private void normalizeToPositiveDirection(Vector3d v) {
    // Find dominant axis and ensure it's positive
    double absX = Math.abs(v.x);
    double absY = Math.abs(v.y);
    double absZ = Math.abs(v.z);

    if (absX >= absY && absX >= absZ) {
      if (v.x < 0) v.negate();
    } else if (absY >= absX && absY >= absZ) {
      if (v.y < 0) v.negate();
    } else {
      if (v.z < 0) v.negate();
    }
  }
}
