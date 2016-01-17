/*******************************************************************************
 * Copyright (c) 2015 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.jpeg.internal;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ShortBuffer;

import javax.imageio.ImageReadParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.bytedeco.javacpp.SizeTPointer;
import org.weasis.image.jni.FileStreamSegment;
import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.jpeg.JpegParameters;
import org.weasis.jpeg.NativeJPEGImage;
import org.weasis.jpeg.cpp.libijg;
import org.weasis.jpeg.cpp.libijg.ByteStreamInfo;

public class CharlsCodec implements NativeCodec {

    public CharlsCodec() {
    }

    @Override
    public String readHeader(NativeImage nImage) throws IOException {
        int ret = 0;
        FileStreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            org.weasis.jpeg.cpp.libijg.JlsParameters p = new org.weasis.jpeg.cpp.libijg.JlsParameters();
            ByteStreamInfo input = null;
            try {
                MappedByteBuffer buffer = seg.getDirectByteBuffer(0);
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                input = libijg.FromByteArray(buffer, size);
                ret = libijg.JpegLsReadHeaderStream(input, p);
                if (ret == libijg.OK) {
                    setParameters((JpegParameters) nImage.getImageParameters(), p);
                }
                buffer.clear();
            } finally {
                // Do not close inChannel (comes from image input stream)
                p.deallocate();
                if (input != null) {
                    input.deallocate();
                }
            }
        }
        return ret == 0 ? null : libijg.getErrorMessage(ret);
    }

    @Override
    public String decompress(NativeImage nImage, ImageReadParam param) throws IOException {
        // TODO use ImageReadParam
        int ret = 0;
        FileStreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            org.weasis.jpeg.cpp.libijg.JlsParameters p = new org.weasis.jpeg.cpp.libijg.JlsParameters();
            ByteStreamInfo input = null;
            try {
                // When multiple fragments segments, aggregate them in the byteBuffer.
                ByteBuffer buffer = seg.getDirectByteBuffer(0, seg.getSegLength().length - 1);
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                input = libijg.FromByteArray(buffer, size);

                JpegParameters params = (JpegParameters) nImage.getImageParameters();
                if (params.getBytesPerLine() == 0) {
                    ret = libijg.JpegLsReadHeaderStream(input, p);
                    buffer.clear();
                    if (ret == libijg.OK) {
                        setParameters(params, p);
                    } else {
                        return "Cannot read JPEG-LS header!";
                    }
                } else {
                    p.width(params.getWidth());
                    p.height(params.getHeight());
                    p.bitspersample(params.getBitsPerSample());
                    p.components(params.getSamplesPerPixel());
                    p.bytesperline(params.getBytesPerLine());
                    p.allowedlossyerror(params.getAllowedLossyError());
                }
                // p.outputBgr('1'); // convert RGB to BGR
                p.colorTransform(0); // default (RGB)

                // Build outputStream here and transform to an array
                ByteBuffer outBuf = ByteBuffer.allocateDirect(p.bytesperline() * p.height());
                outBuf.order(ByteOrder.nativeOrder()); // Not test with big endian system
                SizeTPointer size2 = new SizeTPointer(1);
                size2.put(outBuf.limit());

                ByteStreamInfo outStream = libijg.FromByteArray(outBuf, size2);
                ret = libijg.JpegLsDecodeStream(outStream, input, p);
                // keep a reference to be not garbage collected
                buffer.clear();

                if (ret == libijg.OK) {
                    int bps = p.bitspersample();
                    nImage.setOutputBuffer((bps > 8 && bps <= 16) ? outBuf.asShortBuffer() : outBuf);
                }

            } finally {
                // Do not close inChannel (comes from image input stream)
                p.deallocate();
                if (input != null) {
                    input.deallocate();
                }
            }
        }
        return ret == 0 ? null : libijg.getErrorMessage(ret);
    }

    @Override
    public String compress(NativeImage nImage, ImageOutputStream ouputStream, Rectangle region) throws IOException {
        int ret = 0;
        if (nImage != null && ouputStream != null && nImage.getInputBuffer() != null) {
            org.weasis.jpeg.cpp.libijg.JlsParameters p = new org.weasis.jpeg.cpp.libijg.JlsParameters();
            ByteStreamInfo input = null;
            try {
                JpegParameters params = (JpegParameters) nImage.getImageParameters();
                if (params.getBitsPerSample() != 8 && params.getBitsPerSample() != 16) {
                    return "JPGLS codec supports only 8 and 16-bit per pixel!";
                }
                int components = params.getSamplesPerPixel();
                if (components != 1 && components != 3 && components != 4) {
                    return "JPGLS codec supports only 1, 3 and 4 bands!";
                }
                // Band mode
                if (components == 3 || components == 4) {
                    // Interleaved by pixel
                    p.ilv(libijg.ILV_SAMPLE);
                } else {
                    p.ilv(libijg.ILV_NONE);
                }
                p.width(params.getWidth());
                p.height(params.getHeight());
                p.bitspersample(params.getBitsPerSample());
                p.components(components);
                p.bytesperline(params.getBytesPerLine());
                p.allowedlossyerror(params.getAllowedLossyError());

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
                    return "JPGLS codec exception: not valid input buffer";
                }
                buffer.flip();
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                input = libijg.FromByteArray(buffer, size);

                // Build outputStream here and transform to an array
                ByteBuffer outBuf = ByteBuffer.allocateDirect(params.getWidth() * params.getHeight()
                    * params.getSamplesPerPixel() * params.getBitsPerSample() / 16);
                outBuf.order(params.isBigEndian() ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
                SizeTPointer size2 = new SizeTPointer(1);
                size2.put(outBuf.limit());
                ByteStreamInfo outStream = libijg.FromByteArray(outBuf, size2);
                SizeTPointer bytesWritten = new SizeTPointer(1);

                ret = libijg.JpegLsEncodeStream(outStream, bytesWritten, input, p);
                // keep a reference to be not garbage collected
                buffer.clear();

                if (ret == libijg.OK) {
                    outBuf.rewind();
                    NativeImage.writeByteBuffer(ouputStream, outBuf, (int) bytesWritten.get());
                }
            } finally {
                nImage.setInputBuffer(null);
                // Do not close inChannel (comes from image input stream)
                p.deallocate();
                if (input != null) {
                    input.deallocate();
                }
            }
        }
        return ret == 0 ? null : libijg.getErrorMessage(ret);

    }

    @Override
    public void dispose() {

    }

    @Override
    public NativeJPEGImage buildImage(ImageInputStream iis) throws IOException {
        SOFSegment sof = JpegCodec.getSOFSegment(iis);
        NativeJPEGImage img = new NativeJPEGImage();
        if (sof != null) {
            if (sof.getMarker() != 0xFFF7) {
                throw new IllegalArgumentException("Stream without JPEG-LS marker!");
            }
            ImageParameters params = img.getJpegParameters();
            params.setWidth(sof.getSamplesPerLine());
            params.setHeight(sof.getLines());
            // Adjust tile size to image size for writer.
            params.setTileWidth(params.getWidth());
            params.setTileHeight(params.getHeight());

            params.setBitsPerSample(sof.getSamplePrecision());
            int nbBands = sof.getComponents();
            params.setSamplesPerPixel(nbBands);
            params.setBytesPerLine(
                params.getWidth() * params.getSamplesPerPixel() * ((params.getBitsPerSample() + 7) / 8));
            params.setFormat(nbBands == 1 ? ImageParameters.CM_GRAY
                : nbBands == 3 ? ImageParameters.CM_S_RGB : ImageParameters.CM_S_RGBA);
        }
        return img;
    }

    private static void setParameters(JpegParameters params, org.weasis.jpeg.cpp.libijg.JlsParameters p) {
        if (params != null && p != null) {
            params.setWidth(p.width());
            params.setHeight(p.height());
            /*
             * Adjust tile size to image size. RenderedImage will handle only one tile as this decoder doesn't support
             * well region reading (slow).
             */
            params.setTileWidth(params.getWidth());
            params.setTileHeight(params.getHeight());

            params.setBitsPerSample(p.bitspersample());
            int nbBands = p.components();
            params.setSamplesPerPixel(nbBands);
            params.setBytesPerLine(p.bytesperline());
            params.setAllowedLossyError(p.allowedlossyerror());

            params.setFormat(nbBands == 1 ? ImageParameters.CM_GRAY
                : nbBands == 3 ? ImageParameters.CM_S_RGB : ImageParameters.CM_S_RGBA);
        }
    }
}
