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
package org.weasis.openjpeg.cpp;

import java.nio.ByteBuffer;

import org.bytedeco.javacpp.BoolPointer;
import org.bytedeco.javacpp.FunctionPointer;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Loader;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.bytedeco.javacpp.annotation.ByPtr;
import org.bytedeco.javacpp.annotation.ByPtrPtr;
import org.bytedeco.javacpp.annotation.ByVal;
import org.bytedeco.javacpp.annotation.Cast;
import org.bytedeco.javacpp.annotation.Platform;
import org.bytedeco.javacpp.annotation.Properties;
import org.weasis.openjpeg.internal.OpenJpegCodec;

@Properties({ @Platform(include = { "openjpegutils.h" }) })
public class openjpeg {

    static {
        Loader.load();
    }

    /** enum COLOR_SPACE */
    public static final int OPJ_CLRSPC_UNKNOWN = -1, // not supported by the library
                    OPJ_CLRSPC_UNSPECIFIED = 0, // not specified in the codestream
                    OPJ_CLRSPC_SRGB = 1, // sRGB
                    OPJ_CLRSPC_GRAY = 2, // grayscale
                    OPJ_CLRSPC_SYCC = 3, // YUV
                    OPJ_CLRSPC_EYCC = 4, // e-YCC
                    OPJ_CLRSPC_CMYK = 5; // CMYK

    /** enum CODEC_FORMAT */
    public static final int OPJ_CODEC_UNKNOWN = -1, // place-holder
                    OPJ_CODEC_J2K = 0, // JPEG-2000 codestream : read/write
                    OPJ_CODEC_JPT = 1, // JPT-stream (JPEG 2000, JPIP) : read only
                    OPJ_CODEC_JP2 = 2, // JPEG-2000 file format : read/write
                    OPJ_CODEC_JPP = 3, // JPP-stream (JPEG 2000, JPIP) : to be coded
                    OPJ_CODEC_JPX = 4; // JPX file format (JPEG 2000 Part-2) : to be coded

    public static class SourceData extends Pointer {
        static {
            Loader.load();
        }

        public SourceData() {
            allocate();
        }

        private native void allocate();

        @Cast("unsigned char*")
        private native ByteBuffer data();

        public native void data(@Cast("unsigned char*") ByteBuffer buf);

        public native @ByVal SizeTPointer size();

        public native void size(@ByVal SizeTPointer size);

        public native @ByVal SizeTPointer offset();

        public native void offset(@ByVal SizeTPointer offset);

    }

    /**
     * Defines a single image component
     */
    public static class opj_image_comp extends Pointer {
        static {
            Loader.load();
        }

        public opj_image_comp() {
            allocate();
        }

        private native void allocate();

        /** XRsiz: horizontal separation of a sample of ith component with respect to the reference grid */
        @Cast("unsigned int")
        public native int dx();

        public native opj_image_comp dx(@Cast("unsigned int") int dx);

        /** YRsiz: vertical separation of a sample of ith component with respect to the reference grid */
        @Cast("unsigned int")
        public native int dy();

        public native opj_image_comp dy(@Cast("unsigned int") int dy);

        /** data width */
        @Cast("unsigned int")
        public native int w();

        public native opj_image_comp w(@Cast("unsigned int") int w);

        /** data height */
        @Cast("unsigned int")
        public native int h();

        public native opj_image_comp h(@Cast("unsigned int") int h);

        /** x component offset compared to the whole image */
        @Cast("unsigned int")
        public native int x0();

        public native opj_image_comp x0(@Cast("unsigned int") int x0);

        /** y component offset compared to the whole image */
        @Cast("unsigned int")
        public native int y0();

        public native opj_image_comp y0(@Cast("unsigned int") int y0);

        /** precision */
        @Cast("unsigned int")
        public native int prec();

        public native opj_image_comp prec(@Cast("unsigned int") int prec);

        /** image depth in bits */
        @Cast("unsigned int")
        public native int bpp();

        public native opj_image_comp bpp(@Cast("unsigned int") int bpp);

        /** signed (1) / unsigned (0) */
        @Cast("unsigned int")
        public native int sgnd();

        public native opj_image_comp sgnd(@Cast("unsigned int") int sgnd);

        /** number of decoded resolution */
        @Cast("unsigned int")
        public native int resno_decoded();

        public native opj_image_comp resno_decoded(@Cast("unsigned int") int resno_decoded);

        /** number of division by 2 of the out image compared to the original size of image */
        @Cast("unsigned int")
        public native int factor();

