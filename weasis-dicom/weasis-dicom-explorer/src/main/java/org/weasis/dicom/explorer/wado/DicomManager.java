/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagView;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomFieldsView.DicomData;
import org.weasis.dicom.explorer.Messages;

public class DicomManager {

  /** The single instance of this singleton class. */
  private static DicomManager instance;

  private boolean portableDirCache;
  private final List<DicomData> limitedDicomTags;

  private DicomManager() {
    limitedDicomTags = new ArrayList<>();
    portableDirCache = true;
    initRequiredDicomTags();
  }

  /**
   * Return the single instance of this class. This method guarantees the singleton property of this
   * class.
   */
  public static synchronized DicomManager getInstance() {
    if (instance == null) {
      instance = new DicomManager();
    }
    return instance;
  }

  private void initRequiredDicomTags() {
    TagView[] patient = {
      new TagView(TagD.get(Tag.PatientName)),
      new TagView(TagD.get(Tag.PatientID)),
      new TagView(TagD.get(Tag.IssuerOfPatientID)),
      new TagView(TagD.get(Tag.PatientSex)),
      new TagView(TagD.get(Tag.PatientBirthDate))
    };

    final TagView[] station = {
      new TagView(TagD.get(Tag.Manufacturer)),
      new TagView(TagD.get(Tag.ManufacturerModelName)),
      new TagView(TagD.get(Tag.StationName))
    };

    TagView[] study = {
      new TagView(TagD.get(Tag.StudyInstanceUID)),
      new TagView(TagD.get(Tag.StudyDate)),
      new TagView(TagD.get(Tag.StudyTime)),
      new TagView(TagD.get(Tag.StudyID)),
      new TagView(TagD.get(Tag.AccessionNumber)),
      new TagView(TagD.get(Tag.StudyDescription)),
      new TagView(TagD.get(Tag.StudyComments))
    };

    TagView[] series = {
      new TagView(TagD.get(Tag.SeriesInstanceUID)),
      new TagView(TagD.get(Tag.SeriesDate)),
      new TagView(TagD.get(Tag.SeriesTime)),
      new TagView(TagD.get(Tag.SeriesNumber)),
      new TagView(TagD.get(Tag.Modality)),
      new TagView(TagD.get(Tag.ReferringPhysicianName)),
      new TagView(TagD.get(Tag.InstitutionName)),
      new TagView(TagD.get(Tag.InstitutionalDepartmentName)),
      new TagView(TagD.get(Tag.SeriesDescription)),
      new TagView(TagD.get(Tag.BodyPartExamined))
    };

    TagView[] image = {
      new TagView(TagD.get(Tag.SOPInstanceUID)),
      new TagView(TagD.getTagFromIDs(Tag.FrameType, Tag.ImageType)),
      new TagView(TagD.get(Tag.SOPClassUID)),
      new TagView(TagD.get(Tag.TransferSyntaxUID)),
      new TagView(TagD.get(Tag.InstanceNumber)),
      new TagView(TagD.get(Tag.ImageComments)),
      new TagView(TagD.getTagFromIDs(Tag.FrameLaterality, Tag.ImageLaterality, Tag.Laterality)),
      new TagView(TagD.get(Tag.PhotometricInterpretation)),
      new TagView(TagD.get(Tag.SamplesPerPixel)),
      new TagView(TagD.get(Tag.PixelRepresentation)),
      new TagView(TagD.get(Tag.Columns)),
      new TagView(TagD.get(Tag.Rows)),
      new TagView(TagD.get(Tag.BitsAllocated)),
      new TagView(TagD.get(Tag.BitsStored))
    };

    TagView[] imgPlane = {
      new TagView(TagD.get(Tag.PixelSpacing)),
      new TagView(TagD.get(Tag.SliceLocation)),
      new TagView(TagD.get(Tag.SliceThickness)),
      new TagView(TagD.get(Tag.ImagePositionPatient)),
      new TagView(TagD.get(Tag.ImageOrientationPatient)),
      new TagView(TagD.get(Tag.StudyComments))
    };

    TagView[] imgAcq = {
      new TagView(TagD.get(Tag.KVP)), new TagView(TagD.get(Tag.ContrastBolusAgent))
    };

    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.pat"), patient, TagD.Level.PATIENT));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.station"), station, TagD.Level.SERIES));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.study"), study, TagD.Level.STUDY));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.series"), series, TagD.Level.SERIES));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.object"), image, TagD.Level.INSTANCE));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.plane"), imgPlane, TagD.Level.INSTANCE));
    limitedDicomTags.add(
        new DicomData(Messages.getString("DicomFieldsView.acqu"), imgAcq, TagD.Level.INSTANCE));
  }

  public List<DicomData> getLimitedDicomTags() {
    return limitedDicomTags;
  }

  public boolean isPortableDirCache() {
    return portableDirCache;
  }

  public void setPortableDirCache(boolean portableDirCache) {
    this.portableDirCache = portableDirCache;
  }
}
