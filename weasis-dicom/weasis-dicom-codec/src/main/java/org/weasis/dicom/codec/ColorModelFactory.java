/*
 * ***** BEGIN LICENSE BLOCK ***** Version: MPL 1.1/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the License for the specific language governing rights and limitations under the License.
 * 
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in Java(TM), hosted at
 * http://sourceforge.net/projects/dcm4che.
 * 
 * The Initial Developer of the Original Code is Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005 the Initial Developer. All Rights Reserved.
 * 
 * Contributor(s): See listed authors below.
 * 
 * Alternatively, the contents of this file may be used under the terms of either the GNU General Public License Version
 * 2 or later (the "GPL"), or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in which case the
 * provisions of the GPL or the LGPL are applicable instead of those above. If you wish to allow use of your version of
 * this file only under the terms of either the GPL or the LGPL, and not to allow others to use your version of this
 * file under the terms of the MPL, indicate your decision by deleting the provisions above and replace them with the
 * notice and other provisions required by the GPL or the LGPL. If you do not delete the provisions above, a recipient
 * may use your version of this file under the terms of any one of the MPL, the GPL or the LGPL.
 * 
 * ***** END LICENSE BLOCK *****
 */

package org.weasis.dicom.codec;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.util.Arrays;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.image.PartialComponentColorModel;
import org.dcm4che2.image.SimpleYBRColorSpace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Jul 21, 2007
 */
public class ColorModelFactory {

    private static final Logger log = LoggerFactory.getLogger(ColorModelFactory.class);

    private static final String MONOCHROME1 = "MONOCHROME1"; //$NON-NLS-1$
    private static final String MONOCHROME2 = "MONOCHROME2"; //$NON-NLS-1$
    private static final String PALETTE_COLOR = "PALETTE COLOR"; //$NON-NLS-1$
    private static final String RGB = "RGB"; //$NON-NLS-1$
    private static final String YBR_FULL = "YBR_FULL"; //$NON-NLS-1$
    private static final String YBR_FULL_422 = "YBR_FULL_422"; //$NON-NLS-1$
    private static final String YBR_PARTIAL_422 = "YBR_PARTIAL_422"; //$NON-NLS-1$
    private static final String YBR_PARTIAL_420 = "YBR_PARTIAL_420"; //$NON-NLS-1$

