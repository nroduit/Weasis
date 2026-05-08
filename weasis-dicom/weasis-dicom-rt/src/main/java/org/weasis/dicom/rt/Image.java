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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;

/** Lightweight wrapper around a CT/MR slice exposing the geometry needed by the RT layer. */
public class Image {

  private final DicomImageElement image;
  private final int width;
  private final int height;
  private final String patientPosition;
  private final int prone;
  private final int feetFirst;
  private final Vector3d imageSpacing;

  private DoseLut imageLUT;

  public Image(DicomImageElement image) {
    this.image = image;
    Attributes dcmItems = image.getMediaReader().getDicomObject();
    String pp = dcmItems.getString(Tag.PatientPosition);
    this.patientPosition = pp == null ? "" : pp.toLowerCase();
    this.prone = patientPosition.contains("p") ? -1 : 1; // NON-NLS
    this.feetFirst = patientPosition.contains("ff") ? -1 : 1; // NON-NLS
    this.imageSpacing = image.getRawSliceGeometry().getVoxelSpacing();
    this.width = TagD.getTagValue(image, Tag.Columns, Integer.class);
    this.height = TagD.getTagValue(image, Tag.Rows, Integer.class);
  }

  public String getPatientPosition() {
    return patientPosition;
  }

  public int getProne() {
    return prone;
  }

  public int getFeetFirst() {
    return feetFirst;
  }

  public Vector3d getImageSpacing() {
    return imageSpacing;
  }

  public DoseLut getImageLUT() {
    return imageLUT;
  }

  public void setImageLUT(DoseLut imageLUT) {
    this.imageLUT = imageLUT;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public DicomImageElement getImage() {
    return image;
  }
}
