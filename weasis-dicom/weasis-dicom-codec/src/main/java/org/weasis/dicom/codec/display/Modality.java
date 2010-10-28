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
    ImageModality(Messages.getString("Modality.by_modality")), //$NON-NLS-1$

    Default(Messages.getString("Modality.default")), //$NON-NLS-1$

    CR("Computed Radiography"), //$NON-NLS-1$

    CT("Computed Tomography"), //$NON-NLS-1$

    DX("Digital Radiography"), //$NON-NLS-1$

    ES("Endoscopy"), //$NON-NLS-1$

    MG("Mammography"), //$NON-NLS-1$

    MR("Magnetic Resonance"), //$NON-NLS-1$

    NM("Nuclear Medicine"), //$NON-NLS-1$

    OT("Other"), //$NON-NLS-1$

    PR("Presentation State"), //$NON-NLS-1$

    PX("Panoramic X-Ray"), //$NON-NLS-1$

    PT("Positron emission tomography (PET)"), //$NON-NLS-1$

    RF("Radio Fluoroscopy"), //$NON-NLS-1$

    US("Ultrasound"), //$NON-NLS-1$

    SC("Secondary Capture"), //$NON-NLS-1$

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
