/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.display;

import java.util.Arrays;

import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.Messages;

public enum Modality {

    DEFAULT(Messages.getString("Modality.default")), //$NON-NLS-1$

    AU("Audio"), //$NON-NLS-1$

    BI("Biomagnetic imaging"), //$NON-NLS-1$

    CD("Color flow Doppler"), //$NON-NLS-1$

    DD("Duplex Doppler"), //$NON-NLS-1$

    DG("Diaphanography"), //$NON-NLS-1$

    CR("Computed Radiography"), //$NON-NLS-1$

    CT("Computed Tomography"), //$NON-NLS-1$

    DX("Digital Radiography"), //$NON-NLS-1$

    ECG("Electrocardiography"), //$NON-NLS-1$

    EPS("Cardiac Electrophysiology"), //$NON-NLS-1$

    ES("Endoscopy"), //$NON-NLS-1$

    GM("General Microscopy"), //$NON-NLS-1$

    HC("Hard Copy"), //$NON-NLS-1$

    HD("Hemodynamic Waveform"), //$NON-NLS-1$

    IO("Intra-oral Radiography"), //$NON-NLS-1$

    IVUS("Intravascular Ultrasound"), //$NON-NLS-1$

    LS("Laser surface scan"), //$NON-NLS-1$

    MG("Mammography"), //$NON-NLS-1$

    MR("Magnetic Resonance"), //$NON-NLS-1$

    NM("Nuclear Medicine"), //$NON-NLS-1$

    OT("Other"), //$NON-NLS-1$

    OP("Ophthalmic Photography"), //$NON-NLS-1$

    PR("Presentation State"), //$NON-NLS-1$

    PX("Panoramic X-Ray"), //$NON-NLS-1$

    PT("Positron emission tomography (PET)"), //$NON-NLS-1$

    RF("Radio Fluoroscopy"), //$NON-NLS-1$

    RG("Radiographic imaging (conventional film/screen)"), //$NON-NLS-1$

    RTDOSE("Radiotherapy Dose"), //$NON-NLS-1$

    RTIMAGE("Radiotherapy Image"), //$NON-NLS-1$

    RTPLAN("Radiotherapy Plan"), //$NON-NLS-1$

    RTRECORD("RT Treatment Record"), //$NON-NLS-1$

    RTSTRUCT("Radiotherapy Structure Set"), //$NON-NLS-1$

    SC("Secondary Capture"), //$NON-NLS-1$

    SM("Slide Microscopy"), //$NON-NLS-1$

    SMR("Stereometric Relationship"), //$NON-NLS-1$

    SR("SR Document"), //$NON-NLS-1$

    ST("Single-photon emission computed tomography (SPECT)"), //$NON-NLS-1$

    TG("Thermography"), //$NON-NLS-1$

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
        StringBuilder buf = new StringBuilder(this.name());
        buf.append(" - "); //$NON-NLS-1$
        buf.append(description);
        return buf.toString();
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
