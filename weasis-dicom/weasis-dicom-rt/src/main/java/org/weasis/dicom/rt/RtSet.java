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

import java.awt.Color;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.stream.DoubleStream;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.util.Pair;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.util.DicomObjectUtil;
import org.joml.Vector3d;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.util.MathUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * RtSet is a collection of linked DICOM-RT entities that form the whole treatment case (Plans,
 * Doses, StructureSets)
 *
 * @author Tomas Skripcak
 */
public class RtSet {

  private static final Logger LOGGER = LoggerFactory.getLogger(RtSet.class);

  private final List<MediaElement> rtElements = new ArrayList<>();
  private final Map<RtSpecialElement, StructureSet> structures = new HashMap<>();
  private final Map<RtSpecialElement, Plan> plans = new HashMap<>();
  private final List<MediaElement> images = new ArrayList<>();
  private final Map<String, ArrayList<Contour>> contourMap = new HashMap<>();
  private final String frameOfReferenceUID;

  private Image patientImage;

  private int structureFillTransparency = 115;
  private int isoFillTransparency = 70;
  boolean forceRecalculateDvh = false;
  private final Random rand = new SecureRandom();

  public RtSet(String frameOfReferenceUID, List<MediaElement> rtElements) {
    this.frameOfReferenceUID = Objects.requireNonNull(frameOfReferenceUID);
    this.rtElements.addAll(Objects.requireNonNull(rtElements));
    for (MediaElement rt : rtElements) {
      String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
      if (UID.CTImageStorage.equals(sopUID)) {
        initImage(rt);
      }
    }
  }

