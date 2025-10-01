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

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.joml.Vector3d;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;

public abstract class OriginalStack extends AbstractStack {
  protected static final double EPSILON = 1e-3;
  static TagW seriesReferences = new TagW("series.builder.refs", TagType.STRING, 2, 2);
  static final int[] COPIED_ATTRS = {
    Tag.SpecificCharacterSet,
    Tag.TimezoneOffsetFromUTC,
    Tag.PatientID,
    Tag.PatientName,
    Tag.PatientBirthDate,
    Tag.PatientBirthTime,
    Tag.PatientSex,
    Tag.IssuerOfPatientID,
    Tag.IssuerOfAccessionNumberSequence,
    Tag.PatientWeight,
    Tag.PatientAge,
    Tag.PatientSize,
    Tag.PatientState,
    Tag.PatientComments,
    Tag.StudyID,
    Tag.StudyDate,
    Tag.StudyTime,
    Tag.StudyDescription,
    Tag.StudyComments,
    Tag.AccessionNumber,
    Tag.ModalitiesInStudy,
    Tag.Modality,
    Tag.SeriesDate,
    Tag.SeriesTime,
    Tag.RetrieveAETitle,
    Tag.ReferringPhysicianName,
    Tag.InstitutionName,
    Tag.InstitutionalDepartmentName,
    Tag.StationName,
    Tag.Manufacturer,
    Tag.ManufacturerModelName,
    Tag.AnatomicalOrientationType,
    Tag.SeriesNumber,
    Tag.KVP,
    Tag.Laterality,
    Tag.BodyPartExamined,
    Tag.AnatomicRegionSequence,
    Tag.RescaleSlope,
    Tag.RescaleIntercept,
    Tag.RescaleType,
    Tag.ModalityLUTSequence,
    Tag.WindowCenter,
    Tag.WindowWidth,
    Tag.VOILUTFunction,
    Tag.WindowCenterWidthExplanation,
    Tag.VOILUTSequence
  };

  static {
    Arrays.sort(COPIED_ATTRS);
  }

  protected final List<DicomImageElement> sourceStack;
  private final GeometryOfSlice fistSliceGeometry;
  protected double sliceSpace;
  protected boolean variableSliceSpacing;

  public OriginalStack(
      Plane plane, MediaSeries<DicomImageElement> series, Filter<DicomImageElement> filter) {
    super(plane, series);
    this.sourceStack = series.copyOfMedias(filter, SortSeriesStack.slicePosition);
    this.sliceSpace = initSliceSpace();
    this.fistSliceGeometry = new GeometryOfSlice(getStartingImage().getSliceGeometry());
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
    final Attributes attributes = getMiddleImage().getMediaReader().getDicomObject();
    final Attributes cpTags = new Attributes(attributes, COPIED_ATTRS);
    cpTags.setString(Tag.SeriesDescription, VR.LO, seriesDescription);
    cpTags.setString(Tag.ImageType, VR.CS, ObliqueMpr.imageTypes);
    cpTags.setString(Tag.FrameOfReferenceUID, VR.UI, frameOfReferenceUID);
    return cpTags;
  }

  protected double initSliceSpace() {
    if (sourceStack == null || sourceStack.isEmpty()) {
      return 0.0;
    }

    double totalSpace = 0.0;
    double lastSpace = 0.0;
    double[] firstPos = (double[]) sourceStack.getFirst().getTagValue(TagW.SlicePosition);
    if (firstPos == null || firstPos.length != 3) {
      return 0.0;
    }

    double gantryTilt = getGantryTilt();
    Vector3d lastPosVector = new Vector3d(firstPos[0], firstPos[1], firstPos[2]);
    for (int i = 1; i < sourceStack.size(); i++) {
      double[] sp = (double[]) sourceStack.get(i).getTagValue(TagW.SlicePosition);
      if (sp == null || sp.length != 3) {
        continue;
      }

      Vector3d currentPosVector = new Vector3d(sp[0], sp[1], sp[2]);
      double space = lastPosVector.distance(currentPosVector);
      if (gantryTilt != 0) {
        space = correctSpaceForGantryTilt(space, gantryTilt);
      }
      if (i > 1 && Math.abs(lastSpace - space) > EPSILON) {
        this.variableSliceSpacing = true;
      }
      totalSpace += space;
      lastSpace = space;
      lastPosVector.set(currentPosVector);
    }

    return totalSpace / (sourceStack.size() - 1);
  }

  private double correctSpaceForGantryTilt(double measuredSpace, double gantryTilt) {
    // The corrected spacing is the measured spacing divided by the cosine of the tilt angle
    // This accounts for the fact that the actual slice thickness is larger when tilted
    return measuredSpace / Math.cos(gantryTilt);
  }

  private double getGantryTilt() {
    Vector3d col = new Vector3d(getStartingImage().getSliceGeometry().getColumn());
    Vector3d row = new Vector3d(getStartingImage().getSliceGeometry().getRow());

    // The tilt angle is the deviation from vertical in patient's Z axis
    double tilt =
        switch (plane) {
          case AXIAL -> col.z();
          case CORONAL -> col.y();
          case SAGITTAL -> row.z();
        };
    if (Math.abs(tilt) <= EPSILON) {
      return 0.0;
    }
    // Subtract the angle from pi/2 to get the angle with the vertical axis
    return (Math.PI / 2.0) - Math.acos(tilt);
  }

  public double getSliceSpace() {
    return sliceSpace;
  }

  public boolean isVariableSliceSpacing() {
    return variableSliceSpacing;
  }

  public abstract void generate(BuildContext context);

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    OriginalStack that = (OriginalStack) o;
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
}
