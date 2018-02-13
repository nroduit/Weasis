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
package org.weasis.openjpeg.internal;

import java.awt.Rectangle;
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
import org.bytedeco.javacpp.Pointer;
import org.bytedeco.javacpp.SizeTPointer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.image.jni.ImageParameters;
import org.weasis.image.jni.NativeCodec;
import org.weasis.image.jni.NativeImage;
import org.weasis.image.jni.StreamSegment;
import org.weasis.openjpeg.J2kParameters;
import org.weasis.openjpeg.NativeJ2kImage;
import org.weasis.openjpeg.cpp.openjpeg;
import org.weasis.openjpeg.cpp.openjpeg.SourceData;
import org.weasis.openjpeg.cpp.openjpeg.error_handler;
import org.weasis.openjpeg.cpp.openjpeg.info_handler;
import org.weasis.openjpeg.cpp.openjpeg.opj_image;
import org.weasis.openjpeg.cpp.openjpeg.opj_image_comp;
import org.weasis.openjpeg.cpp.openjpeg.warning_handler;

public class OpenJpegCodec implements NativeCodec {
    public static final Logger LOGGER = LoggerFactory.getLogger(OpenJpegCodec.class);

    public static final int J2K_CFMT = 0;
    public static final int JP2_CFMT = 1;
    public static final int JPT_CFMT = 2;

    static final info_handler infoHandler = new info_handler();
    static final warning_handler warningHandler = new warning_handler();
    static final error_handler errorHandler = new error_handler();

    /** 1 mega of buffersize by default */
    public static final long OPJ_J2K_STREAM_CHUNK_SIZE = 0x100000;

