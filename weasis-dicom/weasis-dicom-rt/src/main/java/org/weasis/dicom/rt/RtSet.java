/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak - initial API and implementation
 ******************************************************************************/

package org.weasis.dicom.rt;

import static org.opencv.core.Core.add;
import static org.opencv.core.Core.multiply;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

/**
 * RtSet is a collection of linked DICOM-RT entities that form the whole treatment case (Plans, Doses, StructureSets)
 */
public class RtSet {

    private static final Logger LOGGER = LoggerFactory.getLogger(RtSet.class);

    private final List<MediaElement> rtElements = new ArrayList<>();
    private final Map<RtSpecialElement, StructureSet> structures = new HashMap<>();
    private final Map<RtSpecialElement, Plan> plans = new HashMap<>();
    private final List<MediaElement> images = new ArrayList<>();
    private final Map<String, ArrayList<Contour>> contourMap = new HashMap<>();

    private Pair<double[], double[]> doseMmLUT;
    private Pair<double[], double[]> dosePixLUT;

    // TODO: this will be configurable via GUI
    private int structureFillTransparency = 115;
    private int isoFillTransparency = 70;
    private boolean forceRecalculateDvh = false;

    private final XYChart dvhChart = new XYChartBuilder().width(600).height(500).title("DVH").xAxisTitle("Dose (cGy)")
        .yAxisTitle("Volume (%)").build();

    public RtSet(List<MediaElement> rtElements) {
        this.rtElements.addAll(Objects.requireNonNull(rtElements));

        for (MediaElement rt : rtElements) {
            String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
            if (UID.CTImageStorage.equals(sopUID)) {
                initImage(rt);
            }
        }
    }

