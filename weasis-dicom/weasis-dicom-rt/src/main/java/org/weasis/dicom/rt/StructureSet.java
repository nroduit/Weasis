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

import java.awt.Color;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegMeasurableLayer;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.HiddenSeriesManager;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;
import org.weasis.opencv.seg.Region;
import org.weasis.opencv.seg.RegionAttributes;
import org.weasis.opencv.seg.Segment;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class StructureSet extends RtSpecialElement implements SpecialElementRegion {
  private final Map<String, Map<String, Set<SegContour>>> refMap = new HashMap<>();
  private final Map<Integer, StructRegion> segAttributes = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  public StructureSet(DicomMediaIO mediaIO) {
    super(mediaIO);
  }

  public Map<String, Map<String, Set<SegContour>>> getRefMap() {
    return refMap;
  }

  @Override
  public Map<String, Set<SegContour>> getPositionMap() {
    return Map.of();
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
    this.opacity = Math.max(0.0f, Math.min(opacity, 1.0f));
    updateOpacityInSegAttributes(this.opacity);
  }

  @Override
  public void initReferences(String originSeriesUID) {
    refMap.clear();
    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems != null) {
      Sequence seriesRef = dcmItems.getSequence(Tag.ReferencedFrameOfReferenceSequence);
      if (seriesRef != null) {
        for (Attributes ref : seriesRef) {
          String frameUID = ref.getString(Tag.FrameOfReferenceUID);
          Sequence refSeq = ref.getSequence(Tag.RTReferencedStudySequence);
          if (refSeq != null) {
            for (Attributes refStudy : refSeq) {
              String studyUID = refStudy.getString(Tag.ReferencedSOPInstanceUID);
              Sequence refSeriesSeq = refStudy.getSequence(Tag.RTReferencedSeriesSequence);
              if (refSeriesSeq != null) {
                for (Attributes refSeries : refSeriesSeq) {
                  String seriesUID = refSeries.getString(Tag.SeriesInstanceUID);
                  if (StringUtil.hasText(seriesUID)) {
                    HiddenSeriesManager.getInstance()
                        .reference2Series
                        .computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>())
                        .add(originSeriesUID);

                    Map<String, Set<SegContour>> map =
                        refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
                    Sequence instanceSeq = refSeries.getSequence(Tag.ContourImageSequence);
                    if (instanceSeq != null) {
                      for (Attributes instance : instanceSeq) {
                        String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUID);
                        if (StringUtil.hasText(sopInstanceUID)) {
                          map.computeIfAbsent(sopInstanceUID, _ -> new LinkedHashSet<>());
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }
    }
  }

  public void initContours(DicomImageElement img) {
    Attributes dcmItems = getMediaReader().getDicomObject();
    if (dcmItems != null) {
      String seriesUID = TagD.getTagValue(img, Tag.SeriesInstanceUID, String.class);
      String label = dcmItems.getString(Tag.StructureSetLabel);
      Date datetime = dcmItems.getDate(Tag.StructureSetDateAndTime);

      // Locate the name and number of each ROI
      Sequence structRoiSeq = dcmItems.getSequence(Tag.StructureSetROISequence);
      if (structRoiSeq != null) {
        for (Attributes ssROIseq : structRoiSeq) {
          int nb = ssROIseq.getInt(Tag.ROINumber, -1);
          String name = ssROIseq.getString(Tag.ROIName);
          if (!StringUtil.hasText(name)) {
            name = "ROI_" + nb; // NON-NLS
          }

          StructRegion structRegion = new StructRegion(nb, name, null);

          structRegion.setDescription(ssROIseq.getString(Tag.ROIDescription));
          structRegion.setType(ssROIseq.getString(Tag.ROIGenerationAlgorithm));
          structRegion.setInteriorOpacity(0.2f);

          segAttributes.put(nb, structRegion);
        }
      }

      // Determine the type of each structure (PTV, organ, external, etc)
      Sequence roiObsSeq = dcmItems.getSequence(Tag.RTROIObservationsSequence);
      if (roiObsSeq != null) {
        for (Attributes rtROIObsSeq : roiObsSeq) {
          StructRegion region = segAttributes.get(rtROIObsSeq.getInt(Tag.ReferencedROINumber, -1));
          if (region != null) {
            region.setObservationNumber(rtROIObsSeq.getInt(Tag.ObservationNumber, -1));
            region.setRtRoiInterpretedType(rtROIObsSeq.getString(Tag.RTROIInterpretedType));
            region.setRoiObservationLabel(rtROIObsSeq.getString(Tag.ROIObservationLabel));
          }
        }
      }

      // The coordinate data of each ROI is stored within ROIContourSequence
      Sequence roiContSeq = dcmItems.getSequence(Tag.ROIContourSequence);
      if (roiContSeq != null) {
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
            // Get the RGB color triplet for the current ROI if it exists
            String[] valColors = roiContourSeq.getStrings(Tag.ROIDisplayColor);
            int[] rgb;
            if (valColors != null && valColors.length == 3) {
              rgb =
                  new int[] {
                    Integer.parseInt(valColors[0]),
                    Integer.parseInt(valColors[1]),
                    Integer.parseInt(valColors[2])
                  };
            } else {
              rgb = null;
            }

            Color rgbColor = RegionAttributes.getColor(rgb, nb, opacity);
            region.setColor(rgbColor);
          }

          Map<KeyDouble, List<StructContour>> planes = new HashMap<>();

          Sequence contourSeq = roiContourSeq.getSequence(Tag.ContourSequence);
          if (contourSeq != null) {
            // Locate the contour sequence for each referenced ROI
            for (Attributes contour : contourSeq) {
              // For each plane, initialize a new plane dictionary
              StructContour plane = buildGraphic(img, contour, nb, region);
              if (plane == null) {
                continue;
              }

              // Each plane which coincides with an image slice will have a unique ID
              // take the first one
              Sequence contImgSeq = contour.getSequence(Tag.ContourImageSequence);
              if (contImgSeq != null) {
                for (Attributes attributes : contImgSeq) {
                  String sopUID = attributes.getString(Tag.ReferencedSOPInstanceUID);
                  if (StringUtil.hasText(sopUID)) {
                    plane.setPoints(contour.getDoubles(Tag.ContourData));
                    refMap
                        .get(seriesUID)
                        .computeIfAbsent(sopUID, _ -> new LinkedHashSet<>())
                        .add(plane);
                  }
                }
              }

              // Add each plane to the planes' dictionary of the current ROI
              KeyDouble z = new KeyDouble(plane.getPositionZ());

              // If there are no contour on specific z position
              if (!planes.containsKey(z)) {
                List<StructContour> stack = new ArrayList<>();
                stack.add(plane);
                planes.put(z, stack);
              }
            }
          }

          // Calculate the plane thickness for the current ROI
          region.setThickness(RtSet.calculatePlaneThickness(planes.keySet()));

          // Add the planes' dictionary to the current ROI
          region.setPlanes(planes);
        }
      }
    }
  }

  private static StructContour buildGraphic(
      DicomImageElement img, Attributes contour, int id, StructRegion region) {
    if (region.getMeasurableLayer() == null) {
      region.setMeasurableLayer(getMeasurableLayer(img, contour));
    }

    // Determine all the plane properties
    String geometricType = contour.getString(Tag.ContourGeometricType);

    Double z = null;
    double[] points = contour.getDoubles(Tag.ContourData);
    if (img != null && points != null && points.length % 3 == 0 && points.length > 1) {
      GeometryOfSlice geometry = img.getDispSliceGeometry();
      Vector3d voxelSpacing = geometry.getVoxelSpacing();
      if (voxelSpacing.x < 0.00001 || voxelSpacing.y < 0.00001) {
        return null;
      }
      Path2D path = getPath2D(geometry, points, voxelSpacing);
      z = points[2];
      if ("CLOSED_PLANAR".equals(geometricType)) { // NON-NLS
        path.closePath();
      }

      Mat binary = Mat.zeros(img.getImage().size(), CvType.CV_8UC1);
      List<MatOfPoint> pts = ImageProcessor.transformShapeToContour(path, true);
      Imgproc.fillPoly(binary, pts, new Scalar(255));
      List<Segment> segmentList = Region.buildSegmentList(ImageCV.toImageCV(binary));
      int nbPixels = Core.countNonZero(binary);
      ImageConversion.releasePlanarImage(ImageCV.toImageCV(binary));

      StructContour segContour = new StructContour(String.valueOf(id), segmentList, nbPixels);
      segContour.setPositionZ(z);
      segContour.setAttributes(region);
      region.addPixels(segContour);
      return segContour;
    }

    return null;
  }

  private static Path2D getPath2D(
      GeometryOfSlice geometry, double[] points, Vector3d voxelSpacing) {
    Vector3d tlhc = geometry.getTLHC();
    Vector3d row = geometry.getRow();
    Vector3d column = geometry.getColumn();

    Path2D path = new Path2D.Double(Path2D.WIND_NON_ZERO);
    double x =
        ((points[0] - tlhc.x) * row.x + (points[1] - tlhc.y) * row.y + (points[2] - tlhc.z) * row.z)
            / voxelSpacing.x;
    double y =
        ((points[0] - tlhc.x) * column.x
                + (points[1] - tlhc.y) * column.y
                + (points[2] - tlhc.z) * column.z)
            / voxelSpacing.y;
    path.moveTo(x, y);
    for (int i = 3; i < points.length; i = i + 3) {
      x =
          ((points[i] - tlhc.x) * row.x
                  + (points[i + 1] - tlhc.y) * row.y
                  + (points[i + 2] - tlhc.z) * row.z)
              / voxelSpacing.x;
      y =
          ((points[i] - tlhc.x) * column.x
                  + (points[i + 1] - tlhc.y) * column.y
                  + (points[i + 2] - tlhc.z) * column.z)
              / voxelSpacing.y;
      path.lineTo(x, y);
    }
    return path;
  }

  private static SegMeasurableLayer<DicomImageElement> getMeasurableLayer(
      DicomImageElement img, Attributes contour) {
    if (img != null) {
      double slabThickness =
          DicomUtils.getDoubleFromDicomElement(contour, Tag.ContourSlabThickness, 1.0);
      return new SegMeasurableLayer<>(img, slabThickness);
    }
    return null;
  }
}
