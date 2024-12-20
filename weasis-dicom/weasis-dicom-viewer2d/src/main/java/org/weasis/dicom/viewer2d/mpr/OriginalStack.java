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

import static org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation.AXIAL;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.viewer2d.mpr.MprView.SliceOrientation;

public abstract class OriginalStack extends AbstractStack {
  protected static final double EPSILON = 1e-3;
  protected static final File MPR_CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(
          AppProperties.FILE_CACHE_DIR.getName(), "mpr"); // NON-NLS
  static TagW seriesReferences = new TagW("series.builder.refs", TagType.STRING, 2, 2);
  static final int[] COPIED_ATTRS = {
    Tag.SpecificCharacterSet,
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
      SliceOrientation sliceOrientation,
      MediaSeries<DicomImageElement> series,
      Filter<DicomImageElement> filter) {
    super(sliceOrientation, series);
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
    return stackOrientation == AXIAL ? getLastImage() : getFirstImage();
  }

  public GeometryOfSlice getFistSliceGeometry() {
    return fistSliceGeometry;
  }

  protected Attributes getCommonAttributes(
      String frameOfReferenceUID, String seriesDescription, String[] imageType) {
    final Attributes attributes = getMiddleImage().getMediaReader().getDicomObject();
    final Attributes cpTags = new Attributes(attributes, COPIED_ATTRS);
    cpTags.setString(Tag.SeriesDescription, VR.LO, seriesDescription);
    cpTags.setString(Tag.ImageType, VR.CS, imageType);
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
    double lastPos = firstPos[0] + firstPos[1] + firstPos[2];

    for (int i = 1; i < sourceStack.size(); i++) {
      double[] sp = (double[]) sourceStack.get(i).getTagValue(TagW.SlicePosition);
      double pos = sp[0] + sp[1] + sp[2];
      double space = Math.abs(pos - lastPos);
      if (i > 1 && Math.abs(lastSpace - space) > EPSILON) {
        this.variableSliceSpacing = true;
      }
      totalSpace += space;
      lastSpace = space;
      lastPos = pos;
    }

    return totalSpace / (sourceStack.size() - 1);
  }

  public double getSliceSpace() {
    return sliceSpace;
  }

  public boolean isVariableSliceSpacing() {
    return variableSliceSpacing;
  }

  public abstract void generate(BuildContext context);
}
