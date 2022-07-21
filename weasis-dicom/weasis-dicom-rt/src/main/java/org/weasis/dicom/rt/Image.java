/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.rt;

import org.apache.commons.math3.util.Pair;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * @author Tomas Skripcak
 */
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
    this.feetFirst = patientPosition.contains("ff") ? -1 : 1; // NON-NLS

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
