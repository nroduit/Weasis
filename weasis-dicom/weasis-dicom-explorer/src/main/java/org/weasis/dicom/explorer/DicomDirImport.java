package org.weasis.dicom.explorer;

import java.io.File;
import java.io.IOException;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.media.DicomDirReader;
import org.dcm4che2.media.DirectoryRecordType;

public class DicomDirImport {

    private DicomDirReader reader;

    public DicomDirImport(File dcmDirFile) {
        if (dcmDirFile == null || dcmDirFile.canRead()) {
            throw new IllegalAccessError("Cannot read DICOMDIR file");
        }
        try {
            reader = new DicomDirReader(dcmDirFile);
            reader.setShowInactiveRecords(false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void parsePatient() throws Exception {
        DicomObject patient = reader.findFirstRootRecord();
        while (patient != null) {
            if (DirectoryRecordType.PATIENT.equals(patient.getString(Tag.DirectoryRecordType))) {
                try {

                    parseStudy(patient);
                } catch (Exception e) {
                    System.out.println(e.toString());
                }
            }
            patient = reader.findNextSiblingRecord(patient);
        }
    }

    private void parseStudy(DicomObject patient) throws IOException {
        DicomObject study = reader.findFirstChildRecord(patient);
        while (study != null) {
            if (DirectoryRecordType.STUDY.equals(study.getString(Tag.DirectoryRecordType))) {
                // update study level tag values

                parseSeries(study);
            }
            study = reader.findNextSiblingRecord(study);
        }// study loop completed
    }

    private void parseSeries(DicomObject study) throws IOException {
        DicomObject series = reader.findFirstChildRecord(study);
        while (series != null) {
            if (DirectoryRecordType.SERIES.equals(series.getString(Tag.DirectoryRecordType))) {
                // update series level tag values

                parseInstance(series);
            }
            series = reader.findNextSiblingRecord(series);
        }
    }

    private void parseInstance(DicomObject series) throws IOException {
        DicomObject instance = reader.findFirstChildRecord(series);
        while (instance != null) {
            if (DirectoryRecordType.IMAGE.equals(instance.getString(Tag.DirectoryRecordType))) {
                // update instance level tag values

                File f = reader.toReferencedFile(instance);

            }
            instance = reader.findNextSiblingRecord(instance);
        }
    }

}
