/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in
 * Java(TM), hosted at http://sourceforge.net/projects/dcm4che.
 *
 * The Initial Developer of the Original Code is
 * Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 * Gunter Zeilinger <gunterze@gmail.com>
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package org.dcm4che2.imageioimpl.plugins.rle;

import java.awt.Rectangle;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.EOFException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author gunter zeilinger(gunterze@gmail.com)
 * @version $Revision: 16277 $ $Date: 2009-07-28 07:38:19 +0200 (Tue, 28 Jul 2009) $
 * @since May 11, 2006
 * 
 */
public class RLEImageReader extends ImageReader {
    private static final Logger log = LoggerFactory.getLogger(RLEImageReader.class);
    private final int[] header = new int[16];
    private final byte[] buf = new byte[8192];
    private long headerPos;
    private long bufOff;
    private int bufPos;
    private int bufLen;
    private ImageInputStream iis;
    private int width = -1, height = -1;
    private ColorModel colorModel;
    private boolean convertSpace = false;
    private int curSeg;
    private int nSegs;
    private long segEnd;

    public RLEImageReader(ImageReaderSpi originator) {
        super(originator);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        iis = (ImageInputStream) input;
    }

    @Override
    public int getHeight(int imageIndex) {
        return height;
    }

    @Override
    public int getWidth(int imageIndex) {
        return width;
    }

