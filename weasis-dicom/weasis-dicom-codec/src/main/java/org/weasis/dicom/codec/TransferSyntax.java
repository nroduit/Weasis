/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum TransferSyntax {
  NONE(null, Messages.getString("keep.original.tsuid")),

  IMPLICIT_VR("1.2.840.10008.1.2", "Implicit VR Endian (default)"), // NON-NLS

  DEFLATE_EXPLICIT_VR_LE("1.2.840.10008.1.2.1.99", "Deflate Explicit VR Little Endian"), // NON-NLS

  EXPLICIT_VR_LE("1.2.840.10008.1.2.1", "Explicit VR Little Endian"), // NON-NLS

  EXPLICIT_VR_BE("1.2.840.10008.1.2.2", "Explicit VR Big Endian"), // NON-NLS

  RLE("1.2.840.10008.1.2.5", "RLE Lossless"), // NON-NLS

  RFC("1.2.840.10008.1.2.6.1", "RFC 2557 MIME Encapsulation"), // NON-NLS

  JPEG_LOSSY_8("1.2.840.10008.1.2.4.50", "JPEG Lossy (8 bits)"), // NON-NLS

  JPEG_LOSSY_12("1.2.840.10008.1.2.4.51", "JPEG Lossy (12 bits)"), // NON-NLS

  JPEG_LOSSLESS_57("1.2.840.10008.1.2.4.57", "JPEG Lossless"), // NON-NLS

  JPEG_LOSSLESS_70("1.2.840.10008.1.2.4.70", "JPEG Lossless"), // NON-NLS

  JPEGLS_LOSSLESS("1.2.840.10008.1.2.4.80", "JPEG-LS Lossless"), // NON-NLS

  JPEGLS_NEAR_LOSSLESS("1.2.840.10008.1.2.4.81", "JPEG-LS Lossy (Near-Lossless)"), // NON-NLS

  JPEG2000_LOSSLESS("1.2.840.10008.1.2.4.90", "JPEG 2000 (Lossless Only)"), // NON-NLS

  JPEG2000("1.2.840.10008.1.2.4.91", "JPEG 2000"), // NON-NLS

  JPEG2000_LOSSLESS_2("1.2.840.10008.1.2.4.92", "JPEG 2000 Part 2 (Lossless Only)"), // NON-NLS

  JPEG2000_2("1.2.840.10008.1.2.4.93", "JPEG 2000 Part 2"), // NON-NLS

  JPIP("1.2.840.10008.1.2.4.94", "JPIP"), // NON-NLS

  JPIP_DEFLATE("1.2.840.10008.1.2.4.95", " JPIP Deflate"), // NON-NLS

  MPEG2("1.2.840.10008.1.2.4.100", "MPEG2 Main Level"), // NON-NLS

  MPEG2_HIGH("1.2.840.10008.1.2.4.101", "JPEG 2000 High Level"), // NON-NLS

  MPEG_4("1.2.840.10008.1.2.4.102", "MPEG-4 AVC/H.264"), // NON-NLS

  MPEG_4_BD("1.2.840.10008.1.2.4.103", "MPEG-4 AVC/H.264 BD-compatible"); // NON-NLS

  private static final Logger LOGGER = LoggerFactory.getLogger(TransferSyntax.class);

  private final String label;
  private final String transferSyntaxUID;

  TransferSyntax(String transferSyntaxUID, String label) {
    this.label = label;
    this.transferSyntaxUID = transferSyntaxUID;
  }

  public String getLabel() {
    return label;
  }

  public String getTransferSyntaxUID() {
    return transferSyntaxUID;
  }

  @Override
  public String toString() {
    if (transferSyntaxUID == null) {
      return label;
    }
    return label + " [" + transferSyntaxUID + "]";
  }

  public static TransferSyntax getTransferSyntax(String name) {
    try {
      return TransferSyntax.valueOf(name);
    } catch (Exception e) {
      LOGGER.error("Cannot get TransferSyntax from {}", name, e);
    }
    return NONE;
  }
}
