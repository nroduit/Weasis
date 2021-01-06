/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum TransferSyntax {
  NONE(null, "None", null), // NON-NLS

  IMPLICIT_VR("1.2.840.10008.1.2", "Implicit VR Endian (default)", null), // NON-NLS

  DEFLATE_EXPLICIT_VR_LE(
      "1.2.840.10008.1.2.1.99", "Deflate Explicit VR Little Endian", null), // NON-NLS

  EXPLICIT_VR_LE("1.2.840.10008.1.2.1", "Explicit VR Little Endian", null), // NON-NLS

  EXPLICIT_VR_BE("1.2.840.10008.1.2.2", "Explicit VR Big Endian", null), // NON-NLS

  RLE("1.2.840.10008.1.2.5", "RLE Lossless", null), // NON-NLS

  RFC("1.2.840.10008.1.2.6.1", "RFC 2557 MIME Encapsulation", null), // NON-NLS

  JPEG_LOSSY_8("1.2.840.10008.1.2.4.50", "JPEG Lossy (8 bits)", 75), // NON-NLS

  JPEG_LOSSY_12("1.2.840.10008.1.2.4.51", "JPEG Lossy (12 bits)", 75), // NON-NLS

  JPEG_LOSSLESS_57("1.2.840.10008.1.2.4.57", "JPEG Lossless", null), // NON-NLS

  JPEG_LOSSLESS_70("1.2.840.10008.1.2.4.70", "JPEG Lossless", null), // NON-NLS

  JPEGLS_LOSSLESS("1.2.840.10008.1.2.4.80", "JPEG-LS Lossless", null), // NON-NLS

  JPEGLS_NEAR_LOSSLESS("1.2.840.10008.1.2.4.81", "JPEG-LS Lossy (Near-Lossless)", null), // NON-NLS

  JPEG2000_LOSSLESS("1.2.840.10008.1.2.4.90", "JPEG 2000 (Lossless Only)", null), // NON-NLS

  JPEG2000("1.2.840.10008.1.2.4.91", "JPEG 2000", 75), // NON-NLS

  JPEG2000_LOSSLESS_2(
      "1.2.840.10008.1.2.4.92", "JPEG 2000 Part 2 (Lossless Only)", null), // NON-NLS

  JPEG2000_2("1.2.840.10008.1.2.4.93", "JPEG 2000 Part 2", 75), // NON-NLS

  JPIP("1.2.840.10008.1.2.4.94", "JPIP", null), // NON-NLS

  JPIP_DEFLATE("1.2.840.10008.1.2.4.95", " JPIP Deflate", null), // NON-NLS

  MPEG2("1.2.840.10008.1.2.4.100", "MPEG2 Main Level", null), // NON-NLS

  MPEG2_HIGH("1.2.840.10008.1.2.4.101", "JPEG 2000 High Level", null), // NON-NLS

  MPEG_4("1.2.840.10008.1.2.4.102", "MPEG-4 AVC/H.264", null), // NON-NLS

  MPEG_4_BD("1.2.840.10008.1.2.4.103", "MPEG-4 AVC/H.264 BD-compatible", null); // NON-NLS

  private static final Logger LOGGER = LoggerFactory.getLogger(TransferSyntax.class);

  private final String label;
  private final String transferSyntaxUID;
  private Integer compression;

  private TransferSyntax(String transferSyntaxUID, String label, Integer compression) {
    this.label = label;
    this.transferSyntaxUID = transferSyntaxUID;
    this.compression = compression;
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

  public Integer getCompression() {
    return compression;
  }

  public void setCompression(Integer compression) {
    this.compression = compression;
  }

  public static TransferSyntax getTransferSyntax(String tsuid) {
    try {
      return TransferSyntax.valueOf(tsuid);
    } catch (Exception e) {
      LOGGER.error("Cannot get TransferSyntax from {}", tsuid, e);
    }
    return NONE;
  }
}
