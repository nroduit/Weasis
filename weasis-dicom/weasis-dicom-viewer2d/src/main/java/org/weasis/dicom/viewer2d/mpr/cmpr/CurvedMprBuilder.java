/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr.cmpr;

import java.awt.geom.Point2D;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.UIDUtils;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.opencv.core.Point3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Taggable;
import org.weasis.core.ui.model.graphic.imp.line.PolylineGraphic;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.MprView.Plane;
import org.weasis.dicom.viewer2d.mpr.Volume;

/**
 * Static helpers that turn a user-drawn polyline on an axial MPR view into curved-MPR derivatives:
 *
 * <ul>
 *   <li>{@link #buildAxis} — assembles the {@link CurvedMprAxis} + {@link CurvedMprImageIO} pair
 *       used by the in-container panoramic cell. The caller (typically {@code MprContainer}) is
 *       responsible for switching its layout and binding the axis to a cell.
 *   <li>{@link #openCrossSectionSeries} — builds a full DICOM series of perpendicular cross-cut
 *       slabs, registers it under the source study in the {@link DicomModel} and opens it in a new
 *       viewer tab.
 * </ul>
 *
 * Tag bookkeeping is centralised here so the panoramic and cross-section IOs always inherit the
 * same patient/study identity and photometric/VOI metadata from the source axial image.
 */
public final class CurvedMprBuilder {

  private static final Logger LOGGER = LoggerFactory.getLogger(CurvedMprBuilder.class);

  private static final int[] BASE_TAG_IDS = {
    Tag.PatientID,
    Tag.PatientName,
    Tag.PatientBirthDate,
    Tag.PatientSex,
    Tag.StudyInstanceUID,
    Tag.StudyID,
    Tag.StudyDate,
    Tag.StudyTime,
    Tag.AccessionNumber,
    Tag.ReferringPhysicianName,
    Tag.SOPClassUID,
    Tag.Modality,
    Tag.BodyPartExamined,
    Tag.PhotometricInterpretation,
    Tag.SamplesPerPixel,
    Tag.BitsAllocated,
    Tag.BitsStored,
    Tag.HighBit,
    Tag.PixelRepresentation
  };

  private static final int[] VOI_LUT_ATTRS = {
    Tag.WindowCenter,
    Tag.WindowWidth,
    Tag.WindowCenterWidthExplanation,
    Tag.VOILUTFunction,
    Tag.VOILUTSequence,
    Tag.Units
  };

  private CurvedMprBuilder() {}

  /**
   * Validate inputs and assemble a {@link CurvedMprAxis} + {@link CurvedMprImageIO} ready for
   * display. The axis is <em>not</em> bound to the polyline yet — the caller does that once the
   * container has positioned a {@link CurvedMprView} cell, so live-edit updates only start after
   * the cell is actually visible.
   *
   * @return the assembled axis, or {@link Optional#empty()} when the polyline is too short or the
   *     view is missing controller/volume state.
   */
  public static Optional<CurvedMprAxis> buildAxis(MprView sourceView, PolylineGraphic polyline) {
    if (sourceView == null || polyline == null) {
      return Optional.empty();
    }
    List<Vector3d> curvePoints3D = extractCurvePoints(sourceView, polyline);
    if (curvePoints3D.size() < 2) {
      LOGGER.warn("Cannot open curved MPR: fewer than 2 valid curve points");
      return Optional.empty();
    }
    MprController controller = sourceView.getMprController();
    if (controller == null || controller.getVolume() == null) {
      LOGGER.warn("Cannot open curved MPR: no controller/volume available");
      return Optional.empty();
    }

    Vector3d planeNormal = effectivePlaneNormal(sourceView);
    CurvedMprAxis axis = new CurvedMprAxis(controller.getVolume(), curvePoints3D, planeNormal);
    CurvedMprImageIO io = new CurvedMprImageIO(axis);
    axis.setIo(io);

    DicomImageElement refImg = sourceView.getImage();
    if (refImg != null) {
      copyBaseTags(io, refImg);
      io.setBaseAttributes(buildBaseAttributes(refImg));
      axis.setPixelSpacingUnit(refImg.getPixelSpacingUnit());
    }
    axis.setImageElement(new DicomImageElement(io, 0));
    return Optional.of(axis);
  }

