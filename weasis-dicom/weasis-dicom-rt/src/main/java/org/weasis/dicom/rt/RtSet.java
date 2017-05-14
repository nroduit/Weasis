/*******************************************************************************
 * Copyright (c) 2017 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *     Tomas Skripcak  - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.rt;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.weasis.core.api.gui.util.MathUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.PresentationStateReader;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class RtSet {

    private final Map<RtSpecialElement, Plan> plans = new HashMap<>();
    private final Map<RtSpecialElement, StructureSet> structures = new HashMap<>();

    private final Map<String, ArrayList<Contour>> coutourMap = new HashMap<>();

    private final List<RtSpecialElement> rtElements;

    public RtSet(List<RtSpecialElement> rtElements) {
        this.rtElements = Objects.requireNonNull(rtElements);

        for (RtSpecialElement rt : rtElements) {
            String sopUID = TagD.getTagValue(rt, Tag.SOPClassUID, String.class);
            if (UID.RTStructureSetStorage.equals(sopUID)) {
                initStructures(rt);
            } else if (UID.RTPlanStorage.equals(sopUID)) {
                initPlan(rt);

            } else if (UID.RTDoseStorage.equals(sopUID)) {
                initDose(rt);
            }
        }
    }

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
                    layer.getStructure().setRtRoiInterpretedType(rtROIObsSeq.getString(Tag.RTROIInterpretedType));
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
                int[] rgb = null;
                if (valColors != null && valColors.length == 3) {
                    rgb = new int[] { Integer.parseInt(valColors[0]), Integer.parseInt(valColors[1]),
                        Integer.parseInt(valColors[2]) };
                } else {
                    Random rand = new Random();
                    rgb = new int[] { rand.nextInt(255), rand.nextInt(255), rand.nextInt(255) };
                }

                Color color = PresentationStateReader.getRGBColor(255, null, rgb);
                layer.getStructure().setColor(color);

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
                                ArrayList<Contour> pls = coutourMap.get(sopUID);
                                if (pls == null) {
                                    pls = new ArrayList<>();
                                    coutourMap.put(sopUID, pls);
                                }
                                pls.add(plane);
                            }
                        }

                        // Add each plane to the planes dictionary of the current ROI
                        double z = plane.getCoordinateZ();

                        // If there are no contour on specific z position
                        if (!planes.containsKey(z)) {
                            planes.put(z, new ArrayList<Contour>());
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
     * Calculates the structure plane thickness
     *
     * @return structure plane thickness
     */
    private static double calculatePlaneThickness(Map<Double, ArrayList<Contour>> planesMap) {
        // Initial thickness (very big)
        double thickness = 10000;

        List<Double> planes = new ArrayList<>();

        // Iterate over each plane in the structure to collect z coordinate
        for (Double z : planesMap.keySet()) {
            planes.add(z);
        }
        Collections.sort(planes);

        // Determine the thickness
        for (int i = 0; i < planes.size(); i++) {
            if (i > 0) {
                double newThickness = planes.get(i) - planes.get(i - 1);
                if (newThickness < thickness) {
                    thickness = newThickness;
                }
            }
        }

        // If the thickness was not detected, set it to 0
        if (thickness == 10000) {
            thickness = 0;
        }

        return thickness;
    }

    private void initDose(RtSpecialElement rt) {
        // TODO Auto-generated method stub

    }

    private void initPlan(RtSpecialElement rt) {

        Attributes dcmItems = rt.getMediaReader().getDicomObject();
        if (dcmItems != null) {
            Plan plan = new Plan();
            plan.setLabel(dcmItems.getString(Tag.RTPlanLabel));
            plan.setDate(dcmItems.getDate(Tag.RTPlanDateAndTime));

            plan.setName("");
            plan.setRxDose(0.0);

            for (Attributes doseRef : dcmItems.getSequence(Tag.DoseReferenceSequence)) {

                String doseRefStructType = doseRef.getString(Tag.DoseReferenceStructureType);
                if ("SITE".equals(doseRefStructType)) {
                    plan.setName("N/A");

                    String doseRefDesc = doseRef.getString(Tag.DoseReferenceDescription);
                    if (StringUtil.hasText(doseRefDesc)) {
                        plan.setName(doseRefDesc);
                    }

                    Double targetPrescDose =
                        DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);
                    if (targetPrescDose != null) {
                        double rxDose = targetPrescDose * 100;
                        if (rxDose > plan.getRxDose()) {
                            plan.setRxDose(rxDose);
                        }
                    }
                } else if ("VOLUME".equals(doseRefStructType)) {
                    Double targetPrescDose =
                        DicomMediaUtils.getDoubleFromDicomElement(doseRef, Tag.TargetPrescriptionDose, null);
                    if (targetPrescDose != null) {
                        plan.setRxDose(targetPrescDose * 100);
                    }
                }

            }

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

            // To int
            // plan.setRxDose(plan.getRxDose().floatToIntBits());

            plans.put(rt, plan);
        }

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

    public Map<String, ArrayList<Contour>> getCoutourMap() {
        return coutourMap;
    }

    public List<RtSpecialElement> getRtElements() {
        return rtElements;
    }

}
