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
package org.weasis.jpeg.cpp;

import java.nio.ByteBuffer;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.javacpp.annotation.ByVal;
import org.bytedeco.javacpp.annotation.Cast;
import org.bytedeco.javacpp.annotation.Platform;
import org.weasis.jpeg.internal.DecoderIJG;

/**
 * Wrap native API of IJG and CHARLS
 *
 *
 */
@Platform(include = { "libcparam.h", "libeijg8.h", "jpeglib8.h", "libijg8.h", "libijg12.h", "libijg16.h",
    "interface.h" })
// "charls.h" }) // new lib 2.0, charls.h instead of interface.h
// , @Platform(value = "windows", define = "CHARLS_STATIC 0x01"),
public class libijg {
    static {
        Loader.load();
    }

    /** enum ERROR_TYPE */
    public static final int NONE = 0, EC_MemoryExhausted = 1, EC_IllegalCall = 2, EJCode_IJG8_Decompression = 3,
                    EJ_Suspension = 4, EJ_UnsupportedColorConversion = 5, EJ_IJG8_FrameBufferTooSmall = 6;

    /** enum J_COLOR_SPACE */
    public static final int JCS_UNKNOWN = 0, /* error/unspecified */
                    JCS_GRAYSCALE = 1, /* monochrome */
                    JCS_RGB = 2, /* red/green/blue */
                    JCS_YCbCr = 3, /* Y/Cb/Cr (also known as YUV) */
                    JCS_CMYK = 4, /* C/M/Y/K */
                    JCS_YCCK = 5; /* Y/Cb/Cr/K */

    /** enum EP_Interpretation: constants for photometric interpretation */
    public static final int EPI_Unknown = 0, // unknown, undefined, invalid
                    // no element value available
                    EPI_Missing = 1,
                    // / monochrome 1
                    EPI_Monochrome1 = 2,
                    // / monochrome 2
                    EPI_Monochrome2 = 3,
                    // / palette color
                    EPI_PaletteColor = 4,
                    // / RGB color
                    EPI_RGB = 5,
                    // / HSV color (retired)
                    EPI_HSV = 6,
                    // / ARGB color (retired)
                    EPI_ARGB = 7,
                    // / CMYK color (retired)
                    EPI_CMYK = 8,
                    // / YCbCr full
                    EPI_YBR_Full = 9,
                    // / YCbCr full 4:2:2
                    EPI_YBR_Full_422 = 10,
                    // / YCbCr partial 4:2:2
                    EPI_YBR_Partial_422 = 11;

    /**
     * enum EJ_Mode: describes the different modes of operation of a JPEG codec
     */
    public static final int EJM_baseline = 0, // JPEG baseline
                    // JPEG extended sequential
                    EJM_sequential = 1,
                    // JPEG spectral selection
                    EJM_spectralSelection = 2,
                    // JPEG full progression
                    EJM_progressive = 3,
                    // JPEG lossless
                    EJM_lossless = 4;

    /**
     * enum E_CompressionColorSpaceConversion: describes how color space conversion should be handled during the
     * conversion of an uncompressed DICOM image to a JPEG-compressed image
     */
    public static final int ECC_lossyYCbCr = 0,
                    /**
                     * encode color images in YCbCr if lossy JPEG. If lossless JPEG, images are encoded as RGB unless
                     * the source image is YCbCr in which case no color conversion is performed.
                     */

                    /**
                     * encode color images in RGB unless the source image is YCbCr in which case no color conversion is
                     * performed.
                     */
                    ECC_lossyRGB = 1,

                    /**
                     * convert color images to monochrome before compressing
                     */
                    ECC_monochrome = 2;

    /**
     * enum E_SubSampling: describes the different types of component sub-sampling to be used with lossy image
     * compression.
     */
    public static final int ESS_444 = 0, // 4:4:4 sampling (no subsampling)
                    // 4:2:2 sampling (horizontal subsampling of chroma components)
                    ESS_422 = 1,
                    // 4:1:1 sampling (horizontal and vertical subsampling of chroma components)
                    ESS_411 = 2;

    public static class RETURN_MSG extends Pointer {
        static {
            Loader.load();
        }

        public RETURN_MSG() {
            allocate();
        }

        private native void allocate();

        @Cast("ERROR_TYPE")
        public native int code();