  /**
   * Build a DICOM series of perpendicular cross-section slabs along the polyline and open it in a
   * new viewer tab under the source study in the explorer. Returns {@code true} on success.
   */
  public static boolean openCrossSectionSeries(
      MprView sourceView, PolylineGraphic polyline, CrossSectionParams params) {
    if (sourceView == null || polyline == null || params == null) {
      return false;
    }
    List<Vector3d> curvePoints3D = extractCurvePoints(sourceView, polyline);
    if (curvePoints3D.size() < 2) {
      LOGGER.warn("Cannot build cross-sections: fewer than 2 valid curve points");
      return false;
    }
    MprController controller = sourceView.getMprController();
    Volume<?, ?> volume = controller == null ? null : controller.getVolume();
    DicomImageElement refImg = sourceView.getImage();
    DataExplorerModel explorerModel =
        sourceView.getSeries() == null
            ? null
            : (DataExplorerModel) sourceView.getSeries().getTagValue(TagW.ExplorerModel);
    if (volume == null || refImg == null || !(explorerModel instanceof DicomModel dicomModel)) {
      LOGGER.warn("Cannot build cross-sections: missing volume/refImg/DicomModel");
      return false;
    }

    Vector3d planeNormal = effectivePlaneNormal(sourceView);
    double pixelMm = volume.getMinPixelRatio();
    // Sample directly at the requested cut spacing — one sample == one cross-section.
    CurveSampler.Sampling sampling =
        CurveSampler.sample(curvePoints3D, planeNormal, params.spacingMm(), pixelMm);
    List<Vector3d> sampled = sampling.sampledPoints();
    List<Vector3d> perps = sampling.perpDirections();
    if (sampled.isEmpty()) {
      LOGGER.warn("Cannot build cross-sections: curve sampling produced no points");
      return false;
    }

    String seriesUid = UIDUtils.createUID();
    DicomSeries series = new DicomSeries(seriesUid, null, DicomModel.series.tagView());
    series.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUid);

    Attributes srcHeader = refImg.getMediaReader().getDicomObject();
    Attributes baseHeader = srcHeader == null ? new Attributes() : new Attributes(srcHeader);
    baseHeader.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    String desc = baseHeader.getString(Tag.SeriesDescription);
    String csDesc = "Curved MPR Cross-Sections"; // NON-NLS
    baseHeader.setString(
        Tag.SeriesDescription, VR.LO, desc == null ? csDesc : desc + " [%s]".formatted(csDesc));
    // The slabs are perpendicular to the curve, so the source axial slice's localization (inherited
    // by cloning its header) does not describe them. Drop it rather than emit a wrong orientation:
    // the cross-sections are then shown without patient-space orientation/position (and
    // crosslines).
    baseHeader.remove(Tag.ImageOrientationPatient);
    baseHeader.remove(Tag.ImagePositionPatient);
    baseHeader.remove(Tag.SliceLocation);
    DicomMediaUtils.writeMetaData(series, baseHeader);
    series.setTag(TagW.ExplorerModel, dicomModel);

