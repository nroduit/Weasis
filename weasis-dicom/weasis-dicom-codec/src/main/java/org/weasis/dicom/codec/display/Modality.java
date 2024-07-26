/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.display;

import java.util.Arrays;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.Messages;

public enum Modality {
  DEFAULT(Messages.getString("Modality.default")),
  ANN("Annotation"), // NON-NLS
  AR("Autorefraction"), // NON-NLS
  ASMT("Content Assessment Results"), // NON-NLS
  AU("Audio"), // NON-NLS
  BDUS("Bone Densitometry (ultrasound)"), // NON-NLS
  BI("Biomagnetic imaging"), // NON-NLS
  BMD("Bone Densitometry (X-Ray)"), // NON-NLS
  CFM("Confocal Microscopy"), // NON-NLS
  CR("Computed Radiography"), // NON-NLS
  CT("Computed Tomography"), // NON-NLS
  CTPROTOCOL("CT Protocol (Performed)"), // NON-NLS
  DMS("Dermoscopy"), // NON-NLS
  DG("Diaphanography"), // NON-NLS
  DOC("Document"), // NON-NLS
  DX("Digital Radiography"), // NON-NLS
  ECG("Electrocardiography"), // NON-NLS
  EEG("Electroencephalography"), // NON-NLS
  EMG("Electromyography"), // NON-NLS
  EOG("Electrooculography"), // NON-NLS
  EPS("Cardiac Electrophysiology"), // NON-NLS
  ES("Endoscopy"), // NON-NLS
  FID("Fiducials"), // NON-NLS
  GM("General Microscopy"), // NON-NLS
  HC("Hard Copy"), // NON-NLS
  HD("Hemodynamic Waveform"), // NON-NLS
  IO("Intra-Oral Radiography"), // NON-NLS
  IOL("Intraocular Lens Data"), // NON-NLS
  IVOCT("Intravascular Optical Coherence Tomography"), // NON-NLS
  IVUS("Intravascular Ultrasound"), // NON-NLS
  KER("Keratometry"), // NON-NLS
  KO("Key Object Selection"), // NON-NLS
  LEN("Lensometry"), // NON-NLS
  LS("Laser surface scan"), // NON-NLS
  MG("Mammography"), // NON-NLS
  MR("Magnetic Resonance"), // NON-NLS
  M3D("Model for 3D Manufacturing"), // NON-NLS
  NM("Nuclear Medicine"), // NON-NLS
  OAM("Ophthalmic Axial Measurements"), // NON-NLS
  OCT("Optical Coherence Tomography (non-Ophthalmic)"), // NON-NLS
  OP("Ophthalmic Photography"), // NON-NLS
  OPM("Ophthalmic Mapping"), // NON-NLS
  OPT("Ophthalmic Tomography"), // NON-NLS
  OPTBSV("Ophthalmic Tomography B-scan Volume Analysis"), // NON-NLS
  OPTENF("Ophthalmic Tomography En Face"), // NON-NLS
  OPV("Ophthalmic Visual Field"), // NON-NLS
  OSS("Optical Surface Scan"), // NON-NLS
  OT("Other"), // NON-NLS
  PA("Photoacoustic"), // NON-NLS
  PLAN("Plan"), // NON-NLS
  POS("Position Sensor"), // NON-NLS
  PR("Presentation State"), // NON-NLS
  PT("Positron emission tomography (PET)"), // NON-NLS
  PX("Panoramic X-Ray"), // NON-NLS
  REG("Registration"), // NON-NLS
  RESP("Respiratory Waveform"), // NON-NLS
  RF("Radio Fluoroscopy"), // NON-NLS
  RG("Radiographic imaging (conventional film/screen)"), // NON-NLS
  RTDOSE("Radiotherapy Dose"), // NON-NLS
  RTIMAGE("Radiotherapy Image"), // NON-NLS
  RTINTENT("Radiotherapy Intent"), // NON-NLS
  RTPLAN("Radiotherapy Plan"), // NON-NLS
  RTRAD("RT Radiation"), // NON-NLS
  RTRECORD("RT Treatment Record"), // NON-NLS
  RTSEGANN("Radiotherapy Segment Annotation"), // NON-NLS
  RTSTRUCT("Radiotherapy Structure Set"), // NON-NLS
  RWV("Real World Value Map"), // NON-NLS
  SEG("Segmentation"), // NON-NLS
  SM("Slide Microscopy"), // NON-NLS
  SMR("Stereometric Relationship"), // NON-NLS
  SR("SR Document"), // NON-NLS
  SRF("Subjective Refraction"), // NON-NLS
  STAIN("Automated Slide Stainer"), // NON-NLS
  TEXTUREMAP("Texture Map"), // NON-NLS
  TG("Thermography"), // NON-NLS
  US("Ultrasound"), // NON-NLS
  VA("Visual Acuity"), // NON-NLS
  XA("X-Ray Angiography"), // NON-NLS
  XAPROTOCOL("XA Protocol (Performed)"), // NON-NLS
  XC("External-camera Photography"); // NON-NLS

  private final String description;

  Modality(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  @Override
  public String toString() {
    return this.name() + " - " + description;
  }

  public static Modality getModality(String modality) {
    Modality v = Modality.DEFAULT;
    if (StringUtil.hasText(modality)) {
      try {
        v = Modality.valueOf(modality);
      } catch (Exception e) {
        // return DEFAULT if unknown
      }
    }
    return v;
  }

  public static Modality[] getAllModalitiesExceptDefault() {
    Modality[] vals = Modality.values();
    return Arrays.copyOfRange(vals, 1, vals.length);
  }
}