        public native RETURN_MSG code(@Cast("ERROR_TYPE") int code);

        @Cast("const char*")
        public native BytePointer msg();

        public native RETURN_MSG msg(@Cast("const char*") BytePointer msg);

    }

    public static class jpeg_decompress_struct extends Pointer {
        static {
            Loader.load();
        }

        public jpeg_decompress_struct() {
            allocate();
        }

        private native void allocate();

        public native int data_precision();

        public native jpeg_decompress_struct data_precision(int data_precision);

        @Cast("J_COLOR_SPACE")
        public native int jpeg_color_space();

        public native jpeg_decompress_struct jpeg_color_space(@Cast("J_COLOR_SPACE") int jpeg_color_space);

        @Cast("unsigned int")
        public native int image_width();

        public native jpeg_decompress_struct image_width(@Cast("unsigned int") int image_width);

        @Cast("unsigned int")
        public native int image_height();

        public native jpeg_decompress_struct image_height(@Cast("unsigned int") int image_width);

        public native int num_components();

        public native jpeg_decompress_struct num_components(int num_components);

        @Cast("unsigned int")
        public native int output_width();

        public native jpeg_decompress_struct output_width(@Cast("unsigned int") int output_width);

        @Cast("unsigned int")
        public native int output_height();

        public native jpeg_decompress_struct output_height(@Cast("unsigned int") int output_height);

        public native int output_components();

        public native jpeg_decompress_struct output_components(int output_components);
    }

    public static class DJDecompressIJG8Bit extends Pointer implements DecoderIJG {
        static {
            Loader.load();
        }

        public DJDecompressIJG8Bit() {
            allocate();
        }

        private native void allocate();

        @Override
        public native jpeg_decompress_struct getJpeg_DecompressStruct();

        @Override
        public native int getDecompressedColorModel();

        @Override
        @Cast("unsigned short")
        public native int bytesPerSample();

        @Override
        @ByVal
        public native RETURN_MSG init(boolean isYBR);

        @Override
        @ByVal
        public native RETURN_MSG readHeader(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize, boolean isSigned);

        @Override
        @ByVal
        public native RETURN_MSG decode(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize,
            @Cast("unsigned char*") ByteBuffer uncompressedFrameBuffer,
            @Cast("unsigned long") long uncompressedFrameBufferSize);
    }

    public static class DJDecompressIJG12Bit extends Pointer implements DecoderIJG {
        static {
            Loader.load();
        }

        public DJDecompressIJG12Bit() {
            allocate();
        }
        
        @Override
        public void close() {
            this.deallocate();
        }

        private native void allocate();

        @Override
        public native jpeg_decompress_struct getJpeg_DecompressStruct();

        @Override
        public native int getDecompressedColorModel();

        @Override
        @Cast("unsigned short")
        public native int bytesPerSample();

        @Override
        @ByVal
        public native RETURN_MSG init(boolean isYBR);

        @Override
        @ByVal
        public native RETURN_MSG readHeader(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize, boolean isSigned);

        @Override
        @ByVal
        public native RETURN_MSG decode(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize,
            @Cast("unsigned char*") ByteBuffer uncompressedFrameBuffer,
            @Cast("unsigned long") long uncompressedFrameBufferSize);
    }

    public static class DJDecompressIJG16Bit extends Pointer implements DecoderIJG {
        static {
            Loader.load();
        }

        public DJDecompressIJG16Bit() {
            allocate();
        }

        private native void allocate();

        @Override
        public native jpeg_decompress_struct getJpeg_DecompressStruct();

        @Override
        public native int getDecompressedColorModel();

        @Override
        @Cast("unsigned short")
        public native int bytesPerSample();

        @Override
        @ByVal
        public native RETURN_MSG init(boolean isYBR);

        @Override
        @ByVal
        public native RETURN_MSG readHeader(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize, boolean isSigned);

        @Override
        @ByVal
        public native RETURN_MSG decode(@Cast("unsigned char*") ByteBuffer compressedFrameBuffer,
            @Cast("unsigned long") long compressedFrameBufferSize,
            @Cast("unsigned char*") ByteBuffer uncompressedFrameBuffer,
            @Cast("unsigned long") long uncompressedFrameBufferSize);
    }

