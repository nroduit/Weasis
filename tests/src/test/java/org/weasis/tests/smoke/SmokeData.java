/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.tests.smoke;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomOutputStream;

/**
 * The synthetic study the smoke scripts run on, written as DICOM files so the suite needs no
 * download and no private data: one patient, one study, a CT stack and an MR stack with known
 * geometry and known pixel values, which the scripts assert on.
 *
 * <ul>
 *   <li>CT: 24 axial slices of 128 × 128, pixel spacing 0.5 mm, slice spacing 2 mm, rescale
 *       intercept −1024; a 40 × 40 square of 1000 HU at (44, 44) on every slice, −1000 HU elsewhere
 *       (so a 100-pixel line measures 50 mm and the square has a mean of 1000).
 *   <li>MR: 12 axial T2 slices (TE 90, TR 4000) of 128 × 128, a gradient.
 * </ul>
 */
public final class SmokeData {

  public static final String PATIENT_ID = "SMOKE-1"; // NON-NLS
  public static final String STUDY_UID = "1.2.826.0.1.3680043.10.5.100.1"; // NON-NLS
  public static final String CT_SERIES_UID = "1.2.826.0.1.3680043.10.5.100.1.1"; // NON-NLS
  public static final String MR_SERIES_UID = "1.2.826.0.1.3680043.10.5.100.1.2"; // NON-NLS
  public static final int SIZE = 128;
  public static final int CT_SLICES = 24;
  public static final int MR_SLICES = 12;

  private SmokeData() {}

  /** Writes the study into the folder (created when needed); the folder. */
  public static Path write(Path folder) throws IOException {
    Files.createDirectories(folder);
    for (int i = 0; i < CT_SLICES; i++) {
      write(folder.resolve("ct-" + (i + 1) + ".dcm"), ctSlice(i)); // NON-NLS
    }
    for (int i = 0; i < MR_SLICES; i++) {
      write(folder.resolve("mr-" + (i + 1) + ".dcm"), mrSlice(i)); // NON-NLS
    }
    return folder;
  }

  static Attributes common(String modality, String seriesUid, int instance, double z) {
    Attributes a = new Attributes();
    a.setString(Tag.SpecificCharacterSet, VR.CS, "ISO_IR 192"); // NON-NLS
    a.setString(Tag.PatientName, VR.PN, "Smoke^Suite"); // NON-NLS
    a.setString(Tag.PatientID, VR.LO, PATIENT_ID);
    a.setString(Tag.PatientBirthDate, VR.DA, "19700101");
    a.setString(Tag.PatientSex, VR.CS, "O"); // NON-NLS
    a.setString(Tag.StudyInstanceUID, VR.UI, STUDY_UID);
    a.setString(Tag.StudyDate, VR.DA, "20260101");
    a.setString(Tag.StudyTime, VR.TM, "120000");
    a.setString(Tag.StudyDescription, VR.LO, "Smoke study"); // NON-NLS
    a.setString(Tag.AccessionNumber, VR.SH, "SMOKE"); // NON-NLS
    a.setString(Tag.StudyID, VR.SH, "1");
    a.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
    a.setString(Tag.SeriesDate, VR.DA, "20260101");
    a.setString(Tag.Modality, VR.CS, modality);
    a.setString(Tag.SOPInstanceUID, VR.UI, seriesUid + "." + (instance + 1));
    a.setInt(Tag.InstanceNumber, VR.IS, instance + 1);
    a.setString(Tag.FrameOfReferenceUID, VR.UI, STUDY_UID + ".9");
    a.setString(Tag.PatientPosition, VR.CS, "HFS"); // NON-NLS
    a.setDouble(Tag.ImagePositionPatient, VR.DS, -32.0, -32.0, z);
    a.setDouble(Tag.ImageOrientationPatient, VR.DS, 1, 0, 0, 0, 1, 0);
    a.setDouble(Tag.PixelSpacing, VR.DS, 0.5, 0.5);
    a.setInt(Tag.Rows, VR.US, SIZE);
    a.setInt(Tag.Columns, VR.US, SIZE);
    a.setInt(Tag.SamplesPerPixel, VR.US, 1);
    a.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2"); // NON-NLS
    a.setInt(Tag.BitsAllocated, VR.US, 16);
    a.setInt(Tag.BitsStored, VR.US, 16);
    a.setInt(Tag.HighBit, VR.US, 15);
    a.setInt(Tag.PixelRepresentation, VR.US, 0);
    a.setString(Tag.Manufacturer, VR.LO, "Weasis smoke suite"); // NON-NLS
    return a;
  }

