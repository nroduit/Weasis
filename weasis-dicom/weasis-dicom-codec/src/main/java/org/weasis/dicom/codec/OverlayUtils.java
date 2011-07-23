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

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.WritableRaster;
import java.awt.image.renderable.ParameterBlock;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RenderedOp;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.image.ByteLookupTable;
import org.dcm4che2.image.LookupTable;
import org.dcm4che2.imageio.plugins.dcm.DicomImageReadParam;
import org.weasis.core.api.media.data.ImageElement;

/**
 * Provides utility methods to extract overlay information from DICOM files.
 * 
 * @author bwallace
 */
public class OverlayUtils {

    private static final int BITS_PER_BYTE = 8;

    /**
     * Returns true if the given frame number references an overlay - that is, is the form 0x60xx yyyy where xx is the
     * overlay number, and yyyy is the overlay frame number. xx must be even.
     * 
     * @param imageIndex
     * @return true if this is an overlay frame.
     */
    public static boolean isOverlay(int imageIndex) {
        return ((imageIndex & 0x60000000) == 0x60000000) && (imageIndex & 0x9F010000) == 0;
    }

    /**
     * Extra the frame number portion of the overlay number/imageIndex value.
     * 
     * @param imageIndex
     * @return
     */
    public static int extractFrameNumber(int imageIndex) {
        if (isOverlay(imageIndex))
            return imageIndex & 0xFFFF;
        throw new IllegalArgumentException("Only frame numbers of overlays can be extracted."); //$NON-NLS-1$
    }

    private static LookupTable reorderBytes;
    static {
        byte[] reorder = new byte[256];
        for (int i = 0; i < 256; i++) {
            for (int j = 0; j < 8; j++) {
                int bitTest = 1 << j;
                if ((i & bitTest) != 0) {
                    reorder[i] |= (0x80 >> j);
                }
            }
        }
        reorderBytes = new ByteLookupTable(8, false, 0, 8, reorder);
    }

    private static byte[] rgbArr = new byte[] { (byte) 0xFF, 0 };

    private static byte[] aArr = new byte[] { (byte) 0x00, (byte) 0xFF };