    /******************
     * IJG Compression
     *****************/

    /**
     * codec parameter for IJG codecs
     */
    // public static class DJCodecParameter extends Pointer {
    // static {
    // Loader.load();
    // }
    //
    // /**
    // * constructor.
    // *
    // * @param pCompressionCSConversion
    // * color conversion mode for compression
    // * @param pOptimizeHuffman
    // * perform huffman table optimization for 8 bits/pixel compression?
    // * @param pSmoothingFactor
    // * smoothing factor for image compression, 0..100
    // * @param pForcedBitDepth
    // * forced bit depth for image compression, 0 (auto) or 8/12/16
    // * @param pFragmentSize
    // * maximum fragment size (in kbytes) for compression, 0 for unlimited.
    // * @param pSampleFactors
    // * subsampling mode for color image compression
    // */
    // public DJCodecParameter(@Cast("E_CompressionColorSpaceConversion") int pCompressionCSConversion,
    // boolean pOptimizeHuffman, int pSmoothingFactor, int pForcedBitDepth,
    // @Cast("unsigned int") int pFragmentSize, @Cast("E_SubSampling") int pSampleFactors) {
    // allocate(pCompressionCSConversion, pOptimizeHuffman, pSmoothingFactor, pForcedBitDepth, pFragmentSize,
    // pSampleFactors);
    // }
    //
    // public DJCodecParameter(@Cast("E_CompressionColorSpaceConversion") int pCompressionCSConversion) {
    // allocate(pCompressionCSConversion);
    // }
    //
    // public DJCodecParameter(@ByRef DJCodecParameter arg) {
    // allocate(arg);
    // }
    //
    // private native void allocate(@Cast("E_CompressionColorSpaceConversion") int pCompressionCSConversion,
    // boolean pOptimizeHuffman, int pSmoothingFactor, int pForcedBitDepth,
    // @Cast("unsigned int") int pFragmentSize, @Cast("E_SubSampling") int pSampleFactors);
    //
    // private native void allocate(@Cast("E_CompressionColorSpaceConversion") int pCompressionCSConversion);
    //
    // private native void allocate(@ByRef DJCodecParameter arg);
    //
    // public native int getSmoothingFactor();
    //
    // }
    //
    // public static class DJCompressIJG8Bit extends Pointer implements EncoderIJG {
    // static {
    // Loader.load();
    // }
    //
    // public DJCompressIJG8Bit(@ByRef DJCodecParameter cp, @Cast("EJ_Mode") int mode,
    // @Cast("unsigned char") byte quality) {
    // allocate(cp, mode, quality);
    // }
    //
    // public DJCompressIJG8Bit(@ByRef DJCodecParameter cp, @Cast("EJ_Mode") int mode, int prediction, int ptrans) {
    // allocate(cp, mode, prediction, ptrans);
    // }
    //
    // private native void allocate(@ByRef DJCodecParameter cp, @Cast("EJ_Mode") int mode,
    // @Cast("unsigned char") byte quality);
    //
    // private native void allocate(@ByRef DJCodecParameter cp, @Cast("EJ_Mode") int mode, int prediction, int ptrans);
    //
    // /**
    // * single frame compression routine for 8-bit raw pixel data. May only be called if bytesPerSample() == 1.
    // *
    // * @param columns
    // * columns of frame
    // * @param rows
    // * rows of frame
    // * @param interpr
    // * photometric interpretation of input frame
    // * @param samplesPerPixel
    // * samples per pixel of input frame
    // * @param image_buffer
    // * pointer to frame buffer
    // * @param to
    // * compressed frame returned in this parameter upon success
    // * @param length
    // * length of compressed frame (in bytes) returned in this parameter upon success; length guaranteed
    // * to be always even.
    // * @return EC_Normal if successful, an error code otherwise.
    // */
    // @Override
    // public native @ByVal RETURN_MSG encode(@Cast("unsigned short") int columns, @Cast("unsigned short") int rows,
    // @Cast("EP_Interpretation") int interpr, @Cast("unsigned short") int samplesPerPixel,
    // @Cast("unsigned char*") ByteBuffer image_buffer, @Cast("unsigned char*") ByteBuffer to,
    // @Cast("unsigned int&") IntPointer length);
    //
    // /**
    // * returns the number of bytes per sample that will be expected when encoding.
    // */
    // @Override
    // @Cast("unsigned short")
    // public native int bytesPerSample();
    //
    // /**
    // * returns the number of bits per sample that will be expected when encoding.
    // */
    // @Override
    // @Cast("unsigned short")
    // public native int bitsPerSample();
    // }

