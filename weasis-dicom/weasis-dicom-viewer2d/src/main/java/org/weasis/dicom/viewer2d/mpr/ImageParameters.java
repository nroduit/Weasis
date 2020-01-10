/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.viewer2d.mpr;

public class ImageParameters {
    public static final int MLIB_BIT = 0;
    public static final int MLIB_BYTE = 1;
    public static final int MLIB_SHORT = 2;
    public static final int MLIB_INT = 3;
    public static final int MLIB_FLOAT = 4;
    public static final int MLIB_DOUBLE = 5;
    public static final int MLIB_USHORT = 6;

    private int height;
    private int width;
    private int bitsPerSample;
    private int samplesPerPixel;
    private int bytesPerLine;
    private boolean bigEndian;
    private int dataType;
    private int bitOffset;
    private int format;

    public ImageParameters() {
        this(0, 0, 0, 0, false);
    }

    public ImageParameters(int height, int width, int bitsPerSample, int samplesPerPixel, boolean bigEndian) {
        this.height = height;
        this.width = width;
        this.bitsPerSample = bitsPerSample;
        this.samplesPerPixel = samplesPerPixel;
        this.bigEndian = bigEndian;
        this.dataType = -1;
        this.bitOffset = 0;
        this.format = 1;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getBitsPerSample() {
        return bitsPerSample;
    }

    public void setBitsPerSample(int bitsPerSample) {
        this.bitsPerSample = bitsPerSample;
    }

    public int getSamplesPerPixel() {
        return samplesPerPixel;
    }

    public void setSamplesPerPixel(int samplesPerPixel) {
        this.samplesPerPixel = samplesPerPixel;
    }

    public int getBytesPerLine() {
        return bytesPerLine;
    }

    public void setBytesPerLine(int bytesPerLine) {
        this.bytesPerLine = bytesPerLine;
    }

    public boolean isBigEndian() {
        return bigEndian;
    }

    public void setBigEndian(boolean bigEndian) {
        this.bigEndian = bigEndian;
    }

    public int getDataType() {
        if (dataType < 0) {
            if (bitsPerSample == 1) {
                return MLIB_BIT;
            }
            if (bitsPerSample <= 8) {
                return MLIB_BYTE;
            }
            if (bitsPerSample <= 16) {
                return MLIB_USHORT;
                // For DICOM pixelRepresentation != 0
                // return ImgParams.MLIB_SHORT;
            }
            if (bitsPerSample <= 32 && samplesPerPixel == 1) {
                return MLIB_INT;
            }
        }
        return MLIB_BYTE;
    }

    public void setDataType(int dataType) {
        this.dataType = dataType;
    }

    public int getFormat() {
        return format;
    }

    public void setFormat(int format) {
        this.format = format;
    }

    public void setBitOffset(int bitOffset) {
        this.bitOffset = bitOffset;
    }

    public int getBitOffset() {
        return bitOffset;
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("Size:"); //$NON-NLS-1$
        buf.append(width);
        buf.append("x"); //$NON-NLS-1$
        buf.append(height);
        buf.append(" Bits/Sample:"); //$NON-NLS-1$
        buf.append(bitsPerSample);
        buf.append(" Samples/Pixel:"); //$NON-NLS-1$
        buf.append(samplesPerPixel);
        buf.append(" Bytes/Line:"); //$NON-NLS-1$
        buf.append(bytesPerLine);
        buf.append(" Big Endian:"); //$NON-NLS-1$
        buf.append(bigEndian);
        return buf.toString();
    }

}