    // Each slab is rendered and written to a real DICOM file (header + pixel data), then
    // re-imported as a genuine on-disk image. This makes the resulting series behave like any
    // imported series — in particular it exports with pixel data, unlike a purely synthetic reader
    // whose getDicomObject() carries headers only.
    Path tempDir = AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "cmpr");

    int instance = 0;
    for (int i = 0; i < sampled.size(); i++) {
      instance++;
      CrossSectionImageIO io =
          new CrossSectionImageIO(
              volume, sampled.get(i), perps.get(i), params.widthMm(), params.heightMm(), instance);
      copyBaseTags(io, refImg);
      String sopUid = (String) io.getTagValue(TagD.get(Tag.SOPInstanceUID));
      Attributes perInstance = new Attributes(baseHeader);
      perInstance.setString(Tag.SOPInstanceUID, VR.UI, sopUid);
      perInstance.setInt(Tag.InstanceNumber, VR.IS, instance);
      io.setBaseAttributes(perInstance);
      io.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUid);

      File file = new File(tempDir.toFile(), sopUid);
      if (io.buildFile(file)) {
        addDicomFileToSeries(series, file);
      } else {
        LOGGER.warn("Failed to write cross-section slice {}", instance);
      }
    }

    if (series.size(null) == 0) {
      LOGGER.warn("Cross-section series ended up empty");
      return false;
    }

    // Look up the study via the original source series carried by the volume's stack — the
    // MprView's own series is an internally derived series and is not a child of any study in
    // the DicomModel hierarchy, so getParent() on it would return null.
    MediaSeriesGroup study = dicomModel.getParent(volume.getStack().getSeries(), DicomModel.study);
    LOGGER.info("Built {} cross-section slices for curved MPR", instance);
    MipView.openSeries(series, explorerModel, dicomModel, study, true);
    return true;
  }

  /**
   * Convert the polyline's screen-space handles to volume voxel coordinates on the source view's
   * current plane.
   */
  public static List<Vector3d> extractCurvePoints(MprView sourceView, PolylineGraphic polyline) {
    List<Vector3d> points = new ArrayList<>();
    for (Point2D pt : polyline.getHandlePointList()) {
      if (pt != null) {
        Point3 v = sourceView.getVolumeCoordinatesFromImage(pt.getX(), pt.getY());
        if (v != null) {
          points.add(new Vector3d(v.x, v.y, v.z));
        }
      }
    }
    return points;
  }

  /**
   * Plane normal of the source view, transformed by the controller's current rotation. Falls back
   * to the canonical plane direction when no rotation is present.
   */
  public static Vector3d effectivePlaneNormal(MprView sourceView) {
    Plane plane = sourceView.getPlane();
    Vector3d planeNormal = plane.getDirection();
    Quaterniond rotation = sourceView.getMprController().getRotation(plane);
    if (rotation != null) {
      rotation.transform(planeNormal);
    }
    return planeNormal;
  }

  /** Read back a written cross-section file and append its image element(s) to the series. */
  private static void addDicomFileToSeries(DicomSeries series, File file) {
    try {
      DicomMediaIO reader = new DicomMediaIO(file);
      if (reader.isReadableDicom()) {
        DicomImageElement[] elements = reader.getMediaElement();
        if (elements != null) {
          for (DicomImageElement element : elements) {
            series.addMedia(element);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.error("Cannot load cross-section DICOM file: {}", file, e);
    }
  }

  /** Copy the standard curved-MPR base tag set onto any {@link Taggable} reader. */
  public static void copyBaseTags(Taggable receiver, DicomImageElement refImg) {
    receiver.copyTags(TagD.getTagFromIDs(BASE_TAG_IDS), refImg, false);
  }

  /**
   * Build a minimal {@link Attributes} carrying patient/study identity plus the VOI/window tags for
   * the panoramic header. Cross-sections clone the full source header instead (they need every
   * photometric/VOI tag in order to drop in as a real DICOM series in the explorer).
   */
  public static Attributes buildBaseAttributes(DicomImageElement refImg) {
    Attributes attrs = new Attributes();
    Object val = refImg.getTagValue(TagD.get(Tag.PatientID));
    if (val != null) attrs.setString(Tag.PatientID, VR.LO, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.PatientName));
    if (val != null) attrs.setString(Tag.PatientName, VR.PN, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.StudyInstanceUID));
    if (val != null) attrs.setString(Tag.StudyInstanceUID, VR.UI, val.toString());
    val = refImg.getTagValue(TagD.get(Tag.Modality));
    if (val != null) attrs.setString(Tag.Modality, VR.CS, val.toString());

    Attributes src = refImg.getMediaReader().getDicomObject();
    if (src != null) {
      attrs.addSelected(src, VOI_LUT_ATTRS);
    }
    return attrs;
  }
}
