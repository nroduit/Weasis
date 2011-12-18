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
package org.weasis.dicom.codec;


public enum TransferSyntax {
    NONE(null, "None", null), //$NON-NLS-1$

    EXPLICIT_VR_LE("1.2.840.10008.1.2.1", "Explicit VR Little Endian", null), //$NON-NLS-1$ //$NON-NLS-2$

    RLE("1.2.840.10008.1.2.5", "RLE Lossless", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG_LOSSY_8("1.2.840.10008.1.2.4.50", "JPEG Lossy (8 bits)", 75), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG_LOSSY_12("1.2.840.10008.1.2.4.51", "JPEG Lossy (12 bits)", 75), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG_LOSSLESS_57("1.2.840.10008.1.2.4.57", "JPEG Lossless", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG_LOSSLESS_70("1.2.840.10008.1.2.4.70", "JPEG Lossless", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEGLS_LOSSLESS("1.2.840.10008.1.2.4.80", "JPEG-LS Lossless", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEGLS_NEAR_LOSSLESS("1.2.840.10008.1.2.4.81", "JPEG-LS Lossy (Near-Lossless)", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG2000_LOSSLESS("1.2.840.10008.1.2.4.90", "JPEG 2000 (Lossless Only)", null), //$NON-NLS-1$ //$NON-NLS-2$

    JPEG2000("1.2.840.10008.1.2.4.91", "JPEG 2000", 75); //$NON-NLS-1$ //$NON-NLS-2$

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
        return label + " [" + transferSyntaxUID + "]"; //$NON-NLS-1$ //$NON-NLS-2$
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
        }
        return NONE;
    }

    public static boolean requiresNativeImageioCodecs(String tsuid) {
        if (tsuid != null && tsuid.startsWith("1.2.840.10008.1.2.4.")) {
            try {
                int val = Integer.parseInt(tsuid.substring(20, 22));
                if (val >= 51 && val <= 81) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