    public void reloadRtCase() {
        // First initialise all RTSTRUCT
        for (MediaElement rt : this.rtElements) {
            String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
            if (UID.RTStructureSetStorage.equals(sopUID) && rt instanceof RtSpecialElement) {
                initStructures((RtSpecialElement) rt);
            }
        }

        // Than initialise all RTPLAN
        for (MediaElement rt : this.rtElements) {
            String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
            if (UID.RTPlanStorage.equals(sopUID) && rt instanceof RtSpecialElement) {
                initPlan((RtSpecialElement) rt);
            }
        }

        // Than initialise all RTDOSE
        for (MediaElement rt : this.rtElements) {
            String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
            if (UID.RTDoseStorage.equals(sopUID)) {
                initDose(rt);
            }
        }

        // Plans and doses are loaded
        if (!plans.isEmpty() && !images.isEmpty()) {
            for (Plan plan : plans.values()) {
                if (plan.hasAssociatedDose() && dosePixLUT == null) {

                    DicomImageElement image = (DicomImageElement) this.getMiddleImage();

                    // Determine if the patient is prone or supine
                    Attributes dcmItems = image.getMediaReader().getDicomObject();
                    String patientPosition = dcmItems.getString(Tag.PatientPosition).toLowerCase();
                    int prone = patientPosition.contains("p") ? -1 : 1;
                    int feetFirst = patientPosition.contains("ff") ? -1 : 1;

                    // Get the image pixel spacing
                    double[] imageSpacing = image.getSliceGeometry().getVoxelSpacingArray();

                    // Init LUTs
                    Pair<double[], double[]> imageLUT = this.calculatePixelLookupTable(image);
                    for (Dose dose : plan.getDoses()) {
                        this.doseMmLUT = this.calculatePixelLookupTable((DicomImageElement) dose.getImages().get(0));
                        this.dosePixLUT =
                            this.calculateDoseGridToImageGrid(imageLUT, imageSpacing, prone, feetFirst, this.doseMmLUT);
                    }

                    this.initIsoDoses(plan, this.doseMmLUT);

                    for (Dose dose : plan.getDoses()) {
                        if (dose.getDoseMax() > 0) {

                            // For all ROIs
                            for (StructureLayer structureLayer : this.getStructureSet(this.getFirstStructure())
                                .values()) {
                                Structure structure = structureLayer.getStructure();

                                // If DVH exists for the structure and setting always recalculate is false
                                Dvh structureDvh = dose.get(structure.getRoiNumber());

                                // Re-calculate DVH if it does not exists or if it is provided and force recalculation
                                // is setup
                                if (structureDvh == null || (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
                                    && this.getForceRecalculateDvh())) {
                                    structureDvh = this.initCalculatedDvh(structure, dose);
                                    dose.put(structure.getRoiNumber(), structureDvh);
                                }
                                // Otherwise read provided DVH
                                else {
                                    // Absolute volume is provided and defined in DVH (in cm^3) so use it
                                    if (structureDvh.getDvhSource().equals(DataSource.PROVIDED)
                                        && structureDvh.getDvhVolumeUnit().equals("CM3")) {
                                        structure.setVolume(structureDvh.getDvhData()[0]);
                                    }
                                }

                                // Display volume
                                double volume = structure.getVolume();
                                String source = structure.getVolumeSource().toString();
                                LOGGER.debug(String.format(
                                    "Structure: " + structure.getRoiName() + ", " + source + " Volume: %.4f cm^3",
                                    volume));

                                // If plan is loaded with prescribed treatment dose calculate DVH statistics
                                String relativeMinDose = String.format(
                                    "Structure: " + structure.getRoiName() + ", "
                                        + structureDvh.getDvhSource().toString() + " Min Dose: %.3f %%",
                                    RtSet.calculateRelativeDose(structureDvh.getDvhMinimumDoseCGy(), plan.getRxDose()));
                                String relativeMaxDose = String.format(
                                    "Structure: " + structure.getRoiName() + ", "
                                        + structureDvh.getDvhSource().toString() + " Max Dose: %.3f %%",
                                    RtSet.calculateRelativeDose(structureDvh.getDvhMaximumDoseCGy(), plan.getRxDose()));
                                String relativeMeanDose = String.format(
                                    "Structure: " + structure.getRoiName() + ", "
                                        + structureDvh.getDvhSource().toString() + " Mean Dose: %.3f %%",
                                    RtSet.calculateRelativeDose(structureDvh.getDvhMeanDoseCGy(), plan.getRxDose()));
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

    /**
     * Initialise RTSTRUCT objects
     * 
     * @param rtElement
     *            RTSTRUCT dicom object
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
                    layer.getStructure().setRtRoiInterpretedType(rtROIObsSeq.getString(Tag.RTROIInterpretedType));
                    layer.getStructure().setRoiObservationLabel(rtROIObsSeq.getString(Tag.ROIObservationLabel));
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
                    rgb = new int[] { Integer.parseInt(valColors[0]), Integer.parseInt(valColors[1]),
                        Integer.parseInt(valColors[2]) };
                } else {
                    Random rand = new Random();
                    rgb = new int[] { rand.nextInt(255), rand.nextInt(255), rand.nextInt(255) };
                }

                Color color1 = PresentationStateReader.getRGBColor(255, null, rgb);
                Color color2 =
                    new Color(color1.getRed(), color1.getGreen(), color1.getBlue(), structureFillTransparency);
                layer.getStructure().setColor(color2);

                Map<Double, ArrayList<Contour>> planes = new HashMap<>();

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
                            DicomMediaUtils.getDoubleArrayFromDicomElement(contour, Tag.ContourOffsetVector, null));
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

                        // Each plane which coincides with a image slice will have a unique ID
                        // take the first one
                        for (Attributes images : contour.getSequence(Tag.ContourImageSequence)) {
                            String sopUID = images.getString(Tag.ReferencedSOPInstanceUID);
                            if (StringUtil.hasText(sopUID)) {
                                ArrayList<Contour> pls = contourMap.computeIfAbsent(sopUID, k -> new ArrayList<>());
                                pls.add(plane);
                            }
                        }

                        // Add each plane to the planes dictionary of the current ROI
                        double z = plane.getCoordinateZ();

                        // If there are no contour on specific z position
                        if (!planes.containsKey(z)) {
                            planes.put(z, new ArrayList<>());
                            planes.get(z).add(plane);
                        }

                    }
                }

                // Calculate the plane thickness for the current ROI
                layer.getStructure().setThickness(calculatePlaneThickness(planes));

                // Add the planes dictionary to the current ROI
                layer.getStructure().setPlanes(planes);
            }

        }
    }

    /**
     * Initialise RTPLAN objects
     * 
     * @param rtElement
     *            RTPLAN dicom object
     */
    private void initPlan(RtSpecialElement rtElement) {

        Attributes dcmItems = rtElement.getMediaReader().getDicomObject();
        if (dcmItems != null) {

            Plan plan = new Plan();
            String planSopInstanceUid = dcmItems.getString(Tag.SOPInstanceUID);
            plan.setSopInstanceUid(planSopInstanceUid);

            // Some plans exists (probably dummy plans)
            if (!plans.isEmpty()) {
                RtSpecialElement se = getPlans().entrySet().stream()
                    .filter(p -> p.getValue().getSopInstanceUid().equals(planSopInstanceUid)).findFirst().get()
                    .getKey();
                // Dummy plan exists, take the dummy and extend it
                if (se == null) {
                    plan = getPlans().entrySet().stream()
                        .filter(p -> p.getValue().getSopInstanceUid().equals(planSopInstanceUid)).findFirst().get()
                        .getValue();
                    // Remove the dummy from the set
                    plans.remove(null, plan);
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

                // POINT (dose reference point specified as ROI)
                if ("POINT".equals(doseRefStructType)) {
                    // NOOP
                    LOGGER.info("Not supported: dose reference point specified as ROI");
                }

                // VOLUME structure is associated with dose (dose reference volume specified as ROI)
                else if ("VOLUME".equals(doseRefStructType)) {
                    Double targetPrescDose =
                        DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);

                    // DICOM specifies prescription dose In Gy -> convert to cGy
                    if (targetPrescDose != null) {
                        plan.setRxDose(targetPrescDose * 100);
                    }
                }

                // COORDINATES (point specified by Dose Reference Point Coordinates (300A,0018))
                else if ("COORDINATES".equals(doseRefStructType)) {
                    // NOOP
                    Double targetPrescDose =
                        DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);

                    // DICOM specifies prescription dose In Gy -> convert to cGy
                    if (targetPrescDose != null) {
                        plan.setRxDose(targetPrescDose * 100);
                    }
                    // TODO: if target prescribed dose is not defined it should be possible to get the dose value from
                    // Dose Reference Point Coordinates
                }

                // SITE structure is associated with dose (dose reference clinical site)
                else if ("SITE".equals(doseRefStructType)) {

                    // Add user defined dose description to plan name
                    String doseRefDesc = doseRef.getString(Tag.DoseReferenceDescription);
                    if (StringUtil.hasText(doseRefDesc)) {
                        plan.setName(plan.getName() + " - " + doseRefDesc);
                    }

                    Double targetPrescDose =
                        DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);

                    // DICOM specifies prescription dose In Gy -> convert to cGy
                    if (targetPrescDose != null) {
                        double rxDose = targetPrescDose * 100;
                        if (rxDose > plan.getRxDose()) {
                            plan.setRxDose(rxDose);
                        }
                    }
                }
            }

            // When fractionation group sequence is defined get prescribed dose from there (in cGy unit)
            if (MathUtil.isEqualToZero(plan.getRxDose())) {
                for (Attributes fractionGroup : dcmItems.getSequence(Tag.FractionGroupSequence)) {
                    Integer fx =
                        DicomMediaUtils.getIntegerFromDicomElement(fractionGroup, Tag.NumberOfFractionsPlanned, null);
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

                    // Only first one
                    break;
                }
            }

            plans.put(rtElement, plan);
        }
    }

    /**
     * Initialise RTDOSE objects
     * 
     * @param rtElement
     *            RTDOSE dicom object
     */
    private void initDose(MediaElement rtElement) {
        Attributes dcmItems = ((DcmMediaReader) rtElement.getMediaReader()).getDicomObject();
        if (dcmItems != null) {

            String sopInstanceUID = dcmItems.getString(Tag.SOPInstanceUID);

            // Dose is Referencing Plan
            Optional<Plan> plan;
            String referencedPlanUid = "";
            for (Attributes refRtPlanSeq : dcmItems.getSequence(Tag.ReferencedRTPlanSequence)) {
                referencedPlanUid = refRtPlanSeq.getString(Tag.ReferencedSOPInstanceUID);
            }

            // Plan is already loaded
            if (!plans.isEmpty()) {
                String finalReferencedPlanUid = referencedPlanUid;
                plan = getPlans().entrySet().stream()
                    .filter(p -> p.getValue().getSopInstanceUid().equals(finalReferencedPlanUid)).map(p -> p.getValue())
                    .findFirst();
            }
            // Dummy plan will be created
            else {
                plan = Optional.of(new Plan());
                plan.get().setSopInstanceUid(referencedPlanUid);
                plans.put(null, plan.get());
            }

            if (plan.isPresent()) {

                Dose rtDose = plan.get().getDoses().stream().filter(i -> i.getSopInstanceUid().equals(sopInstanceUID))
                    .findFirst().orElseGet(Dose::new);

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
                            rtDvh.setReferencedRoiNumber(dvhRefRoiAttributes.getInt(Tag.ReferencedROINumber, -1));

                            LOGGER.debug("Found DVH for ROI: " + rtDvh.getReferencedRoiNumber());
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
                                    System.arraycopy(interpCumVolume, 0, cumDvhData, missingDose.length,
                                        interpCumVolume.length);

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

                plan.get().getDoses().add(rtDose);

                // Add dose image
                rtDose.getImages().add(rtElement);
            }
        }
    }

    /**
     * Initialise ISO dose levels
     */
    private void initIsoDoses(Plan plan, Pair<double[], double[]> doseMmLUT) {
        // Init IsoDose levels for each dose
        for (Dose dose : plan.getDoses()) {

            if (dose.getIsoDoseSet().isEmpty()) {

                int doseMaxLevel = (int) calculateRelativeDose((dose.getDoseMax() * dose.getDoseGridScaling() * 100),
                    plan.getRxDose());

                // Max and standard levels 102, 100, 98, 95, 90, 80, 70, 50, 30
                if (doseMaxLevel > 0) {
                    dose.getIsoDoseSet().put(doseMaxLevel, new IsoDoseLayer(
                        new IsoDose(doseMaxLevel, new Color(120, 0, 0, isoFillTransparency), "Max", plan.getRxDose())));
                    dose.getIsoDoseSet().put(102, new IsoDoseLayer(
                        new IsoDose(102, new Color(170, 0, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(100, new IsoDoseLayer(
                        new IsoDose(100, new Color(238, 69, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(98, new IsoDoseLayer(
                        new IsoDose(98, new Color(255, 165, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(95, new IsoDoseLayer(
                        new IsoDose(95, new Color(255, 255, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(90, new IsoDoseLayer(
                        new IsoDose(90, new Color(0, 255, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(80, new IsoDoseLayer(
                        new IsoDose(80, new Color(0, 139, 0, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(70, new IsoDoseLayer(
                        new IsoDose(70, new Color(0, 255, 255, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(50, new IsoDoseLayer(
                        new IsoDose(50, new Color(0, 0, 255, isoFillTransparency), "", plan.getRxDose())));
                    dose.getIsoDoseSet().put(30, new IsoDoseLayer(
                        new IsoDose(30, new Color(0, 0, 128, isoFillTransparency), "", plan.getRxDose())));

                    // Commented level just for testing
                    // dose.getIsoDoseSet().put(0, new IsoDoseLayer(new IsoDose(0, new Color(0, 0, 111,
                    // isoFillTransparency), "", plan.getRxDose())));

                    // Go through whole dose grid
                    for (int i = 0; i < dose.getImages().size(); i++) {

                        double z = dose.getGridFrameOffsetVector()[i] + dose.getImagePositionPatient()[2];

                        for (IsoDoseLayer isoDoseLayer : dose.getIsoDoseSet().values()) {
                            double isoDoseThreshold = isoDoseLayer.getIsoDose().getAbsoluteDose();

                            List<MatOfPoint> isoContours = dose.getIsoDoseContourPoints(z, isoDoseThreshold);

                            // Create empty hash map of planes for IsoDose layer if there is none
                            if (isoDoseLayer.getIsoDose().getPlanes() == null) {
                                isoDoseLayer.getIsoDose().setPlanes(new HashMap<>());
                            }

                            for (int j = 0; j < isoContours.size(); j++) {

                                // Create a new IsoDose contour plane for Z or select existing one
                                // it will hold list of contours for that plane
                                isoDoseLayer.getIsoDose().getPlanes().computeIfAbsent(z, k -> new ArrayList<>());

                                // For each iso contour create a new contour
                                MatOfPoint contour = isoContours.get(j);
                                Contour isoContour = new Contour(isoDoseLayer);

                                // Populate point coordinates
                                double[] newContour = new double[contour.toArray().length * 3];
                                int k = 0;

                                // TODO: not sure if this is necessary for rendering
                                double voxelCenterOffsetX = ((DicomImageElement) dose.getImages().get(0))
                                    .getSliceGeometry().getVoxelSpacingArray()[0] / 2;
                                double voxelCenterOffsetY = ((DicomImageElement) dose.getImages().get(0))
                                    .getSliceGeometry().getVoxelSpacingArray()[1] / 2;

                                for (Point point : contour.toList()) {
                                    double[] coordinates = new double[2];

                                    coordinates[0] = doseMmLUT.getFirst()[(int) point.x] - voxelCenterOffsetX;
                                    coordinates[1] = doseMmLUT.getSecond()[(int) point.y] - voxelCenterOffsetY;

                                    newContour[k] = coordinates[0];
                                    newContour[k + 1] = coordinates[1];
                                    newContour[k + 2] = z;
                                    k += 3;
                                }

                                isoContour.setPoints(newContour);
                                isoContour.setContourPoints(newContour.length);
                                isoContour.setGeometricType("CLOSED_PLANAR");

                                // Assign
                                isoDoseLayer.getIsoDose().getPlanes().get(z).add(isoContour);
                            }
                        }
                    }

                    // When finished creation of iso contours plane data calculate the plane thickness
                    for (IsoDoseLayer isoDoseLayer : dose.getIsoDoseSet().values()) {
                        isoDoseLayer.getIsoDose()
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

    public XYChart getDvhChart() {
        return this.dvhChart;
    }

    public List<MediaElement> getRtElements() {
        return rtElements;
    }

    public MediaElement getMiddleImage() {
        // If more than one image, set first image to middle of the series
        return this.images.get((this.images.size() / 2) - 1);
    }

    public int getStructureFillTransparency() {
        return this.structureFillTransparency;
    }

    public void setStructureFillTransparency(int value) {
        this.structureFillTransparency = value;
    }

    public int getIsoFillTransparency() {
        return this.isoFillTransparency;
    }

    public void setIsoFillTransparency(int value) {
        this.isoFillTransparency = value;
    }

    public boolean getForceRecalculateDvh() {
        return this.forceRecalculateDvh;
    }

    public void setForceRecalculateDvh(boolean value) {
        this.forceRecalculateDvh = value;
    }

    /**
     * Calculates the structure plane thickness
     *
     * @return structure plane thickness
     */
    private static double calculatePlaneThickness(Map<Double, ArrayList<Contour>> planesMap) {
        // Sort the list of z coordinates
        List<Double> planes = new ArrayList<>();
        planes.addAll(planesMap.keySet());
        Collections.sort(planes);

        // Set maximum thickness as initial value
        double thickness = 10000;

        // Compare z of each two next to each other planes in order to find the minimal shift in z
        for (int i = 1; i < planes.size(); i++) {
            double newThickness = planes.get(i) - planes.get(i - 1);
            if (newThickness < thickness) {
                thickness = newThickness;
            }
        }

        // When no other then initial thickness was detected, set 0
        if (thickness > 9999) {
            thickness = 0.0;
        }

        return thickness;
    }

    private static double[] interpolate(int[] interpolatedX, double[] xCoordinates, double[] yCoordinates) {
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
     * @param dose
     *            absolute simulated dose in cGy
     * @param planDose
     *            absolute planned dose in cGy
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
        double[] doseImageSpacing = doseImage.getSliceGeometry().getVoxelSpacingArray();
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
        for (Map.Entry<Double, ArrayList<Contour>> entry : structure.getPlanes().entrySet()) {
            double z = entry.getKey();

            // Calculate the area for each contour in the current plane
            Pair<Integer, Double> maxContour = structure.calculateLargestContour(entry.getValue());
            int maxContourIndex = maxContour.getFirst();

            // If dose plane does not exist for z, continue with next plane
            MediaElement dosePlane = dose.getDosePlaneBySlice(z);
            if (dosePlane == null) {
                continue;
            }

            // Calculate histogram for each contour on the plane
            for (int c = 0; c < entry.getValue().size(); c++) {

                Contour contour = entry.getValue().get(c);

                Mat contourMask = calculateContourMask(this.doseMmLUT, contour);
                Mat hist = dose.getMaskedDosePlaneHist(z, contourMask, (int) maxDose);

                double vol = 0;
                for (int i = 0; i < hist.rows(); i++) {
                    vol += hist.get(i, 0)[0] * (doseImageSpacing[0] * doseImageSpacing[1] * structure.getThickness());
                }

                // If this is a largest contour
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
        double sumHistogram = 0;
        for (int i = 0; i < histogram.rows(); i++) {
            sumHistogram += histogram.get(i, 0)[0];
        }
        Scalar scalar = new Scalar(volume / sumHistogram);
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

    private Pair<double[], double[]> calculateDoseGridToImageGrid(Pair<double[], double[]> imageLUT,
        double[] imageSpacing, int prone, int feetFirst, Pair<double[], double[]> doseMmLUT) {

        // Transpose the dose grid LUT onto the image grid LUT
        double[] x = new double[doseMmLUT.getFirst().length];
        for (int i = 0; i < doseMmLUT.getFirst().length; i++) {
            x[i] = (doseMmLUT.getFirst()[i] - imageLUT.getFirst()[0]) * prone * feetFirst / imageSpacing[0];

        }
        double[] y = new double[doseMmLUT.getSecond().length];
        for (int j = 0; j < doseMmLUT.getSecond().length; j++) {
            y[j] = (doseMmLUT.getSecond()[j]) - imageLUT.getSecond()[0] * prone / imageSpacing[1];
        }

        return new Pair<>(x, y);
    }

    private Pair<double[], double[]> calculatePixelLookupTable(DicomImageElement dicomImage) {

        double deltaI = dicomImage.getSliceGeometry().getVoxelSpacingArray()[0];
        double deltaJ = dicomImage.getSliceGeometry().getVoxelSpacingArray()[1];

        double[] rowDirection = dicomImage.getSliceGeometry().getRowArray();
        double[] columnDirection = dicomImage.getSliceGeometry().getColumnArray();

        double[] position = dicomImage.getSliceGeometry().getTLHCArray();

        // DICOM C.7.6.2.1 Equation C.7.6.2.1-1.
        double[][] m = { { rowDirection[0] * deltaI, columnDirection[0] * deltaJ, 0, position[0] },
            { rowDirection[1] * deltaI, columnDirection[1] * deltaJ, 0, position[1] },
            { rowDirection[2] * deltaI, columnDirection[2] * deltaJ, 0, position[2] }, { 0, 0, 0, 1 } };
        RealMatrix matrix = MatrixUtils.createRealMatrix(m);

        double[] x = new double[dicomImage.getImage().width()];
        // column index to the image plane.
        for (int i = 0; i < dicomImage.getImage().width(); i++) {
            x[i] = matrix.multiply(MatrixUtils.createColumnRealMatrix(new double[] { i, 0, 0, 1 })).getRow(0)[0];
        }

        double[] y = new double[dicomImage.getImage().height()];
        // row index to the image plane
        for (int j = 0; j < dicomImage.getImage().height(); j++) {
            y[j] = matrix.multiply(MatrixUtils.createColumnRealMatrix(new double[] { 0, j, 0, 1 })).getRow(1)[0];
        }

        return new Pair<>(x, y);
    }

    public void getDoseValueForPixel(Plan plan, int pixelX, int pixelY, double z) {
        if (this.dosePixLUT != null) {
            // closest x
            double[] xDistance = new double[this.dosePixLUT.getFirst().length];
            for (int i = 0; i < xDistance.length; i++) {
                xDistance[i] = Math.abs(this.dosePixLUT.getFirst()[i] - pixelX);
            }

            double minDistanceX = Arrays.stream(xDistance).min().getAsDouble();
            int xDoseIndex = firstIndexOf(xDistance, minDistanceX, 0.001);

            // closest y
            double[] yDistance = new double[this.dosePixLUT.getSecond().length];
            for (int j = 0; j < yDistance.length; j++) {
                yDistance[j] = Math.abs(this.dosePixLUT.getSecond()[j] - pixelY);
            }

            double minDistanceY = Arrays.stream(yDistance).min().getAsDouble();
            int yDoseIndex = firstIndexOf(yDistance, minDistanceY, 0.001);

            Dose dose = plan.getFirstDose();
            if (dose != null) {
                MediaElement dosePlane = dose.getDosePlaneBySlice(z);
                Double doseGyValue = ((DicomImageElement) dosePlane).getImage().get(xDoseIndex, yDoseIndex)[0]
                    * dose.getDoseGridScaling();
                LOGGER.debug("X: " + pixelX + ", Y: " + pixelY + ", Dose: " + doseGyValue + " Gy / "
                    + calculateRelativeDose(doseGyValue * 100, this.getFirstPlan().getRxDose()) + " %");
            }
        }
    }

    private static int firstIndexOf(double[] array, double valueToFind, double tolerance) {
        for (int i = 0; i < array.length; i++) {
            if (Math.abs(array[i] - valueToFind) < tolerance) {
                return i;
            }
        }
        return -1;
    }

    private Mat calculateContourMask(Pair<double[], double[]> doseMmLUT, Contour contour) {

        int rows = doseMmLUT.getFirst().length;
        int cols = doseMmLUT.getSecond().length;

        MatOfPoint2f mop = new MatOfPoint2f();
        mop.fromList(contour.getListOfPoints());

        Mat binaryMask = new Mat(rows, cols, CvType.CV_32FC1);

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                double distance =
                    Imgproc.pointPolygonTest(mop, new Point(doseMmLUT.getFirst()[i], doseMmLUT.getSecond()[j]), false);
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
