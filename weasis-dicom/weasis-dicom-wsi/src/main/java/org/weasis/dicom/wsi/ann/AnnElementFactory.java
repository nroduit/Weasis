/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.ann;

import org.pf4j.Extension;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.DicomSpecialElementFactory;
import org.weasis.plugin.SingletonExtension;

/** Loads Microscopy Bulk Simple Annotations (modality ANN) as special elements of the study. */
@Extension
@SingletonExtension
public class AnnElementFactory implements DicomSpecialElementFactory {

  public static final String SERIES_ANN_MIMETYPE = "ann/dicom"; // NON-NLS

  private static final String[] MODALITIES = {"ANN"}; // NON-NLS

  @Override
  public String getSeriesMimeType() {
    return SERIES_ANN_MIMETYPE;
  }

  @Override
  public String[] getModalities() {
    return MODALITIES;
  }

  @Override
  public DicomSpecialElement buildDicomSpecialElement(DicomMediaIO mediaIO) {
    return new AnnSpecialElement(mediaIO);
  }
}
