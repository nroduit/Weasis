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

import static org.opencv.core.Core.add;
import static org.opencv.core.Core.multiply;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.opencv.op.ImageConversion;

/**
 * RtSet is a collection of linked DICOM-RT entities that form the whole treatment case (Plans,
 * Doses, StructureSets)
 *
 * @author Tomas Skripcak
 */
public class RtSet {

  private static final Logger LOGGER = LoggerFactory.getLogger(RtSet.class);

  private final List<MediaElement> rtElements = new ArrayList<>();
  private final LinkedHashSet<StructureSet> structures = new LinkedHashSet<>();
  private final LinkedHashSet<Plan> plans = new LinkedHashSet<>();
  private final DicomSeries series;

  private Image patientImage;

  public RtSet(DicomSeries series, List<RtSpecialElement> rtElements) {
    this.series = Objects.requireNonNull(series);
    this.rtElements.addAll(Objects.requireNonNull(rtElements));
    Object image = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (image instanceof DicomImageElement dicomImageElement) {
      this.patientImage = new Image(dicomImageElement);
      this.patientImage.setImageLUT(calculatePixelLookupTable(dicomImageElement));
    }
  }

  public DicomSeries getSeries() {
    return series;
  }

  public Image getPatientImage() {
    return patientImage;
  }

  /**
   * Reload all RT objects (RTSTRUCT, RTPLAN, RTDOSE) and re-link them. Structures must be loaded
   * first so that doses can attach to plans referencing them.
   *
   * <p>Only DVHs stored in the RTDOSE files are exposed at load time. DVH (re)calculation from dose
   * grids is an <b>experimental, unvalidated</b> feature (algorithm derived from dicompyler) and is
   * performed only on demand via {@link #computeDvhOnDemand(StructRegion)}.
   */
  public void reloadRtCase() {
    // Order matters: structures -> plans -> doses (a dose attaches to its plan in initPlan).
    for (MediaElement rt : rtElements) {
      if (rt instanceof StructureSet structureSet) {
        structureSet.initContours(patientImage.getImage());
        structures.add(structureSet);
      }
    }
    for (MediaElement rt : rtElements) {
      if (rt instanceof Plan plan) {
        initPlan(plan);
      }
    }
    for (MediaElement rt : rtElements) {
      if (rt instanceof Dose dose) {
        dose.initPlan(this);
      }
    }

    if (plans.isEmpty()) {
      return;
    }
    for (Plan plan : plans) {
      initDoseLuts(plan);
      initIsoDoses(plan);
      attachStoredDvhs(plan);
    }
  }

  private void initDoseLuts(Plan plan) {
    for (Dose dose : plan.getDoses()) {
      dose.setDoseMmLUT(
          calculatePixelLookupTable(
              Objects.requireNonNull(dose.getSeries().getMedia(MEDIA_POSITION.FIRST, null, null))));
      dose.initialiseDoseGridToImageGrid(patientImage);
    }
  }

  private void attachStoredDvhs(Plan plan) {
    for (Dose dose : plan.getDoses()) {
      if (dose.getDoseMax() > 0) {
        getStructures().stream()
            .flatMap(structure -> structure.getSegAttributes().values().stream())
            .forEach(region -> attachStoredDvh(plan, dose, region));
      }
    }
  }

  /**
   * Attach to the structure the DVH stored in the RTDOSE for that region (if any). This never
   * triggers a calculation.
   */
  private void attachStoredDvh(Plan plan, Dose dose, StructRegion region) {
    Dvh structureDvh = dose.getDvhMap().get(region.getId());
    if (structureDvh == null) {
      return;
    }
    if (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
        && "CM3".equals(structureDvh.getDvhVolumeUnit())) {
      // Absolute volume is provided in cm^3 - use it
      region.setVolume(structureDvh.getDvhData()[0]);
    }
    structureDvh.setPlan(plan);
    region.setDvh(structureDvh);

    if (LOGGER.isDebugEnabled()) {
      logDvhSummary(plan, region, structureDvh);
    }
  }

  /**
   * Compute a DVH for the given region from the dose grid using the experimental, unvalidated
   * dicompyler-derived algorithm. The newly calculated DVH is stored in the dose's DVH map and
   * attached to the region. This is meant to be called <b>on demand only</b> (e.g. after the user
   * has explicitly accepted the experimental warning).
   *
   * @return {@code true} if at least one DVH was actually computed (i.e. the region had no stored
   *     DVH for at least one dose belonging to the loaded plans)
   */
  public boolean computeDvhOnDemand(StructRegion region) {
    boolean computed = false;
    for (Plan plan : plans) {
      for (Dose dose : plan.getDoses()) {
        if (dose.getDoseMax() <= 0) {
          continue;
        }
        Dvh existing = dose.getDvhMap().get(region.getId());
        if (existing != null && existing.getDvhSource().equals(DataSource.PROVIDED)) {
          // Keep the stored DVH untouched.
          continue;
        }
        Dvh dvh = initCalculatedDvh(region, dose);
        dose.getDvhMap().put(region.getId(), dvh);
        dvh.setPlan(plan);
        region.setDvh(dvh);
        computed = true;
        if (LOGGER.isDebugEnabled()) {
          logDvhSummary(plan, region, dvh);
        }
      }
    }
    return computed;
  }

