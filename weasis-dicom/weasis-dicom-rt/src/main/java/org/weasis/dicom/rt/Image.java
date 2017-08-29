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

import org.apache.commons.math3.util.Pair;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.DicomImageElement;

public class Image {

    private String patientPosition;
    private int prone;
    private int feetFirst;
    private double[] imageSpacing;

    // Image LUT
    Pair<double[], double[]> imageLUT;

    public Image(DicomImageElement image) {
        // Determine if the patient is prone or supine
        Attributes dcmItems = image.getMediaReader().getDicomObject();
        this.patientPosition = dcmItems.getString(Tag.PatientPosition).toLowerCase();
        this.prone = patientPosition.contains("p") ? -1 : 1;
        this.feetFirst = patientPosition.contains("ff") ? -1 : 1;

        // Get the image pixel spacing
        this.imageSpacing = image.getSliceGeometry().getVoxelSpacingArray();
    }

    public String getPatientPosition() {
        return this.patientPosition;
    }

    public void setPatientPosition(String value) {
        this.patientPosition = value;
    }

    public int getProne() {
        return this.prone;
    }

    public void setProne(int prone) {
        this.prone = prone;
    }

    public int getFeetFirst() {
        return this.feetFirst;
    }

    public void setFeetFirst(int feetFirst) {
        this.feetFirst = feetFirst;
    }

    public double[] getImageSpacing() {
        return this.imageSpacing;
    }

    public void setImageSpacing(double[] imageSpacing) {
        this.imageSpacing = imageSpacing;
    }

    public Pair<double[], double[]> getImageLUT() {
        return this.imageLUT;
    }

    public void setImageLUT(Pair<double[], double[]> imageLUT) {
        this.imageLUT = imageLUT;
    }
    
}
