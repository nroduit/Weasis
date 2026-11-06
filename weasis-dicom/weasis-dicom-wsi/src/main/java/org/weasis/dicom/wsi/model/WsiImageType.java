/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.model;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

/** Role of a whole slide instance, from value 3 of Image Type (PS3.3 C.8.12.4.1.1). */
public enum WsiImageType {
  VOLUME,
  THUMBNAIL,
  OVERVIEW,
  LABEL;

  public static WsiImageType of(Attributes dcm) {
    String[] imageType = dcm.getStrings(Tag.ImageType);
    String value = imageType != null && imageType.length > 2 ? imageType[2] : null;
    return switch (value) {
      case "THUMBNAIL" -> THUMBNAIL;
      case "OVERVIEW" -> OVERVIEW;
      // LOCALIZER is the retired term of Supplement 145 for OVERVIEW, still written by old scanners
      case "LOCALIZER" -> OVERVIEW;
      case "LABEL" -> LABEL;
      // Lenient: a missing or unknown value 3 (non-conformant, the attribute is Type 1) is read
      // as a pyramid level, the only role that can be checked afterwards against the matrix sizes
      case null, default -> VOLUME;
    };
  }
}
