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

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
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
import org.opencv.core.MatOfPoint2f;
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

  boolean forceRecalculateDvh = false;

  public RtSet(DicomSeries series, List<RtSpecialElement> rtElements) {
    this.series = Objects.requireNonNull(series);
    this.rtElements.addAll(Objects.requireNonNull(rtElements));
    Object image = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    if (image instanceof DicomImageElement dicomImageElement) {
      this.patientImage = new Image(dicomImageElement);
      this.patientImage.setImageLUT(this.calculatePixelLookupTable(dicomImageElement));
    }
  }

  public DicomSeries getSeries() {
    return series;
  }

  public Image getPatientImage() {
    return patientImage;
  }

  public void reloadRtCase(boolean forceRecalculateDvh) {
    this.forceRecalculateDvh = forceRecalculateDvh;

    // First initialise all RTSTRUCT
    for (MediaElement rt : this.rtElements) {
      if (rt instanceof StructureSet structureSet) {
        structureSet.initContours(patientImage.getImage());
        structures.add(structureSet);
      }
    }

    // Then initialise all RTPLAN
    for (MediaElement rt : this.rtElements) {
      // Photon and Proton Plans
      if (rt instanceof Plan plan) {
        initPlan(plan);
      }
    }

    // Then initialise all RTDOSE
    for (MediaElement rt : this.rtElements) {
      if (rt instanceof Dose dose) {
        dose.initPlan(this);
      }
    }

    // Plans and doses are loaded
    if (!plans.isEmpty()) {
      for (Plan plan : plans) {

        // Init Dose LUTs
        for (Dose dose : plan.getDoses()) {
          dose.setDoseMmLUT(
              calculatePixelLookupTable(
                  Objects.requireNonNull(
                      dose.getSeries().getMedia(MEDIA_POSITION.FIRST, null, null))));
          dose.initialiseDoseGridToImageGrid(patientImage);
        }

        this.initIsoDoses(plan);

        // Re-init DVHs
        for (Dose dose : plan.getDoses()) {
          if (dose.getDoseMax() > 0) {
            getFirstStructure().getSegAttributes().values().parallelStream()
                .forEach(region -> computeDvh(plan, dose, region));
          }
        }
      }
    }
  }

  private void computeDvh(Plan plan, Dose dose, StructRegion region) {
    // If DVH exists for the structure and setting always recalculate is false
    Dvh structureDvh = dose.getDvhMap().get(region.getId());

    // Re-calculate DVH if it does not exist or if it is provided and force recalculation
    // is set up
    if (structureDvh == null
        || (structureDvh.getDvhSource().equals(DataSource.PROVIDED) && this.forceRecalculateDvh)) {
      structureDvh = this.initCalculatedDvh(region, dose);
      dose.getDvhMap().put(region.getId(), structureDvh);
    }
    // Otherwise, read provided DVH
    else {
      // Absolute volume is provided and defined in DVH (in cm^3) so use it
      if (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
          && structureDvh.getDvhVolumeUnit().equals("CM3")) {
        region.setVolume(structureDvh.getDvhData()[0]);
      }
    }

    // Associate Plan with DVH to make it accessible from DVH
    structureDvh.setPlan(plan);
    // Associate DVH with structure to make this data accessible from structure
    region.setDvh(structureDvh);

    // Display volume
    double volume = region.getVolume();
    String source = region.getVolumeSource().toString();
    if (LOGGER.isDebugEnabled()) {
      LOGGER.debug(
          "Structure: {}, {} Volume: {} cm³",
          region.getLabel(),
          source,
          String.format("%.4f", volume));

      // If plan is loaded with prescribed treatment dose calculate DVH statistics
      String relativeMinDose =
          String.format(
              "Structure: %s, %s Min Dose: %.3f %%",
              region.getLabel(),
              structureDvh.getDvhSource(),
              Dose.calculateRelativeDose(structureDvh.getDvhMinimumDoseCGy(), plan.getRxDose()));
      String relativeMaxDose =
          String.format(
              "Structure: %s, %s Max Dose: %.3f %%",
              region.getLabel(),
              structureDvh.getDvhSource(),
              Dose.calculateRelativeDose(structureDvh.getDvhMaximumDoseCGy(), plan.getRxDose()));
      String relativeMeanDose =
          String.format(
              "Structure:  %s,  %s Mean Dose: %.3f %%",
              region.getLabel(),
              structureDvh.getDvhSource(),
              Dose.calculateRelativeDose(structureDvh.getDvhMeanDoseCGy(), plan.getRxDose()));
      LOGGER.debug(relativeMinDose);
      LOGGER.debug(relativeMaxDose);
      LOGGER.debug(relativeMeanDose);
    }
  }

  @Override
  public int hashCode() {
    return series.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    RtSet other = (RtSet) obj;
    return (!series.equals(other.series));
  }

  /**
   * Initialise RTPLAN objects
   *
   * @param plan the RTPLAN dicom object
   */
  private void initPlan(Plan plan) {

    Attributes dcmItems = plan.getMediaReader().getDicomObject();
    if (dcmItems != null) {
      String planSopInstanceUid = dcmItems.getString(Tag.SOPInstanceUID);
      plan.setSopInstanceUid(planSopInstanceUid);

      // Some plans exist (probably dummy plans)
      if (!plans.isEmpty()) {

        // Plan with such SOP already exists
        boolean planWithSopExists =
            getPlans().stream().anyMatch(p -> p.getSopInstanceUid().equals(planSopInstanceUid));
        if (planWithSopExists) {
          // Plan do not have associated RTSpecialElement = it is dummy
          Optional<Plan> opPlan =
              getPlans().stream()
                  .filter(p -> p.getSopInstanceUid().equals(planSopInstanceUid))
                  .findFirst();

          if (opPlan.isPresent() && opPlan.get().getKey() == null) {
            plan = opPlan.get();
            // Remove the dummy from the set
            plans.remove(plan);
          }
        }
      }

      plan.setLabel(dcmItems.getString(Tag.RTPlanLabel));
      plan.setName(dcmItems.getString(Tag.RTPlanName));
      plan.setDescription(dcmItems.getString(Tag.RTPlanDescription));
      plan.setDate(dcmItems.getDate(Tag.RTPlanDateAndTime));
      plan.setGeometry(dcmItems.getString(Tag.RTPlanGeometry));

      plan.setRxDose(0.0);

      // When DoseReferenceSequence is defined - get prescribed dose from there (in cGy unit)
      Sequence doseRefSeq = dcmItems.getSequence(Tag.DoseReferenceSequence);
      if (doseRefSeq != null) {
        for (Attributes doseRef : doseRefSeq) {

          String doseRefStructType = doseRef.getString(Tag.DoseReferenceStructureType);

          // Prescribed dose in Gy
          Double targetDose =
              DicomUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);

          if (targetDose != null) {

            // DICOM specifies prescription dose In Gy -> convert to cGy
            double rxDose = targetDose * 100;

            // POINT (dose reference point specified as ROI)
            if ("POINT".equals(doseRefStructType)) {
              // NOOP
              LOGGER.info("Not supported: dose reference point specified as ROI");
            }

            // VOLUME structure is associated with dose (dose reference volume specified as ROI)
            // SITE structure is associated with dose (dose reference clinical site)
            // COORDINATES (point specified by Dose Reference Point Coordinates (300A,0018))
            else if ("VOLUME".equals(doseRefStructType)
                || "SITE".equals(doseRefStructType)
                || "COORDINATES".equals(doseRefStructType)) {

              // Keep the highest prescribed dose
              if (plan.getRxDose() != null && rxDose > plan.getRxDose()) {
                plan.setRxDose(rxDose);

                // Add user defined dose description to plan name
                String doseRefDesc = doseRef.getString(Tag.DoseReferenceDescription);
                if (StringUtil.hasText(doseRefDesc)) {
                  plan.appendName(doseRefDesc);
                }
              }
            }
          }
          // TODO: if target prescribed dose is not defined it should be possible to get the dose
          // value from
          // Dose Reference Point Coordinates
        }
      }

      // When fraction group sequence is defined get prescribed dose from there (in cGy unit)
      if (MathUtil.isEqualToZero(plan.getRxDose())) {
        Attributes fractionGroup = dcmItems.getNestedDataset(Tag.FractionGroupSequence);
        Integer fx =
            DicomUtils.getIntegerFromDicomElement(
                fractionGroup, Tag.NumberOfFractionsPlanned, null);
        if (fx != null) {
          Sequence refBeamSeq = fractionGroup.getSequence(Tag.ReferencedBeamSequence);
          if (refBeamSeq != null) {
            for (Attributes beam : refBeamSeq) {
              if (beam.contains(Tag.BeamDose) && beam.containsValue(Tag.BeamDose)) {
                Double rxDose = plan.getRxDose();
                Double beamDose = DicomUtils.getDoubleFromDicomElement(beam, Tag.BeamDose, null);
                if (beamDose != null && rxDose != null) {
                  plan.setRxDose(rxDose + (beamDose * fx * 100));
                }
              }
            }
          }
        }
      }

      plans.add(plan);
    }
  }

  /** Initialise ISO dose levels */
  private void initIsoDoses(Plan plan) {
    // Init IsoDose levels for each dose
    for (Dose dose : plan.getDoses()) {
      // Plan has specified prescribed dose and IsoDoses have not been initialised for specific dose
      // yet
      if (plan.getRxDose() != null && dose.getIsoDoseSet().isEmpty()) {
        dose.initDoseSet(plan.getRxDose(), this);
      }
    }
  }

  public Set<StructureSet> getStructures() {
    return structures;
  }

  public StructureSet getFirstStructure() {
    if (structures.isEmpty()) {
      return null;
    }
    return structures.getFirst();
  }

  public Set<Plan> getPlans() {
    return plans;
  }

  public Plan getFirstPlan() {
    if (!plans.isEmpty()) {
      return this.plans.getFirst();
    }
    return null;
  }

  public List<MediaElement> getRtElements() {
    return rtElements;
  }

  /**
   * Calculates the structure plane thickness
   *
   * @return structure plane thickness
   */
  static double calculatePlaneThickness(Set<KeyDouble> planesSet) {
    // Sort the list of z coordinates
    List<KeyDouble> planes = new ArrayList<>(planesSet);
    Collections.sort(planes);

    // Set maximum thickness as initial value
    double thickness = 10000;

    // Compare z of each two next to each other planes in order to find the minimal shift in z
    for (int i = 1; i < planes.size(); i++) {
      double newThickness = planes.get(i).getValue() - planes.get(i - 1).getValue();
      if (newThickness < thickness) {
        thickness = newThickness;
      }
    }

    // When no other than initial thickness was detected, set 0
    if (thickness > 9999) {
      thickness = 0.0;
    }

    return thickness;
  }

  public Dvh initCalculatedDvh(StructRegion region, Dose dose) {
    Dvh dvh = new Dvh();
    dvh.setReferencedRoiNumber(region.getId());
    dvh.setDvhSource(DataSource.CALCULATED);
    dvh.setType("CUMULATIVE");
    dvh.setDoseUnit("CGY");
    dvh.setDvhVolumeUnit("CM3");
    dvh.setDvhDoseScaling(1.0);

    // Calculate differential DVH
    Mat difHistogram = calculateDifferentialDvh(region, dose);

    // Convert differential DVH to cumulative DVH
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

    // Prepare empty histogram (vector of bins in cGy) for structure
    Mat histogram = new Mat((int) maxDose, 1, CvType.CV_32FC1, new Scalar(0.0));

    // Go through all structure plane slices
    for (Entry<KeyDouble, List<StructContour>> entry : region.getPlanes().entrySet()) {
      KeyDouble z = entry.getKey();

      // Calculate the area for each contour in the current plane
      AbstractMap.SimpleImmutableEntry<Integer, Double> maxContour =
          region.calculateLargestContour(entry.getValue());
      int maxContourIndex = maxContour.getKey();

      // If dose plane does not exist for z, continue with next plane
      MediaElement dosePlane = dose.getDosePlaneBySlice(z.getValue());
      if (dosePlane == null) {
        continue;
      }

      // Calculate histogram for each contour on the plane
      for (int c = 0; c < entry.getValue().size(); c++) {

        SegContour contour = entry.getValue().get(c);

        Mat contourMask = calculateContourMask(dose.getDoseMmLUT(), contour);
        Mat hist = dose.getMaskedDosePlaneHist(z.getValue(), contourMask, (int) maxDose);

        double vol = 0;
        for (int i = 0; i < hist.rows(); i++) {
          vol +=
              hist.get(i, 0)[0] * (doseImageSpacing.x * doseImageSpacing.y * region.getThickness());
        }

        // If this is the largest contour
        if (c == maxContourIndex) {
          volume += vol;
          add(histogram, hist, histogram);
        }
        // TODO: Otherwise add or subtract depending on contour location
        else {

        }
      }
    }

    // Volume units are given in cm^3
    volume /= 1000;

    // Rescale the histogram to reflect the total volume
    double sumHistogram = 0.0;
    for (int i = 0; i < histogram.rows(); i++) {
      sumHistogram += histogram.get(i, 0)[0];
    }
    Scalar scalar = new Scalar(volume / (sumHistogram == 0.0 ? 1.0 : sumHistogram));
    multiply(histogram, scalar, histogram);

    // TODO: Remove the zero bins from the end of histogram

    return histogram;
  }

  private double[] convertDifferentialToCumulativeDvh(Mat difHistogram) {
    if (difHistogram == null) {
      return new double[0];
    }
    int size = difHistogram.rows();
    double[] cumDvh = new double[size];

    for (int i = 0; i < size; i++) {
      cumDvh[i] = 0;
      for (int j = i; j < size; j++) {
        cumDvh[i] += difHistogram.get(j, 0)[0];
      }
    }

    return cumDvh;
  }

  private AbstractMap.SimpleImmutableEntry<double[], double[]> calculatePixelLookupTable(
      DicomImageElement dicomImage) {

    double deltaI = dicomImage.getRawSliceGeometry().getVoxelSpacing().x;
    double deltaJ = dicomImage.getRawSliceGeometry().getVoxelSpacing().y;

    Vector3d rowDirection = dicomImage.getRawSliceGeometry().getRow();
    Vector3d columnDirection = dicomImage.getRawSliceGeometry().getColumn();

    Vector3d position = dicomImage.getRawSliceGeometry().getTLHC();

    // DICOM C.7.6.2.1 Equation C.7.6.2.1-1.
    double[][] m = {
      {rowDirection.x * deltaI, columnDirection.x * deltaJ, 0, position.x},
      {rowDirection.y * deltaI, columnDirection.y * deltaJ, 0, position.y},
      {rowDirection.z * deltaI, columnDirection.z * deltaJ, 0, position.z},
      {0, 0, 0, 1}
    };

    double[] x = new double[dicomImage.getImage().width()];
    // column index to the image plane.
    for (int i = 0; i < dicomImage.getImage().width(); i++) {
      double[][] data = new double[][] {{i}, {0}, {0}, {1}};
      x[i] = multiplyMatrix(m, data)[0][0];
    }

    double[] y = new double[dicomImage.getImage().height()];
    // row index to the image plane
    for (int j = 0; j < dicomImage.getImage().height(); j++) {
      double[][] data = new double[][] {{0}, {j}, {0}, {1}};
      y[j] = multiplyMatrix(m, data)[1][0];
    }

    return new AbstractMap.SimpleImmutableEntry<>(x, y);
  }

  private double[][] multiplyMatrix(double[][] rotation, double[][] data) {
    final int nRows = rotation.length;
    final int nCols = data[0].length;
    final int nSum = rotation[0].length;
    final double[][] out = new double[nRows][nCols];
    for (int row = 0; row < nRows; ++row) {
      for (int col = 0; col < nCols; ++col) {
        double sum = 0;
        for (int i = 0; i < nSum; ++i) {
          sum += rotation[row][i] * data[i][col];
        }
        out[row][col] = sum;
      }
    }

    return out;
  }

  // TODO: this has to consider all plan doses
  // public void getDoseValueForPixel(Plan plan, int pixelX, int pixelY, double z) {
  // if (this.dosePixLUT != null) {
  // // closest x
  // double[] xDistance = new double[this.dosePixLUT.getFirst().length];
  // for (int i = 0; i < xDistance.length; i++) {
  // xDistance[i] = Math.abs(this.dosePixLUT.getFirst()[i] - pixelX);
  // }
  //
  // double minDistanceX = Arrays.stream(xDistance).min().getAsDouble();
  // int xDoseIndex = firstIndexOf(xDistance, minDistanceX, 0.001);
  //
  // // closest y
  // double[] yDistance = new double[this.dosePixLUT.getSecond().length];
  // for (int j = 0; j < yDistance.length; j++) {
  // yDistance[j] = Math.abs(this.dosePixLUT.getSecond()[j] - pixelY);
  // }
  //
  // double minDistanceY = Arrays.stream(yDistance).min().getAsDouble();
  // int yDoseIndex = firstIndexOf(yDistance, minDistanceY, 0.001);
  //
  // Dose dose = plan.getFirstDose();
  // if (dose != null) {
  // MediaElement dosePlane = dose.getDosePlaneBySlice(z);
  // Double doseGyValue = ((DicomImageElement)dosePlane).getImage().get(xDoseIndex, yDoseIndex)[0] *
  // dose.getDoseGridScaling();
  // LOGGER.debug("X: " + pixelX + ", Y: " + pixelY + ", Dose: " + doseGyValue + " Gy / " +
  // calculateRelativeDose(doseGyValue * 100, this.getFirstPlan().getRxDose()) + " %");
  // }
  // }
  // }

  private Mat calculateContourMask(
      AbstractMap.SimpleImmutableEntry<double[], double[]> doseMmLUT, SegContour contour) {

    int cols = doseMmLUT.getKey().length;
    int rows = doseMmLUT.getValue().length;

    List<Point> list = new ArrayList<>();
    if (contour instanceof StructContour structContour) {
      double[] points = structContour.getPoints();
      if (points != null && points.length % 3 == 0 && points.length > 1) {
        for (int i = 0; i < points.length; i = i + 3) {
          list.add(new Point(points[i], points[i + 1]));
        }
      }
    }
    MatOfPoint2f mop = new MatOfPoint2f();
    mop.fromList(list);

    Mat binaryMask = new Mat(rows, cols, CvType.CV_32FC1);

    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        double distance =
            Imgproc.pointPolygonTest(
                mop, new Point(doseMmLUT.getKey()[j], doseMmLUT.getValue()[i]), false);
        // TODO: Include the border line as well?
        if (distance > 0) {
          binaryMask.put(i, j, 255);
        } else {
          binaryMask.put(i, j, 0);
        }
      }
    }

    return binaryMask;
  }
}