        public native opj_image_comp factor(@Cast("unsigned int") int factor);

        /** image component data */
        public native IntPointer data();

        public native opj_image_comp data(IntPointer data);
    }

    /**
     * Defines image data and characteristics
     */
    public static class opj_image extends Pointer {
        static {
            Loader.load();
        }

        public opj_image() {
            allocate();
        }

        private native void allocate();

        /** XOsiz: horizontal offset from the origin of the reference grid to the left side of the image area */
        @Cast("unsigned int")
        public native int x0();

        public native opj_image x0(@Cast("unsigned int") int x0);

        /** YOsiz: vertical offset from the origin of the reference grid to the top side of the image area */
        @Cast("unsigned int")
        public native int y0();

        public native opj_image y0(@Cast("unsigned int") int y0);

        /** Xsiz: width of the reference grid */
        @Cast("unsigned int")
        public native int x1();

        public native opj_image x1(@Cast("unsigned int") int x1);

        /** Ysiz: height of the reference grid */
        @Cast("unsigned int")
        public native int y1();

        public native opj_image y1(@Cast("unsigned int") int y1);

        /** number of components in the image */
        @Cast("unsigned int")
        public native int numcomps();

        public native opj_image numcomps(@Cast("unsigned int") int numcomps);

        /** color space: sRGB, Greyscale or YUV */
        public native @Cast("COLOR_SPACE") int color_space();

        public native opj_image color_space(@Cast("COLOR_SPACE") int color_space);

        /** image components */
        public native opj_image_comp comps();

        public native opj_image comps(opj_image_comp comps);

        /** 'restricted' ICC profile */
        public native @Cast("unsigned char*") ByteBuffer icc_profile_buf();

        public native opj_image icc_profile_buf(@Cast("unsigned char*") ByteBuffer icc_profile_buf);

        /** size of ICC profile */
        @Cast("unsigned int")
        public native int icc_profile_len();

        public native opj_image icc_profile_len(@Cast("unsigned int") int icc_profile_len);
    }

    public static class opj_dparameters extends Pointer {
        static {
            Loader.load();
        }

        public opj_dparameters() {
            allocate();
        }

        private native void allocate();

        /**
         * Set the number of highest resolution levels to be discarded. The image resolution is effectively divided by 2
         * to the power of the number of discarded levels. The reduce factor is limited by the smallest total number of
         * decomposition levels among tiles. if != 0, then original dimension divided by 2^(reduce); if == 0 or not
         * used, image is decoded to the full resolution
         */
        @Cast("unsigned int")
        public native int cp_reduce();

        public native opj_dparameters cp_reduce(@Cast("unsigned int") int cp_reduce);

        /**
         * Set the maximum number of quality layers to decode. If there are less quality layers than the specified
         * number, all the quality layers are decoded. if != 0, then only the first "layer" layers are decoded; if == 0
         * or not used, all the quality layers are decoded
         */
        @Cast("unsigned int")
        public native int cp_layer();

        public native opj_dparameters cp_layer(@Cast("unsigned int") int cp_layer);

        /** input file format 0: J2K, 1: JP2, 2: JPT */
        public native int decod_format();

        public native opj_dparameters decod_format(int decod_format);

        /** Tile index */
        @Cast("unsigned int")
        public native int tile_index();

        public native opj_dparameters tile_index(@Cast("unsigned int") int tile_index);

        /** Number of tile to decode */
        @Cast("unsigned int")
        public native int nb_tile_to_decode();

        public native opj_dparameters nb_tile_to_decode(@Cast("unsigned int") int nb_tile_to_decode);
    }

    public static class info_handler extends FunctionPointer {
        static {
            Loader.load();
        }

        public info_handler() {
            allocate();
        }

        private native void allocate();

        public void call(@Cast("const char*") String msg, @Cast("void*") Pointer client_data) throws Exception {
            if (msg != null) {
                OpenJpegCodec.LOGGER.debug("{}", msg.replace("\n", "").replace("\r", ""));
            }
        }
    }

    public static class warning_handler extends FunctionPointer {
        static {
            Loader.load();
        }

        public warning_handler() {
            allocate();
        }

        private native void allocate();

        public void call(@Cast("const char*") String msg, @Cast("void*") Pointer client_data) throws Exception {
            if (msg != null) {
                OpenJpegCodec.LOGGER.warn("{}", msg.replace("\n", "").replace("\r", ""));
            }
        }
    }

    public static class error_handler extends FunctionPointer {
        static {
            Loader.load();
        }