    /**
     * Read an overlay image or region instead of a regular image. Specify the overlayNumber by the 0x60xx 0000 number.
     * This will return a bitmap overlay, in the colour specified. The image reader isn't required unless overlays
     * encoded in the high bits are being read.
     * 
     * @param ds
     *            is a DicomObject to read the overlay from.
     * @param overlayNumber
     *            - of the form 0x60xx yyyy where xx is the overlay index and yyyy is the frame index
     * @param reader
     *            is the image reader used to extract the raster for the high bits
     * @param rgb
     *            is the colour to apply, can be null to use black & white overlays.
     * @return A single bit buffered image, transparent except in the given colour where bits are 1.
     * @throws IOException
     *             only if an image from the image reader is attempted and throws an exception.
     */
    public static BufferedImage extractOverlay(DicomObject ds, int overlayNumber, ImageReader reader, String rgbs)
        throws IOException {
        // We need the original overlay number.
        if (!OverlayUtils.isOverlay(overlayNumber))
            throw new IllegalArgumentException("Overlays must start with 0x60xx xxxx but it starts with " //$NON-NLS-1$
                + Integer.toString(overlayNumber, 16));
        int frameNumber = extractFrameNumber(overlayNumber);
        overlayNumber = overlayNumber & 0x60FE0000;

        int rows = getOverlayHeight(ds, overlayNumber);
        int cols = getOverlayWidth(ds, overlayNumber);
        if (cols == 0 || rows == 0)
            throw new IllegalArgumentException("No overlay found for " + Integer.toString(overlayNumber)); //$NON-NLS-1$
        int position = ds.getInt(overlayNumber | Tag.OverlayBitPosition);
        byte[] data;
        if (position == 0) {
            byte[] unpaddedData = ds.getBytes(overlayNumber | Tag.OverlayData);

            // Need to ensure that every row starts at a byte boundary
            data = padToFixRowByteBoundary(unpaddedData, rows, cols);

            // Extract a sub-frame IF there is a sub-frame, and one is
            // specified. There must be at least 2 frames worth
            // of data to even consider this operation.
            if (frameNumber > 0 && data.length >= rows * cols * 2 / BITS_PER_BYTE) {
                byte[] frameData = new byte[rows * cols / BITS_PER_BYTE];
                // TODO Replace with Array.copyOfRange once we are on 1.6
                System.arraycopy(data, (frameNumber - 1) * frameData.length, frameData, 0, frameData.length);
                data = frameData;
            }
        } else {
            Raster raw = reader.readRaster(frameNumber, null);
            int rowLen = (cols + 7) / 8;
            data = new byte[rows * rowLen];
            int[] pixels = new int[cols];
            int bit = (1 << position);
            for (int y = 0; y < rows; y++) {
                pixels = raw.getPixels(0, y, cols, 1, pixels);
                for (int x = 0; x < cols; x++) {
                    if ((pixels[x] & bit) != 0) {
                        data[rowLen * y + x / 8] |= (1 << (x % 8));
                    }
                }
            }
        }
        DataBuffer db = new DataBufferByte(data, data.length);
        // byte[] pixelByte = new byte[rows*(cols/8 + (cols%8==0?0:1))];

        WritableRaster wr = Raster.createPackedRaster(db, cols, rows, 1, new Point());
        byte[] rArr = rgbArr;
        byte[] gArr = rgbArr;
        byte[] bArr = rgbArr;
        if (rgbs != null && rgbs.length() > 0) {
            if (rgbs.startsWith("#")) { //$NON-NLS-1$
                rgbs = rgbs.substring(1);
            }
            int rgb = Integer.parseInt(rgbs, 16);
            rArr = new byte[] { 0, (byte) ((rgb >> 16) & 0xFF) };
            gArr = new byte[] { 0, (byte) ((rgb >> 8) & 0xFF) };
            bArr = new byte[] { 0, (byte) (rgb & 0xFF) };
        }
        ColorModel cm = new IndexColorModel(1, 2, rArr, gArr, bArr, aArr);
        BufferedImage bi = new BufferedImage(cm, wr, false, null);
        reorderBytes.lookup(bi.getRaster(), bi.getRaster());

        return bi;
    }