    @Override
    public String readHeader(NativeImage nImage) throws IOException {
        String msg = null;
        StreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            J2kParameters params = (J2kParameters) nImage.getImageParameters();

            Pointer lstream = null;
            Pointer codec = null;
            openjpeg.opj_image image = null;
            try {
                ByteBuffer buffer = seg.getDirectByteBuffer(0);

                SourceData j2kFile = new SourceData();
                j2kFile.data(buffer);
                SizeTPointer size = new SizeTPointer(1);
                size.put(buffer.limit());
                j2kFile.size(size);

                lstream = openjpeg.opj_stream_create_memory_stream(j2kFile, 0x10000, true); // 65536 bytes
                if (lstream.isNull()) {
                    throw new IOException("Cannot initialize stream!");
                }

                codec = getCodec(params.getType());
                if (codec == null || codec.isNull()) {
                    throw new IOException("No j2k decoder for this type: " + params.getType());
                }

                /* catch events using our callbacks and give a local context */
                openjpeg.opj_set_info_handler(codec, infoHandler, null);
                openjpeg.opj_set_warning_handler(codec, warningHandler, null);
                openjpeg.opj_set_error_handler(codec, errorHandler, null);

                /* setup the decoder decoding parameters using user parameters */
                /* Read the main header of the codestream and if necessary the JP2 boxes */
                openjpeg.opj_dparameters parameters = new openjpeg.opj_dparameters();
                openjpeg.opj_set_default_decoder_parameters(parameters);
                if (!openjpeg.opj_setup_decoder(codec, parameters)) {
                    throw new IOException("Failed to setup the decoder");
                }

                /* Read the main header of the codestream and if necessary the JP2 boxes */
                image = new openjpeg.opj_image();
                if (!openjpeg.opj_read_header(lstream, codec, image)) {
                    throw new IOException("Failed to read the j2k header");
                }
                setParameters(nImage.getImageParameters(), image);
                // keep a reference to be not garbage collected
                StreamSegment.safeToBuffer(buffer).clear();
                j2kFile.deallocate();
            } finally {
                if (lstream != null) {
                    openjpeg.opj_stream_destroy(lstream);
                    lstream.deallocate();
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
    public String decompress(NativeImage nImage, ImageReadParam param) throws IOException {
        String msg = null;
        StreamSegment seg = nImage.getStreamSegment();
        if (seg != null) {
            Pointer lstream = null;
            Pointer codec = null;
            openjpeg.opj_image image = null;
            try {
                // When multiple fragments segments, aggregate them in the byteBuffer.
                ByteBuffer buffer = seg.getDirectByteBuffer(0, seg.getSegLength().length - 1);
                // TODO apply signed at DICOM level?
                // boolean signed = params.isSignedData();

                J2kParameters j2kparams = (J2kParameters) nImage.getImageParameters();

                SourceData j2kFile = new SourceData();
                j2kFile.data(buffer);
                SizeTPointer srcDataSize = new SizeTPointer(1);
                srcDataSize.put(buffer.limit());
                j2kFile.size(srcDataSize);

                lstream = openjpeg.opj_stream_create_memory_stream(j2kFile, OPJ_J2K_STREAM_CHUNK_SIZE, true);
                if (lstream.isNull()) {
                    throw new IOException("Cannot initialize stream!");
                }

                codec = getCodec(j2kparams.getType());
                if (codec == null || codec.isNull()) {
                    throw new IOException("No j2k decoder for this type: " + j2kparams.getType());
                }

                /* catch events using our callbacks and give a local context */
                openjpeg.opj_set_info_handler(codec, infoHandler, null);
                openjpeg.opj_set_warning_handler(codec, warningHandler, null);
                openjpeg.opj_set_error_handler(codec, errorHandler, null);

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
                if (!openjpeg.opj_read_header(lstream, codec, image)) {
                    throw new IOException("Failed to read the j2k header");
                }
                setParameters(nImage.getImageParameters(), image);
                int bps = j2kparams.getBitsPerSample();
                if (bps < 1 || bps > 16) {
                    throw new IllegalArgumentException("Invalid bit per sample: " + bps);
                }

                Rectangle area = param.getSourceRegion();
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

                long start = System.currentTimeMillis();
                /* Get the decoded image */
                if (!(openjpeg.opj_decode(codec, lstream, image) && openjpeg.opj_end_decompress(codec, lstream))) {
                    throw new IOException("Failed to set the decoded image!");
                }
                LOGGER.debug("OpenJPEG decode time: {} ms", (System.currentTimeMillis() - start)); //$NON-NLS-1$
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

                /*
                 * Has not effect on releasing memory but only keep a reference to be not garbage collected during the
                 * native decode (ByteBuffer.allocateDirect() has PhantomReference)
                 */
                StreamSegment.safeToBuffer(buffer).clear();
                openjpeg.opj_stream_destroy(lstream);
                j2kFile.deallocate();
                lstream.deallocate();
                lstream = null;

                int bands = image.numcomps();
                if (bands > 0) {

                    // if (image.color_space() == openjpeg.OPJ_CLRSPC_SYCC) {
                    // openjpeg.color_sycc_to_rgb(image);
                    // }

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
                    if (area == null) {
                        area = new Rectangle(0, 0, j2kparams.getWidth(), j2kparams.getHeight());
                    }
                    int imgSize = area.width * area.height;
                    int length = imgSize * bands;
                    Object array = bps <= 8 ? new byte[length] : new short[length];
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

                    if (array != null) {
                        nImage.fillOutputBuffer(array, 0, length);
                    }
                }
            } finally {
                if (lstream != null) {
                    openjpeg.opj_stream_destroy(lstream);
                    lstream.deallocate();
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
    public String compress(NativeImage nImage, ImageOutputStream ouputStream, ImageWriteParam param)
        throws IOException {
        String msg = null;
        if (nImage != null && ouputStream != null && nImage.getInputBuffer() != null) {
            try {
                J2kParameters params = (J2kParameters) nImage.getImageParameters();
                int bps = params.getBitsPerSample();
                if (bps < 1 || bps > 16) {
                    return "OPENJPEG codec: invalid bit per sample: " + bps;
                }
                int samplesPerPixel = params.getSamplesPerPixel();
                if (samplesPerPixel != 1 && samplesPerPixel != 3) {
                    return "OPENJPEG codec supports only 1 and 3 bands!";
                }

                Buffer b = nImage.getInputBuffer();

                ByteBuffer buffer;
                if (b instanceof ByteBuffer) {
                    buffer = ByteBuffer.allocateDirect(b.limit());
                    buffer.order(ByteOrder.LITTLE_ENDIAN);
                    buffer.put((ByteBuffer) b);
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

                // set lossless
                // parameters.tcp_numlayers = 1;
                // parameters.tcp_rates[0] = 0;
                // parameters.cp_disto_alloc = 1;
                //
                // if (!openjpeg.opj_setup_eecoder(codec, parameters)) {
                // throw new IOException("Failed to setup the decoder");
                // }

            } finally {
                // Do not close inChannel (comes from image input stream)
            }
        }
        // // set lossless
        // parameters.tcp_numlayers = 1;
        // parameters.tcp_rates[0] = 0;
        // parameters.cp_disto_alloc = 1;
        //
        // if(djcp->getUseCustomOptions())
        // {
        // parameters.cblockw_init = djcp->get_cblkwidth();
        // parameters.cblockh_init = djcp->get_cblkheight();
        // }
        //
        // // turn on/off MCT depending on transfer syntax
        // if(supportedTransferSyntax() == EXS_JPEG2000LosslessOnly)
        // parameters.tcp_mct = 0;
        // else if(supportedTransferSyntax() == EXS_JPEG2000MulticomponentLosslessOnly)
        // parameters.tcp_mct = (image->numcomps >= 3) ? 1 : 0;
        //
        // // We have no idea how big the compressed pixel data will be and we have no
        // // way to find out, so we just allocate a buffer large enough for the raw data
        // // plus a little more for JPEG metadata.
        // // Yes, this is way too much for just a little JPEG metadata, but some
        // // test-images showed that the buffer previously was too small. Plus, at some
        // // places charls fails to do proper bounds checking and writes behind the end
        // // of the buffer (sometimes way behind its end...).
        // size_t size = frameSize + 1024;
        // Uint8 *buffer = new Uint8[size];
        //
        // // Set up the information structure for OpenJPEG
        // opj_stream_t *l_stream = NULL;
        // opj_codec_t* l_codec = NULL;
        // l_codec = opj_create_compress(OPJ_CODEC_J2K);
        //
        // opj_set_info_handler(l_codec, msg_callback, NULL);
        // opj_set_warning_handler(l_codec, msg_callback, NULL);
        // opj_set_error_handler(l_codec, msg_callback, NULL);
        //
        // if (result.good() && !opj_setup_encoder(l_codec, &parameters, image))
        // {
        // opj_destroy_codec(l_codec);
        // l_codec = NULL;
        // result = EC_MemoryExhausted;
        // }
        //
        // DecodeData mysrc((unsigned char*)buffer, size);
        // l_stream = opj_stream_create_memory_stream(&mysrc, size, OPJ_FALSE);
        //
        // if(!opj_start_compress(l_codec,image,l_stream))
        // {
        // result = EC_CorruptedData;
        // }
        //
        // if(result.good() && !opj_encode(l_codec, l_stream))
        // {
        // result = EC_InvalidStream;
        // }
        //
        // if(result.good() && opj_end_compress(l_codec, l_stream))
        // {
        // result = EC_Normal;
        // }
        //
        // opj_stream_destroy(l_stream); l_stream = NULL;
        // opj_destroy_codec(l_codec); l_codec = NULL;
        // opj_image_destroy(image); image = NULL;
        //
        // size = mysrc.offset;

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
