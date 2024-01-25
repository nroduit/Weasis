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
import java.util.*;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.seg.Segment;
import org.weasis.opencv.seg.SegmentAttributes;
import org.weasis.opencv.seg.SegmentCategory;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class Dose extends HashMap<Integer, Dvh> implements SpecialElementRegion {
  private final Map<String, Map<String, Set<SegContour>>> refMap = new HashMap<>();

  private volatile float opacity = 1.0f;
  private volatile boolean visible = true;

  private String sopInstanceUid;
  private double[] imagePositionPatient;
  private String comment;
  private String doseUnit;
  private String doseType;
  private String doseSummationType;
  private double[] gridFrameOffsetVector;
  private double doseGridScaling;
  private double doseMax;

  private final DicomSeries series;
  private double doseSlicePositionThreshold;

  private Map<Integer, IsoDoseRegion> isoDoseSet = new LinkedHashMap<>();
  private Map<String, List<StructContour>> isoContourMap = new HashMap<>();

  // Dose LUTs
  private AbstractMap.SimpleImmutableEntry<double[], double[]> doseMmLUT;
  private AbstractMap.SimpleImmutableEntry<double[], double[]> dosePixLUT;

  public Dose(DicomSeries series) {
    // Default threshold in mm to determine the max difference from slicePosition to the closest
    // dose frame without interpolation
    this.doseSlicePositionThreshold = 0.5;
    this.series = series;
    this.doseMax = 0.0;
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
    return sopInstanceUid;
  }

  public void setSopInstanceUid(String sopInstanceUid) {
    this.sopInstanceUid = sopInstanceUid;
  }

  public double[] getImagePositionPatient() {
    return imagePositionPatient;
  }

  public void setImagePositionPatient(double[] imagePositionPatient) {
    this.imagePositionPatient = imagePositionPatient;
  }

  public String getComment() {
    return this.comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public String getDoseUnit() {
    return this.doseUnit;
  }

  public void setDoseUnit(String doseUnit) {
    this.doseUnit = doseUnit;
  }

  public String getDoseType() {
    return doseType;
  }

  public void setDoseType(String doseType) {
    this.doseType = doseType;
  }

  public String getDoseSummationType() {
    return doseSummationType;
  }

  public void setDoseSummationType(String doseSummationType) {
    this.doseSummationType = doseSummationType;
  }

  public double[] getGridFrameOffsetVector() {
    return this.gridFrameOffsetVector;
  }

  public void setGridFrameOffsetVector(double[] gridFrameOffsetVector) {
    this.gridFrameOffsetVector = gridFrameOffsetVector;
  }

  public double getDoseGridScaling() {
    return doseGridScaling;
  }

  public void setDoseGridScaling(double doseGridScaling) {
    this.doseGridScaling = doseGridScaling;
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
          System.out.println("Error: " + e.getMessage());
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

  public void setIsoDoseSet(Map<Integer, IsoDoseRegion> isoDoseSet) {
    this.isoDoseSet = isoDoseSet;
  }

  public Map<String, List<StructContour>> getIsoContourMap() {
    return this.isoContourMap;
  }

  public void setIsoContourMap(Map<String, List<StructContour>> isoContourMap) {
    this.isoContourMap = isoContourMap;
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

      // Go through whole imaging grid (CT)
      for (DicomImageElement image : this.series.getMedias(null, null)) {
        // Image slice UID and position
        String uidKey = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
        KeyDouble z = new KeyDouble(image.getSliceGeometry().getTLHC().z);

        for (IsoDoseRegion isoDoseLayer : isoDoseSet.values()) {
          double isoDoseThreshold = isoDoseLayer.getAbsoluteDose();

          StructContour isoContour = getIsoDoseContour(z, isoDoseThreshold, isoDoseLayer);
          if (isoContour == null) {
            continue;
          }

          // Create empty hash map of planes for IsoDose layer if there is none
          if (isoDoseLayer.getPlanes() == null) {
            isoDoseLayer.setPlanes(new HashMap<>());
          }

          // Create a new IsoDose contour plane for Z or select existing one
          // it will hold list of contours for that plane
          isoDoseLayer.getPlanes().computeIfAbsent(z, _ -> new ArrayList<>()).add(isoContour);
          // For lookup from GUI use specific image UID
          if (StringUtil.hasText(uidKey)) {
            List<StructContour> pls =
                getIsoContourMap().computeIfAbsent(uidKey, _ -> new ArrayList<>());
            pls.add(isoContour);
          }

          DicomImageElement zImage = rtSet.getSeries().getNearestImage(z.getKey(), 0, null, null);
          if (zImage != null) {
            String sopUID = TagD.getTagValue(zImage, Tag.SOPInstanceUID, String.class);
            refMap
                .get(seriesUID)
                .computeIfAbsent(sopUID, _ -> new LinkedHashSet<>())
                .add(isoContour);
          }
        }
      }

      // When finished creation of iso contours plane data calculate the plane thickness
      for (IsoDoseRegion isoDoseLayer : isoDoseSet.values()) {
        isoDoseLayer.setThickness(RtSet.calculatePlaneThickness(isoDoseLayer.getPlanes()));
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

  public List<MatOfPoint> getIsoDoseContourPoints(
      KeyDouble slicePosition, double isoDoseThreshold) {
    List<MatOfPoint> contours = new ArrayList<>();

    // Convert from threshold in cCy to raw pixel value threshold
    double rawThreshold = (isoDoseThreshold / 100) / this.doseGridScaling;

    DicomImageElement dosePlane =
        (DicomImageElement) this.getDosePlaneBySlice(slicePosition.getValue());

    int rows = dosePlane.getImage().toMat().rows();
    int cols = dosePlane.getImage().toMat().cols();

    Mat src = new Mat(rows, cols, CvType.CV_32FC1);
    Mat thr = new Mat(rows, cols, CvType.CV_32FC1);

    dosePlane.getImage().toMat().convertTo(src, CvType.CV_32FC1);

    Mat hierarchy = new Mat();

    Imgproc.threshold(src, thr, rawThreshold, 255, Imgproc.THRESH_BINARY);
    ImageConversion.releaseMat(src);
    Mat thrSrc = new Mat(rows, cols, CvType.CV_8U);
    thr.convertTo(thrSrc, CvType.CV_8U);
    ImageConversion.releaseMat(thr);

    Imgproc.findContours(
        thrSrc, contours, hierarchy, Imgproc.RETR_TREE, Imgproc.CHAIN_APPROX_SIMPLE);
    ImageConversion.releaseMat(thrSrc);
    return contours;
  }

  public StructContour getIsoDoseContour(
      KeyDouble slicePosition, double isoDoseThreshold, IsoDoseRegion region) {
    SegmentAttributes attributes = region.getAttributes();
    SegmentCategory category = region.getCategory();
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

    StructContour segContour =
        new StructContour(String.valueOf(slicePosition.getKey()), segmentList, nbPixels);
    segContour.setPositionZ(slicePosition.getValue());
    region.addPixels(segContour);
    segContour.setAttributes(attributes);
    segContour.setCategory(category);
    return segContour;
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
}