    /**
     * This method is used for soon-to-be-rasterized bit arrays that are contained within byte arrays (e.g. certain
     * overlays). This method accepts a byte array (containing the bit array) and fixes the byte array so that the
     * beginnings of rows in the bit array coincide with byte-boundaries. This method pads (with 0's) and logically
     * forward bit shifts across byte boundaries as necessary to accomplish this fix.
     * 
     * @param unpaddedData
     *            The byte array containing the bit array to be padded as necessary
     * @param rows
     *            The height of the image in pixels
     * @param cols
     *            The width of the image in pixels
     * @return The byte array fixed to have bit-level row beginnings coincide with byte array boundaries
     */
    protected static byte[] padToFixRowByteBoundary(byte[] unpaddedData, int rows, int cols) {
        int numRowBytes = (cols + 7) / 8;
        int paddedLength = rows * numRowBytes;
        if ((unpaddedData.length == paddedLength) && (cols % 8) == 0)
            return unpaddedData;

        byte[] data = new byte[paddedLength];

        for (int y = 0; y < rows; y++) {
            int posnPad = y * numRowBytes;
            int posnUnpad = y * cols;
            // Bits from the current byte needed
            int bits = posnUnpad % 8;
            posnUnpad /= 8;
            int prevBits = 8 - bits;
            if (bits == 0) {
                // Not only an optimization for performance - also prevents an exception if the last pixel doesn't need
                // to overflow from the next unpadded byte...
                System.arraycopy(unpaddedData, posnUnpad, data, posnPad, numRowBytes);
                continue;
            }
            int mask = (0xFF << bits) & 0xFF;
            int nextMask = (0xFF >> prevBits) & 0xFF;
            for (int x = 0; x < numRowBytes; x++) {
                try {
                    byte firstByte = (byte) ((unpaddedData[posnUnpad + x] & mask) >> bits);
                    byte secondByte = 0;
                    // The very last byte can use nothing from the next byte if there are unused bits in it
                    if (posnUnpad + x + 1 < unpaddedData.length) {
                        secondByte = (byte) ((unpaddedData[posnUnpad + x + 1] & nextMask) << prevBits);
                    }
                    data[posnPad + x] = (byte) (firstByte | secondByte);
                } catch (ArrayIndexOutOfBoundsException e) {
                    ArrayIndexOutOfBoundsException newEx =
                        new ArrayIndexOutOfBoundsException("Did not find enough source data (" + unpaddedData.length //$NON-NLS-1$
                            + ") in overlay to pad data for " + rows + " rows, " + cols + "columns"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                    newEx.initCause(e);
                    throw newEx;
                }
            }
        }

        return data;
    }

    /**
     * Reads the width of the overlay - needs to be done separately from the primary width even though they are supposed
     * to be identical, as a stand alone overlay won't have any width/height except in the overlay tags.
     * 
     * @param overlayNumber
     * @return The width in pixels of the given overlay.
     * @throws IOException
     */
    public static int getOverlayWidth(DicomObject ds, int overlayNumber) {
        // Zero out the frame index first.
        overlayNumber &= 0x60FF0000;
        return ds.getInt(Tag.OverlayColumns | overlayNumber);
    }

    /**
     * Reads the height of the overlay - needs to be done separately from the primary width even though they are
     * supposed to be identical, as a stand alone overlay won't have any width/height except in the overlay tags.
     * 
     * @param overlayNumber
     * @return
     */
    public static int getOverlayHeight(DicomObject ds, int overlayNumber) {
        overlayNumber &= 0x60FF0000;
        return ds.getInt(Tag.OverlayRows | overlayNumber);
    }

    private static final byte[] icmColorValues = new byte[] { (byte) 0xFF, (byte) 0x00 };
    private static final byte[] bitSwapLut = makeBitSwapLut();

    private static byte[] makeBitSwapLut() {
        byte[] rc = new byte[256];
        for (int i = 0; i < 256; i++) {
            rc[i] = byte_reverse(i);
        }
        return rc;
    }

    // reverse the bits in a byte
    private static byte byte_reverse(int b) {
        int out = 0;
        for (int i = 0; i < 8; i++) {
            out = (out << 1) | ((b >> i) & 1);
        }
        return (byte) out;
    }

    private static int groupedTag(int group, int tag) {
        int x = group << 16;
        return tag + x;
    }

    private static int getInt(DicomObject ds, int group, int tag, int def) {
        return ds.getInt(groupedTag(group, tag), def);
    }

    private static int[] getInts(DicomObject ds, int group, int tag) {
        return ds.getInts(groupedTag(group, tag));
    }

    private static String getString(DicomObject ds, int group, int tag) {
        return ds.getString(groupedTag(group, tag));
    }

    /**
     * Merge the overlays into the buffered image.
     * 
     * The overlay implementation is minimal.
     * 
     * 
     * Currently
     * 
     * <ul>
     * <li>In-Pixel-Data overlays are not supported (they are retired as of the current dicom standard)</li>
     * <li>More than 1 overlay data frame is not supported - there is nothing in the standard, no idea how this is
     * supposed to look.</li>
     * <li>Only the first overlay group is supported. Again, nothing in standard, will implement if given example
     * images.</li>
     * </ul>
     * 
     * @param imageElement
     *            .getImage()
     * 
     * @param bi
     * @param ds
     */
    public static RenderedImage getOverlays(ImageElement imageElement, DicomMediaIO reader, int frame, int width,
        int height) throws IOException {
        DicomObject ds = reader.getDicomObject();

        // long t1 = System.currentTimeMillis();
        IndexColorModel icm = new IndexColorModel(1, 2, icmColorValues, icmColorValues, icmColorValues, 0);
        BufferedImage overBi = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY, icm);
        // MultiPixelPackedSampleModel sampleModel = new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, width,
        // height,
        // 1); // one
        // TiledImage tiledImage = new TiledImage(0, 0, width, height, 0, 0, sampleModel, icm);
        // WritableRaster wr = tiledImage.getWritableTile(0, 0);

        ArrayList<Integer> oldStyleOverlayPlanes = new ArrayList<Integer>();

        for (int group = 0; group < 0x20; group += 2) {

            int oBitPosition = getInt(ds, group, Tag.OverlayBitPosition, -1);
            int oRows = getInt(ds, group, Tag.OverlayRows, -1);
            int oCols = getInt(ds, group, Tag.OverlayColumns, -1);
            int[] oOrigin = getInts(ds, group, Tag.OverlayOrigin);
            int oBitsAllocated = getInt(ds, group, Tag.OverlayBitsAllocated, -1);
            String oType = getString(ds, group, Tag.OverlayType);
            int oNumberOfFrames = getInt(ds, group, 0x60000015, 1);
            int oFrameStart = getInt(ds, group, 0x60000051, 1) - 1;
            int oFrameEnd = oFrameStart + oNumberOfFrames;

            if (oBitPosition == -1 && oBitsAllocated == -1 && oRows == -1 && oCols == -1) {
                // log.trace("No overlay data associated with image for group {}", group);
                continue;
            }

            if ("R".equals(oType)) { //$NON-NLS-1$
                // log.debug("Overlay ROI bitmap, not doing anything");
                continue;
            }

            if ((oBitsAllocated != 1) && (oBitPosition != 0)) {
                // log.debug("Overlay: {}  OldStyle bitPostion {}", group, oBitPosition);
                oldStyleOverlayPlanes.add(oBitPosition);
                continue;
            }

            if ("GR".indexOf(oType) < 0) { //$NON-NLS-1$
                // log.warn("mergeOverlays(): Overlay Type {} not supported", oType);
                continue;
            }

            int oX1 = 0;
            int oY1 = 0;
            if (oOrigin != null) {
                oX1 = oOrigin[1] - 1;
                oY1 = oOrigin[0] - 1;
            }

            // log.debug("Overlay: {} OverlayType: {}", group, oType);
            // log.debug("Overlay: {} OverlayRows: {}", group, oRows);
            // log.debug("Overlay: {} OverlayColumns: {}", group, oCols);
            // log.debug("Overlay: {} OverlayOrigin: {} {}", new Object[]
            // {
            // group, oX1, oY1
            // });
            // log.debug("Overlay: {} for Frames: [{}, {})", new Object[]
            // {
            // group, oFrameStart, oFrameEnd
            // });

            if (!((oFrameStart <= frame) && (frame < oFrameEnd))) {
                // log.debug("Overlay: frame {} not in range, skipping", frame);
            }

            int oFrameOffset = frame - oFrameStart;
            int bitOffset = oFrameOffset * oRows * oCols;
            int byteOffset = bitOffset / 8; // dont round up!
            int numBits = oRows * oCols;
            int numBytes = (numBits + 7) / 8; // round up!

            // log.debug("Overlay: {} bitOffset: {}", group, bitOffset);
            // log.debug("Overlay: {} byteOffset: {}", group, byteOffset);
            // log.debug("Overlay: {} numBits: {}", group, numBits);
            // log.debug("Overlay: {} numBytes: {}", group, numBytes);

            byte[] bb = ds.get(groupedTag(group, Tag.OverlayData)).getBytes();
            // log.debug("Overlay: {} ByteBuffer: {}", group, bb);
            // log.debug("Overlay: {} ByteBuffer ByteOrder: {}", group, (bb.order() == ByteOrder.BIG_ENDIAN) ?
            // "BigEndian"
            // : "LittleEndian");
            // DataBufferByte db = new DataBufferByte(bb, bb.length);
            // sampleModel.setDataElements(oX1, oY1, bb, db);

            DataBufferByte dataBufferByte = (DataBufferByte) overBi.getRaster().getDataBuffer();
            byte[] dest = dataBufferByte.getData();

            // java awt cant even handle non byte packed lines
            // so if a line lenght is not a multiple of 8, it gets really slow
            int packedRowBits = (oCols + 7) & (~7);

            // log.debug("packed row bits: {}", packedRowBits);

            if (packedRowBits == oCols) {
                // overlay is 8-bit padded, we can do it fast
                for (int i = 0; i < numBytes; i++) {
                    int idx = bb[byteOffset + i] & 0xFF;
                    dest[i] |= bitSwapLut[idx];
                }
            } else {
                // no joy, slow version
                int packedRowBytes = packedRowBits / 8;
                for (int y = 0; y < oRows; y++) {
                    int rowBitOffset = bitOffset + y * oCols;
                    int rowByteOffset = rowBitOffset / 8;
                    int packedRowByteOffset = y * packedRowBytes;
                    int bitsToMove = (rowBitOffset % 8);
                    if (bitsToMove != 0) {
                        for (int i = 0, size = packedRowBytes - 1; i < size; i++) {
                            int inOffset = rowByteOffset + i;
                            int b1 = bb[inOffset] & 0xFF;
                            int b2 = bb[inOffset + 1] & 0xFF;
                            int rc = ((b1 >> bitsToMove) ^ ((b2 << (8 - bitsToMove)) & 0xFF));
                            dest[packedRowByteOffset + i] |= bitSwapLut[rc];
                        }
                    } else {
                        for (int i = 0, size = packedRowBytes - 1; i < size; i++) {
                            int inOffset = rowByteOffset + i;
                            int b1 = bb[inOffset] & 0xFF;
                            dest[packedRowByteOffset + i] |= bitSwapLut[b1];
                        }
                    }
                }
            }
        }

        if (oldStyleOverlayPlanes.size() > 0) {
            try {

                // int bitsStored = ds.getInt(Tag.BitsStored, -1);
                // short overlayValue = (short) ((1 << bitsStored) - 1);
                PlanarImage source = imageElement.getImage();
                if (source != null) {
                    int dataType = source.getSampleModel().getDataType();
                    if (dataType == DataBuffer.TYPE_SHORT || dataType == DataBuffer.TYPE_USHORT) {
                        int mask = Integer.MAX_VALUE;
                        for (int i = 0, size = oldStyleOverlayPlanes.size(); i < size; i++) {
                            int val = (1 << oldStyleOverlayPlanes.get(i));
                            if (dataType == DataBuffer.TYPE_SHORT) {
                                // TODO need to be validated (no test available)
                                val = val - 32768;
                            }
                            if (val < mask) {
                                mask = val;
                            }
                        }

                        // get the image again, this time without windowing/maskpixeldata
                        ImageReadParam param = reader.getDefaultReadParam();
                        if (param instanceof DicomImageReadParam) {
                            ((DicomImageReadParam) param).setAutoWindowing(false);
                        }

                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(source);
                        pb.add((double) imageElement.getMinValue());
                        pb.add((double) (mask - 1));
                        RenderedOp result = JAI.create("ThresholdToBin", pb, null); //$NON-NLS-1$

                        // pb.add((double) (mask - 1));
                        // RenderedOp result = JAI.create("binarize", pb, null);
                        // pb = new ParameterBlock();
                        // pb.addSource(result);
                        // return JAI.create("NotBinary", pb);
                        return result;
                    } else {
                        // log.warn("mergeOverlays(): data buffer type {} not supported", _buffer.getDataType());
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // log.error("mergeOverlays(): ERROR", e);
            }
        }
        return overBi;
    }
}
