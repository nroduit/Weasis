/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.jpeg.internal;

import java.io.EOFException;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.bytedeco.javacpp.IntPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.image.jni.StreamSegment;
import org.weasis.jpeg.JpegParameters;
import org.weasis.jpeg.NativeJPEGImage;
import org.weasis.jpeg.cpp.libijg;
import org.weasis.jpeg.cpp.libijg.DJDecompressIJG12Bit;
import org.weasis.jpeg.cpp.libijg.DJDecompressIJG16Bit;
import org.weasis.jpeg.cpp.libijg.DJDecompressIJG8Bit;
import org.weasis.jpeg.cpp.libijg.RETURN_MSG;
import org.weasis.jpeg.cpp.libijg.jpeg_decompress_struct;

import com.sun.media.imageioimpl.common.ExtendImageParam;

public class JpegCodec implements NativeCodec {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpegCodec.class);

    public JpegCodec() {
    }

    @Override
    public String readHeader(NativeImage nImage) throws IOException {
        String msg = null;
        StreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            try (DecoderIJG decomp = new DJDecompressIJG8Bit()) {
                ByteBuffer buffer = seg.getDirectByteBuffer(0);
                decomp.init(false);
                RETURN_MSG val = decomp.readHeader(buffer, buffer.limit(), false);
                if (val != null && val.code() == libijg.OK) {
                    setParameters(nImage.getImageParameters(), decomp);
                } else if (val != null) {
                    msg = val.msg().getString();
                }
                // keep a reference to be not garbage collected
                StreamSegment.safeToBuffer(buffer).clear();
            }
        }
        return msg;
    }

    @Override
    public String decompress(NativeImage nImage, ImageReadParam param) throws IOException {
        // TODO use ImageReadParam
        String msg = null;
        StreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            JpegParameters params = (JpegParameters) nImage.getImageParameters();
            int bps = params.getBitsPerSample();
            if (bps < 1 || bps > 16) {
                return "JPEG codec: invalid bit per sample: " + bps;
            }
            try (DecoderIJG decomp = bps > 12 ? new DJDecompressIJG16Bit()
                : bps > 8 ? new DJDecompressIJG12Bit() : new DJDecompressIJG8Bit()) {
                int segmentFragment = 0;
                ByteBuffer buffer = seg.getDirectByteBuffer(segmentFragment);
                boolean signed = params.isSignedData();
                // Use to handle conversion
                Boolean ybr = true;
                if (param instanceof ExtendImageParam) {
                    String cmd = ((ExtendImageParam) param).getYbrColorModel();
                    if(cmd != null) {
                        // Force JPEG Baseline (1.2.840.10008.1.2.4.50) to YBR_FULL_422
                        // http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_8.2.html#sect_8.2.1
                        ybr = cmd.startsWith("YBR") || (cmd.equalsIgnoreCase("RGB") && params.getMarker() == 0xffc0);
                    }
                }
                decomp.init(ybr);
                // Force RBG (for gray keeps grayscale model), except for signed data where the conversion is not
                // supported.
                RETURN_MSG val = decomp.readHeader(buffer, buffer.limit(), signed);
                if (val != null && val.code() == libijg.OK) {
                    setParameters(nImage.getImageParameters(), decomp);
                    LOGGER.debug("Input color space {}", decomp.getJpeg_DecompressStruct().jpeg_color_space());
                    // Build outputStream here and transform to an array
                    ByteBuffer outBuf = ByteBuffer.allocateDirect(params.getBytesPerLine() * params.getHeight());
                    outBuf.order(ByteOrder.LITTLE_ENDIAN);

                    int result = libijg.EJ_Suspension;
                    while (result == libijg.EJ_Suspension) {
                        val = decomp.decode(buffer, buffer.limit(), outBuf, outBuf.limit());
                        result = val.code();
                        if (result == libijg.EJ_Suspension) {
                            segmentFragment++;
                            buffer = seg.getDirectByteBuffer(segmentFragment);
                        }
                    }

                    bps = params.getBitsPerSample();
                    nImage.setOutputBuffer((bps > 8 && bps <= 16) ? outBuf.asShortBuffer() : outBuf);
                } else if (val != null) {
                    msg = val.msg().getString();
                }
                // keep a reference to be not garbage collected
                StreamSegment.safeToBuffer(buffer).clear();
            }
        }
        return msg;
    }

    @Override
    public String compress(NativeImage nImage, ImageOutputStream ouputStream, ImageWriteParam param)
        throws IOException {
        String msg = null;
        if (nImage != null && ouputStream != null && nImage.getInputBuffer() != null) {
            try {
                JpegParameters params = (JpegParameters) nImage.getImageParameters();
                int bps = params.getBitsPerSample();
                if (bps < 1 || bps > 16) {
                    return "JPEG codec: invalid bit per sample: " + bps;
                }
                int samplesPerPixel = params.getSamplesPerPixel();
                if (samplesPerPixel != 1 && samplesPerPixel != 3) {
                    return "JPEG codec supports only 1 and 3 bands!";
                }
                // DecoderIJG decomp =
                // bps > 12 ? new DJDecompressIJG16Bit() : bps > 8 ? new DJDecompressIJG12Bit()
                // : new DJDecompressIJG8Bit();

                // DJCodecParameter djParams = new DJCodecParameter(libijg.ECC_lossyYCbCr);
                // EncoderIJG comp = new DJCompressIJG8Bit(djParams, libijg.EJM_baseline, (byte) 90);
                long start = System.currentTimeMillis();
                // TODO get directly array
                Buffer b = nImage.getInputBuffer();

                ByteBuffer buffer;
                if (b instanceof ByteBuffer) {
                    buffer = ByteBuffer.allocateDirect(b.limit());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put(((ByteBuffer) b));
                } else if (b instanceof ShortBuffer) {
                    ShortBuffer sBuf = (ShortBuffer) b;
                    buffer = ByteBuffer.allocateDirect(sBuf.limit() * 2);
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    while (sBuf.hasRemaining()) {
                        buffer.putShort(sBuf.get());
                    }
                } else {
                    return "JPEG driver exception: not valid input buffer";
                }
                StreamSegment.safeToBuffer(buffer).flip();
                long stop = System.currentTimeMillis();
                System.out.println("Convert array time: " + (stop - start) + " ms"); //$NON-NLS-1$

                // Build outputStream here and transform to an array
                ByteBuffer outBuf = ByteBuffer.allocateDirect(params.getWidth() * params.getHeight()
                    * params.getSamplesPerPixel() * params.getBitsPerSample() / 16);
                outBuf.order(params.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);

                int columns = params.getWidth();
                int rows = params.getHeight();
                int interpr = samplesPerPixel == 1 ? libijg.EPI_Monochrome2 : libijg.EPI_RGB;
                IntPointer bytesWritten = new IntPointer(1);

                // start = System.currentTimeMillis();
                // // byte[] to = null;
                // RETURN_MSG val = comp.encode(columns, rows, interpr, samplesPerPixel, buffer, outBuf, bytesWritten);
                // // keep a reference to be not garbage collected
                // buffer.clear();
                // if (val == null || val.code() != libijg.OK) {
                // msg = val == null ? "error" : val.msg().getString();
                // }
                // stop = System.currentTimeMillis();
                // System.out
                // .println("Native encoder time: " + (stop - start) + " ms. BytesWritten add: " + bytesWritten.get());
                // //$NON-NLS-1$
                // if (msg == null) {
                // // ByteBuffer outBuf = ByteBuffer.wrap(to);
                // outBuf.rewind();
                // // TODO write directly in native lib by calling: ouputStream.write(outBuf.array(), 0, 4096);
                // NativeImage.writeByteBuffer(ouputStream, outBuf, 154618);
                // System.out.println("Write encoder time: " + (System.currentTimeMillis() - stop) + " ms");
                // //$NON-NLS-1$
                // }
            } finally {
                nImage.setInputBuffer(null);
                // Do not close inChannel (comes from image input stream)
            }
        }
        return msg;
    }

    @Override
    public void dispose() {
    }

    private static void setParameters(ImageParameters params, DecoderIJG codec) {
        if (params != null && codec != null) {
            jpeg_decompress_struct p = codec.getJpeg_DecompressStruct();
            params.setWidth(p.image_width());
            params.setHeight(p.image_height());
            /*
             * Adjust tile size to image size. RenderedImage will handle only one tile as this decoder doesn't support
             * well region reading (slow).
             */
            params.setTileWidth(params.getWidth());
            params.setTileHeight(params.getHeight());
            params.setBitsPerSample(p.data_precision());
            params.setSamplesPerPixel(p.num_components());
            params.setBytesPerLine(
                params.getWidth() * params.getSamplesPerPixel() * ((params.getBitsPerSample() + 7) / 8));
            // params.setAllowedLossyError(p.allowedlossyerror());
        }
    }

    @Override
    public NativeImage buildImage(ImageInputStream iis) throws IOException {
        SOFSegment sof = getSOFSegment(iis);
        NativeJPEGImage img = new NativeJPEGImage();
        if (sof != null) {
            JpegParameters params = img.getJpegParameters();
            params.setMarker(sof.getMarker());
            params.setWidth(sof.getSamplesPerLine());
            params.setHeight(sof.getLines());
            // Adjust tile size to image size for writer
            params.setTileWidth(params.getWidth());
            params.setTileHeight(params.getHeight());
            params.setBitsPerSample(sof.getSamplePrecision());
            params.setSamplesPerPixel(sof.getComponents());
            params.setBytesPerLine(
                params.getWidth() * params.getSamplesPerPixel() * ((params.getBitsPerSample() + 7) / 8));
        }
        return img;
    }

    public static SOFSegment getSOFSegment(ImageInputStream iis) throws IOException {
        iis.mark();
        try {
            int byte1 = iis.read();
            int byte2 = iis.read();
            // Magic numbers for JPEG (general jpeg marker)
            if ((byte1 != 0xFF) || (byte2 != 0xD8)) {
                return null;
            }
            do {
                byte1 = iis.read();
                byte2 = iis.read();
                // Something wrong, but try to read it anyway
                if (byte1 != 0xFF) {
                    break;
                }
                // Start of scan
                if (byte2 == 0xDA) {
                    break;
                }
                // Start of Frame, also known as SOF55, indicates a JPEG-LS file.
                if (byte2 == 0xF7) {
                    return getSOF(iis, (byte1 << 8) + byte2);
                }
                // 0xffc0: // SOF_0: JPEG baseline
                // 0xffc1: // SOF_1: JPEG extended sequential DCT
                // 0xffc2: // SOF_2: JPEG progressive DCT
                // 0xffc3: // SOF_3: JPEG lossless sequential
                if ((byte2 >= 0xC0) && (byte2 <= 0xC3)) {
                    return getSOF(iis, (byte1 << 8) + byte2);
                }
                // 0xffc5: // SOF_5: differential (hierarchical) extended sequential, Huffman
                // 0xffc6: // SOF_6: differential (hierarchical) progressive, Huffman
                // 0xffc7: // SOF_7: differential (hierarchical) lossless, Huffman
                if ((byte2 >= 0xC5) && (byte2 <= 0xC7)) {
                    return getSOF(iis, (byte1 << 8) + byte2);
                }
                // 0xffc9: // SOF_9: extended sequential, arithmetic
                // 0xffca: // SOF_10: progressive, arithmetic
                // 0xffcb: // SOF_11: lossless, arithmetic
                if ((byte2 >= 0xC9) && (byte2 <= 0xCB)) {
                    return getSOF(iis, (byte1 << 8) + byte2);
                }
                // 0xffcd: // SOF_13: differential (hierarchical) extended sequential, arithmetic
                // 0xffce: // SOF_14: differential (hierarchical) progressive, arithmetic
                // 0xffcf: // SOF_15: differential (hierarchical) lossless, arithmetic
                if ((byte2 >= 0xCD) && (byte2 <= 0xCF)) {
                    return getSOF(iis, (byte1 << 8) + byte2);
                }
                int length = iis.read() << 8;
                length += iis.read();
                length -= 2;
                while (length > 0) {
                    length -= iis.skipBytes(length);
                }
            } while (true);
            return null;
        } finally {
            iis.reset();
        }
    }

    protected static SOFSegment getSOF(ImageInputStream iis, int marker) throws IOException {
        readUnsignedShort(iis);
        int samplePrecision = readUnsignedByte(iis);
        int lines = readUnsignedShort(iis);
        int samplesPerLine = readUnsignedShort(iis);
        int componentsInFrame = readUnsignedByte(iis);
        return new SOFSegment(marker, samplePrecision, lines, samplesPerLine, componentsInFrame);
    }

    private static final int readUnsignedByte(ImageInputStream iis) throws IOException {
        int ch = iis.read();
        if (ch < 0) {
            throw new EOFException();
        }
        return ch;
    }

    private static final int readUnsignedShort(ImageInputStream iis) throws IOException {
        int ch1 = iis.read();
        int ch2 = iis.read();
        if ((ch1 | ch2) < 0) {
            throw new EOFException();
        }
        return (ch1 << 8) + ch2;
    }

}
