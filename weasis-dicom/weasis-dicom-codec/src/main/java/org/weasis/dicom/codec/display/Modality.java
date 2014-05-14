/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.codec.display;

import org.weasis.dicom.codec.Messages;

public enum Modality {

    Default(Messages.getString("Modality.default")), //$NON-NLS-1$

    AU("Audio"),

    BI("Biomagnetic imaging"),

    CD(" Color flow Doppler"),

    DD("Duplex Doppler"),

    DG("Diaphanography"),

    CR("Computed Radiography"), //$NON-NLS-1$

    CT("Computed Tomography"), //$NON-NLS-1$

    DX("Digital Radiography"), //$NON-NLS-1$

    ECG("Electrocardiography"),

    EPS("Cardiac Electrophysiology"),

    ES("Endoscopy"), //$NON-NLS-1$

    GM("General Microscopy"),

    HC("Hard Copy"),

    HD("Hemodynamic Waveform"),

    IO("Intra-oral Radiography"),

    IVUS("Intravascular Ultrasound"),

    LS("Laser surface scan"),

    MG("Mammography"), //$NON-NLS-1$

    MR("Magnetic Resonance"), //$NON-NLS-1$

    NM("Nuclear Medicine"), //$NON-NLS-1$

    OT("Other"), //$NON-NLS-1$

    OP("Ophthalmic Photography"),

    PR("Presentation State"), //$NON-NLS-1$

    PX("Panoramic X-Ray"), //$NON-NLS-1$

    PT("Positron emission tomography (PET)"), //$NON-NLS-1$

    RF("Radio Fluoroscopy"), //$NON-NLS-1$

    RG("Radiographic imaging (conventional film/screen)"),

    RTDOSE("Radiotherapy Dose"),

    RTIMAGE("Radiotherapy Image"),

    RTPLAN("Radiotherapy Plan"),

    RTRECORD("RT Treatment Record"),

    RTSTRUCT("Radiotherapy Structure Set"),

    SC("Secondary Capture"), //$NON-NLS-1$

    SM("Slide Microscopy"),

    SMR("Stereometric Relationship"),

    SR("SR Document"),

    ST("Single-photon emission computed tomography (SPECT)"),

    TG("Thermography"),

    US("Ultrasound"), //$NON-NLS-1$

    XA("X-Ray Angiography"), //$NON-NLS-1$

    XC("External-camera Photography"); //$NON-NLS-1$

    private final String description;

    private Modality(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return description;
    }

    public static Modality getModality(String modality) {
        Modality v = Modality.Default;
        if (modality != null) {
            try {
                v = Modality.valueOf(modality);
            } catch (Exception e) {
                // System.err.println("Modality not supported: " + modality);
            }
        }
        return v;
    }
}