    public static ColorModel createColorModel(DicomObject ds) {
        int samples = ds.getInt(Tag.SamplesPerPixel, 1);
        if (samples != 1 && samples != 3) {
            throw new IllegalArgumentException("Unsupported Samples per Pixel: " + samples); //$NON-NLS-1$
        }
        int allocated = ds.getInt(Tag.BitsAllocated, 8);
        int stored = ds.getInt(Tag.BitsStored, allocated);
        int dataType =
            allocated <= 8 ? DataBuffer.TYPE_BYTE : ds.getInt(Tag.PixelRepresentation) != 0 ? DataBuffer.TYPE_SHORT
                : DataBuffer.TYPE_USHORT;
        if (allocated > 16 && samples == 1) {
            dataType = DataBuffer.TYPE_INT;
        }
        int[] bits = new int[samples];
        Arrays.fill(bits, stored);
        ColorSpace cs = null;
        String pmi = null;
        if (samples == 1) {
            pmi = ds.getString(Tag.PhotometricInterpretation, MONOCHROME2);
            if (pmi.equals(MONOCHROME2) || pmi.equals(MONOCHROME1)) {
                cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
            } else if (pmi.equals(PALETTE_COLOR)) {
                return createPaletteColorModel(ds);
            }
        } else if (samples == 3) {
            pmi = ds.getString(Tag.PhotometricInterpretation, RGB);
            if (pmi.equals(RGB)) {
                log.debug("Color space is RGB."); //$NON-NLS-1$
                cs = createRGBColorSpace(ds);
            } else if (pmi.equals(YBR_FULL) || pmi.equals(YBR_FULL_422)) {
                log.debug("Color space is YBR full"); //$NON-NLS-1$
                cs = SimpleYBRColorSpace.createYBRFullColorSpace(createRGBColorSpace(ds));
            } else if (pmi.equals(YBR_PARTIAL_422) || pmi.equals(YBR_PARTIAL_420)) {
                log.debug("Color space is YBR partial"); //$NON-NLS-1$
                cs = SimpleYBRColorSpace.createYBRPartialColorSpace(createRGBColorSpace(ds));
            }
            if (pmi.endsWith("422")) { //$NON-NLS-1$
                return new PartialComponentColorModel(cs, 2, 1);
            }
            if (pmi.endsWith("420")) { //$NON-NLS-1$
                return new PartialComponentColorModel(cs, 2, 2);
            }
        } else {
            throw new IllegalArgumentException("Unsupported Samples per Pixel: " + samples); //$NON-NLS-1$
        }
        if (cs == null) {
            throw new IllegalArgumentException("Unsupported Photometric Interpretation: " + pmi //$NON-NLS-1$
                + " with Samples per Pixel: " + samples); //$NON-NLS-1$

        }
        return new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, dataType);
    }

    private static ColorSpace createRGBColorSpace(DicomObject ds) {
        byte[] iccProfile = ds.getBytes(Tag.ICCProfile);
        return iccProfile != null ? new ICC_ColorSpace(ICC_Profile.getInstance(iccProfile)) : ColorSpace
            .getInstance(ColorSpace.CS_sRGB);
    }

    public static boolean isMonochrome(DicomObject ds) {
        return ds.getInt(Tag.SamplesPerPixel, 1) == 1 && !isPaletteColor(ds);
    }

    public static boolean isPaletteColor(DicomObject ds) {
        return PALETTE_COLOR.equals(ds.getString(Tag.PhotometricInterpretation));
    }

    public static ColorModel createPaletteColorModel(DicomObject ds) {
        int bits = ds.getInt(Tag.BitsStored, 8);
        int size = 1 << bits;
        byte[] r =
            decodePaletteColorLut(size, ds, Tag.RedPaletteColorLookupTableDescriptor,
                Tag.RedPaletteColorLookupTableData, Tag.SegmentedRedPaletteColorLookupTableData);
        byte[] g =
            decodePaletteColorLut(size, ds, Tag.GreenPaletteColorLookupTableDescriptor,
                Tag.GreenPaletteColorLookupTableData, Tag.SegmentedGreenPaletteColorLookupTableData);
        byte[] b =
            decodePaletteColorLut(size, ds, Tag.BluePaletteColorLookupTableDescriptor,
                Tag.BluePaletteColorLookupTableData, Tag.SegmentedBluePaletteColorLookupTableData);
        return new IndexColorModel(bits, size, r, g, b);
    }

    private static void throwLutLengthMismatch(int lutLen, int descLen) {
        throw new IllegalArgumentException("LUT Data length: " + lutLen + " mismatch entry value: " + descLen //$NON-NLS-1$ //$NON-NLS-2$
            + " in LUT Descriptor"); //$NON-NLS-1$
    }

    private static byte[] decodePaletteColorLut(int size, DicomObject ds, int descTag, int dataTag, int segmTag) {
        int[] desc = ds.getInts(descTag);
        if (desc == null) {
            throw new IllegalArgumentException("Missing LUT Descriptor!"); //$NON-NLS-1$
        }
        if (desc.length != 3) {
            throw new IllegalArgumentException("Illegal number of LUT Descriptor values: " + desc.length); //$NON-NLS-1$
        }
        int len = desc[0] == 0 ? 0x10000 : desc[0];
        int off = desc[1];
        int bits = desc[2];
        if (len < 0) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: len=" + len); //$NON-NLS-1$
        }
        if (off < 0) {
            throw new IllegalArgumentException("Unsupported LUT Descriptor: off=" + off); //$NON-NLS-1$
        }

        if (bits != 8 && bits != 16) {
            throw new IllegalArgumentException("Illegal LUT Descriptor: bits=" + bits); //$NON-NLS-1$
        }

        byte[] out = new byte[size];
        byte[] data = ds.getBytes(dataTag);

        if (data == null) {
            short[] segm = ds.getShorts(segmTag);
            if (segm == null) {
                throw new IllegalArgumentException("Missing LUT Data!"); //$NON-NLS-1$
            }
            if (bits == 8) {
                throw new IllegalArgumentException("Segmented LUT Data with LUT Descriptor: bits=8"); //$NON-NLS-1$
            }
            inflateSegmentedLut(segm, out, off, len);
        } else {
            if (bits == 8) {
                if (data.length != len) {
                    if (data.length == 2 * len) {
                        short[] data16 = ds.getShorts(dataTag);
                        if (data16 != null) {
                            data = new byte[len];
                            for (int i = 0; i < len; i++) {
                                data[i] = (byte) data16[i];
                            }
                        }
                    }
                    if (data.length != len) {
                        throwLutLengthMismatch(data.length, len);
                    }
                }
                System.arraycopy(data, 0, out, off, len);
            } else { // bits == 16
                if (data.length != len << 1) {
                    throwLutLengthMismatch(data.length, len);
                }
                int hibyte = ds.bigEndian() ? 0 : 1;
                for (int i = 0; i < len; i++) {
                    out[i + off] = data[(i << 1) + hibyte];
                }
            }
        }

        Arrays.fill(out, 0, off, out[off]);
        Arrays.fill(out, off + len, size, out[off + len - 1]);
        return out;
    }

    private static void inflateSegmentedLut(short[] in, byte[] out, int off, int len) {
        int x0 = off;
        int y0 = 0;
        int y1, dy;
        for (int i = 0; i < in.length;) {
            int op = in[i++];
            int n = in[i++] & 0xffff;
            switch (op) {
                case 0:
                    for (int j = 0; j < n; ++j) {
                        out[x0++] = (byte) ((y0 = in[i++] & 0xffff) >> 8);
                    }
                    break;
                case 1:
                    y1 = in[i++] & 0xffff;
                    dy = y1 - y0;
                    for (int j = 0; j < n;) {
                        out[x0++] = (byte) ((y0 + dy * ++j / n) >> 8);
                    }
                    y0 = y1;
                    break;
                case 2:
                    int i1 = (in[i++] & 0xffff) | (in[i++] << 16);
                    for (int j = 0; j < n; ++j) {
                        int op2 = in[i1++];
                        int n2 = in[i1++] & 0xffff;
                        switch (op2) {
                            case 0:
                                for (int j2 = 0; j2 < n2; ++j2) {
                                    out[x0++] = (byte) ((y0 = in[i1++] & 0xffff) >> 8);
                                }
                                break;
                            case 1:
                                y1 = in[i1++] & 0xffff;
                                dy = y1 - y0;
                                for (int j2 = 0; j2 < n2;) {
                                    out[x0++] = (byte) ((y0 + dy * ++j2 / n2) >> 8);
                                }
                                y0 = y1;
                                break;
                            default:
                                throw new IllegalArgumentException("illegal op code:" + op2 + ", index:" + (i1 - 4)); //$NON-NLS-1$ //$NON-NLS-2$
                        }
                    }
                    break;
                default:
                    throw new IllegalArgumentException("illegal op code:" + op + ", index:" + (i - 4)); //$NON-NLS-1$ //$NON-NLS-2$
            }
        }
        if (x0 - off != len) {
            throwLutLengthMismatch(x0 - off, len);
        }
    }
}