  static Attributes ctSlice(int i) {
    Attributes a = common("CT", CT_SERIES_UID, i, i * 2.0); // NON-NLS
    a.setString(Tag.SOPClassUID, VR.UI, UID.CTImageStorage);
    a.setString(Tag.SeriesDescription, VR.LO, "Smoke CT axial 2mm"); // NON-NLS
    a.setInt(Tag.SeriesNumber, VR.IS, 1);
    a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "AXIAL"); // NON-NLS
    a.setString(Tag.BodyPartExamined, VR.CS, "CHEST"); // NON-NLS
    a.setString(Tag.ConvolutionKernel, VR.SH, "B30f"); // NON-NLS
    a.setDouble(Tag.SliceThickness, VR.DS, 2.0);
    a.setDouble(Tag.SliceLocation, VR.DS, i * 2.0);
    a.setDouble(Tag.RescaleIntercept, VR.DS, -1024);
    a.setDouble(Tag.RescaleSlope, VR.DS, 1);
    a.setString(Tag.RescaleType, VR.LO, "HU"); // NON-NLS
    a.setDouble(Tag.WindowCenter, VR.DS, 40);
    a.setDouble(Tag.WindowWidth, VR.DS, 400);
    byte[] pixels = new byte[SIZE * SIZE * 2];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        boolean inside = x >= 44 && x < 84 && y >= 44 && y < 84;
        int stored = inside ? 1000 + 1024 : -1000 + 1024;
        int offset = (y * SIZE + x) * 2;
        pixels[offset] = (byte) (stored & 0xFF);
        pixels[offset + 1] = (byte) ((stored >> 8) & 0xFF);
      }
    }
    a.setBytes(Tag.PixelData, VR.OW, pixels);
    return a;
  }

  static Attributes mrSlice(int i) {
    Attributes a = common("MR", MR_SERIES_UID, i, i * 4.0); // NON-NLS
    a.setString(Tag.SOPClassUID, VR.UI, UID.MRImageStorage);
    a.setString(Tag.SeriesDescription, VR.LO, "Smoke MR T2 axial"); // NON-NLS
    a.setInt(Tag.SeriesNumber, VR.IS, 2);
    a.setString(Tag.ImageType, VR.CS, "ORIGINAL", "PRIMARY", "M", "ND"); // NON-NLS
    a.setString(Tag.BodyPartExamined, VR.CS, "BRAIN"); // NON-NLS
    a.setString(Tag.ScanningSequence, VR.CS, "SE"); // NON-NLS
    a.setDouble(Tag.EchoTime, VR.DS, 90);
    a.setDouble(Tag.RepetitionTime, VR.DS, 4000);
    a.setDouble(Tag.SliceThickness, VR.DS, 4.0);
    a.setDouble(Tag.SliceLocation, VR.DS, i * 4.0);
    a.setDouble(Tag.WindowCenter, VR.DS, 500);
    a.setDouble(Tag.WindowWidth, VR.DS, 1000);
    byte[] pixels = new byte[SIZE * SIZE * 2];
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        int stored = (x + y) * 4;
        int offset = (y * SIZE + x) * 2;
        pixels[offset] = (byte) (stored & 0xFF);
        pixels[offset + 1] = (byte) ((stored >> 8) & 0xFF);
      }
    }
    a.setBytes(Tag.PixelData, VR.OW, pixels);
    return a;
  }

  static void write(Path file, Attributes dataset) throws IOException {
    Attributes fmi = dataset.createFileMetaInformation(UID.ExplicitVRLittleEndian);
    try (DicomOutputStream out = new DicomOutputStream(file.toFile())) {
      out.writeDataset(fmi, dataset);
    }
  }
}
