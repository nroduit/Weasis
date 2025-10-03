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

import org.dcm4che3.data.UID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum TransferSyntax {
  NONE(null, Messages.getString("keep.original.tsuid")),

  IMPLICIT_VR(UID.ImplicitVRLittleEndian, "Implicit VR Endian (default)"), // NON-NLS

  DEFLATE_EXPLICIT_VR_LE(
      UID.DeflatedExplicitVRLittleEndian, "Deflate Explicit VR Little Endian"), // NON-NLS

  EXPLICIT_VR_LE(UID.ExplicitVRLittleEndian, "Explicit VR Little Endian"), // NON-NLS

  EXPLICIT_VR_BE(UID.ExplicitVRBigEndian, "Explicit VR Big Endian"), // NON-NLS

  RLE(UID.RLELossless, "RLE Lossless"), // NON-NLS

  RFC(UID.RFC2557MIMEEncapsulation, "RFC 2557 MIME Encapsulation"), // NON-NLS

  JPEG_LOSSY_8(UID.JPEGBaseline8Bit, "JPEG Lossy (8 bits)"), // NON-NLS

  JPEG_LOSSY_12(UID.JPEGExtended12Bit, "JPEG Lossy (12 bits)"), // NON-NLS

  JPEG_LOSSLESS_57(UID.JPEGLossless, "JPEG Lossless"), // NON-NLS

  JPEG_LOSSLESS_70(UID.JPEGLosslessSV1, "JPEG Lossless"), // NON-NLS

  JPEGLS_LOSSLESS(UID.JPEGLSLossless, "JPEG-LS Lossless"), // NON-NLS

  JPEGLS_NEAR_LOSSLESS(UID.JPEGLSNearLossless, "JPEG-LS Lossy (Near-Lossless)"), // NON-NLS

  JPEG2000_LOSSLESS(UID.JPEG2000Lossless, "JPEG 2000 (Lossless Only)"), // NON-NLS

  JPEG2000(UID.JPEG2000, "JPEG 2000"), // NON-NLS

  JPEG2000_LOSSLESS_2(UID.JPEG2000MCLossless, "JPEG 2000 Part 2 (Lossless Only)"), // NON-NLS

  JPEG2000_2(UID.JPEG2000MC, "JPEG 2000 Part 2"), // NON-NLS

  JPEGXL_LOSSLESS(UID.JPEGXLLossless, "JPEG XL Lossless"),

  JPEGXL_RECOMPRESSION(UID.JPEGXLJPEGRecompression, "JPEG XL Recompression"), // NON-NLS

  JPEGXL(UID.JPEGXL, "JPEG XL"), // NON-NLS

  JPIP(UID.JPIPReferenced, "JPIP"), // NON-NLS

  JPIP_DEFLATE(UID.JPIPReferencedDeflate, " JPIP Deflate"), // NON-NLS

  MPEG2(UID.MPEG2MPML, "MPEG2 Main Level"), // NON-NLS

  MPEG2_HIGH(UID.MPEG2MPHL, "JPEG 2000 High Level"), // NON-NLS

  MPEG_4(UID.MPEG4HP41, "MPEG-4 AVC/H.264"), // NON-NLS

  MPEG_4_BD(UID.MPEG4HP41BD, "MPEG-4 AVC/H.264 BD-compatible"); // NON-NLS

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
