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
package org.weasis.openjpeg.internal;

import java.awt.Rectangle;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;

import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.image.jni.FileStreamSegment;
import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.openjpeg.J2kParameters;
import org.weasis.openjpeg.NativeJ2kImage;
import org.weasis.openjpeg.cpp.openjpeg;
import org.weasis.openjpeg.cpp.openjpeg.close_stream;
import org.weasis.openjpeg.cpp.openjpeg.error_handler;
import org.weasis.openjpeg.cpp.openjpeg.info_handler;
import org.weasis.openjpeg.cpp.openjpeg.j2kfile;
import org.weasis.openjpeg.cpp.openjpeg.opj_image;
import org.weasis.openjpeg.cpp.openjpeg.opj_image_comp;
import org.weasis.openjpeg.cpp.openjpeg.warning_handler;

public class OpenJpegCodec implements NativeCodec {
    public static final Logger LOGGER = LoggerFactory.getLogger(OpenJpegCodec.class);

    public static final int J2K_CFMT = 0;
    public static final int JP2_CFMT = 1;
    public static final int JPT_CFMT = 2;

    /** 1 mega of buffersize by default */
    public static final long OPJ_J2K_STREAM_CHUNK_SIZE = 0x100000;

    public OpenJpegCodec() {
    }

    @Override
    public String readHeader(NativeImage nImage) throws IOException {
        String msg = null;
        FileStreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            J2kParameters params = (J2kParameters) nImage.getImageParameters();

            Pointer l_stream = null;
            Pointer codec = null;
            openjpeg.opj_image image = null;
            try {
                MappedByteBuffer buffer = seg.getDirectByteBuffer(0);

                l_stream = openjpeg.opj_stream_create(0x10000, true); // 65536 bytes

                if (l_stream.isNull()) {
                    throw new IOException("Cannot initialize stream!");
                }

                j2kfile j2kFile = new j2kfile();
                j2kFile.data(buffer);
                j2kFile.curData(buffer);
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                j2kFile.count(size);
                openjpeg.opj_stream_set_user_data(l_stream, j2kFile, new close_stream());
                openjpeg.opj_stream_set_user_data_length(l_stream, buffer.limit());

                openjpeg.opj_stream_init_function(l_stream);

                codec = getCodec(params.getType());
                if (codec.isNull()) {
                    throw new IOException("No j2k decoder for this type: " + params.getType());
                }

                /* catch events using our callbacks and give a local context */
                openjpeg.opj_set_info_handler(codec, new info_handler(), null);
                openjpeg.opj_set_warning_handler(codec, new warning_handler(), null);
                openjpeg.opj_set_error_handler(codec, new error_handler(), null);

                /* setup the decoder decoding parameters using user parameters */
                /* Read the main header of the codestream and if necessary the JP2 boxes */
                openjpeg.opj_dparameters parameters = new openjpeg.opj_dparameters();
                openjpeg.opj_set_default_decoder_parameters(parameters);
                if (!openjpeg.opj_setup_decoder(codec, parameters)) {
                    throw new IOException("Failed to setup the decoder");
                }

                /* Read the main header of the codestream and if necessary the JP2 boxes */
                image = new openjpeg.opj_image();
                if (!openjpeg.opj_read_header(l_stream, codec, image)) {
                    throw new IOException("Failed to read the j2k header");
                }
                setParameters(nImage.getImageParameters(), image);
            } finally {
                if (l_stream != null) {
                    openjpeg.opj_stream_destroy(l_stream);
                    l_stream.deallocate();
                }
                if (codec != null) {
                    openjpeg.opj_destroy_codec(codec);
                    codec.deallocate();
                }
                if (image != null) {
                    openjpeg.opj_image_destroy(image);
                    image.deallocate();
                }
                // Do not close inChannel (comes from image input stream)
            }
        }
        return msg;
    }

    @Override
    public String decompress(NativeImage nImage, Rectangle region) throws IOException {
        String msg = null;
        FileStreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            ImageParameters params = nImage.getImageParameters();

            Pointer l_stream = null;
            Pointer codec = null;
            openjpeg.opj_image image = null;
            try {
                // When multiple fragments segments, aggregate them in the byteBuffer.
                ByteBuffer buffer = seg.getDirectByteBuffer(0, seg.getSegLength().length - 1);
                // TODO apply signed at DICOM level?
                // boolean signed = params.isSignedData();

                J2kParameters j2kparams = (J2kParameters) nImage.getImageParameters();
                l_stream = openjpeg.opj_stream_create(OPJ_J2K_STREAM_CHUNK_SIZE, true);

                if (l_stream.isNull()) {
                    throw new IOException("Cannot initialize stream!");
                }

                j2kfile j2kFile = new j2kfile();
                j2kFile.data(buffer);
                j2kFile.curData(buffer);
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                j2kFile.count(size);
                openjpeg.opj_stream_set_user_data(l_stream, j2kFile, new close_stream());
                openjpeg.opj_stream_set_user_data_length(l_stream, buffer.limit());

                openjpeg.opj_stream_init_function(l_stream);

                codec = getCodec(j2kparams.getType());
                if (codec.isNull()) {
                    throw new IOException("No j2k decoder for this type: " + j2kparams.getType());
                }

                /* catch events using our callbacks and give a local context */
                openjpeg.opj_set_info_handler(codec, new info_handler(), null);
                openjpeg.opj_set_warning_handler(codec, new warning_handler(), null);
                openjpeg.opj_set_error_handler(codec, new error_handler(), null);

                /* setup the decoder decoding parameters using user parameters */
                /* Read the main header of the codestream and if necessary the JP2 boxes */
                openjpeg.opj_dparameters parameters = new openjpeg.opj_dparameters();
                openjpeg.opj_set_default_decoder_parameters(parameters);
                parameters.decod_format(j2kparams.getType());
                parameters.cp_layer(0);
                parameters.cp_reduce(0);

                if (!openjpeg.opj_setup_decoder(codec, parameters)) {
                    throw new IOException("Failed to setup the decoder");
                }

                /* Read the main header of the codestream and if necessary the JP2 boxes */
                image = new openjpeg.opj_image();
                if (!openjpeg.opj_read_header(l_stream, codec, image)) {
                    throw new IOException("Failed to read the j2k header");
                }
                setParameters(nImage.getImageParameters(), image);
                int bps = params.getBitsPerSample();
                if (bps < 1 || bps > 16) {
                    throw new IllegalArgumentException("Invalid bit per sample: " + bps);
                }

                Rectangle area = region;
                // Rectangle area = new Rectangle();
                // area.width = j2kparams.getWidth();
                // area.height = j2kparams.getHeight();

                /* Do not decode the entire image if are is not null */
                if (area != null && !openjpeg.opj_set_decode_area(codec, image, area.x, area.y, area.x + area.width,
                    area.y + area.height)) {
                    throw new IOException("Failed to set the decoded area!");
                }

                // TODO need to be tested

                // if (parameters.nb_tile_to_decode() > 0) {
                // ByteBuffer outBuf = null;
                // BoolPointer l_go_on = new BoolPointer(1);
                // l_go_on.put(true);
                // IntPointer l_data_size = new IntPointer(1);
                // IntPointer l_tile_index = new IntPointer(1);
                // IntPointer l_nb_comps = new IntPointer(1);
                // l_nb_comps.put(0);
                // IntPointer l_tile_x0 = new IntPointer(1);
                // IntPointer l_tile_y0 = new IntPointer(1);
                // IntPointer l_tile_x1 = new IntPointer(1);
                // IntPointer l_tile_y1 = new IntPointer(1);
                //
                // while (l_go_on.get()) {
                // if (!openjpeg.opj_read_tile_header(codec, l_stream, l_tile_index, l_data_size, l_tile_x0,
                // l_tile_y0, l_tile_x1, l_tile_y1, l_nb_comps, l_go_on)) {
                // // throw new IOException("Failed to read tile header: " + l_tile_index.get() + "!");
                // }
                //
                // if (l_go_on.get()) {
                // if (outBuf == null || l_data_size.get() > outBuf.capacity()) {
                // outBuf = ByteBuffer.allocateDirect(l_data_size.get());
                // }
                //
                // if (!openjpeg.opj_decode_tile_data(codec, l_tile_index.get(), outBuf, l_data_size.get(),
                // l_stream)) {
                // throw new IOException("Failed to decode tile " + l_tile_index.get() + "!");
                // }
                // LOGGER.debug("tile {} is decoded", l_tile_index.get());
                // }
                // }
                // } else {
                /* Get the decoded image */
                if (!(openjpeg.opj_decode(codec, l_stream, image) && openjpeg.opj_end_decompress(codec, l_stream))) {
                    throw new IOException("Failed to set the decoded image!");
                }
                // }

                // if (tile_index >= 0) {
                // /* It is just here to illustrate how to use the resolution after set parameters */
                // /*
                // * if (!openjpeg.opj_set_decoded_resolution_factor(l_codec, 5)) {
                // * openjpeg.opj_stream_destroy_v3(l_stream); throw new
                // * IOException("Failed to set the resolution factor tile!"); }
                // */
                //
                // if (!openjpeg.opj_get_decoded_tile(codec, l_stream, image, tile_index)) {
                // throw new IOException("Failed to decode tile " + tile_index + "!");
                // }
                // LOGGER.debug("tile {} is decoded", tile_index);
                // } else {
                //
                // /* Get the decoded image */
                // if (!(openjpeg.opj_decode(codec, l_stream, image)
                // && openjpeg.opj_end_decompress(codec, l_stream))) {
                // throw new IOException("Failed to set the decoded image!");
                // }
                // }

                /* Close the byte stream */
                openjpeg.opj_stream_destroy(l_stream);
                l_stream.deallocate();
                l_stream = null;

                int bands = image.numcomps();
                if (bands > 0) {

                    if (image.color_space() == openjpeg.OPJ_CLRSPC_SYCC) {
                        openjpeg.color_sycc_to_rgb(image);
                    }

                    // if(image.color_space() != openjpeg.OPJ_CLRSPC_SYCC
                    // && bands == 3 && image->comps[0].dx == image->comps[0].dy
                    // && image->comps[1].dx != 1 ) {
                    // image.color_space(openjpeg.OPJ_CLRSPC_SYCC);
                    // } else if (bands <= 2) {
                    // image.color_space(openjpeg.OPJ_CLRSPC_GRAY);
                    // }

                    // if(image->icc_profile_buf) {
                    // #if defined(OPJ_HAVE_LIBLCMS1) || defined(OPJ_HAVE_LIBLCMS2)
                    // color_apply_icc_profile(image); /* FIXME */
                    // #endif
                    // free(image->icc_profile_buf);
                    // image->icc_profile_buf = NULL; image->icc_profile_len = 0;
                    // }

                    // Build outputStream here and transform to an array
                    // Convert band interleaved from openjpeg to pixel interleaved (to display)
                    int imgSize = params.getWidth() * params.getHeight();
                    int length = imgSize * bands;
                    Object array = null;
                    if (bps > 0 && bps <= 16) {
                        array = bps <= 8 ? new byte[length] : new short[length];
                        opj_image_comp cp = image.comps().position(0);
                        int dx = cp.dx();
                        int dy = cp.dy();
                        for (int i = 0; i < bands; i++) {
                            if (i > 0) {
                                cp = image.comps().position(i);
                                if (cp.prec() != bps) {
                                    LOGGER.error(
                                        "Cannot read band {} because bits per sample = {}, which is different from the first band = {}.",
                                        new Object[] { i, cp.prec(), bps });
                                    continue;
                                }
                                if (cp.dx() != dx || cp.dy() != dy) {
                                    LOGGER.error(
                                        "Cannot read band {} because separation of a sample is different from the first band.",
                                        i);
                                    continue;
                                }
                            }

                            // TODO convert band to pixel interleaved, store in temporary file when size >= 1024
                            IntPointer intBuf = cp.data();
                            if (imgSize <= cp.w() * cp.h()) {
                                if (bps <= 8) {
                                    byte[] data = (byte[]) array;
                                    for (int k = 0; k < imgSize; k++) {
                                        data[k * bands + i] = (byte) intBuf.get(k);
                                    }
                                } else {
                                    short[] data = (short[]) array;

                                    // boolean signed = params.isSignedData();
                                    // if (signed) {
                                    // int singedOffset = (1 << bps) / 2;
                                    // for (int k = 0; k < imgSize; k++) {
                                    // int val = intBuf.get(k);
                                    // data[k * bands + i] =
                                    // (short) (val < singedOffset ? val + singedOffset : val - singedOffset);
                                    // }
                                    // } else {
                                    for (int k = 0; k < imgSize; k++) {
                                        data[k * bands + i] = (short) intBuf.get(k);
                                    }
                                    // }
                                }
                            }
                        }
                    }

                    if (array != null) {
                        nImage.fillOutputBuffer(array, 0, length);
                    }
                }
            } finally {
                if (l_stream != null) {
                    openjpeg.opj_stream_destroy(l_stream);
                    l_stream.deallocate();
                }
                if (codec != null) {
                    openjpeg.opj_destroy_codec(codec);
                    codec.deallocate();
                }
                if (image != null) {
                    openjpeg.opj_image_destroy(image);
                    image.deallocate();
                }
                // Do not close inChannel (comes from image input stream)
            }
        }
        return msg;
    }

    @Override
    public String compress(NativeImage nImage, ImageOutputStream ouputStream, Rectangle region) throws IOException {
        return null;
    }

    @Override
    public void dispose() {
    }

    private Pointer getCodec(int type) {
        switch (type) {
            case J2K_CFMT: /* JPEG-2000 codestream */
                return openjpeg.opj_create_decompress(openjpeg.OPJ_CODEC_J2K);
            case JP2_CFMT: /* JPEG 2000 compressed image data */
                return openjpeg.opj_create_decompress(openjpeg.OPJ_CODEC_JP2);
            default:
                return null;
        }
    }

    private static void setParameters(ImageParameters params, opj_image image) {
        if (params != null && image != null) {
            int bands = image.numcomps();
            if (bands > 0) {
                opj_image_comp cp = image.comps().position(0);
                params.setWidth(cp.w());
                params.setHeight(cp.h());
                // TODO change this once tile reading has been implemented.
                params.setTileWidth(params.getWidth());
                params.setTileHeight(params.getHeight());
                params.setBitsPerSample(cp.prec());
                params.setSamplesPerPixel(bands);
                params.setBytesPerLine(
                    params.getWidth() * params.getSamplesPerPixel() * ((params.getBitsPerSample() + 7) / 8));
                params.setSignedData(cp.sgnd() != 0);
                // params.setAllowedLossyError(p.allowedlossyerror());
            }
        }
    }

    @Override
    public NativeImage buildImage(ImageInputStream iis) throws IOException {
        int type = getType(iis);
        NativeJ2kImage img = new NativeJ2kImage();

        J2kParameters params = img.getJ2kParameters();
        params.setType(type);
        // params.setWidth(sof.getSamplesPerLine());
        // params.setHeight(sof.getLines());
        // params.setBitsPerSample(sof.getSamplePrecision());
        // params.setSamplesPerPixel(sof.getComponents());
        // params.setBytesPerLine(params.getWidth() * params.getSamplesPerPixel() * ((params.getBitsPerSample() + 7) /
        // 8));

        return img;
    }

    public static int getType(ImageInputStream iis) throws IOException {
        iis.mark();
        try {
            byte[] b = new byte[12];
            iis.readFully(b);

            // TODO J2K_CFMT (JPIP)

            // J2K_CODESTREAM_MAGIC
            if ((b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0x4F && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x51) {
                return J2K_CFMT;
            }

            // JP2_MAGIC
            if ((b[0] & 0xFF) == 0x0D && (b[1] & 0xFF) == 0x0A && (b[2] & 0xFF) == 0x87 && (b[3] & 0xFF) == 0x0A) {
                return JP2_CFMT;
            }

            // JP2_RFC3745_MAGIC
            if (b[0] == 0 && b[1] == 0 && b[2] == 0 && (b[3] & 0xFF) == 0x0C && (b[4] & 0xff) == 0x6A
                && (b[5] & 0xFF) == 0x50 && (b[6] & 0xFF) == 0x20 && (b[7] & 0xFF) == 0x20 && (b[8] & 0xFF) == 0x0D
                && (b[9] & 0xFF) == 0x0A && (b[10] & 0xFF) == 0x87 && (b[11] & 0xFF) == 0x0A) {
                return JP2_CFMT;
            }
            return -1;
        } finally {
            iis.reset();
        }
    }

}
