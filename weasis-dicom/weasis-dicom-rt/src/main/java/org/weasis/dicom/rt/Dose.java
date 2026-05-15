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
import static org.weasis.dicom.codec.geometry.GeometryOfSlice.MIN_SPACING;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
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
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.GeometryOfSlice;
import org.weasis.dicom.codec.seg.LazyContourLoader;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.op.ImageConversion;

/**
 * @author Tomas Skripcak
 * @author Nicolas Roduit
 */
public class Dose extends RtSpecialElement implements SpecialElementRegion {
  private static final Logger LOGGER = LoggerFactory.getLogger(Dose.class);

  /** Default standard isodose levels and their colors (RGB). */
  private static final List<IsoLevel> STANDARD_LEVELS =
      List.of(
          new IsoLevel(102, new Color(170, 0, 0)),
          new IsoLevel(100, new Color(238, 69, 0)),
          new IsoLevel(98, new Color(255, 165, 0)),
          new IsoLevel(95, new Color(255, 255, 0)),
          new IsoLevel(90, new Color(0, 255, 0)),
          new IsoLevel(80, new Color(0, 139, 0)),
          new IsoLevel(70, new Color(0, 255, 255)),
          new IsoLevel(50, new Color(0, 0, 255)),
          new IsoLevel(30, new Color(0, 0, 128)));

  private final Map<String, Map<String, Set<LazyContourLoader>>> refMap = new HashMap<>();

  private volatile float opacity = 0.5f;
  private volatile boolean visible = false;

  private final double[] imagePositionPatient;
  private final String comment;
  private final String doseUnit;
  private final String doseType;
  private final String doseSummationType;
  private final double[] gridFrameOffsetVector;
  private final double doseGridScaling;
  private double doseMax;
  private boolean doseMaxComputed;

  private final DicomSeries series;
  private double doseSlicePositionThreshold;

  private final Map<Integer, IsoDoseRegion> isoDoseSet = new LinkedHashMap<>();
  private final Map<Integer, Dvh> dvhMap = new HashMap<>();

  private DoseLut doseMmLUT;
  private DoseLut dosePixLUT;

  /** Cached patient-coordinate Z position of each dose plane (initial-position + offset vector). */
  private double[] dosePlanesZ;