    @Override
    public int getNumImages(boolean allowSearch) {
        return 1;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) {
        return null;
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    @Override
    public WritableRaster readRaster(int imageIndex, ImageReadParam param) throws IOException {
        BufferedImage bi = getReadImage(param);
        return readRasterInternal(bi, imageIndex, param);
    }

    protected WritableRaster readRasterInternal(BufferedImage bi, int imageIndex, ImageReadParam param)
        throws IOException {
        if (input == null) {
            throw new IllegalStateException("Input not set");
        }
        readRLEHeader();
        nSegs = header[0];
        checkDestination(nSegs, bi);
        WritableRaster raster = bi.getRaster();
        DataBuffer db = raster.getDataBuffer();
        if (db instanceof DataBufferByte) {
            DataBufferByte dbb = (DataBufferByte) db;
            byte[][] bankData = dbb.getBankData();
            ComponentSampleModel sm = (ComponentSampleModel) bi.getSampleModel();
            int[] bankIndices = sm.getBankIndices();
            int[] bandOffsets = sm.getBandOffsets();
            int pixelStride = sm.getPixelStride();
            for (int i = 0; i < nSegs; i++) {
                seekSegment(i + 1);
                unrle(bankData[bankIndices[i]], bandOffsets[i], pixelStride);
            }
        } else {
            short[] ss =
                db instanceof DataBufferUShort ? ((DataBufferUShort) db).getData() : ((DataBufferShort) db).getData();
            seekSegment(1);
            unrle(ss, 8);
            seekSegment(2);
            unrle(ss, 0);
        }
        seekInputToEndOfRLEData();
        return raster;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        BufferedImage bi = getReadImage(param);
        readRasterInternal(bi, imageIndex, param);

        BufferedImage retImage = getDestination(param, bi);
        if (retImage == bi) {
            log.debug("Returning raw, unconverted image.");
            return retImage;
        }
        log.debug("RLE image being converted.");
        if (retImage.getColorModel().getNumComponents() == 3) {
            convertColorSpaceToRGB(param, bi, retImage);
        } else {
            copyGrayRegion(param, bi, retImage);
        }
        return retImage;
    }

    /**
     * Copies the subregion/sub-sampling in param from src to dest
     * 
     * @param param
     * @param src
     * @param dest
     */
    private void copyGrayRegion(ImageReadParam param, BufferedImage src, BufferedImage dest) {
        Rectangle region = param.getSourceRegion();
        int w = width;
        int h = height;
        int x = 0;
        int y = 0;
        if (region != null) {
            w = (int) region.getWidth();
            h = (int) region.getHeight();
            x = (int) region.getX();
            y = (int) region.getY();
        }

        int sampleX = param.getSourceXSubsampling();
        int sampleY = param.getSourceYSubsampling();
        int srcX = param.getSubsamplingXOffset() + x;
        int srcY = param.getSubsamplingYOffset() + y;
        int srcW = Math.min(w, width - srcX);
        int srcH = Math.min(h, height - srcY);
        int destW = srcW / sampleX;
        int destH = srcH / sampleY;

        int[] pixel = null;
        SampleModel srcSm = src.getSampleModel();
        DataBuffer srcDb = src.getRaster().getDataBuffer();
        SampleModel destSm = dest.getSampleModel();
        DataBuffer destDb = dest.getRaster().getDataBuffer();
        for (int iy = 0; iy < destH; iy++) {
            for (int ix = 0; ix < destW; ix++) {
                pixel = srcSm.getPixel(ix * sampleX + srcX, iy * sampleY + srcY, pixel, srcDb);
                destSm.setPixel(ix, iy, pixel, destDb);
            }
        }
    }

    /**
     * This method uses the source colour space to allow conversion to RGB from YBR CS
     */
    private void convertColorSpaceToRGB(ImageReadParam param, BufferedImage src, BufferedImage dest) {
        Rectangle region = param.getSourceRegion();
        int w = width;
        int h = height;
        int x = 0;
        int y = 0;
        if (region != null) {
            w = (int) region.getWidth();
            h = (int) region.getHeight();
            x = (int) region.getX();
            y = (int) region.getY();
        }

        int sampleX = param.getSourceXSubsampling();
        int sampleY = param.getSourceYSubsampling();
        int srcX = param.getSubsamplingXOffset() + x;
        int srcY = param.getSubsamplingYOffset() + y;
        int srcW = Math.min(w, width - srcX);
        int srcH = Math.min(h, height - srcY);
        int destW = srcW / sampleX;
        int[] srcRgb = new int[srcW];
        int[] destRgb = srcRgb;
        if (srcW != destW) {
            destRgb = new int[destW];
        }
        int destH = srcH / sampleY;
        log.debug("Converting image " + src.getColorModel().getColorSpace() + " to "
            + dest.getColorModel().getColorSpace());
        for (int iy = 0; iy < destH; iy++) {
            src.getRGB(srcX, iy * sampleY + srcY, srcW, 1, srcRgb, 0, width);
            if (srcRgb != destRgb) {
                for (int ix = 0; ix < destW; ix++) {
                    destRgb[ix] = srcRgb[ix * sampleX];
                }
            }
            dest.setRGB(0, iy, destW, 1, destRgb, 0, width);
        }
    }

    /**
     * This returns a full buffered image containing the entire image data, in whatever color space the image is
     * actually in. This MAY be the final destination BI, but not necessarily if the size/ position or color spaces
     * change.
     * 
     * @param param
     * @return
     */
    private BufferedImage getReadImage(ImageReadParam param) {
        BufferedImage bi = param.getDestination();
        ImageTypeSpecifier imageType = param.getDestinationType();
        if (bi == null && imageType == null) {
            throw new IllegalArgumentException(
                "RLE Image Reader needs set ImageReadParam.destination or an ImageTypeSpecifier");
        }
        if (imageType != null) {
            width = imageType.getSampleModel().getWidth();
            height = imageType.getSampleModel().getHeight();
            colorModel = imageType.getColorModel();
        } else {
            return bi;
        }

        convertSpace =
            (colorModel.getColorSpace().getType() == ColorSpace.TYPE_YCbCr && (bi == null || bi.getColorModel()
                .getColorSpace().getType() != ColorSpace.TYPE_YCbCr));
        WritableRaster raster = Raster.createWritableRaster(imageType.getSampleModel(), null);
        bi = new BufferedImage(colorModel, raster, false, null);
        return bi;
    }

    /**
     * Get the destination buffered image, in the correct colour space etc. if this returns readImage directly, just use
     * readImage as the return value.
     * 
     * @param param
     * @param readImage
     * @return
     */
    private BufferedImage getDestination(ImageReadParam param, BufferedImage readImage) {
        BufferedImage bi = param.getDestination();
        if (bi != null) {
            return bi;
        }

        Rectangle region = param.getSourceRegion();
        int sampleX = param.getSourceXSubsampling();
        int sampleY = param.getSourceYSubsampling();
        if (region != null && region.getX() == 0 && region.getY() == 0 && region.getWidth() == width
            && region.getHeight() == height) {
            region = null;
        }
        if (region == null && sampleX == 1 && sampleY == 1 && !convertSpace) {
            return readImage;
        }
        int destWidth = width / sampleX;
        int destHeight = height / sampleY;
        if (region != null) {
            destWidth = (int) (region.getWidth() / sampleX);
            destHeight = (int) (region.getHeight() / sampleY);
        }
        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (convertSpace || readImage.getColorModel().getNumComponents() == 3) {
            type = BufferedImage.TYPE_INT_RGB;
        } else if (readImage.getColorModel().getComponentSize(0) > 8) {
            type = BufferedImage.TYPE_USHORT_GRAY;
        }
        return new BufferedImage(destWidth, destHeight, type);
    }

    private void readRLEHeader() throws IOException {
        headerPos = iis.getStreamPosition();
        fillBuffer();
        if (bufLen < 64) {
            throw new EOFException();
        }
        for (int i = 0; i < 16; i++, bufPos += 4) {
            header[i] =
                buf[bufPos] & 0xff | (buf[bufPos + 1] & 0xff) << 8 | (buf[bufPos + 2] & 0xff) << 16
                    | (buf[bufPos + 3] & 0xff) << 24;
        }
    }

    private void seekSegment(int seg) throws IOException {
        long segPos = headerPos + header[seg];
        segEnd = seg < nSegs ? headerPos + header[seg + 1] : Long.MAX_VALUE;
        if (segPos < bufOff) { // backwards seek should not happen!
            iis.seek(segPos);
            fillBuffer();
        } else {
            while (segPos - bufOff >= bufLen) {
                fillBuffer();
            }
            bufPos = (int) (segPos - bufOff);
        }
        curSeg = seg;
    }

    private void seekInputToEndOfRLEData() throws IOException {
        iis.seek(bufOff + bufPos);
    }

    private byte nextByte() throws IOException {
        if (bufOff + bufPos >= segEnd) {
            throw new EOFException();
        }
        if (bufPos == bufLen) {
            fillBuffer();
        }
        return buf[bufPos++];
    }

    private void nextBytes(byte[] bs, int off, int len) throws IOException {
        int read, pos = 0;
        while (pos < len) {
            if (bufPos == bufLen) {
                fillBuffer();
            }
            read = Math.min(len - pos, bufLen - bufPos);
            System.arraycopy(buf, bufPos, bs, off + pos, read);
            bufPos += read;
            pos += read;
        }
    }

    private void fillBuffer() throws IOException {
        bufOff = iis.getStreamPosition();
        bufPos = 0;
        bufLen = iis.read(buf);
        if (bufLen <= 0) {
            throw new EOFException();
        }
    }

    private void checkDestination(int nSegs, BufferedImage bi) throws IIOException {
        WritableRaster raster = bi.getRaster();
        int nBands = raster.getNumBands();
        int dataType = raster.getTransferType();
        if (nSegs == 1 || nSegs == 3) {
            if (nBands == nSegs && dataType == DataBuffer.TYPE_BYTE) {
                return;
            }
        } else if (nSegs == 2) {
            if (nBands == 1 && (dataType == DataBuffer.TYPE_USHORT || dataType == DataBuffer.TYPE_SHORT)) {
                return;
            }
        } else {
            throw new IIOException("Unsupported Number of RLE Segments: " + (nSegs & 0xffffffffL));
        }
        throw new IIOException("Number of RLE Segments: " + nSegs + " incompatible with Destination[bands=" + nBands
            + ", data=" + raster.getDataBuffer() + "]");
    }

    private void unrle(byte[] bs, int off, int pixelStride) throws IOException {
        if (pixelStride == 1) {
            unrle(bs);
            return;
        }
        int l, pos = off;
        byte b;
        try {
            while (pos < bs.length) {
                b = nextByte();
                if (b >= 0) {
                    l = checkLengthTooLong(b + 1, (off + bs.length - pos) / pixelStride);
                    for (int i = 0; i < l; i++, pos += pixelStride) {
                        bs[pos] = nextByte();
                    }
                } else if (b != -128) {
                    l = checkLengthTooLong(-b + 1, (off + bs.length - pos) / pixelStride);
                    b = nextByte();
                    for (int i = 0; i < l; i++, pos += pixelStride) {
                        bs[pos] = b;
                    }
                }
            }
        } catch (EOFException e) {
            log.warn("RLE Segment #{} too short, set missing {} bytes to 0", Integer.valueOf(curSeg),
                Integer.valueOf((bs.length - pos + off) / pixelStride));
        }
    }

    private void unrle(byte[] bs) throws IOException {
        int l, pos = 0;
        byte b;
        try {
            while (pos < bs.length) {
                b = nextByte();
                if (b >= 0) {
                    l = checkLengthTooLong(b + 1, bs.length - pos);
                    nextBytes(bs, pos, l);
                    pos += l;
                } else if (b != -128) {
                    l = checkLengthTooLong(-b + 1, bs.length - pos);
                    b = nextByte();
                    Arrays.fill(bs, pos, pos + l, b);
                    pos += l;
                }
            }
        } catch (EOFException e) {
            log.warn("RLE Segment #{} too short, set missing {} bytes to 0", Integer.valueOf(curSeg),
                Integer.valueOf(bs.length - pos));
        }
    }

    private int checkLengthTooLong(int length, int max) {
        if (length > max) {
            log.warn("RLE Segment #{} too long, truncate {} bytes", Integer.valueOf(curSeg),
                Integer.valueOf(length - max));
            return max;
        }
        return length;
    }

    private void unrle(short[] ss, int shiftLeft) throws IOException {
        int v, l, pos = 0;
        byte b;
        try {
            while (pos < ss.length) {
                b = nextByte();
                if (b >= 0) {
                    l = checkLengthTooLong(b + 1, ss.length - pos);
                    for (int i = 0; i < l; i++, pos++) {
                        ss[pos] |= (nextByte() & 0xff) << shiftLeft;
                    }
                } else if (b != -128) {
                    l = checkLengthTooLong(-b + 1, ss.length - pos);
                    v = (nextByte() & 0xff) << shiftLeft;
                    for (int i = 0; i < l; i++, pos++) {
                        ss[pos] |= v;
                    }
                }
            }
        } catch (EOFException e) {
            log.warn("RLE Segment #{} too short, set missing {} bytes to 0", Integer.valueOf(curSeg),
                Integer.valueOf(ss.length - pos));
        }
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

}
