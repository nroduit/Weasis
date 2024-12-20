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

import java.util.AbstractMap;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/**
 * @author Tomas Skripcak
 */
public class Image {

  private final DicomImageElement image;
  private final int width;
  private final int height;
  private final String patientPosition;
  private final int prone;
  private final int feetFirst;
  private final Vector3d imageSpacing;

  // Image LUT
  AbstractMap.SimpleImmutableEntry<double[], double[]> imageLUT;

  public Image(DicomImageElement image) {
    this.image = image;
    // Determine if the patient is prone or supine
    Attributes dcmItems = image.getMediaReader().getDicomObject();
    this.patientPosition = dcmItems.getString(Tag.PatientPosition).toLowerCase();
    this.prone = patientPosition.contains("p") ? -1 : 1;
    this.feetFirst = patientPosition.contains("ff") ? -1 : 1; // NON-NLS

    // Get the image pixel spacing
    this.imageSpacing = image.getRawSliceGeometry().getVoxelSpacing();
    this.width = TagD.getTagValue(image, Tag.Columns, Integer.class);
    this.height = TagD.getTagValue(image, Tag.Rows, Integer.class);
  }

  public String getPatientPosition() {
    return this.patientPosition;
  }

  public int getProne() {
    return this.prone;
  }

  public int getFeetFirst() {
    return this.feetFirst;
  }

  public Vector3d getImageSpacing() {
    return this.imageSpacing;
  }

  public AbstractMap.SimpleImmutableEntry<double[], double[]> getImageLUT() {
    return this.imageLUT;
  }

  public void setImageLUT(AbstractMap.SimpleImmutableEntry<double[], double[]> imageLUT) {
    this.imageLUT = imageLUT;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public DicomImageElement getImage() {
    return this.image;
  }
}
