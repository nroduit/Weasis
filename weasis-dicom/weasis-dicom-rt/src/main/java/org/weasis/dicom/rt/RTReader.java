package org.weasis.dicom.rt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.macro.SeriesAndInstanceReference;
import org.weasis.dicom.codec.utils.DicomMediaUtils;

public class RTReader {

    // region Finals

    private final DicomSpecialElement dicomRT;
    private final Attributes dcmItems;
    private final HashMap<TagW, Object> tags = new HashMap<>();

    // endregion

    // region Constructors

    public RTReader(Series series, DicomSpecialElement dicomRT) {
        this.dicomRT = Objects.requireNonNull(dicomRT);

        DicomMediaIO dicomImageLoader = dicomRT.getMediaReader();
        dcmItems = dicomImageLoader.getDicomObject();
        DataExplorerModel model = (DataExplorerModel) series.getTagValue(TagW.ExplorerModel);
        if (model instanceof TreeModel) {
            TreeModel treeModel = (TreeModel) model;

            String patientPseudoUID =
                DicomMediaUtils.buildPatientPseudoUID(dcmItems.getString(Tag.PatientID, TagW.NO_VALUE),
                    dcmItems.getString(Tag.IssuerOfPatientID), dcmItems.getString(Tag.PatientName, TagW.NO_VALUE));

            // MediaSeriesGroup patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, patientPseudoUID);
            // if (patient == null) {
            MediaSeriesGroup patient =
                new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), patientPseudoUID, null);
            DicomMediaUtils.writeMetaData(patient, dcmItems);
            patient.getTagEntrySetIterator().forEachRemaining(e -> tags.put(e.getKey(), e.getValue()));
            // dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            // }