  private static void logDvhSummary(Plan plan, StructRegion region, Dvh dvh) {
    LOGGER.debug(
        "Structure: {}, {} Volume: {} cm³",
        region.getLabel(),
        region.getVolumeSource(),
        String.format("%.4f", region.getVolume()));
    String label = region.getLabel();
    DataSource src = dvh.getDvhSource();
    double rxDose = plan.getRxDose();
    LOGGER.debug(
        "Structure: {}, {} Min Dose: {} %",
        label, src, Dose.calculateRelativeDose(dvh.getDvhMinimumDoseCGy(), rxDose));
    LOGGER.debug(
        "Structure: {}, {} Max Dose: {} %",
        label, src, Dose.calculateRelativeDose(dvh.getDvhMaximumDoseCGy(), rxDose));
    LOGGER.debug(
        "Structure: {}, {} Mean Dose: {} %",
        label, src, Dose.calculateRelativeDose(dvh.getDvhMeanDoseCGy(), rxDose));
  }

  @Override
  public int hashCode() {
    return series.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof RtSet other && series.equals(other.series);
  }

  /**
   * Initialise an RTPLAN object and merge it with any pre-existing dummy plan with the same UID.
   */
  private void initPlan(Plan plan) {
    Attributes dcmItems = plan.getMediaReader().getDicomObject();
    if (dcmItems == null) {
      return;
    }
    String planSopInstanceUid = dcmItems.getString(Tag.SOPInstanceUID);
    plan.setSopInstanceUid(planSopInstanceUid);

    Plan target = findExistingDummyPlan(planSopInstanceUid).orElse(plan);
    if (target != plan) {
      plans.remove(target);
    }

    populatePlanMetadata(target, dcmItems);
    target.setRxDose(0.0);

    Sequence doseRefSeq = dcmItems.getSequence(Tag.DoseReferenceSequence);
    if (doseRefSeq != null) {
      extractRxDoseFromDoseRef(target, doseRefSeq);
    }
    if (MathUtil.isEqualToZero(target.getRxDose())) {
      extractRxDoseFromFractionGroup(target, dcmItems);
    }

    plans.add(target);
  }

  private Optional<Plan> findExistingDummyPlan(String sopInstanceUid) {
    return plans.stream()
        .filter(p -> sopInstanceUid.equals(p.getSopInstanceUid()))
        .filter(p -> p.getKey() == null)
        .findFirst();
  }

  private static void populatePlanMetadata(Plan plan, Attributes dcm) {
    plan.setLabel(dcm.getString(Tag.RTPlanLabel));
    plan.setName(dcm.getString(Tag.RTPlanName));
    plan.setDescription(dcm.getString(Tag.RTPlanDescription));
    plan.setDate(dcm.getDate(Tag.RTPlanDateAndTime));
    plan.setGeometry(dcm.getString(Tag.RTPlanGeometry));
  }