        public error_handler() {
            allocate();
        }

        private native void allocate();

        public void call(@Cast("const char*") String msg, @Cast("void*") Pointer client_data) throws Exception {
            if (msg != null) {
                OpenJpegCodec.LOGGER.error("{}", msg.replace("\n", "").replace("\r", ""));
            }
        }
    }

    public static class close_stream extends FunctionPointer {
        static {
            Loader.load();
        }

        public close_stream() {
            allocate();
        }

        private native void allocate();

        public void call(@Cast("void*") Pointer client_data) throws Exception {
            // Do nothing. Handle in Java code.
        }
    }

    /*
     * ==========================================================
     *
     * image functions definitions
     *
     * ==========================================================
     */

    /**
     * Create an image
     *
     * @param numcmpts
     *            number of components
     * @param cmptparms
     *            components parameters
     * @param clrspc
     *            image color space
     * @return returns a new image structure if successful, returns NULL otherwise
     */
    // public static native @ByPtr opj_image opj_image_create(@Cast("unsigned int") int numcmpts, opj_image_cmptparm_t
    // *cmptparms, OPJ_COLOR_SPACE clrspc);

    /**
     * Deallocate any resources associated with an image
     *
     * @param image
     *            image to be destroyed
     */
    public static native void opj_image_destroy(@ByPtr opj_image image);

    /**
     * Creates an image without allocating memory for the image (used in the new version of the library).
     *
     * @param numcmpts
     *            the number of components
     * @param cmptparms
     *            the components parameters
     * @param clrspc
     *            the image color space
     *
     * @return a new image structure if successful, NULL otherwise.
     */
    // public static native @ByPtr opj_image opj_image_tile_create(@Cast("unsigned int") int numcmpts,
    // opj_image_cmptparm_t *cmptparms, OPJ_COLOR_SPACE clrspc);

    /*
     *
     *
     * /* ==========================================================
     *
     * codec functions definitions
     *
     * ==========================================================
     */

    /**
     * Creates a J2K/JP2 decompression structure
     *
     * @param format
     *            Decoder to select
     *
     * @return Returns a handle to a decompressor if successful, returns NULL otherwise
     */
    public static native @Cast("void**") Pointer opj_create_decompress(@Cast("OPJ_CODEC_FORMAT") int format);

    /**
     * Destroy a decompressor handle
     *
     * @param p_codec
     *            decompressor handle to destroy
     */
    public static native void opj_destroy_codec(@Cast("void**") Pointer p_codec);

    /**
     * Read after the codestream if necessary
     *
     * @param p_codec
     *            the JPEG2000 codec to read.
     * @param p_stream
     *            the JPEG2000 stream.
     */
    public static native @Cast("OPJ_BOOL") boolean opj_end_decompress(@Cast("void**") Pointer p_codec,
        @Cast("void**") Pointer p_stream);

    /**
     * Set decoding parameters to default values
     *
     * @param parameters
     *            Decompression parameters
     */
    public static native void opj_set_default_decoder_parameters(opj_dparameters parameters);

    /**
     * Setup the decoder with decompression parameters provided by the user and with the message handler provided by the
     * user.
     *
     * @param p_codec
     *            decompressor handler
     * @param parameters
     *            decompression parameters
     *
     * @return true if the decoder is correctly set
     */
    public static native @Cast("OPJ_BOOL") boolean opj_setup_decoder(@Cast("void**") Pointer p_codec,
        opj_dparameters parameters);

    /**
     * Decodes an image header.
     *
     * @param p_stream
     *            the jpeg2000 stream.
     * @param p_codec
     *            the jpeg2000 codec to read.
     * @param p_image
     *            the image structure initialized with the characteristics of encoded image.
     *
     * @return true if the main header of the codestream and the JP2 header is correctly read.
     */
    public static native @Cast("OPJ_BOOL") boolean opj_read_header(@Cast("void**") Pointer p_stream,
        @Cast("void**") Pointer p_codec, @ByPtrPtr opj_image p_image);

    /**
     * Sets the given area to be decoded. This function should be called right after opj_read_header and before any tile
     * header reading.
     *
     * @param p_codec
     *            the jpeg2000 codec.
     * @param p_image
     *            the decoded image previously set by opj_read_header
     * @param p_start_x
     *            the left position of the rectangle to decode (in image coordinates).
     * @param p_end_x
     *            the right position of the rectangle to decode (in image coordinates).
     * @param p_start_y
     *            the up position of the rectangle to decode (in image coordinates).
     * @param p_end_y
     *            the bottom position of the rectangle to decode (in image coordinates).
     *
     * @return true if the area could be set.
     */
    public static native @Cast("OPJ_BOOL") boolean opj_set_decode_area(@Cast("void**") Pointer p_codec,
        @ByPtr opj_image p_image, @Cast("unsigned int") int p_start_x, @Cast("unsigned int") int p_start_y,
        @Cast("unsigned int") int p_end_x, @Cast("unsigned int") int p_end_y);