            String studyUID = (String) TagD.getUID(Level.STUDY).getValue(dcmItems);
            // MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, studyUID);
            // if (study == null) {
            MediaSeriesGroup study =
                new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), studyUID, null);
            DicomMediaUtils.writeMetaData(study, dcmItems);

            study.getTagEntrySetIterator().forEachRemaining(e -> tags.put(e.getKey(), e.getValue()));

            // dicomModel.addHierarchyNode(patient, study);
            // }

            // MediaSeriesGroup patient = treeModel.getParent(series, model.getTreeModelNodeForNewPlugin());
            // if (patient == null) {
            // String patientID = dcmItems.getString(Tag.PatientID, DicomMediaIO.NO_VALUE);
            // tags.put(TagW.PatientID, patientID);
            // String name = DicomMediaUtils.buildPatientName(dcmItems.getString(Tag.PatientName));
            // tags.put(TagW.PatientName, name);
            // Date birthdate = DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.PatientBirthDate, null);
            // DicomMediaUtils.setTagNoNull(tags, TagW.PatientBirthDate, birthdate);
            // // Global Identifier for the patient.
            // tags.put(TagW.PatientPseudoUID, DicomMediaUtils.buildPatientPseudoUID(patientID,
            // dcmItems.getString(Tag.IssuerOfPatientID), name, null));
            // tags.put(TagW.PatientSex, DicomMediaUtils.buildPatientSex(dcmItems.getString(Tag.PatientSex)));
            //
            // } else {
            // tags.put(TagW.PatientName, patient.getTagValue(TagW.PatientName));
            // tags.put(TagW.PatientID, patient.getTagValue(TagW.PatientID));
            // tags.put(TagW.PatientBirthDate, patient.getTagValue(TagW.PatientBirthDate));
            // tags.put(TagW.PatientSex, patient.getTagValue(TagW.PatientSex));
            // }

            // MediaSeriesGroup study = treeModel.getParent(series, DicomModel.study);
            // if (study == null) {
            // DicomMediaUtils.setTagNoNull(tags, TagW.StudyID, dcmItems.getString(Tag.StudyID));
            // DicomMediaUtils.setTagNoNull(tags, TagW.StudyDate,
            // TagW.dateTime(DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.StudyDate, null),
            // DicomMediaUtils.getDateFromDicomElement(dcmItems, Tag.StudyTime, null)));
            // DicomMediaUtils.setTagNoNull(tags, TagW.AccessionNumber, dcmItems.getString(Tag.AccessionNumber));
            // DicomMediaUtils.setTagNoNull(tags, TagW.ReferringPhysicianName,
            // DicomMediaUtils.buildPersonName(dcmItems.getString(Tag.ReferringPhysicianName)));
            // } else {
            // tags.put(TagW.StudyDate, study.getTagValue(TagW.StudyDate));
            // tags.put(TagW.StudyID, study.getTagValue(TagW.StudyID));
            // tags.put(TagW.AccessionNumber, study.getTagValue(TagW.AccessionNumber));
            // tags.put(TagW.ReferringPhysicianName, series.getTagValue(TagW.ReferringPhysicianName));
            // }
        }

    }

    // endregion

    // region Methods

    /**
     * Get ROIs
     * 
     * @return Map of ROIs
     */
    public Map<Integer, Structure> getStructures() {

        Map<Integer, Structure> structures = new HashMap<>();

        if (dcmItems != null && dcmItems.getString(Tag.SOPClassUID).equals("1.2.840.10008.5.1.4.1.1.481.3")) {

            // Locate the name and number of each ROI
            for (Attributes ssROIseq : dcmItems.getSequence(Tag.StructureSetROISequence)) {
                Structure structure = new Structure();
                structure.setRoiNumber(ssROIseq.getInt(Tag.ROINumber, -1));
                structure.setRoiName(ssROIseq.getString(Tag.ROIName));
                structures.put(structure.getRoiNumber(), structure);
            }

            // Determine the type of each structure (PTV, organ, external, etc)
            for (Attributes rtROIObsSeq : dcmItems.getSequence(Tag.RTROIObservationsSequence)) {
                Structure structure = structures.get(rtROIObsSeq.getInt(Tag.ReferencedROINumber, -1));
                structure.setRtRoiInterpretedType(rtROIObsSeq.getString(Tag.RTROIInterpretedType));
            }

            // The coordinate data of each ROI is stored within ROIContourSequence
            for (Attributes roiContourSeq : dcmItems.getSequence(Tag.ROIContourSequence)) {
                Structure structure = structures.get(roiContourSeq.getInt(Tag.ReferencedROINumber, -1));

                // Generate a random color for the current ROI
                // structures[number]['color'] = np.array((
                // random.randint(0,255),
                // random.randint(0,255),
                // random.randint(0,255)), dtype=float)

                // Get the RGB color triplet for the current ROI if it exists
                String[] color = roiContourSeq.getStrings(Tag.ROIDisplayColor);

                if (color != null && color.length > 0) {
                    structure.setColor(this.getColor(color));
                }

                Map<Float, ArrayList<Contour>> planes = new HashMap<>();
                // Locate the contour sequence for each referenced ROI
                for (Attributes contour : roiContourSeq.getSequence(Tag.ContourSequence)) {
                    // For each plane, initialize a new plane dictionary
                    Contour plane = new Contour();

                    // Determine all the plane properties
                    plane.setGeometricType(contour.getString(Tag.ContourGeometricType));
                    plane.setContourPoints(contour.getInt(Tag.NumberOfContourPoints, -1));
                    plane.setContourData(this.getContourPoints(contour.getStrings(Tag.ContourData)));

                    // Each plane which coincides with a image slice will have a unique ID
                    // take the first one
                    for (Attributes images : contour.getSequence(Tag.ContourImageSequence)) {
                        plane.setUid(images.getString(Tag.ReferencedSOPInstanceUID));
                        break;
                    }

                    // Add each plane to the planes dictionary of the current ROI
                    // original python ('%.2f' % plane['contourData'][0][2]).replace('-0','0')
                    float z = plane.getContourData().get(0).get('z');

                    // If there are no contour on specific z position
                    if (!planes.containsKey(z)) {
                        planes.put(z, new ArrayList<Contour>());
                        planes.get(z).add(plane);
                    }

                }

                // Calculate the plane thickness for the current ROI
                structure.setThickness(this.calculatePlaneThickness(planes));

                // Add the planes dictionary to the current ROI
                structure.setPlanes(planes);
            }

        }

        return structures;
    }

    /**
     * Returns the plan information
     */
    public Plan getPlan() {

        Plan plan = new Plan();

        // if (self.ds.SOPClassUID == '1.2.840.10008.5.1.4.1.1.481.2'):
        // return 'rtdose'
        // elif (self.ds.SOPClassUID == '1.2.840.10008.5.1.4.1.1.2'):
        // return 'ct'

        if (dcmItems != null && dcmItems.getString(Tag.SOPClassUID).equals("1.2.840.10008.5.1.4.1.1.481.5")) {
            plan.setLabel(dcmItems.getString(Tag.RTPlanLabel));
            plan.setDate(dcmItems.getDate(Tag.RTPlanDateAndTime));
            ;
            plan.setName("");
            plan.setRxDose(0.0f);

            for (Attributes doseRef : dcmItems.getSequence(Tag.DoseReferenceSequence)) {

                String doseRefStructType = doseRef.getString(Tag.DoseReferenceStructureType);
                if (doseRefStructType.equals("SITE")) {
                    plan.setName("N/A");

                    String doseRefDesc = doseRef.getString(Tag.DoseReferenceDescription);
                    if (doseRefDesc != null && !doseRefDesc.equals("")) {
                        plan.setName(doseRefDesc);
                    }
                    float targetPrescDose = doseRef.getFloat(Tag.TargetPrescriptionDose, 0.0f);
                    if (targetPrescDose != 0.0f) {
                        float rxDose = targetPrescDose * 100;
                        if (rxDose > plan.getRxDose()) {
                            plan.setRxDose(rxDose);
                        }
                    }
                } else if (doseRefStructType.equals("VOLUME")) {
                    float targetPrescDose = doseRef.getFloat(Tag.TargetPrescriptionDose, 0.0f);
                    if (targetPrescDose != 0.0f) {
                        plan.setRxDose(targetPrescDose * 100);

                    }
                }

            }

            if (plan.getRxDose() == 0.0f) {
                for (Attributes fractionGroup : dcmItems.getSequence(Tag.FractionGroupSequence)) {

                    if (fractionGroup.contains(Tag.ReferencedBeamSequence)
                        && fractionGroup.contains(Tag.NumberOfFractionsPlanned)) {
                        int fx = fractionGroup.getInt(Tag.NumberOfFractionsPlanned, -1);
                        for (Attributes beam : fractionGroup.getSequence(Tag.ReferencedBeamSequence)) {
                            if (beam.contains(Tag.BeamDose) && beam.containsValue(Tag.BeamDose)) {
                                float rxDose = plan.getRxDose();
                                float beamDose = beam.getFloat(Tag.BeamDose, 0.0f);
                                float finalDose = rxDose + (beamDose * fx * 100);
                                plan.setRxDose(finalDose);
                            }
                        }
                    }

                    // Only first one
                    break;
                }

            }

            // To int
            // plan.setRxDose(plan.getRxDose().floatToIntBits());

        }

        return plan;
    }

    /**
     * Return the referenced beams from the specified fraction
     */
    public void getReferencedBeamsInFraction(int fx) {
        // fx = 0
        // beams = {}
        // if ("Beams" in self.ds):
        // bdict = self.ds.Beams
        // elif("IonBeams"in self.ds):
        // bdict = self.ds.IonBeams
        // else:
        // return beams
        //
        // #Obtain the beam information
        // for b in bdict:
        // beam = {}
        // beam['name'] = b.BeamName if "BeamName" in b else""
        // beam['description'] = b.BeamDescription\
        // if "BeamDescription" in b else""
        // beams[b.BeamNumber] = beam
        //
        // #Obtain the referenced beam info from the fraction info
        // if ("FractionGroups" in self.ds):
        // fg = self.ds.FractionGroups[fx]
        // if ("ReferencedBeams" in fg):
        // rb = fg.ReferencedBeams
        // nfx = fg.NumberofFractionsPlanned
        // for b in rb:
        // if "BeamDose" in b:
        // beams[b.ReferencedBeamNumber]['dose'] =\
        // b.BeamDose * nfx * 100
        // return beams
    }

    public void getDVHs() {

    }

    /**
     * Return the patient demographics from a DICOM file
     */
    public void GetStructureInfo() {
        String structure = null;

        if (dcmItems != null) {
            String label = dcmItems.getString(Tag.StructureSetLabel);
            Date datetime = dcmItems.getDate(Tag.StructureSetDateAndTime);
            int contoursCount = dcmItems.getSequence(Tag.ROIContourSequence).size();
        }

        // return structure;
    }

    public MediaElement getDicom() {
        return dicomRT;
    }

    public Attributes getDcmobj() {
        return dcmItems;
    }

    public HashMap<TagW, Object> getTags() {
        return tags;
    }

    public Object getTagValue(TagW key, Object defaultValue) {
        if (key == null) {
            return defaultValue;
        }
        Object object = tags.get(key);
        return object == null ? defaultValue : object;
    }

    public SeriesAndInstanceReference getSeriesAndInstanceReference() {
        if (dcmItems != null) {
            return new SeriesAndInstanceReference(dcmItems);
        }
        return null;
    }

    // endregion

    // region Private methods

    private String getReferencedContentItemIdentifier(int[] refs) {
        if (refs != null) {
            StringBuilder r = new StringBuilder();
            for (int j = 0; j < refs.length - 1; j++) {
                r.append(refs[j]);
                r.append('.');
            }
            if (refs.length - 1 >= 0) {
                r.append(refs[refs.length - 1]);
            }
            return r.toString();
        }
        return null;
    }

    /**
     * Parses an array of xyz points and returns a array of 3d point maps.
     * 
     * @param array
     *            array of strings representing x,y,z coordinates
     * @return array of 3d point maps
     */
    private List<Map<Character, Float>> getContourPoints(String[] array) {
        List<Map<Character, Float>> points3d = new ArrayList<>();

        for (int i = 0; i < array.length; i += 3) {
            Map<Character, Float> point3d = new HashMap<>();
            point3d.put('x', Float.parseFloat(array[i]));
            point3d.put('y', Float.parseFloat(array[i + 1]));
            point3d.put('z', Float.parseFloat(array[i + 2]));

            points3d.add(point3d);
        }

        return points3d;
    }

    /**
     * Parses an array of rgb values and returns a color map
     * 
     * @param array
     *            array of strings representing r,g,b parts
     * @return color map
     */
    private Map<Character, Float> getColor(String[] array) {
        Map<Character, Float> color = new HashMap<>();

        if (array.length == 3) {
            color.put('r', Float.parseFloat(array[0]));
            color.put('r', Float.parseFloat(array[1]));
            color.put('r', Float.parseFloat(array[2]));
        }

        return color;
    }

    /**
     * Calculates the structure plane thickness
     * 
     * @return structure plane thickness
     */
    private float calculatePlaneThickness(Map<Float, ArrayList<Contour>> planesMap) {
        // Initial thickness (very big)
        float thickness = 10000;

        List<Float> planes = new ArrayList<>();

        // Iterate over each plane in the structure to collect z coordinate
        for (Float z : planesMap.keySet()) {
            planes.add(z);
        }
        Collections.sort(planes);

        // Determine the thickness
        for (int i = 0; i < planes.size(); i++) {
            if (i > 0) {
                float newThickness = planes.get(i) - planes.get(i - 1);
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

    // endregion

}