  private static void extractRxDoseFromDoseRef(Plan plan, Sequence doseRefSeq) {
    for (Attributes doseRef : doseRefSeq) {
      String type = doseRef.getString(Tag.DoseReferenceStructureType);
      Double targetDose =
          DicomUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);
      if (targetDose == null) {
        // TODO: support Dose Reference Point Coordinates fallback
        continue;
      }
      // DICOM specifies prescription dose in Gy -> convert to cGy
      double rxDose = targetDose * 100;

      switch (type) {
        case "POINT" -> LOGGER.info("Not supported: dose reference point specified as ROI");
        case "VOLUME", "SITE", "COORDINATES" -> {
          // Keep the highest prescribed dose
          if (plan.getRxDose() != null && rxDose > plan.getRxDose()) {
            plan.setRxDose(rxDose);
            String desc = doseRef.getString(Tag.DoseReferenceDescription);
            if (StringUtil.hasText(desc)) {
              plan.appendName(desc);
            }
          }
        }
        case null, default -> {
          /* ignore */
        }
      }
    }
  }

  private static void extractRxDoseFromFractionGroup(Plan plan, Attributes dcmItems) {
    Attributes fractionGroup = dcmItems.getNestedDataset(Tag.FractionGroupSequence);
    Integer fx =
        DicomUtils.getIntegerFromDicomElement(fractionGroup, Tag.NumberOfFractionsPlanned, null);
    if (fx == null) {
      return;
    }
    Sequence refBeamSeq = fractionGroup.getSequence(Tag.ReferencedBeamSequence);
    if (refBeamSeq == null) {
      return;
    }
    for (Attributes beam : refBeamSeq) {
      if (!beam.contains(Tag.BeamDose) || !beam.containsValue(Tag.BeamDose)) {
        continue;
      }
      Double rxDose = plan.getRxDose();
      Double beamDose = DicomUtils.getDoubleFromDicomElement(beam, Tag.BeamDose, null);
      if (beamDose != null && rxDose != null) {
        plan.setRxDose(rxDose + (beamDose * fx * 100));
      }
    }
  }

  private void initIsoDoses(Plan plan) {
    for (Dose dose : plan.getDoses()) {
      if (plan.getRxDose() != null && dose.getIsoDoseSet().isEmpty()) {
        dose.initDoseSet(plan.getRxDose(), this);
      }
    }
  }

  public Set<StructureSet> getStructures() {
    return structures;
  }

  public StructureSet getFirstStructure() {
    return structures.isEmpty() ? null : structures.getFirst();
  }

  public Set<Plan> getPlans() {
    return plans;
  }

  public Plan getFirstPlan() {
    return plans.isEmpty() ? null : plans.getFirst();
  }

  public List<MediaElement> getRtElements() {
    return rtElements;
  }

  /** Calculates the structure plane thickness from a sorted set of z coordinates. */
  static double calculatePlaneThickness(Set<KeyDouble> planesSet) {
    if (planesSet.size() < 2) {
      return 0.0;
    }
    // Avoid List<KeyDouble> boxing/sort: use a primitive double[] sort
    double[] zs = new double[planesSet.size()];
    int idx = 0;
    for (KeyDouble kd : planesSet) {
      zs[idx++] = kd.getValue();
    }
    Arrays.sort(zs);
    double thickness = Double.POSITIVE_INFINITY;
    for (int i = 1; i < zs.length; i++) {
      double diff = zs[i] - zs[i - 1];
      if (diff < thickness) {
        thickness = diff;
      }
    }
    return Double.isInfinite(thickness) ? 0.0 : thickness;
  }

  public Dvh initCalculatedDvh(StructRegion region, Dose dose) {
    Dvh dvh = new Dvh();
    dvh.setReferencedRoiNumber(region.getId());
    dvh.setDvhSource(DataSource.CALCULATED);
    dvh.setType("CUMULATIVE");
    dvh.setDoseUnit("CGY");
    dvh.setDvhVolumeUnit("CM3");
    dvh.setDvhDoseScaling(1.0);

    Mat difHistogram = calculateDifferentialDvh(region, dose);
    double[] cumHistogram = convertDifferentialToCumulativeDvh(difHistogram);
    dvh.setDvhData(cumHistogram);
    dvh.setDvhNumberOfBins(cumHistogram.length);
    return dvh;
  }

  private Mat calculateDifferentialDvh(StructRegion region, Dose dose) {
    if (region.getPlanes() == null || region.getPlanes().isEmpty()) {
      return null;
    }
    DicomImageElement doseImage = dose.getSeries().getMedia(MEDIA_POSITION.FIRST, null, null);
    Vector3d doseImageSpacing = doseImage.getRawSliceGeometry().getVoxelSpacing();
    double maxDose = dose.getDoseMax() * dose.getDoseGridScaling() * 100;

    double volume = 0.0;
    Mat histogram = new Mat((int) maxDose, 1, CvType.CV_32FC1, new Scalar(0.0));
    double sliceVoxelVolume = doseImageSpacing.x * doseImageSpacing.y * region.getThickness();

    for (Entry<KeyDouble, List<StructContour>> entry : region.getPlanes().entrySet()) {
      KeyDouble z = entry.getKey();
      List<StructContour> planeContours = entry.getValue();
      int maxContourIndex = region.calculateLargestContour(planeContours).index();
      if (dose.getDosePlaneBySlice(z.getValue()) == null) {
        continue;
      }

      for (int c = 0; c < planeContours.size(); c++) {
        Mat contourMask = calculateContourMask(dose.getDoseMmLUT(), planeContours.get(c));
        Mat hist = dose.getMaskedDosePlaneHist(z.getValue(), contourMask, (int) maxDose);

        double vol = sumColumn0(hist) * sliceVoxelVolume;
        if (c == maxContourIndex) {
          volume += vol;
          add(histogram, hist, histogram);
        }
        // TODO: otherwise add or subtract depending on contour location
        ImageConversion.releaseMat(contourMask);
        ImageConversion.releaseMat(hist);
      }
    }

    // Volume units are given in cm^3
    volume /= 1000;

    double sumHistogram = sumColumn0(histogram);
    multiply(histogram, new Scalar(volume / (sumHistogram == 0.0 ? 1.0 : sumHistogram)), histogram);

    return histogram;
  }

  private static double sumColumn0(Mat singleColumn) {
    int rows = singleColumn.rows();
    if (rows == 0) {
      return 0.0;
    }
    // Bulk-read the whole column in one JNI call instead of one call per element.
    float[] buf = new float[rows];
    singleColumn.get(0, 0, buf);
    double sum = 0.0;
    for (float v : buf) {
      sum += v;
    }
    return sum;
  }

  private double[] convertDifferentialToCumulativeDvh(Mat difHistogram) {
    if (difHistogram == null) {
      return new double[0];
    }
    int size = difHistogram.rows();
    float[] buf = new float[size];
    difHistogram.get(0, 0, buf);
    double[] cumDvh = new double[size];
    double tail = 0;
    for (int i = size - 1; i >= 0; i--) {
      tail += buf[i];
      cumDvh[i] = tail;
    }
    return cumDvh;
  }

  private DoseLut calculatePixelLookupTable(DicomImageElement dicomImage) {
    Vector3d voxelSpacing = dicomImage.getRawSliceGeometry().getVoxelSpacing();
    Vector3d rowDirection = dicomImage.getRawSliceGeometry().getRow();
    Vector3d columnDirection = dicomImage.getRawSliceGeometry().getColumn();
    Vector3d position = dicomImage.getRawSliceGeometry().getTLHC();

    // DICOM C.7.6.2.1 Equation C.7.6.2.1-1 reduced for the i-axis (x) and j-axis (y) only.
    double dxPerI = rowDirection.x * voxelSpacing.x;
    double dyPerJ = columnDirection.y * voxelSpacing.y;
    int width = dicomImage.getImage().width();
    int height = dicomImage.getImage().height();

    double[] x = new double[width];
    for (int i = 0; i < width; i++) {
      x[i] = dxPerI * i + position.x;
    }
    double[] y = new double[height];
    for (int j = 0; j < height; j++) {
      y[j] = dyPerJ * j + position.y;
    }
    return new DoseLut(x, y);
  }

  /**
   * Computes a binary {@code CV_32FC1} mask (255 inside the polygon, 0 outside) for the given
   * contour, using the dose-grid mm LUT to convert patient coordinates into pixel indices.
   *
   * <p>Implementation: previously this performed an {@code Imgproc.pointPolygonTest} per pixel
   * (O(rows · cols · vertices)). It now rasterises the polygon once with {@code fillPoly}.
   */
  private Mat calculateContourMask(DoseLut doseMmLUT, SegContour contour) {
    double[] xLut = doseMmLUT.x();
    double[] yLut = doseMmLUT.y();
    int cols = xLut.length;
    int rows = yLut.length;

    Mat binaryMask = Mat.zeros(rows, cols, CvType.CV_32FC1);
    if (!(contour instanceof StructContour structContour)) {
      return binaryMask;
    }
    double[] points = structContour.getPoints();
    if (points == null || points.length < 6 || points.length % 3 != 0) {
      return binaryMask;
    }

    // Linear mm -> pixel mapping derived from the (regularly-spaced) LUT.
    double xOrigin = xLut[0];
    double yOrigin = yLut[0];
    double dx = (xLut[cols - 1] - xOrigin) / (cols - 1);
    double dy = (yLut[rows - 1] - yOrigin) / (rows - 1);
    if (dx == 0.0 || dy == 0.0) {
      return binaryMask;
    }

    int n = points.length / 3;
    Point[] pixelPoints = new Point[n];
    for (int i = 0, p = 0; i < points.length; i += 3, p++) {
      pixelPoints[p] = new Point((points[i] - xOrigin) / dx, (points[i + 1] - yOrigin) / dy);
    }

    MatOfPoint poly = new MatOfPoint();
    poly.fromArray(roundPoints(pixelPoints));

    Mat byteMask = Mat.zeros(rows, cols, CvType.CV_8UC1);
    Imgproc.fillPoly(byteMask, java.util.List.of(poly), new Scalar(255));
    byteMask.convertTo(binaryMask, CvType.CV_32FC1);
    poly.release();
    ImageConversion.releaseMat(byteMask);
    return binaryMask;
  }

  private static org.opencv.core.Point[] roundPoints(Point[] in) {
    Point[] out = new Point[in.length];
    for (int i = 0; i < in.length; i++) {
      out[i] = new Point(Math.round(in[i].x), Math.round(in[i].y));
    }
    return out;
  }
}