  private record IsoLevel(int level, Color color) {}

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
    if (dvhSeq == null) {
      return;
    }
    for (Attributes dvhAttributes : dvhSeq) {
      Sequence dvhRefRoiSeq = dvhAttributes.getSequence(Tag.DVHReferencedROISequence);
      if (dvhRefRoiSeq == null || dvhRefRoiSeq.size() != 1) {
        continue;
      }
      Dvh rtDvh = new Dvh();
      rtDvh.setReferencedRoiNumber(dvhRefRoiSeq.getFirst().getInt(Tag.ReferencedROINumber, -1));
      rtDvh.setDvhSource(DataSource.PROVIDED);
      LOGGER.debug("Found DVH for ROI: {}", rtDvh.getReferencedRoiNumber());

      String dvhType = dvhAttributes.getString(Tag.DVHType);
      if ("DIFFERENTIAL".equals(dvhType)) {
        parseDifferentialDvh(rtDvh, dvhAttributes);
      } else {
        parseCumulativeDvh(rtDvh, dvhAttributes);
      }
      populateDvhMetadata(rtDvh, dvhAttributes);
      dvhMap.put(rtDvh.getReferencedRoiNumber(), rtDvh);
    }
  }

  private static void parseDifferentialDvh(Dvh rtDvh, Attributes att) {
    LOGGER.info("Not supported: converting differential DVH to cumulative");
    double[] data = att.getDoubles(Tag.DVHData);
    if (data == null || data.length % 2 != 0) {
      return;
    }
    int half = data.length / 2;
    double[] doseX = new double[half];
    double[] volume = new double[half];
    double maxVolume = 0.0;
    double sumDose = 0.0;
    for (int i = 0, j = 0; i < data.length; i += 2, j++) {
      doseX[j] = data[i];
      volume[j] = data[i + 1];
      maxVolume += data[i + 1];
      sumDose += data[i];
    }

    int minDose = (int) (doseX[0] * 100);
    int maxDose = (int) sumDose;

    // Suffix sums (cumulative volume from the right) and prefix sums (cumulative dose from
    // the left), each computed in a single pass instead of repeatedly summing slices (was O(n²)).
    double[] cumVolume = new double[half];
    double[] cumDose = new double[half];
    double suffix = 0.0;
    double prefix = 0.0;
    for (int k = half - 1; k >= 0; k--) {
      suffix += volume[k];
      cumVolume[k] = suffix;
    }
    for (int k = 0; k < half; k++) {
      cumDose[k] = prefix * 100;
      prefix += doseX[k];
    }

    int[] interpDose = new int[maxDose + 1 - minDose];
    for (int l = 0; l < interpDose.length; l++) {
      interpDose[l] = minDose + l;
    }
    double[] interpCumVolume = interpolate(interpDose, cumDose, cumVolume);

    double[] cumDvhData = new double[minDose + interpCumVolume.length];
    for (int i = 0; i < minDose; i++) {
      cumDvhData[i] = maxVolume;
    }
    System.arraycopy(interpCumVolume, 0, cumDvhData, minDose, interpCumVolume.length);
    rtDvh.setDvhData(cumDvhData);
    rtDvh.setDvhNumberOfBins(cumDvhData.length);
  }

  private static void parseCumulativeDvh(Dvh rtDvh, Attributes att) {
    // "filler" values are included in DVH data array (every second is DVH value)
    double[] data = att.getDoubles(Tag.DVHData);
    if (data != null && data.length % 2 == 0) {
      double[] newData = new double[data.length / 2];
      for (int i = 1, j = 0; i < data.length; i += 2, j++) {
        newData[j] = data[i];
      }
      rtDvh.setDvhData(newData);
    }
    rtDvh.setDvhNumberOfBins(att.getInt(Tag.DVHNumberOfBins, -1));
  }

  private static void populateDvhMetadata(Dvh rtDvh, Attributes att) {
    rtDvh.setType("CUMULATIVE");
    rtDvh.setDoseUnit(att.getString(Tag.DoseUnits));
    rtDvh.setDoseType(att.getString(Tag.DoseType));
    rtDvh.setDvhDoseScaling(att.getDouble(Tag.DVHDoseScaling, 1.0));
    rtDvh.setDvhVolumeUnit(att.getString(Tag.DVHVolumeUnits));
    // -1.0 means that it needs to be calculated later
    rtDvh.setDvhMinimumDose(att.getDouble(Tag.DVHMinimumDose, -1.0));
    rtDvh.setDvhMaximumDose(att.getDouble(Tag.DVHMaximumDose, -1.0));
    rtDvh.setDvhMeanDose(att.getDouble(Tag.DVHMeanDose, -1.0));
  }

  public DicomSeries getSeries() {
    return series;
  }

  public Map<Integer, Dvh> getDvhMap() {
    return dvhMap;
  }

  public Map<String, Map<String, Set<LazyContourLoader>>> getRefMap() {
    return refMap;
  }

  @Override
  public NavigableMap<Double, Set<LazyContourLoader>> getPositionMap() {
    return SpecialElementRegion.emptyPositionMap();
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
    this.opacity = Math.clamp(opacity, 0.0f, 1.0f);
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
    return comment;
  }

  public String getDoseUnit() {
    return doseUnit;
  }

  public String getDoseType() {
    return doseType;
  }

  public String getDoseSummationType() {
    return doseSummationType;
  }

  public double[] getGridFrameOffsetVector() {
    return gridFrameOffsetVector;
  }

  public double getDoseGridScaling() {
    return doseGridScaling;
  }

  /** Returns the maximum raw dose value across the whole RT Dose series (computed once, cached). */
  public double getDoseMax() {
    if (!doseMaxComputed && series.size(null) > 1) {
      doseMaxComputed = true;
      for (DicomImageElement img : series.getMedias(null, null)) {
        try {
          double m = minMaxLoc(img.getImage().toMat()).maxVal;
          if (m > doseMax) {
            doseMax = m;
          }
        } catch (Exception e) {
          LOGGER.error("Get max dose", e);
        }
      }
    }
    return doseMax;
  }

  public double getDoseSlicePositionThreshold() {
    return doseSlicePositionThreshold;
  }

  public void setDoseSlicePositionThreshold(double doseSlicePositionThreshold) {
    this.doseSlicePositionThreshold = doseSlicePositionThreshold;
  }

  public Map<Integer, IsoDoseRegion> getIsoDoseSet() {
    return isoDoseSet;
  }

  public DoseLut getDoseMmLUT() {
    return doseMmLUT;
  }

  public void setDoseMmLUT(DoseLut lut) {
    this.doseMmLUT = lut;
  }

  public DoseLut getDosePixLUT() {
    return dosePixLUT;
  }

  public void setDosePixLUT(DoseLut lut) {
    this.dosePixLUT = lut;
  }

  /**
   * Builds the standard set of isodose levels for the given prescribed dose and produces the
   * corresponding contour overlays for every CT slice in the patient series.
   */
  public void initDoseSet(double rxDose, RtSet rtSet) {
    int doseMaxLevel =
        (int) calculateRelativeDose((getDoseMax() * getDoseGridScaling() * 100), rxDose);
    if (doseMaxLevel <= 0) {
      return;
    }
    String seriesUID =
        TagD.getTagValue(rtSet.getPatientImage().getImage(), Tag.SeriesInstanceUID, String.class);
    seedIsoDoseLevels(doseMaxLevel, rxDose);
    buildIsoDoseContoursPerSlice(rtSet, seriesUID);
  }

  private void seedIsoDoseLevels(int doseMaxLevel, double rxDose) {
    isoDoseSet.put(
        doseMaxLevel,
        new IsoDoseRegion(
            doseMaxLevel, withOpacity(new Color(120, 0, 0)), "Max", rxDose)); // NON-NLS
    for (IsoLevel iso : STANDARD_LEVELS) {
      isoDoseSet.put(
          iso.level(), new IsoDoseRegion(iso.level(), withOpacity(iso.color()), "", rxDose));
    }
  }

  private Color withOpacity(Color base) {
    return new Color(base.getRed() / 255f, base.getGreen() / 255f, base.getBlue() / 255f, opacity);
  }

  private void buildIsoDoseContoursPerSlice(RtSet rtSet, String seriesUID) {
    Map<String, Set<LazyContourLoader>> map =
        refMap.computeIfAbsent(seriesUID, _ -> new HashMap<>());
    Set<KeyDouble> zSet = new LinkedHashSet<>();

    // Reverse iteration so the largest (outermost) isodose is drawn first
    List<IsoDoseRegion> reverseLevels = new ArrayList<>(isoDoseSet.values());
    for (DicomImageElement image : rtSet.getSeries().getMedias(null, null)) {
      String sopUID = TagD.getTagValue(image, Tag.SOPInstanceUID, String.class);
      KeyDouble z = new KeyDouble(image.getRawSliceGeometry().getTLHC().z);
      zSet.add(z);

      PlaneContourLoader contours = new PlaneContourLoader();
      for (ListIterator<IsoDoseRegion> it = reverseLevels.listIterator(reverseLevels.size());
          it.hasPrevious(); ) {
        IsoDoseRegion doseRegion = it.previous();
        StructContour isoContour =
            getIsoDoseContour(z, doseRegion.getAbsoluteDose(), doseRegion, rtSet);
        if (isoContour != null) {
          contours.addContour(isoContour);
        }
      }
      if (contours.getLazyContours().isEmpty()) {
        map.remove(sopUID);
      } else {
        map.computeIfAbsent(sopUID, _ -> new LinkedHashSet<>()).add(contours);
      }
    }

    double thickness = RtSet.calculatePlaneThickness(zSet);
    for (IsoDoseRegion isoDoseLayer : isoDoseSet.values()) {
      isoDoseLayer.setThickness(thickness);
    }
  }

  void initPlan(RtSet rtSet) {
    DicomMediaIO reader = getMediaReader();
    Attributes dcmItems = reader.getDicomObject();
    if (dcmItems == null) {
      return;
    }

    // RT Dose may reference at most one RT Plan; keep the last reference if several are listed.
    String referencedPlanUid = "";
    Sequence refPlanSeq = dcmItems.getSequence(Tag.ReferencedRTPlanSequence);
    if (refPlanSeq != null && !refPlanSeq.isEmpty()) {
      referencedPlanUid = refPlanSeq.getLast().getString(Tag.ReferencedSOPInstanceUID);
    }

    Set<Plan> plans = rtSet.getPlans();
    Plan plan;
    if (plans.isEmpty()) {
      plan = new Plan(reader);
      plan.setSopInstanceUid(referencedPlanUid);
      plans.add(plan);
    } else {
      String finalReferencedPlanUid = referencedPlanUid;
      plan =
          plans.stream()
              .filter(p -> p.getSopInstanceUid().equals(finalReferencedPlanUid))
              .findFirst()
              .orElse(null);
    }
    if (plan != null) {
      plan.getDoses().add(this);
    }
  }

  /**
   * Returns the dose plane closest to {@code slicePosition} (in patient mm). Returns the nearest
   * stored plane if it lies within {@link #doseSlicePositionThreshold} mm, otherwise an
   * interpolated plane between the two nearest neighbours.
   */
  public MediaElement getDosePlaneBySlice(double slicePosition) {
    if (gridFrameOffsetVector.length == 0) {
      return null;
    }
    double[] zArr = computeDosePlanesZ();

    // Single pass: compute min/max bounds and the closest plane.
    double minZ = zArr[0];
    double maxZ = zArr[0];
    int closestIndex = 0;
    double closestDistance = Math.abs(zArr[0] - slicePosition);
    for (int i = 1; i < zArr.length; i++) {
      double z = zArr[i];
      if (z < minZ) minZ = z;
      else if (z > maxZ) maxZ = z;
      double d = Math.abs(z - slicePosition);
      if (d < closestDistance) {
        closestDistance = d;
        closestIndex = i;
      }
    }
    if (slicePosition < minZ || slicePosition > maxZ) {
      return null;
    }
    if (closestDistance < doseSlicePositionThreshold) {
      return series.getMedia(closestIndex, null, null);
    }

    // Otherwise interpolate between the two nearest planes
    int secondIndex = secondNearest(zArr, closestIndex, slicePosition);
    double fractionalDistance =
        (slicePosition - zArr[secondIndex]) / (zArr[closestIndex] - zArr[secondIndex]);
    return interpolateDosePlanes(closestIndex, secondIndex, fractionalDistance);
  }

  private static int secondNearest(double[] zArr, int exclude, double slicePosition) {
    int idx = 0;
    double best = Double.POSITIVE_INFINITY;
    for (int i = 0; i < zArr.length; i++) {
      if (i == exclude) continue;
      double d = Math.abs(zArr[i] - slicePosition);
      if (d < best) {
        best = d;
        idx = i;
      }
    }
    return idx;
  }

  private double[] computeDosePlanesZ() {
    if (dosePlanesZ == null) {
      double[] arr = new double[gridFrameOffsetVector.length];
      double base = imagePositionPatient[2];
      for (int i = 0; i < arr.length; i++) {
        arr[i] = gridFrameOffsetVector[i] + base;
      }
      dosePlanesZ = arr;
    }
    return dosePlanesZ;
  }

  public Mat getMaskedDosePlaneHist(double slicePosition, Mat mask, int maxDose) {
    DicomImageElement dosePlane = (DicomImageElement) getDosePlaneBySlice(slicePosition);
    Mat raw = dosePlane.getImage().toMat();

    Mat src = new Mat(raw.rows(), raw.cols(), CvType.CV_32FC1);
    raw.convertTo(src, CvType.CV_32FC1);
    Mat doseMatrix = new Mat(raw.rows(), raw.cols(), CvType.CV_32FC1);
    multiply(src, new Scalar(doseGridScaling * 100), doseMatrix);
    ImageConversion.releaseMat(src);

    Mat maskSrc = new Mat(mask.rows(), mask.cols(), CvType.CV_8U);
    mask.convertTo(maskSrc, CvType.CV_8U);

    Mat hist = new Mat();
    Imgproc.calcHist(
        List.of(doseMatrix),
        new MatOfInt(0),
        maskSrc,
        hist,
        new MatOfInt(maxDose),
        new MatOfFloat(0, maxDose));

    ImageConversion.releaseMat(maskSrc);
    ImageConversion.releaseMat(doseMatrix);
    return hist;
  }

  public StructContour getIsoDoseContour(
      KeyDouble slicePosition, double isoDoseThreshold, IsoDoseRegion region, RtSet rtSet) {
    // Convert from threshold in cGy to raw pixel value threshold
    double rawThreshold = (isoDoseThreshold / 100) / doseGridScaling;
    DicomImageElement dosePlane = (DicomImageElement) getDosePlaneBySlice(slicePosition.getValue());
    if (dosePlane == null || dosePlane.getImage() == null) {
      return null;
    }

    Image patientImage = rtSet.getPatientImage();
    if (patientImage == null || doseMmLUT == null) {
      return null;
    }
    double[] mmX = doseMmLUT.x();
    double[] mmY = doseMmLUT.y();
    if (mmX == null || mmY == null || mmX.length < 2 || mmY.length < 2) {
      return null;
    }
    GeometryOfSlice geometry = patientImage.getImage().getSliceGeometry();
    Vector3d voxelSpacing = geometry.getVoxelSpacing();
    if (voxelSpacing.x < MIN_SPACING || voxelSpacing.y < MIN_SPACING) {
      return null;
    }

    Mat doseMask = buildBinaryDoseMask(dosePlane, rawThreshold);
    Mat affine = buildAffineDoseToImage(geometry, mmX, mmY, voxelSpacing, slicePosition.getValue());
    Mat resampled =
        resampleMaskToImage(doseMask, affine, patientImage.getWidth(), patientImage.getHeight());

    int nbPixels = Core.countNonZero(resampled);
    if (nbPixels == 0) {
      ImageConversion.releaseMat(resampled);
      return null;
    }

    StructContour segContour =
        new StructContour(
            String.valueOf(slicePosition.getKey()), ImageCV.fromMat(resampled), nbPixels);
    segContour.setPositionZ(slicePosition.getValue());
    region.addPixels(segContour);
    segContour.setAttributes(region);
    return segContour;
  }

  private static Mat buildBinaryDoseMask(DicomImageElement dosePlane, double rawThreshold) {
    Mat src = dosePlane.getImage().toMat();
    Mat srcF = new Mat();
    src.convertTo(srcF, CvType.CV_32FC1);
    Mat thr = new Mat();
    Imgproc.threshold(srcF, thr, rawThreshold, 255, Imgproc.THRESH_BINARY);
    ImageConversion.releaseMat(srcF);
    Mat doseMask = new Mat();
    thr.convertTo(doseMask, CvType.CV_8UC1);
    ImageConversion.releaseMat(thr);
    return doseMask;
  }

  private static Mat buildAffineDoseToImage(
      GeometryOfSlice geometry, double[] mmX, double[] mmY, Vector3d voxelSpacing, double z) {
    // dose-grid pixel (i, j) -> patient-image pixel via mm space (TLHC/row/column/voxel spacing).
    Vector3d tlhc = geometry.getTLHC();
    Vector3d row = geometry.getRow();
    Vector3d column = geometry.getColumn();
    double bx = (mmX[mmX.length - 1] - mmX[0]) / (mmX.length - 1);
    double by = (mmY[mmY.length - 1] - mmY[0]) / (mmY.length - 1);
    double ax = mmX[0];
    double ay = mmY[0];

    double a = bx * row.x / voxelSpacing.x;
    double b = by * row.y / voxelSpacing.x;
    double c =
        ((ax - tlhc.x) * row.x + (ay - tlhc.y) * row.y + (z - tlhc.z) * row.z) / voxelSpacing.x;
    double d = bx * column.x / voxelSpacing.y;
    double e = by * column.y / voxelSpacing.y;
    double f =
        ((ax - tlhc.x) * column.x + (ay - tlhc.y) * column.y + (z - tlhc.z) * column.z)
            / voxelSpacing.y;

    Mat affine = new Mat(2, 3, CvType.CV_64FC1);
    affine.put(0, 0, a, b, c, d, e, f);
    return affine;
  }

  private static Mat resampleMaskToImage(Mat doseMask, Mat affine, int imgW, int imgH) {
    Mat resampled = new Mat();
    Imgproc.warpAffine(
        doseMask,
        resampled,
        affine,
        new Size(imgW, imgH),
        Imgproc.INTER_NEAREST,
        Core.BORDER_CONSTANT,
        Scalar.all(0));
    affine.release();
    ImageConversion.releaseMat(doseMask);
    return resampled;
  }

  public void initialiseDoseGridToImageGrid(Image patientImage) {
    double[] doseX = doseMmLUT.x();
    double[] doseY = doseMmLUT.y();
    double[] imgLutX = patientImage.getImageLUT().x();
    double[] imgLutY = patientImage.getImageLUT().y();
    int prone = patientImage.getProne();
    int feetFirst = patientImage.getFeetFirst();
    Vector3d sp = patientImage.getImageSpacing();

    double[] x = new double[doseX.length];
    for (int i = 0; i < doseX.length; i++) {
      x[i] = (doseX[i] - imgLutX[0]) * prone * feetFirst / sp.x;
    }
    double[] y = new double[doseY.length];
    for (int j = 0; j < doseY.length; j++) {
      y[j] = doseY[j] - imgLutY[0] * prone / sp.y;
    }
    this.dosePixLUT = new DoseLut(x, y);
  }

  private MediaElement interpolateDosePlanes(
      int upperBoundaryIndex, int lowerBoundaryIndex, double fractionalDistance) {
    DicomImageElement upperPlane = series.getMedia(upperBoundaryIndex, null, null);
    DicomImageElement lowerPlane = series.getMedia(lowerBoundaryIndex, null, null);

    // Linear interpolation (lerp) of the two planes
    Mat dosePlaneMat = new Mat();
    addWeighted(
        lowerPlane.getImage().toMat(),
        1.0 - fractionalDistance,
        upperPlane.getImage().toMat(),
        fractionalDistance,
        0.0,
        dosePlaneMat);

    // TODO: wrap dosePlaneMat as a MediaElement; until then return null so the caller skips it.
    return null;
  }

  private static double[] interpolate(
      int[] interpolatedX, double[] xCoordinates, double[] yCoordinates) {
    double[] interpolatedY = new double[interpolatedX.length];
    PolynomialSplineFunction psf = interpolate(xCoordinates, yCoordinates);
    for (int i = 0; i < interpolatedX.length; i++) {
      interpolatedY[i] = psf.value(interpolatedX[i]);
    }
    return interpolatedY;
  }

  public static PolynomialSplineFunction interpolate(double[] x, double[] y) {
    if (x.length != y.length || x.length < 2) {
      throw new IllegalArgumentException("invalid interpolation input");
    }
    int length = x.length - 1;
    PolynomialFunction[] polynomials = new PolynomialFunction[length];
    for (int i = 0; i < length; i++) {
      double slope = (y[i + 1] - y[i]) / (x[i + 1] - x[i]);
      polynomials[i] = new PolynomialFunction(new double[] {y[i], slope});
    }

    return new PolynomialSplineFunction(x, polynomials);
  }
}