    /*******************************************************************************************************************************************
     *
     * CHARLS LIB
     *
     *******************************************************************************************************************************************/

    /** enum interleavemode */
    public static final int ILV_NONE = 0, ILV_LINE = 1, ILV_SAMPLE = 2;

    /** enum JLS_ERROR */
    public static final int OK = 0;

    public static final String getErrorMessage(int error) {
        switch (error) {
            case 0:
                return "No error";
            case 1:
                return "Invalid JlsParameters";
            case 2:
                return "Parameter Value Not Supported";
            case 3:
                return "Uncompressed Buffer Too Small";
            case 4:
                return "Compressed Buffer Too Small";
            case 5:
                // This error is returned when the encoded bit stream contains a general structural problem.
                return "Invalid Compressed Data";
            case 6:
                return "Too Much Compressed Data";
            case 7:
                // This error is returned when the bit stream is encoded with an option that is not supported by this
                // implementation.
                return "Image Type Not Supported";
            case 8:
                return "Unsupported Bit Depth For Transform";
            case 9:
                return "Unsupported Color Transform";
            case 10:
                // This error is returned when an encoded frame is found that is not encoded with the JPEG-LS algorithm.
                return "Unsupported Encoding";
            case 11:
                // This error is returned when an unknown JPEG marker code is detected in the encoded bit stream.
                return "Unknown Jpeg Marker";
            case 12:
                // This error is returned when the algorithm expect a 0xFF code (indicates start of a JPEG marker) but
                // none was found.
                return "Missing Jpeg Marker Start";
            default:
                return "Unexpected error";
        }
    }

    public static class ByteStreamInfo extends Pointer {
        static {
            Loader.load();
        }

        public ByteStreamInfo() {
            allocate();
        }

        private native void allocate();

        @Cast("unsigned char*")
        private native ByteBuffer rawData();

        public native void rawData(@Cast("unsigned char*") ByteBuffer buf);

        @ByVal
        public native SizeTPointer count();

        public native void count(@ByVal SizeTPointer count);

    }

    @ByVal
    public static native ByteStreamInfo FromByteArray(ByteBuffer buf, @ByVal SizeTPointer count);

    @ByVal
    public static native ByteStreamInfo FromByteArray(byte[] buf, @ByVal SizeTPointer count);

    @ByVal
    public static native ByteStreamInfo FromByteArray(BytePointer buf, @ByVal SizeTPointer count);

    public static class JlsParameters extends Pointer {
        static {
            Loader.load();
        }

        public JlsParameters() {
            allocate();
        }

        private native void allocate();

        public native int width();

        public native JlsParameters width(int width);

        public native int height();

        public native JlsParameters height(int height);

        public native int bitspersample();

        public native JlsParameters bitspersample(int bitspersample);

        public native int bytesperline();

        public native JlsParameters bytesperline(int bytesperline);

        public native int components();

        public native JlsParameters components(int components);

        public native int allowedlossyerror();

        public native JlsParameters allowedlossyerror(int allowedlossyerror);

        @Cast("interleavemode")
        public native int ilv();

        public native JlsParameters ilv(@Cast("interleavemode") int ilv);

        public native int colorTransform();

        public native JlsParameters colorTransform(int colorTransform);

        public native char outputBgr();

        public native JlsParameters outputBgr(char outputBgr);

        // struct JlsCustomParameters custom;
        // struct JfifParameters jfif;
    }

    @Cast("JLS_ERROR")
    public static native int JpegLsEncodeStream(@ByVal ByteStreamInfo rawStream, SizeTPointer bytesWritten,
        @ByVal ByteStreamInfo inputStream, JlsParameters info);

    @Cast("JLS_ERROR")
    public static native int JpegLsDecodeStream(@ByVal ByteStreamInfo output, @ByVal ByteStreamInfo input,
        JlsParameters info);

    @Cast("JLS_ERROR")
    public static native int JpegLsReadHeaderStream(@ByVal ByteStreamInfo input, JlsParameters info);
}