  public void reloadRtCase(boolean forceRecalculateDvh) {

    this.forceRecalculateDvh = forceRecalculateDvh;

    // First initialise all RTSTRUCT
    for (MediaElement rt : this.rtElements) {
      String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
      if (UID.RTStructureSetStorage.equals(sopUID)
          && rt instanceof RtSpecialElement rtSpecialElement) {
        initStructures(rtSpecialElement);
      }
    }

    // Then initialise all RTPLAN
    for (MediaElement rt : this.rtElements) {
      String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
      // Photon and Proton Plans
      if ((UID.RTPlanStorage.equals(sopUID) && rt instanceof RtSpecialElement)
          || (UID.RTIonPlanStorage.equals(sopUID) && rt instanceof RtSpecialElement)) {
        initPlan((RtSpecialElement) rt);
      }
    }

    // Then initialise all RTDOSE
    for (MediaElement rt : this.rtElements) {
      String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
      if (UID.RTDoseStorage.equals(sopUID)) {
        initDose(rt);
      }
    }

    // If more than one image, set first image to middle of the series
    if (!images.isEmpty()) {
      DicomImageElement image = (DicomImageElement) this.images.get((this.images.size() / 2) - 1);
      this.patientImage = new Image(image);
      this.patientImage.setImageLUT(this.calculatePixelLookupTable(image));
    }

    // Plans and doses are loaded
    if (!plans.isEmpty()) {
      for (Plan plan : plans.values()) {

        // Init Dose LUTs
        for (Dose dose : plan.getDoses()) {
          dose.setDoseMmLUT(
              this.calculatePixelLookupTable((DicomImageElement) dose.getImages().get(0)));
          dose.initialiseDoseGridToImageGrid(this.patientImage);
        }

        this.initIsoDoses(plan);

        // Re-init DVHs
        for (Dose dose : plan.getDoses()) {
          if (dose.getDoseMax() > 0) {

            // For all ROIs
            for (StructureLayer structureLayer :
                this.getStructureSet(this.getFirstStructure()).values()) {
              Structure structure = structureLayer.getStructure();

              // If DVH exists for the structure and setting always recalculate is false
              Dvh structureDvh = dose.get(structure.getRoiNumber());

              // Re-calculate DVH if it does not exist or if it is provided and force recalculation
              // is set up
              if (structureDvh == null
                  || (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
                      && this.forceRecalculateDvh)) {
                structureDvh = this.initCalculatedDvh(structure, dose);
                dose.put(structure.getRoiNumber(), structureDvh);
              }
              // Otherwise, read provided DVH
              else {
                // Absolute volume is provided and defined in DVH (in cm^3) so use it
                if (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
                    && structureDvh.getDvhVolumeUnit().equals("CM3")) {
                  structure.setVolume(structureDvh.getDvhData()[0]);
                }
              }

              // Associate Plan with DVH to make it accessible from DVH
              structureDvh.setPlan(plan);
              // Associate DVH with structure to make this data accessible from structure
              structure.setDvh(structureDvh);

              // Display volume
              double volume = structure.getVolume();
              String source = structure.getVolumeSource().toString();
              if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                    "Structure: {}, {} Volume: {} cm^3",
                    structure.getRoiName(),
                    source,
                    String.format("%.4f", volume));

                // If plan is loaded with prescribed treatment dose calculate DVH statistics
                String relativeMinDose =
                    String.format(
                        "Structure: %s, %s Min Dose: %.3f %%",
                        structure.getRoiName(),
                        structureDvh.getDvhSource(),
                        RtSet.calculateRelativeDose(
                            structureDvh.getDvhMinimumDoseCGy(), plan.getRxDose()));
                String relativeMaxDose =
                    String.format(
                        "Structure: %s, %s Max Dose: %.3f %%",
                        structure.getRoiName(),
                        structureDvh.getDvhSource(),
                        RtSet.calculateRelativeDose(
                            structureDvh.getDvhMaximumDoseCGy(), plan.getRxDose()));
                String relativeMeanDose =
                    String.format(
                        "Structure:  %s,  %s Mean Dose: %.3f %%",
                        structure.getRoiName(),
                        structureDvh.getDvhSource(),
                        RtSet.calculateRelativeDose(
                            structureDvh.getDvhMeanDoseCGy(), plan.getRxDose()));
                LOGGER.debug(relativeMinDose);
                LOGGER.debug(relativeMaxDose);
                LOGGER.debug(relativeMeanDose);
              }
            }
          }
        }
      }
    }
  }

  @Override
  public int hashCode() {
    return frameOfReferenceUID.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    RtSet other = (RtSet) obj;
    return (!frameOfReferenceUID.equals(other.frameOfReferenceUID));
  }

  /**
   * Initialise RTSTRUCT objects
   *
   * @param rtElement RTSTRUCT dicom object
   */
  private void initStructures(RtSpecialElement rtElement) {

    Attributes dcmItems = rtElement.getMediaReader().getDicomObject();
    if (dcmItems != null) {
      String label = dcmItems.getString(Tag.StructureSetLabel);
      Date datetime = dcmItems.getDate(Tag.StructureSetDateAndTime);
      StructureSet stucts = new StructureSet(label, datetime);

      // Locate the name and number of each ROI
      for (Attributes ssROIseq : dcmItems.getSequence(Tag.StructureSetROISequence)) {
        Structure structure = new Structure();
        structure.setRoiNumber(ssROIseq.getInt(Tag.ROINumber, -1));
        structure.setRoiName(ssROIseq.getString(Tag.ROIName));
        stucts.put(structure.getRoiNumber(), new StructureLayer(structure));
      }

      structures.put(rtElement, stucts);

      // Determine the type of each structure (PTV, organ, external, etc)
      for (Attributes rtROIObsSeq : dcmItems.getSequence(Tag.RTROIObservationsSequence)) {
        StructureLayer layer = stucts.get(rtROIObsSeq.getInt(Tag.ReferencedROINumber, -1));
        if (layer != null) {
          layer.getStructure().setObservationNumber(rtROIObsSeq.getInt(Tag.ObservationNumber, -1));
          layer
              .getStructure()
              .setRtRoiInterpretedType(rtROIObsSeq.getString(Tag.RTROIInterpretedType));
          layer
              .getStructure()
              .setRoiObservationLabel(rtROIObsSeq.getString(Tag.ROIObservationLabel));
        }
      }

      // The coordinate data of each ROI is stored within ROIContourSequence
      for (Attributes roiContourSeq : dcmItems.getSequence(Tag.ROIContourSequence)) {
        StructureLayer layer = stucts.get(roiContourSeq.getInt(Tag.ReferencedROINumber, -1));
        if (layer == null) {
          continue;
        }

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
          rgb = new int[] {rand.nextInt(255), rand.nextInt(255), rand.nextInt(255)};
        }

        Color color1 = DicomObjectUtil.getRGBColor(0xFFFF, rgb);
        Color color2 =
            new Color(
                color1.getRed(), color1.getGreen(), color1.getBlue(), structureFillTransparency);
        layer.getStructure().setColor(color2);

        Map<KeyDouble, List<Contour>> planes = new HashMap<>();

        Sequence cseq = roiContourSeq.getSequence(Tag.ContourSequence);
        if (cseq != null) {
          // Locate the contour sequence for each referenced ROI
          for (Attributes contour : cseq) {
            // For each plane, initialize a new plane dictionary
            Contour plane = new Contour(layer);

            // Determine all the plane properties
            plane.setGeometricType(contour.getString(Tag.ContourGeometricType));
            plane.setContourSlabThickness(
                DicomMediaUtils.getDoubleFromDicomElement(contour, Tag.ContourSlabThickness, null));
            plane.setContourOffsetVector(
                DicomMediaUtils.getDoubleArrayFromDicomElement(
                    contour, Tag.ContourOffsetVector, null));
            Integer pts =
                DicomMediaUtils.getIntegerFromDicomElement(contour, Tag.NumberOfContourPoints, -1);
            plane.setContourPoints(pts);

            double[] points = contour.getDoubles(Tag.ContourData);
            if (points != null && points.length % 3 == 0) {
              plane.setPoints(points);
              if (pts == -1) {
                plane.setContourPoints(points.length / 3);
              }
            }

            // Each plane which coincides with an image slice will have a unique ID
            // take the first one
            for (Attributes attributes : contour.getSequence(Tag.ContourImageSequence)) {
              String sopUID = attributes.getString(Tag.ReferencedSOPInstanceUID);
              if (StringUtil.hasText(sopUID)) {
                ArrayList<Contour> pls = contourMap.computeIfAbsent(sopUID, k -> new ArrayList<>());
                pls.add(plane);
              }
            }

            // Add each plane to the planes' dictionary of the current ROI
            KeyDouble z = new KeyDouble(plane.getCoordinateZ());

            // If there are no contour on specific z position
            if (!planes.containsKey(z)) {
              ArrayList<Contour> list = new ArrayList<>();
              list.add(plane);
              planes.put(z, list);
            }
          }
        }

        // Calculate the plane thickness for the current ROI
        layer.getStructure().setThickness(calculatePlaneThickness(planes));

        // Add the planes' dictionary to the current ROI
        layer.getStructure().setPlanes(planes);
      }
    }
  }

  /**
   * Initialise RTPLAN objects
   *
   * @param rtElement RTPLAN dicom object
   */
  private void initPlan(RtSpecialElement rtElement) {

    Attributes dcmItems = rtElement.getMediaReader().getDicomObject();
    if (dcmItems != null) {

      Plan plan = new Plan();
      String planSopInstanceUid = dcmItems.getString(Tag.SOPInstanceUID);
      plan.setSopInstanceUid(planSopInstanceUid);

      // Some plans exist (probably dummy plans)
      if (!plans.isEmpty()) {

        // Plan with such SOP already exists
        boolean planWithSopExists =
            getPlans().entrySet().stream()
                .anyMatch(p -> p.getValue().getSopInstanceUid().equals(planSopInstanceUid));
        if (planWithSopExists) {
          // Plan do not have associated RTSpecialElement = it is dummy
          Optional<Entry<RtSpecialElement, Plan>> opPlan =
              getPlans().entrySet().stream()
                  .filter(p -> p.getValue().getSopInstanceUid().equals(planSopInstanceUid))
                  .findFirst();

          if (opPlan.isPresent() && opPlan.get().getKey() == null) {
            plan = opPlan.get().getValue();
            // Remove the dummy from the set
            plans.remove(null, plan);
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
      for (Attributes doseRef : dcmItems.getSequence(Tag.DoseReferenceSequence)) {

        String doseRefStructType = doseRef.getString(Tag.DoseReferenceStructureType);

        // Prescribed dose in Gy
        Double targetDose =
            DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);

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

      // When fraction group sequence is defined get prescribed dose from there (in cGy unit)
      if (MathUtil.isEqualToZero(plan.getRxDose())) {
        Attributes fractionGroup = dcmItems.getNestedDataset(Tag.FractionGroupSequence);
        Integer fx =
            DicomMediaUtils.getIntegerFromDicomElement(
                fractionGroup, Tag.NumberOfFractionsPlanned, null);
        if (fx != null) {
          for (Attributes beam : fractionGroup.getSequence(Tag.ReferencedBeamSequence)) {

            if (beam.contains(Tag.BeamDose) && beam.containsValue(Tag.BeamDose)) {
              Double rxDose = plan.getRxDose();
              Double beamDose = DicomMediaUtils.getDoubleFromDicomElement(beam, Tag.BeamDose, null);
              if (beamDose != null && rxDose != null) {
                plan.setRxDose(rxDose + (beamDose * fx * 100));
              }
            }
          }
        }
      }

      plans.put(rtElement, plan);
    }
  }

  /**
   * Initialise RTDOSE objects
   *
   * @param rtElement RTDOSE dicom object
   */
  private void initDose(MediaElement rtElement) {
    Attributes dcmItems = ((DcmMediaReader) rtElement.getMediaReader()).getDicomObject();
    if (dcmItems != null) {

      String sopInstanceUID = dcmItems.getString(Tag.SOPInstanceUID);

      // Dose is Referencing Plan
      Plan plan = null;
      String referencedPlanUid = "";
      for (Attributes refRtPlanSeq : dcmItems.getSequence(Tag.ReferencedRTPlanSequence)) {
        referencedPlanUid = refRtPlanSeq.getString(Tag.ReferencedSOPInstanceUID);
      }

      // Plan is already loaded
      if (!plans.isEmpty()) {
        String finalReferencedPlanUid = referencedPlanUid;
        Optional<Entry<RtSpecialElement, Plan>> opPlan =
            getPlans().entrySet().stream()
                .filter(p -> p.getValue().getSopInstanceUid().equals(finalReferencedPlanUid))
                .findFirst();
        if (opPlan.isPresent()) {
          plan = opPlan.get().getValue();
        }
      }
      // Dummy plan will be created
      else {
        plan = new Plan();
        plan.setSopInstanceUid(referencedPlanUid);
        plans.put(null, plan);
      }

      // Dose for plan
      if (plan != null) {
        Dose rtDose = null;

        // Dose object with such SOP already exists, use it
        if (!plan.getDoses().isEmpty()) {
          boolean doseWithSopExists =
              plan.getDoses().stream().anyMatch(i -> i.getSopInstanceUid().equals(sopInstanceUID));
          if (doseWithSopExists) {
            Optional<Dose> opPlan =
                plan.getDoses().stream()
                    .filter(i -> i.getSopInstanceUid().equals(sopInstanceUID))
                    .findFirst();
            if (opPlan.isPresent()) {
              rtDose = opPlan.get();
            }
          }
        }
        // Create a new dose object
        else {
          rtDose = new Dose();
          plan.getDoses().add(rtDose);

          rtDose.setSopInstanceUid(sopInstanceUID);
          rtDose.setImagePositionPatient(dcmItems.getDoubles(Tag.ImagePositionPatient));
          rtDose.setComment(dcmItems.getString(Tag.DoseComment));
          rtDose.setDoseUnit(dcmItems.getString(Tag.DoseUnits));
          rtDose.setDoseType(dcmItems.getString(Tag.DoseType));
          rtDose.setDoseSummationType(dcmItems.getString(Tag.DoseSummationType));
          rtDose.setGridFrameOffsetVector(dcmItems.getDoubles(Tag.GridFrameOffsetVector));
          rtDose.setDoseGridScaling(dcmItems.getDouble(Tag.DoseGridScaling, 0.0));

          // Check whether DVH is included
          Sequence dvhSeq = dcmItems.getSequence(Tag.DVHSequence);
          if (dvhSeq != null) {

            for (Attributes dvhAttributes : dvhSeq) {

              // Need to refer to delineated contour
              Dvh rtDvh = null;
              Sequence dvhRefRoiSeq = dvhAttributes.getSequence(Tag.DVHReferencedROISequence);
              if (dvhRefRoiSeq == null) {
                continue;
              } else if (dvhRefRoiSeq.size() == 1) {
                rtDvh = new Dvh();
                Attributes dvhRefRoiAttributes = dvhRefRoiSeq.get(0);
                rtDvh.setReferencedRoiNumber(
                    dvhRefRoiAttributes.getInt(Tag.ReferencedROINumber, -1));

                LOGGER.debug("Found DVH for ROI: {}", rtDvh.getReferencedRoiNumber());
              }

              if (rtDvh != null) {
                rtDvh.setDvhSource(DataSource.PROVIDED);
                // Convert Differential DVH to Cumulative
                if (dvhSeq.get(0).getString(Tag.DVHType).equals("DIFFERENTIAL")) {

                  LOGGER.info("Not supported: converting differential DVH to cumulative");

                  double[] data = dvhAttributes.getDoubles(Tag.DVHData);
                  if (data != null && data.length % 2 == 0) {

                    // X of histogram
                    double[] dose = new double[data.length / 2];

                    // Y of histogram
                    double[] volume = new double[data.length / 2];

                    // Separate the dose and volume values into distinct arrays
                    for (int i = 0; i < data.length; i = i + 2) {
                      dose[i] = data[i];
                      volume[i] = data[i + 1];
                    }

                    // Get the min and max dose in cGy
                    int minDose = (int) (dose[0] * 100);
                    int maxDose = (int) DoubleStream.of(dose).sum();

                    // Get volume values
                    double maxVolume = DoubleStream.of(volume).sum();

                    // Determine the dose values that are missing from the original data
                    double[] missingDose = new double[minDose];
                    for (int j = 0; j < minDose; j++) {
                      missingDose[j] = maxVolume;
                    }

                    // Cumulative dose - x of histogram
                    // Cumulative volume data - y of histogram
                    double[] cumVolume = new double[dose.length];
                    double[] cumDose = new double[dose.length];
                    for (int k = 0; k < dose.length; k++) {
                      cumVolume[k] =
                          DoubleStream.of(Arrays.copyOfRange(volume, k, dose.length)).sum();
                      cumDose[k] = DoubleStream.of(Arrays.copyOfRange(dose, 0, k)).sum() * 100;
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

                rtDose.put(rtDvh.getReferencedRoiNumber(), rtDvh);
              }
            }
          }
        }

        // Add dose image to the dose
        if (rtDose != null) {
          rtDose.getImages().add(rtElement);
        }
      }
    }
  }

  /** Initialise ISO dose levels */
  private void initIsoDoses(Plan plan) {
    // Init IsoDose levels for each dose
    for (Dose dose : plan.getDoses()) {

      // Plan has specified prescribed dose and IsoDoses have not been initialised for specific dose
      // yet
      if (plan.getRxDose() != null && dose.getIsoDoseSet().isEmpty()) {

        int doseMaxLevel =
            (int)
                calculateRelativeDose(
                    (dose.getDoseMax() * dose.getDoseGridScaling() * 100), plan.getRxDose());

        // Max and standard levels 102, 100, 98, 95, 90, 80, 70, 50, 30
        if (doseMaxLevel > 0) {
          dose.getIsoDoseSet()
              .put(
                  doseMaxLevel,
                  new IsoDoseLayer(
                      new IsoDose(
                          doseMaxLevel,
                          new Color(120, 0, 0, isoFillTransparency),
                          "Max", // NON-NLS
                          plan.getRxDose()))); // NON-NLS
          dose.getIsoDoseSet()
              .put(
                  102,
                  new IsoDoseLayer(
                      new IsoDose(
                          102, new Color(170, 0, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  100,
                  new IsoDoseLayer(
                      new IsoDose(
                          100, new Color(238, 69, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  98,
                  new IsoDoseLayer(
                      new IsoDose(
                          98, new Color(255, 165, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  95,
                  new IsoDoseLayer(
                      new IsoDose(
                          95, new Color(255, 255, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  90,
                  new IsoDoseLayer(
                      new IsoDose(
                          90, new Color(0, 255, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  80,
                  new IsoDoseLayer(
                      new IsoDose(
                          80, new Color(0, 139, 0, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  70,
                  new IsoDoseLayer(
                      new IsoDose(
                          70, new Color(0, 255, 255, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  50,
                  new IsoDoseLayer(
                      new IsoDose(
                          50, new Color(0, 0, 255, isoFillTransparency), "", plan.getRxDose())));
          dose.getIsoDoseSet()
              .put(
                  30,
                  new IsoDoseLayer(
                      new IsoDose(
                          30, new Color(0, 0, 128, isoFillTransparency), "", plan.getRxDose())));

          // Commented level just for testing
          // dose.getIsoDoseSet().put(2, new IsoDoseLayer(new IsoDose(2, new Color(0, 0, 111,
          // isoFillTransparency), "", plan.getRxDose())));

          // Go through whole imaging grid (CT)
          for (MediaElement me : this.images) {

            // Image slice UID and position
            DicomImageElement image = (DicomImageElement) me;
            String uidKey = TagD.getTagValue(me, Tag.SOPInstanceUID, String.class);
            KeyDouble z = new KeyDouble(image.getSliceGeometry().getTLHC().z);

            for (IsoDoseLayer isoDoseLayer : dose.getIsoDoseSet().values()) {
              double isoDoseThreshold = isoDoseLayer.getIsoDose().getAbsoluteDose();

              List<MatOfPoint> isoContours = dose.getIsoDoseContourPoints(z, isoDoseThreshold);

              // Create empty hash map of planes for IsoDose layer if there is none
              if (isoDoseLayer.getIsoDose().getPlanes() == null) {
                isoDoseLayer.getIsoDose().setPlanes(new HashMap<>());
              }

              for (MatOfPoint matOfPoint : isoContours) {

                // Create a new IsoDose contour plane for Z or select existing one
                // it will hold list of contours for that plane
                isoDoseLayer.getIsoDose().getPlanes().computeIfAbsent(z, k -> new ArrayList<>());

                // For each iso contour create a new contour
                Contour isoContour = new Contour(isoDoseLayer);

                // Populate point coordinates
                double[] newContour = new double[matOfPoint.toArray().length * 3];
                int k = 0;

                for (Point point : matOfPoint.toList()) {
                  double[] coordinates = new double[2];

                  coordinates[0] = dose.getDoseMmLUT().getFirst()[(int) point.x];
                  coordinates[1] = dose.getDoseMmLUT().getSecond()[(int) point.y];

                  newContour[k] = coordinates[0];
                  newContour[k + 1] = coordinates[1];
                  newContour[k + 2] = z.getValue();
                  k += 3;
                }

                isoContour.setPoints(newContour);
                isoContour.setContourPoints(newContour.length);
                isoContour.setGeometricType("CLOSED_PLANAR"); // NON-NLS

                // For lookup from GUI use specific image UID
                if (StringUtil.hasText(uidKey)) {
                  ArrayList<Contour> pls =
                      dose.getIsoContourMap().computeIfAbsent(uidKey, l -> new ArrayList<>());
                  pls.add(isoContour);
                }

                // Assign
                isoDoseLayer.getIsoDose().getPlanes().get(z).add(isoContour);
              }
            }
          }

          // When finished creation of iso contours plane data calculate the plane thickness
          for (IsoDoseLayer isoDoseLayer : dose.getIsoDoseSet().values()) {
            isoDoseLayer
                .getIsoDose()
                .setThickness(calculatePlaneThickness(isoDoseLayer.getIsoDose().getPlanes()));
          }
        }
      }
    }
  }

  private void initImage(MediaElement rtElement) {
    images.add(rtElement);
  }

  public StructureSet getStructureSet(RtSpecialElement rt) {
    return structures.get(rt);
  }

  public Map<RtSpecialElement, StructureSet> getStructures() {
    return structures;
  }

  public RtSpecialElement getFirstStructure() {
    if (structures.isEmpty()) {
      return null;
    }
    return structures.keySet().iterator().next();
  }

  public Map<String, ArrayList<Contour>> getContourMap() {
    return contourMap;
  }

  public Plan getPlan(RtSpecialElement rt) {
    return plans.get(rt);
  }

  public Map<RtSpecialElement, Plan> getPlans() {
    return plans;
  }

  public RtSpecialElement getFirstPlanKey() {
    if (plans.isEmpty()) {
      return null;
    }
    return plans.keySet().iterator().next();
  }

  public Plan getFirstPlan() {
    if (!plans.isEmpty()) {
      return this.plans.entrySet().iterator().next().getValue();
    }

    return null;
  }

  public List<MediaElement> getRtElements() {
    return rtElements;
  }

  public int getStructureFillTransparency() {
    return this.structureFillTransparency;
  }

  public void setStructureFillTransparency(int value) {
    this.structureFillTransparency = value;
    structures.values().stream()
        .flatMap(s -> s.values().stream())
        .forEach(
            s -> {
              Color c = s.getStructure().getColor();
              s.getStructure()
                  .setColor(
                      new Color(c.getRed(), c.getGreen(), c.getBlue(), structureFillTransparency));
            });
  }

  public int getIsoFillTransparency() {
    return this.isoFillTransparency;
  }

  public void setIsoFillTransparency(int value) {
    this.isoFillTransparency = value;
    plans.values().stream()
        .flatMap(p -> p.getDoses().stream())
        .forEach(
            d ->
                d.getIsoDoseSet()
                    .values()
                    .forEach(
                        l -> {
                          Color c = l.getIsoDose().getColor();
                          l.getIsoDose()
                              .setColor(
                                  new Color(
                                      c.getRed(), c.getGreen(), c.getBlue(), isoFillTransparency));
                        }));
  }

  /**
   * Calculates the structure plane thickness
   *
   * @return structure plane thickness
   */
  private static double calculatePlaneThickness(Map<KeyDouble, List<Contour>> planesMap) {
    // Sort the list of z coordinates
    List<KeyDouble> planes = new ArrayList<>(planesMap.keySet());
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

  private static double[] interpolate(
      int[] interpolatedX, double[] xCoordinates, double[] yCoordinates) {
    double[] interpolatedY = new double[interpolatedX.length];

    LinearInterpolator li = new LinearInterpolator();
    PolynomialSplineFunction psf = li.interpolate(xCoordinates, yCoordinates);

    for (int i = 0; i <= interpolatedX.length; ++i) {
      interpolatedY[0] = psf.value(interpolatedX[i]);
    }

    return interpolatedY;
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

  public Dvh initCalculatedDvh(Structure structure, Dose dose) {
    Dvh dvh = new Dvh();
    dvh.setReferencedRoiNumber(structure.getRoiNumber());
    dvh.setDvhSource(DataSource.CALCULATED);
    dvh.setType("CUMULATIVE");
    dvh.setDoseUnit("CGY");
    dvh.setDvhVolumeUnit("CM3");
    dvh.setDvhDoseScaling(1.0);

    // Calculate differential DVH
    Mat difHistogram = calculateDifferentialDvh(structure, dose);

    // Convert differential DVH to cumulative DVH
    double[] cumHistogram = convertDifferentialToCumulativeDvh(difHistogram);
    dvh.setDvhData(cumHistogram);
    dvh.setDvhNumberOfBins(cumHistogram.length);

    return dvh;
  }

  private Mat calculateDifferentialDvh(Structure structure, Dose dose) {

    DicomImageElement doseImage = (DicomImageElement) dose.getImages().get(0);
    Vector3d doseImageSpacing = doseImage.getSliceGeometry().getVoxelSpacing();
    double maxDose = dose.getDoseMax() * dose.getDoseGridScaling() * 100;

    double volume = 0f;

    // Prepare empty histogram (vector of bins in cGy) for structure
    Mat histogram = new Mat((int) maxDose, 1, CvType.CV_32FC1);
    if (structure.getPlanes() != null && !structure.getPlanes().isEmpty()) {
      // Each bin in histogram represents 1 cGy
      for (int i = 0; i < histogram.rows(); i++) {
        histogram.put(i, 0, 0.0);
      }
    }

    // Go through all structure plane slices
    for (Map.Entry<KeyDouble, List<Contour>> entry : structure.getPlanes().entrySet()) {
      KeyDouble z = entry.getKey();

      // Calculate the area for each contour in the current plane
      Pair<Integer, Double> maxContour = structure.calculateLargestContour(entry.getValue());
      int maxContourIndex = maxContour.getFirst();

      // If dose plane does not exist for z, continue with next plane
      MediaElement dosePlane = dose.getDosePlaneBySlice(z.getValue());
      if (dosePlane == null) {
        continue;
      }

      // Calculate histogram for each contour on the plane
      for (int c = 0; c < entry.getValue().size(); c++) {

        Contour contour = entry.getValue().get(c);

        Mat contourMask = calculateContourMask(dose.getDoseMmLUT(), contour);
        Mat hist = dose.getMaskedDosePlaneHist(z.getValue(), contourMask, (int) maxDose);

        double vol = 0;
        for (int i = 0; i < hist.rows(); i++) {
          vol +=
              hist.get(i, 0)[0]
                  * (doseImageSpacing.x * doseImageSpacing.y * structure.getThickness());
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

  private Pair<double[], double[]> calculatePixelLookupTable(DicomImageElement dicomImage) {

    double deltaI = dicomImage.getSliceGeometry().getVoxelSpacing().x;
    double deltaJ = dicomImage.getSliceGeometry().getVoxelSpacing().y;

    Vector3d rowDirection = dicomImage.getSliceGeometry().getRow();
    Vector3d columnDirection = dicomImage.getSliceGeometry().getColumn();

    Vector3d position = dicomImage.getSliceGeometry().getTLHC();

    // DICOM C.7.6.2.1 Equation C.7.6.2.1-1.
    double[][] m = {
      {rowDirection.x * deltaI, columnDirection.x * deltaJ, 0, position.x},
      {rowDirection.y * deltaI, columnDirection.y * deltaJ, 0, position.y},
      {rowDirection.z * deltaI, columnDirection.z * deltaJ, 0, position.z},
      {0, 0, 0, 1}
    };
    RealMatrix matrix = MatrixUtils.createRealMatrix(m);

    double[] x = new double[dicomImage.getImage().width()];
    // column index to the image plane.
    for (int i = 0; i < dicomImage.getImage().width(); i++) {
      x[i] =
          matrix.multiply(MatrixUtils.createColumnRealMatrix(new double[] {i, 0, 0, 1}))
              .getRow(0)[0];
    }

    double[] y = new double[dicomImage.getImage().height()];
    // row index to the image plane
    for (int j = 0; j < dicomImage.getImage().height(); j++) {
      y[j] =
          matrix.multiply(MatrixUtils.createColumnRealMatrix(new double[] {0, j, 0, 1}))
              .getRow(1)[0];
    }

    return new Pair<>(x, y);
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

  private Mat calculateContourMask(Pair<double[], double[]> doseMmLUT, Contour contour) {

    int cols = doseMmLUT.getFirst().length;
    int rows = doseMmLUT.getSecond().length;

    MatOfPoint2f mop = new MatOfPoint2f();
    mop.fromList(contour.getListOfPoints());

    Mat binaryMask = new Mat(rows, cols, CvType.CV_32FC1);

    for (int i = 0; i < rows; i++) {
      for (int j = 0; j < cols; j++) {
        double distance =
            Imgproc.pointPolygonTest(
                mop, new Point(doseMmLUT.getFirst()[j], doseMmLUT.getSecond()[i]), false);
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
