/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import static org.weasis.dicom.codec.geometry.GeometryOfSlice.MIN_SPACING;

import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageAnalyzer;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.RegionAttributes;
import org.weasis.opencv.seg.Segment;

/**
 * RTSTRUCT element: a set of {@link StructRegion} ROIs (one per anatomical structure) with their
 * 3-D contours, observation metadata and references to the underlying CT/MR series.
 *
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class StructureSet extends RtSpecialElement implements SpecialElementRegion {
  private final Map<String, Map<String, Set<LazyContourLoader>>> refMap = new HashMap<>();
  private final Map<Integer, StructRegion> segAttributes = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  public StructureSet(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public Map<String, Map<String, Set<LazyContourLoader>>> getRefMap() {
    return refMap;
  }

  @Override
  public NavigableMap<Double, Set<LazyContourLoader>> getPositionMap() {
    return SpecialElementRegion.emptyPositionMap();
  }

  public Map<Integer, StructRegion> getSegAttributes() {
    return segAttributes;
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
  }

  public float getOpacity() {
    return opacity;
  }

  public void setOpacity(float opacity) {
    this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
    updateOpacityInSegAttributes(this.opacity);
  }

  @Override
  public String toString() {
    String name = TagD.getTagValue(getMediaReader(), Tag.StructureSetName, String.class);
    if (!StringUtil.hasText(label)) {
      return StringUtil.hasText(name) ? name : TagW.NO_VALUE;
    }
    return (StringUtil.hasText(name) && !name.equals(label)) ? label + " (" + name + ")" : label;
  }

  @Override
  public void initReferences(String originSeriesUID) {
    refMap.clear();
    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems == null) {
      return;
    }
    Sequence seriesRef = dcmItems.getSequence(Tag.ReferencedFrameOfReferenceSequence);
    if (seriesRef == null) {
      return;
    }
    for (Attributes ref : seriesRef) {
      Sequence refSeq = ref.getSequence(Tag.RTReferencedStudySequence);
      if (refSeq != null) {
        for (Attributes refStudy : refSeq) {
          processReferencedStudy(refStudy, originSeriesUID);
        }
      }
    }
  }

  private void processReferencedStudy(Attributes refStudy, String originSeriesUID) {
    Sequence refSeriesSeq = refStudy.getSequence(Tag.RTReferencedSeriesSequence);
    if (refSeriesSeq == null) {
      return;
    }
    for (Attributes refSeries : refSeriesSeq) {
      String seriesUID = refSeries.getString(Tag.SeriesInstanceUID);
      if (!StringUtil.hasText(seriesUID)) {
        continue;
      }
      HiddenSeriesManager.getInstance()
          .reference2Series
          .computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>())
          .add(originSeriesUID);

      Map<String, Set<LazyContourLoader>> map =
          refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
      Sequence instanceSeq = refSeries.getSequence(Tag.ContourImageSequence);
      if (instanceSeq != null) {
        for (Attributes instance : instanceSeq) {
          String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUID);
          if (StringUtil.hasText(sopInstanceUID)) {
            map.put(sopInstanceUID, null);
          }
        }
      }
    }
  }

  /**
   * Parses the structure set ROIs, observations and per-slice contours from the underlying DICOM
   * dataset. Must be called once {@code img} (any image of the referenced CT/MR series) is known so
   * that contours can be projected into the patient image grid.
   */
  public void initContours(DicomImageElement img) {
    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems == null) {
      return;
    }
    String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
    setTagNoNull(TagD.get(Tag.StructureSetName), dcmItems.getString(Tag.StructureSetName));
    Date datetime = dcmItems.getDate(Tag.StructureSetDateAndTime);
    setTagNoNull(TagD.get(Tag.StructureSetDate), datetime);

    loadRoiDefinitions(dcmItems);
    applyRoiObservations(dcmItems);
    loadRoiContours(dcmItems, img, seriesUID);
  }

  private void loadRoiDefinitions(Attributes dcmItems) {
    Sequence structRoiSeq = dcmItems.getSequence(Tag.StructureSetROISequence);
    if (structRoiSeq == null) {
      return;
    }
    for (Attributes ssROIseq : structRoiSeq) {
      int nb = ssROIseq.getInt(Tag.ROINumber, -1);
      String name = ssROIseq.getString(Tag.ROIName);
      if (!StringUtil.hasText(name)) {
        name = "ROI_" + nb; // NON-NLS
      }
      StructRegion region = new StructRegion(nb, name, null);
      region.setDescription(ssROIseq.getString(Tag.ROIDescription));
      region.setType(ssROIseq.getString(Tag.ROIGenerationAlgorithm));
      region.setInteriorOpacity(0.2f);
      segAttributes.put(nb, region);
    }
  }

  private void applyRoiObservations(Attributes dcmItems) {
    Sequence roiObsSeq = dcmItems.getSequence(Tag.RTROIObservationsSequence);
    if (roiObsSeq == null) {
      return;
    }
    for (Attributes obs : roiObsSeq) {
      StructRegion region = segAttributes.get(obs.getInt(Tag.ReferencedROINumber, -1));
      if (region != null) {
        region.setObservationNumber(obs.getInt(Tag.ObservationNumber, -1));
        region.setRtRoiInterpretedType(obs.getString(Tag.RTROIInterpretedType));
        region.setRoiObservationLabel(obs.getString(Tag.ROIObservationLabel));
      }
    }
  }

  private void loadRoiContours(Attributes dcmItems, DicomImageElement img, String seriesUID) {
    Sequence roiContSeq = dcmItems.getSequence(Tag.ROIContourSequence);
    if (roiContSeq == null) {
      return;
    }
    for (Attributes roiContourSeq : roiContSeq) {
      int nb = roiContourSeq.getInt(Tag.ReferencedROINumber, -1);
      if (nb == -1) {
        continue;
      }
      StructRegion region = segAttributes.get(nb);
      if (region == null) {
        continue;
      }
      if (region.getColor() == null) {
        region.setColor(RegionAttributes.getColor(extractRoiColor(roiContourSeq), nb, opacity));
      }

      Map<KeyDouble, List<StructContour>> planes = new HashMap<>();
      Sequence contourSeq = roiContourSeq.getSequence(Tag.ContourSequence);
      if (contourSeq != null) {
        for (Attributes contour : contourSeq) {
          parseRoiContour(contour, img, region, nb, seriesUID, planes);
        }
      }
      region.setThickness(RtSet.calculatePlaneThickness(planes.keySet()));
      region.setPlanes(planes);
    }
  }

  private void parseRoiContour(
      Attributes contour,
      DicomImageElement img,
      StructRegion region,
      int nb,
      String seriesUID,
      Map<KeyDouble, List<StructContour>> planes) {
    double[] pts = contour.getDoubles(Tag.ContourData);
    if (pts == null || pts.length % 3 != 0 || pts.length < 6) {
      return;
    }
    StructContour plane = buildGraphic(img, contour, nb, region, pts);
    if (plane == null) {
      return;
    }
    plane.setPoints(pts);

    PlaneContourLoader contours = new PlaneContourLoader();
    contours.addContour(plane);
    attachContourToImageRefs(contour, contours, seriesUID);

    // Multiple contours can share the same z position (e.g. holes or disjoint regions)
    KeyDouble z = new KeyDouble(plane.getPositionZ());
    planes.computeIfAbsent(z, _ -> new ArrayList<>(2)).add(plane);
  }

  private void attachContourToImageRefs(
      Attributes contour, PlaneContourLoader contours, String seriesUID) {
    Sequence contImgSeq = contour.getSequence(Tag.ContourImageSequence);
    if (contImgSeq == null) {
      return;
    }
    Map<String, Set<LazyContourLoader>> seriesMap = refMap.get(seriesUID);
    if (seriesMap == null) {
      return;
    }
    for (Attributes attributes : contImgSeq) {
      String sopUID = attributes.getString(Tag.ReferencedSOPInstanceUID);
      if (StringUtil.hasText(sopUID)) {
        seriesMap.computeIfAbsent(sopUID, _ -> new LinkedHashSet<>()).add(contours);
      }
    }
  }

  private static int[] extractRoiColor(Attributes roiContourSeq) {
    String[] valColors = roiContourSeq.getStrings(Tag.ROIDisplayColor);
    if (valColors == null || valColors.length != 3) {
      return null;
    }
    try {
      return new int[] {
        Integer.parseInt(valColors[0]),
        Integer.parseInt(valColors[1]),
        Integer.parseInt(valColors[2])
      };
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static StructContour buildGraphic(
      DicomImageElement img, Attributes contour, int id, StructRegion region, double[] points) {
    if (img == null) {
      return null;
    }
    if (region.getMeasurableLayer() == null) {
      region.setMeasurableLayer(getMeasurableLayer(img, contour));
    }
    GeometryOfSlice geometry = img.getSliceGeometry();
    Vector3d voxelSpacing = geometry.getVoxelSpacing();
    if (voxelSpacing.x < MIN_SPACING || voxelSpacing.y < MIN_SPACING) {
      return null;
    }
    Path2D path = getPath2D(geometry, points, voxelSpacing);
    if ("CLOSED_PLANAR".equals(contour.getString(Tag.ContourGeometricType))) { // NON-NLS
      path.closePath();
    }

    Mat binary = Mat.zeros(img.getImage().size(), CvType.CV_8UC1);
    try {
      List<MatOfPoint> pts = ImageAnalyzer.transformShapeToContour(path, true);
      Imgproc.fillPoly(binary, pts, new Scalar(255));
      List<Segment> segmentList = Region.buildSegmentList(ImageCV.fromMat(binary));
      int nbPixels = Core.countNonZero(binary);

      StructContour segContour = new StructContour(String.valueOf(id), segmentList, nbPixels);
      segContour.setPositionZ(points[2]);
      segContour.setAttributes(region);
      region.addPixels(segContour);
      return segContour;
    } finally {
      ImageConversion.releasePlanarImage(ImageCV.fromMat(binary));
    }
  }

  private static Path2D getPath2D(
      GeometryOfSlice geometry, double[] points, Vector3d voxelSpacing) {
    Vector3d tlhc = geometry.getTLHC();
    Vector3d row = geometry.getRow();
    Vector3d column = geometry.getColumn();
    double invX = 1.0 / voxelSpacing.x;
    double invY = 1.0 / voxelSpacing.y;

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    for (int i = 0; i < points.length; i += 3) {
      double dx = points[i] - tlhc.x;
      double dy = points[i + 1] - tlhc.y;
      double dz = points[i + 2] - tlhc.z;
      double x = (dx * row.x + dy * row.y + dz * row.z) * invX;
      double y = (dx * column.x + dy * column.y + dz * column.z) * invY;
      if (i == 0) {
        path.moveTo(x, y);
      } else {
        path.lineTo(x, y);
      }
    }
    return path;
  }

  private static SegMeasurableLayer<DicomImageElement> getMeasurableLayer(
      DicomImageElement img, Attributes contour) {
    if (img == null) {
      return null;
    }
    double slabThickness =
        DicomUtils.getDoubleFromDicomElement(contour, Tag.ContourSlabThickness, 1.0);
    return new SegMeasurableLayer<>(img, slabThickness);
  }
}
