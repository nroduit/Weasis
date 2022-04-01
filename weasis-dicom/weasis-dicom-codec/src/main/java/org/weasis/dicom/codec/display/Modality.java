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

  AU("Audio"), // NON-NLS

  BI("Biomagnetic imaging"), // NON-NLS

  CD("Color flow Doppler"), // NON-NLS

  DD("Duplex Doppler"), // NON-NLS

  DG("Diaphanography"), // NON-NLS

  CR("Computed Radiography"), // NON-NLS

  CT("Computed Tomography"), // NON-NLS

  DX("Digital Radiography"), // NON-NLS

  ECG("Electrocardiography"), // NON-NLS

  EPS("Cardiac Electrophysiology"), // NON-NLS

  ES("Endoscopy"), // NON-NLS

  GM("General Microscopy"), // NON-NLS

  HC("Hard Copy"), // NON-NLS

  HD("Hemodynamic Waveform"), // NON-NLS

  IO("Intra-oral Radiography"), // NON-NLS

  IVUS("Intravascular Ultrasound"), // NON-NLS

  LS("Laser surface scan"), // NON-NLS

  MG("Mammography"), // NON-NLS

  MR("Magnetic Resonance"), // NON-NLS

  NM("Nuclear Medicine"), // NON-NLS

  OT("Other"), // NON-NLS

  OP("Ophthalmic Photography"), // NON-NLS

  PR("Presentation State"), // NON-NLS

  PX("Panoramic X-Ray"), // NON-NLS

  PT("Positron emission tomography (PET)"), // NON-NLS

  RF("Radio Fluoroscopy"), // NON-NLS

  RG("Radiographic imaging (conventional film/screen)"), // NON-NLS

  RTDOSE("Radiotherapy Dose"), // NON-NLS

  RTIMAGE("Radiotherapy Image"), // NON-NLS

  RTPLAN("Radiotherapy Plan"), // NON-NLS

  RTRECORD("RT Treatment Record"), // NON-NLS

  RTSTRUCT("Radiotherapy Structure Set"), // NON-NLS

  SC("Secondary Capture"), // NON-NLS

  SM("Slide Microscopy"), // NON-NLS

  SMR("Stereometric Relationship"), // NON-NLS

  SR("SR Document"), // NON-NLS

  ST("Single-photon emission computed tomography (SPECT)"), // NON-NLS

  TG("Thermography"), // NON-NLS

  US("Ultrasound"), // NON-NLS

  XA("X-Ray Angiography"), // NON-NLS

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