    /**
     * Decode an image from a JPEG-2000 codestream
     *
     * @param p_decompressor
     *            decompressor handle
     * @param p_stream
     *            Input buffer stream
     * @param p_image
     *            the decoded image
     * @return true if success, otherwise false
     */
    public static native @Cast("OPJ_BOOL") boolean opj_decode(@Cast("void**") Pointer p_decompressor,
        @Cast("void**") Pointer p_stream, @ByPtr opj_image p_image);

    /**
     * Get the decoded tile from the codec
     *
     * @param p_codec
     *            the jpeg2000 codec.
     * @param p_stream
     *            input streamm
     * @param p_image
     *            output image
     * @param tile_index
     *            index of the tile which will be decode
     *
     * @return true if success, otherwise false
     */
    public static native @Cast("OPJ_BOOL") boolean opj_get_decoded_tile(@Cast("void**") Pointer p_codec,
        @Cast("void**") Pointer p_stream, @ByPtr opj_image p_image, @Cast("unsigned int") int tile_index);

    /**
     * Set the resolution factor of the decoded image
     *
     * @param p_codec
     *            the jpeg2000 codec.
     * @param res_factor
     *            resolution factor to set
     *
     * @return true if success, otherwise false
     */
    public static native @Cast("OPJ_BOOL") boolean opj_set_decoded_resolution_factor(@Cast("void**") Pointer p_codec,
        @Cast("unsigned int") int res_factor);

    /**
     * Reads a tile header. This function is compulsory and allows one to know the size of the tile that will be
     * decoded. The user may need to refer to the image got by opj_read_header to understand the size being taken by the
     * tile.
     *
     * @param p_codec
     *            the jpeg2000 codec.
     * @param p_tile_index
     *            pointer to a value that will hold the index of the tile being decoded, in case of success.
     * @param p_data_size
     *            pointer to a value that will hold the maximum size of the decoded data, in case of success. In case of
     *            truncated codestreams, the actual number of bytes decoded may be lower. The computation of the size is
     *            the same as depicted in opj_write_tile.
     * @param p_tile_x0
     *            pointer to a value that will hold the x0 pos of the tile (in the image).
     * @param p_tile_y0
     *            pointer to a value that will hold the y0 pos of the tile (in the image).
     * @param p_tile_x1
     *            pointer to a value that will hold the x1 pos of the tile (in the image).
     * @param p_tile_y1
     *            pointer to a value that will hold the y1 pos of the tile (in the image).
     * @param p_nb_comps
     *            pointer to a value that will hold the number of components in the tile.
     * @param p_should_go_on
     *            pointer to a boolean that will hold the fact that the decoding should go on. In case the codestream is
     *            over at the time of the call, the value will be set to false. The user should then stop the decoding.
     * @param p_stream
     *            the stream to decode.
     * @return true if the tile header could be decoded. In case the decoding should end, the returned value is still
     *         true. returning false may be the result of a shortage of memory or an internal error.
     */
    public static native @Cast("OPJ_BOOL") boolean opj_read_tile_header(@Cast("void**") Pointer p_codec,
        @Cast("void**") Pointer p_stream, @Cast("unsigned int*") IntPointer p_tile_index,
        @Cast("unsigned int*") IntPointer p_data_size, IntPointer p_tile_x0, IntPointer p_tile_y0, IntPointer p_tile_x1,
        IntPointer p_tile_y1, @Cast("unsigned int*") IntPointer p_nb_comps,
        @Cast("OPJ_BOOL*") BoolPointer p_should_go_on);

    /**
     * Reads a tile data. This function is compulsory and allows one to decode tile data. opj_read_tile_header should be
     * called before. The user may need to refer to the image got by opj_read_header to understand the size being taken
     * by the tile.
     *
     * @param p_codec
     *            the jpeg2000 codec.
     * @param p_tile_index
     *            the index of the tile being decoded, this should be the value set by opj_read_tile_header.
     * @param p_data
     *            pointer to a memory block that will hold the decoded data.
     * @param p_data_size
     *            size of p_data. p_data_size should be bigger or equal to the value set by opj_read_tile_header.
     * @param p_stream
     *            the stream to decode.
     *
     * @return true if the data could be decoded.
     */
    public static native @Cast("OPJ_BOOL") boolean opj_decode_tile_data(@Cast("void**") Pointer p_codec,
        @Cast("unsigned int") int p_tile_index, @Cast("unsigned char*") ByteBuffer p_data,
        @Cast("unsigned int") int p_data_size, @Cast("void**") Pointer p_stream);

