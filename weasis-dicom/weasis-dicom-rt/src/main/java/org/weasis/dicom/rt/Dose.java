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

import static org.opencv.core.Core.addWeighted;
import static org.opencv.core.Core.minMaxLoc;
import static org.opencv.core.Core.multiply;

import java.awt.*;
import java.awt.geom.Point2D;
import java.util.*;
import java.util.List;
import java.util.stream.DoubleStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Segment;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class Dose extends RtSpecialElement implements SpecialElementRegion {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dose.class);
  private final Map<String, Map<String, Set<SegContour>>> refMap = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = false;

  private final double[] imagePositionPatient;
  private final String comment;
  private final String doseUnit;
  private final String doseType;
  private final String doseSummationType;
  private final double[] gridFrameOffsetVector;
  private final double doseGridScaling;
  private double doseMax;

  private final DicomSeries series;
  private double doseSlicePositionThreshold;

  private final Map<Integer, IsoDoseRegion> isoDoseSet = new LinkedHashMap<>();

  private final Map<Integer, Dvh> dvhMap = new HashMap<>();

  // Dose LUTs
  private AbstractMap.SimpleImmutableEntry<double[], double[]> doseMmLUT;
  private AbstractMap.SimpleImmutableEntry<double[], double[]> dosePixLUT;

  public Dose(DicomMediaIO mediaIO) {
    super(mediaIO);
    // Default threshold in mm to determine the max difference from slicePosition to the closest
    // dose frame without interpolation
    this.doseSlicePositionThreshold = 0.5;
    this.series = getMediaReader().getMediaSeries();
    this.doseMax = 0.0;

    Attributes dcmItems = mediaIO.getDicomObject();
    this.imagePositionPatient = dcmItems.getDoubles(Tag.ImagePositionPatient);
    this.comment = dcmItems.getString(Tag.DoseComment);
    this.doseUnit = dcmItems.getString(Tag.DoseUnits);
    this.doseType = dcmItems.getString(Tag.DoseType);
    this.doseSummationType = dcmItems.getString(Tag.DoseSummationType);
    this.gridFrameOffsetVector = dcmItems.getDoubles(Tag.GridFrameOffsetVector);
    this.doseGridScaling = dcmItems.getDouble(Tag.DoseGridScaling, 0.0);
    initDvh(dcmItems.getSequence(Tag.DVHSequence));
  }

  private void initDvh(Sequence dvhSeq) {
    if (dvhSeq != null) {
      for (Attributes dvhAttributes : dvhSeq) {

        // Need to refer to delineated contour
        Dvh rtDvh = null;
        Sequence dvhRefRoiSeq = dvhAttributes.getSequence(Tag.DVHReferencedROISequence);
        if (dvhRefRoiSeq == null) {
          continue;
        } else if (dvhRefRoiSeq.size() == 1) {
          rtDvh = new Dvh();
          Attributes dvhRefRoiAttributes = dvhRefRoiSeq.getFirst();
          rtDvh.setReferencedRoiNumber(dvhRefRoiAttributes.getInt(Tag.ReferencedROINumber, -1));

          LOGGER.debug("Found DVH for ROI: {}", rtDvh.getReferencedRoiNumber());
        }

        if (rtDvh != null) {
          rtDvh.setDvhSource(DataSource.PROVIDED);
          // Convert Differential DVH to Cumulative
          if (dvhSeq.getFirst().getString(Tag.DVHType).equals("DIFFERENTIAL")) {

            LOGGER.info("Not supported: converting differential DVH to cumulative");

            double[] data = dvhAttributes.getDoubles(Tag.DVHData);
            if (data != null && data.length % 2 == 0) {

              // X of histogram
              double[] doseX = new double[data.length / 2];

              // Y of histogram
              double[] volume = new double[data.length / 2];

              // Separate the dose and volume values into distinct arrays
              for (int i = 0; i < data.length; i = i + 2) {
                doseX[i] = data[i];
                volume[i] = data[i + 1];
              }

              // Get the min and max dose in cGy
              int minDose = (int) (doseX[0] * 100);
              int maxDose = (int) DoubleStream.of(doseX).sum();

              // Get volume values
              double maxVolume = DoubleStream.of(volume).sum();

              // Determine the dose values that are missing from the original data
              double[] missingDose = new double[minDose];
              Arrays.fill(missingDose, maxVolume);

              // Cumulative dose - x of histogram
              // Cumulative volume data - y of histogram
              double[] cumVolume = new double[doseX.length];
              double[] cumDose = new double[doseX.length];
              for (int k = 0; k < doseX.length; k++) {
                cumVolume[k] = DoubleStream.of(Arrays.copyOfRange(volume, k, doseX.length)).sum();
                cumDose[k] = DoubleStream.of(Arrays.copyOfRange(doseX, 0, k)).sum() * 100;
              }

              // Interpolated dose data for 1 cGy bins (between min and max)
              int[] interpDose = new int[maxDose + 1 - minDose];
              int m = 0;
              for (int l = minDose; l < maxDose + 1; l++) {
                interpDose[m] = l;
                m++;
              }

              // Interpolated volume data
              double[] interpCumVolume = interpolate(interpDose, cumDose, cumVolume);

              // Append the interpolated values to the missing dose values
              double[] cumDvhData = new double[missingDose.length + interpCumVolume.length];
              System.arraycopy(missingDose, 0, cumDvhData, 0, cumDvhData.length);
              System.arraycopy(
                  interpCumVolume, 0, cumDvhData, missingDose.length, interpCumVolume.length);

              rtDvh.setDvhData(cumDvhData);
              rtDvh.setDvhNumberOfBins(cumDvhData.length);
            }
          }
          // Cumulative
          else {
            // "filler" values are included in DVH data array (every second is DVH value)
            double[] data = dvhAttributes.getDoubles(Tag.DVHData);
            if (data != null && data.length % 2 == 0) {
              double[] newData = new double[data.length / 2];

              int j = 0;
              for (int i = 1; i < data.length; i = i + 2) {
                newData[j] = data[i];
                j++;
              }

              rtDvh.setDvhData(newData);
            }

            rtDvh.setDvhNumberOfBins(dvhAttributes.getInt(Tag.DVHNumberOfBins, -1));
          }

          // Always cumulative - differential was converted
          rtDvh.setType("CUMULATIVE");
          rtDvh.setDoseUnit(dvhAttributes.getString(Tag.DoseUnits));
          rtDvh.setDoseType(dvhAttributes.getString(Tag.DoseType));
          rtDvh.setDvhDoseScaling(dvhAttributes.getDouble(Tag.DVHDoseScaling, 1.0));
          rtDvh.setDvhVolumeUnit(dvhAttributes.getString(Tag.DVHVolumeUnits));
          // -1.0 means that it needs to be calculated later
          rtDvh.setDvhMinimumDose(dvhAttributes.getDouble(Tag.DVHMinimumDose, -1.0));
          rtDvh.setDvhMaximumDose(dvhAttributes.getDouble(Tag.DVHMaximumDose, -1.0));
          rtDvh.setDvhMeanDose(dvhAttributes.getDouble(Tag.DVHMeanDose, -1.0));

          dvhMap.put(rtDvh.getReferencedRoiNumber(), rtDvh);
        }
      }
    }
  }

  public DicomSeries getSeries() {
    return series;
  }

  public Map<Integer, Dvh> getDvhMap() {
    return dvhMap;
  }

  public Map<String, Map<String, Set<SegContour>>> getRefMap() {
    return refMap;
  }

  public Map<Integer, IsoDoseRegion> getSegAttributes() {
    return isoDoseSet;
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

  /**
   * Calculated relative dose with respect to absolute planned dose
   *
   * @param dose absolute simulated dose in cGy
   * @param planDose absolute planned dose in cGy
   * @return relative dose in %
   */
  public static double calculateRelativeDose(double dose, double planDose) {
    return (100 / planDose) * dose;
  }

  public String getSopInstanceUid() {
    return TagD.getTagValue(this, Tag.SOPInstanceUID, String.class);
  }

  public double[] getImagePositionPatient() {
    return imagePositionPatient;
  }

  public String getComment() {
    return this.comment;
  }

  public String getDoseUnit() {
    return this.doseUnit;
  }

  public String getDoseType() {
    return doseType;
  }

  public String getDoseSummationType() {
    return doseSummationType;
  }

  public double[] getGridFrameOffsetVector() {
    return this.gridFrameOffsetVector;
  }

  public double getDoseGridScaling() {
    return doseGridScaling;
  }

  public double getDoseMax() {
    // Initialise max dose once dose images are available
    if (this.series.size(null) > 1 && doseMax < 0.01) {
      for (DicomImageElement img : series.getMedias(null, null)) {
        try {
          Core.MinMaxLocResult minMaxLoc = minMaxLoc(img.getImage().toMat());
          if (doseMax < minMaxLoc.maxVal) {
            doseMax = minMaxLoc.maxVal;
          }
        } catch (Exception e) {
          LOGGER.error("Get max dose", e);
        }
      }
    }

    return doseMax;
  }

  public double getDoseSlicePositionThreshold() {
    return this.doseSlicePositionThreshold;
  }

  public void setDoseSlicePositionThreshold(double doseSlicePositionThreshold) {
    this.doseSlicePositionThreshold = doseSlicePositionThreshold;
  }

  public Map<Integer, IsoDoseRegion> getIsoDoseSet() {
    return this.isoDoseSet;
  }

  public AbstractMap.SimpleImmutableEntry<double[], double[]> getDoseMmLUT() {
    return this.doseMmLUT;
  }

  public void setDoseMmLUT(AbstractMap.SimpleImmutableEntry<double[], double[]> lut) {
    this.doseMmLUT = lut;
  }

  public AbstractMap.SimpleImmutableEntry<double[], double[]> getDosePixLUT() {
    return this.dosePixLUT;
  }

  public void setDosePixLUT(AbstractMap.SimpleImmutableEntry<double[], double[]> lut) {
    this.dosePixLUT = lut;
  }

  public void initDoseSet(double rxDose, RtSet rtSet) {
    int doseMaxLevel =
        (int) Dose.calculateRelativeDose((getDoseMax() * getDoseGridScaling() * 100), rxDose);

    // Max and standard levels 102, 100, 98, 95, 90, 80, 70, 50, 30
    if (doseMaxLevel > 0) {
      String seriesUID =
          TagD.getTagValue(rtSet.getPatientImage().getImage(), Tag.SeriesInstanceUID, String.class);
      isoDoseSet.put(
          doseMaxLevel,
          new IsoDoseRegion(
              doseMaxLevel,
              new Color(120 / 255f, 0, 0, opacity),
              "Max", // NON-NLS
              rxDose)); // NON-NLS
      isoDoseSet.put(102, new IsoDoseRegion(102, new Color(170 / 255f, 0, 0, opacity), "", rxDose));
      isoDoseSet.put(
          100, new IsoDoseRegion(100, new Color(238 / 255f, 69 / 255f, 0, opacity), "", rxDose));
      isoDoseSet.put(98, new IsoDoseRegion(98, new Color(1f, 165 / 255f, 0, opacity), "", rxDose));
      isoDoseSet.put(95, new IsoDoseRegion(95, new Color(1f, 1f, 0, opacity), "", rxDose));
      isoDoseSet.put(90, new IsoDoseRegion(90, new Color(0, 1f, 0, opacity), "", rxDose));
      isoDoseSet.put(80, new IsoDoseRegion(80, new Color(0, 139 / 255f, 0, opacity), "", rxDose));
      isoDoseSet.put(70, new IsoDoseRegion(70, new Color(0, 1f, 1f, opacity), "", rxDose));
      isoDoseSet.put(50, new IsoDoseRegion(50, new Color(0, 0, 1f, opacity), "", rxDose));
      isoDoseSet.put(30, new IsoDoseRegion(30, new Color(0, 0, 128 / 255f, opacity), "", rxDose));

      // Commented level just for testing
      //           isoDoseSet.put(2, new IsoDoseLayer(new IsoDose(2, new Color(0, 0,
      // 111/255f,
      //           opacity), "", rxDose)));
      Map<String, Set<SegContour>> map = refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
      Set<KeyDouble> zSet = new LinkedHashSet<>();
      // Go through whole imaging grid (CT)
      for (DicomImageElement image : rtSet.getSeries().getMedias(null, null)) {
        Set<SegContour> contours = new LinkedHashSet<>();
        // Image slice UID and position
        String sopUID = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
        KeyDouble z = new KeyDouble(image.getSliceGeometry().getTLHC().z);

        List<IsoDoseRegion> reverseValues = new ArrayList<>(isoDoseSet.values());
        Collections.reverse(reverseValues);
        for (IsoDoseRegion doseRegion : reverseValues) {
          zSet.add(z);
          double isoDoseThreshold = doseRegion.getAbsoluteDose();

          StructContour isoContour = getIsoDoseContour(z, isoDoseThreshold, doseRegion, rtSet);
          if (isoContour == null) {
            continue;
          }
          contours.add(isoContour);
        }
        if (contours.isEmpty()) {
          map.remove(sopUID);
        } else {
          map.put(sopUID, contours);
        }
      }

      // When finished creation of iso contours plane data calculate the plane thickness
      for (IsoDoseRegion isoDoseLayer : isoDoseSet.values()) {
        isoDoseLayer.setThickness(RtSet.calculatePlaneThickness(zSet));
      }
    }
  }

  void initPlan(RtSet rtSet) {
    DicomMediaIO reader = getMediaReader();
    Plan plan = null;
    Attributes dcmItems = reader.getDicomObject();
    if (dcmItems != null) {
      // Dose is Referencing Plan
      String referencedPlanUid = "";
      Sequence refPlanSeq = dcmItems.getSequence(Tag.ReferencedRTPlanSequence);
      if (refPlanSeq != null) {
        for (Attributes refRtPlanSeq : refPlanSeq) {
          referencedPlanUid = refRtPlanSeq.getString(Tag.ReferencedSOPInstanceUID);
        }
      }

      Set<Plan> plans = rtSet.getPlans();
      // Plan is already loaded
      if (!plans.isEmpty()) {
        String finalReferencedPlanUid = referencedPlanUid;
        Optional<Plan> opPlan =
            plans.stream()
                .filter(p -> p.getSopInstanceUid().equals(finalReferencedPlanUid))
                .findFirst();
        if (opPlan.isPresent()) {
          plan = opPlan.get();
        }
      }
      // Dummy plan will be created
      else {
        plan = new Plan(reader);
        plan.setSopInstanceUid(referencedPlanUid);
        plans.add(plan);
      }
      if (plan != null) {
        plan.getDoses().add(this);
      }
    }
  }

  public MediaElement getDosePlaneBySlice(double slicePosition) {
    MediaElement dosePlane = null;

    // If dose contains a multi-frame dose pixel array
    if (this.gridFrameOffsetVector.length > 0) {

      // Initial dose grid position Z (in patient coordinates)
      double imagePatientPositionZ = this.imagePositionPatient[2];

      // Add initial image patient position Z to the offset vector to determine the Z coordinate of
      // each dose
      // plane
      double[] dosePlanesZ = new double[this.gridFrameOffsetVector.length];
      for (int i = 0; i < dosePlanesZ.length; i++) {
        dosePlanesZ[i] = this.gridFrameOffsetVector[i] + imagePatientPositionZ;
      }

      // Check whether the requested plane is within the dose grid boundaries
      if (Arrays.stream(dosePlanesZ).min().getAsDouble() <= slicePosition
          && slicePosition <= Arrays.stream(dosePlanesZ).max().getAsDouble()) {

        // Calculate the absolute distance vector between dose planes and requested slice position
        double[] absoluteDistance = new double[dosePlanesZ.length];
        for (int i = 0; i < absoluteDistance.length; i++) {
          absoluteDistance[i] = Math.abs(dosePlanesZ[i] - slicePosition);
        }

        // Check to see if the requested plane exists in the array (or is close enough)
        int doseSlicePosition = -1;
        double minDistance = Arrays.stream(absoluteDistance).min().getAsDouble();
        if (minDistance < this.doseSlicePositionThreshold) {
          doseSlicePosition = firstIndexOf(absoluteDistance, minDistance, 0.001);
        }

        // Dose slice position found return the plane
        if (doseSlicePosition != -1) {
          dosePlane = series.getMedia(doseSlicePosition, null, null);
        }
        // There is no dose plane for such slice position, so interpolate between planes
        else {

          // First minimum distance - upper boundary
          int upperBoundaryIndex = firstIndexOf(absoluteDistance, minDistance, 0.001);

          // Prepare modified absolute distance vector to find the second minimum
          double[] modifiedAbsoluteDistance =
              Arrays.copyOf(absoluteDistance, absoluteDistance.length);
          modifiedAbsoluteDistance[upperBoundaryIndex] =
              Arrays.stream(absoluteDistance).max().getAsDouble();

          // Second minimum distance - lower boundary
          minDistance = Arrays.stream(modifiedAbsoluteDistance).min().getAsDouble();
          int lowerBoundaryIndex = firstIndexOf(modifiedAbsoluteDistance, minDistance, 0.001);

          // Fractional distance of dose plane between upper and lower boundary (from bottom to top)
          // E.g. if = 1, the plane is at the upper plane, = 0, it is at the lower plane.
          double fractionalDistance =
              (slicePosition - dosePlanesZ[lowerBoundaryIndex])
                  / (dosePlanesZ[upperBoundaryIndex] - dosePlanesZ[lowerBoundaryIndex]);

          dosePlane =
              this.interpolateDosePlanes(
                  upperBoundaryIndex, lowerBoundaryIndex, fractionalDistance);
        }
      }
    }

    return dosePlane;
  }

  public Mat getMaskedDosePlaneHist(double slicePosition, Mat mask, int maxDose) {

    DicomImageElement dosePlane = (DicomImageElement) this.getDosePlaneBySlice(slicePosition);

    int rows = dosePlane.getImage().toMat().rows();
    int cols = dosePlane.getImage().toMat().cols();

    // Calculate dose matrix for OpenCV
    Mat src = new Mat(rows, cols, CvType.CV_32FC1);
    dosePlane.getImage().toMat().convertTo(src, CvType.CV_32FC1);
    Scalar scalar = new Scalar(this.doseGridScaling * 100);
    Mat doseMatrix = new Mat(rows, cols, CvType.CV_32FC1);
    multiply(src, scalar, doseMatrix);
    ImageConversion.releaseMat(src);
    List<Mat> doseMatrixVector = new ArrayList<>();
    doseMatrixVector.add(doseMatrix);

    // Masked dose plan histogram
    Mat hist = new Mat();
    // Number of histogram bins
    MatOfInt histSize = new MatOfInt(maxDose);
    // Dose varies from 0 to maxDose
    MatOfFloat histRange = new MatOfFloat(0, maxDose);
    // Only one 0-th channel
    MatOfInt channels = new MatOfInt(0);

    // Ned to change the structure dose mask type vor OpenCV histogram calculation
    Mat maskSrc = new Mat(mask.rows(), mask.cols(), CvType.CV_8U);
    mask.convertTo(maskSrc, CvType.CV_8U);

    Imgproc.calcHist(doseMatrixVector, channels, maskSrc, hist, histSize, histRange);
    ImageConversion.releaseMat(maskSrc);
    ImageConversion.releaseMat(doseMatrix);
    return hist;
  }

  public StructContour getIsoDoseContour(
      KeyDouble slicePosition, double isoDoseThreshold, IsoDoseRegion region, RtSet rtSet) {
    if (region.getMeasurableLayer() == null) {
      //  region.setMeasurableLayer(getMeasurableLayer(img, contour));
    }

    // Convert from threshold in cCy to raw pixel value threshold
    double rawThreshold = (isoDoseThreshold / 100) / this.doseGridScaling;
    DicomImageElement dosePlane =
        (DicomImageElement) this.getDosePlaneBySlice(slicePosition.getValue());

    int rows = dosePlane.getImage().toMat().rows();
    int cols = dosePlane.getImage().toMat().cols();

    Mat src = new Mat(rows, cols, CvType.CV_32FC1);
    Mat thr = new Mat(rows, cols, CvType.CV_32FC1);

    dosePlane.getImage().toMat().convertTo(src, CvType.CV_32FC1);

    Imgproc.threshold(src, thr, rawThreshold, 255, Imgproc.THRESH_BINARY);
    ImageConversion.releaseMat(src);
    Mat thrSrc = new Mat(rows, cols, CvType.CV_8U);
    thr.convertTo(thrSrc, CvType.CV_8U);
    ImageConversion.releaseMat(thr);

    List<Segment> segmentList = SegContour.buildSegmentList(ImageCV.toImageCV(thrSrc));
    if (segmentList.isEmpty()) {
      ImageConversion.releaseMat(thrSrc);
      return null;
    }

    int nbPixels = Core.countNonZero(thrSrc);
    ImageConversion.releaseMat(thrSrc);

    GeometryOfSlice geometry = rtSet.getPatientImage().getImage().getDispSliceGeometry();
    Vector3d voxelSpacing = geometry.getVoxelSpacing();
    if (voxelSpacing.x < 0.00001 || voxelSpacing.y < 0.00001) {
      return null;
    }

    double z = slicePosition.getValue();
    transformGeometry(geometry, z, segmentList);

    StructContour segContour =
        new StructContour(String.valueOf(slicePosition.getKey()), segmentList, nbPixels);
    segContour.setPositionZ(z);
    region.addPixels(segContour);
    segContour.setAttributes(region);
    return segContour;
  }

  private void transformGeometry(GeometryOfSlice geometry, double z, List<Segment> segmentList) {
    Vector3d tlhc = geometry.getTLHC();
    Vector3d row = geometry.getRow();
    Vector3d column = geometry.getColumn();
    Vector3d voxelSpacing = geometry.getVoxelSpacing();

    for (Segment segment : segmentList) {
      for (Point2D pt : segment) {
        double sx = doseMmLUT.getKey()[(int) pt.getX()];
        double sy = doseMmLUT.getValue()[(int) pt.getY()];
        double x =
            ((sx - tlhc.x) * row.x + (sy - tlhc.y) * row.y + (z - tlhc.z) * row.z) / voxelSpacing.x;
        double y =
            ((sx - tlhc.x) * column.x + (sy - tlhc.y) * column.y + (z - tlhc.z) * column.z)
                / voxelSpacing.y;
        pt.setLocation(x, y);
      }
      List<Segment> children = segment.getChildren();
      if (!children.isEmpty()) {
        transformGeometry(geometry, z, children);
      }
    }
  }

  public void initialiseDoseGridToImageGrid(Image patientImage) {

    // Transpose the dose grid LUT onto the image grid LUT
    double[] x = new double[this.doseMmLUT.getKey().length];
    for (int i = 0; i < this.doseMmLUT.getKey().length; i++) {
      x[i] =
          (this.doseMmLUT.getKey()[i] - patientImage.getImageLUT().getKey()[0])
              * patientImage.getProne()
              * patientImage.getFeetFirst()
              / patientImage.getImageSpacing().x;
    }
    double[] y = new double[this.doseMmLUT.getValue().length];
    for (int j = 0; j < this.doseMmLUT.getValue().length; j++) {
      y[j] =
          (this.doseMmLUT.getValue()[j])
              - patientImage.getImageLUT().getValue()[0]
                  * patientImage.getProne()
                  / patientImage.getImageSpacing().y;
    }
    this.dosePixLUT = new AbstractMap.SimpleImmutableEntry<>(x, y);
  }

  private MediaElement interpolateDosePlanes(
      int upperBoundaryIndex, int lowerBoundaryIndex, double fractionalDistance) {
    MediaElement dosePlane = null;

    DicomImageElement upperPlane = series.getMedia(upperBoundaryIndex, null, null);
    DicomImageElement lowerPlane = series.getMedia(lowerBoundaryIndex, null, null);

    // A simple linear interpolation (lerp)
    Mat dosePlaneMat = new Mat();
    addWeighted(
        lowerPlane.getImage().toMat(),
        1.0 - fractionalDistance,
        upperPlane.getImage().toMat(),
        fractionalDistance,
        0.0,
        dosePlaneMat);

    // TODO: dosePlaneMat should be an image for new dosePlane MediaElement

    return dosePlane;
  }

  private static int firstIndexOf(double[] array, double valueToFind, double tolerance) {
    for (int i = 0; i < array.length; i++) {
      if (Math.abs(array[i] - valueToFind) < tolerance) {
        return i;
      }
    }
    return -1;
  }

  private static double[] interpolate(
      int[] interpolatedX, double[] xCoordinates, double[] yCoordinates) {
    double[] interpolatedY = new double[interpolatedX.length];

    PolynomialSplineFunction psf = interpolate(xCoordinates, yCoordinates);

    for (int i = 0; i <= interpolatedX.length; ++i) {
      interpolatedY[0] = psf.value(interpolatedX[i]);
    }

    return interpolatedY;
  }

  public static PolynomialSplineFunction interpolate(double[] x, double[] y) {
    if (x.length != y.length || x.length < 2) {
      throw new IllegalStateException();
    }

    // Number of intervals
    int length = x.length - 1;

    // Slope of the lines between the data points
    final double[] m = new double[length];
    for (int i = 0; i < length; i++) {
      m[i] = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
    }

    final PolynomialFunction[] polynomials = new PolynomialFunction[length];
    final double[] coefficients = new double[2];
    for (int i = 0; i < length; i++) {
      coefficients[0] = y[i];
      coefficients[1] = m[i];
      polynomials[i] = new PolynomialFunction(coefficients);
    }

    return new PolynomialSplineFunction(x, polynomials);
  }
}