    /*
     * ==========================================================
     *
     * stream functions definitions
     *
     * ==========================================================
     */

    /**
     * Creates an abstract stream. This function does nothing except allocating memory and initializing the abstract
     * stream.
     *
     * @param p_is_input
     *            if set to true then the stream will be an input stream, an output stream else.
     *
     * @return a stream object.
     */
    public static native @Cast("void**") Pointer opj_stream_default_create(@Cast("OPJ_BOOL") boolean p_is_input);

    /**
     * Creates an abstract stream. This function does nothing except allocating memory and initializing the abstract
     * stream.
     *
     * @param p_buffer_size
     *            FIXME DOC
     * @param p_is_input
     *            if set to true then the stream will be an input stream, an output stream else.
     *
     * @return a stream object.
     */
    public static native @Cast("void**") Pointer opj_stream_create(@Cast("OPJ_SIZE_T") long p_buffer_size,
        @Cast("OPJ_BOOL") boolean p_is_input);

    public static native @Cast("void**") Pointer opj_stream_create_memory_stream(SourceData p_mem,
        @Cast("OPJ_SIZE_T") long p_size, @Cast("OPJ_BOOL") boolean p_is_read_stream);

    /**
     * Destroys a stream created by opj_create_stream. This function does NOT close the abstract stream. If needed the
     * user must close its own implementation of the stream.
     *
     * @param p_stream
     *            the stream to destroy.
     */

    public static native void opj_stream_destroy(@Cast("void**") Pointer p_stream);

    /**
     * Sets the given data to be used as a user data for the stream.
     *
     * @param p_stream
     *            the stream to modify
     * @param p_data
     *            the data to set.
     * @param p_function
     *            the function to free p_data when opj_stream_destroy() is called.
     */
    public static native void opj_stream_set_user_data(@Cast("void**") Pointer p_stream, SourceData p_data,
        close_stream close_function);

    /**
     * Sets the length of the user data for the stream.
     *
     * @param p_stream
     *            the stream to modify
     * @param data_length
     *            length of the user_data.
     */
    public static native void opj_stream_set_user_data_length(@Cast("void**") Pointer p_stream,
        @Cast("unsigned long") long data_length);

    // public static native void color_sycc_to_rgb(@ByPtr opj_image p_image);
    //
    // public static native void color_apply_icc_profile(@ByPtr opj_image p_image);
    //
    // public static native void color_cielab_to_rgb(@ByPtr opj_image p_image);
    //
    // public static native void color_cmyk_to_rgb(@ByPtr opj_image p_image);
    //
    // public static native void color_esycc_to_rgb(@ByPtr opj_image p_image);

    /*
     * ==========================================================
     *
     * event manager functions definitions
     *
     * ==========================================================
     */
    /**
     * Set the info handler use by openjpeg.
     *
     * @param p_codec
     *            the codec previously initialise
     * @param p_callback
     *            the callback function which will be used
     * @param p_user_data
     *            client object where will be returned the message
     */
    public static native @Cast("OPJ_BOOL") boolean opj_set_info_handler(@Cast("void**") Pointer p_codec,
        info_handler p_callback, @Cast("void*") Pointer p_user_data);

    /**
     * Set the warning handler use by openjpeg.
     *
     * @param p_codec
     *            the codec previously initialise
     * @param p_callback
     *            the callback function which will be used
     * @param p_user_data
     *            client object where will be returned the message
     */
    public static native @Cast("OPJ_BOOL") boolean opj_set_warning_handler(@Cast("void**") Pointer p_codec,
        warning_handler p_callback, @Cast("void*") Pointer p_user_data);

    /**
     * Set the error handler use by openjpeg.
     *
     * @param p_codec
     *            the codec previously initialise
     * @param p_callback
     *            the callback function which will be used
     * @param p_user_data
     *            client object where will be returned the message
     */
    public static native @Cast("OPJ_BOOL") boolean opj_set_error_handler(@Cast("void**") Pointer p_codec,
        error_handler p_callback, @Cast("void*") Pointer p_user_data);

}
