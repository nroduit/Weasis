/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.Component;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractBufferHandler;
import org.weasis.opencv.op.ImageConversion;

/**
 * The Class ImageFiler.
 *
 * @author Nicolas Roduit
 */
public class ImageFiler extends AbstractBufferHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageFiler.class);

    public static final int TILESIZE = 512;
    public static final int LIMIT_TO_TILE = 768;
    public static final int SAVE_TILED = 0;
    public static final int SAVE_MULTI = 3;
    public static final int SAVE_CANVAS = 1;
    public static final int SAVE_SVG = 4;
    public static final int OUTPUT_BINARY = 0;
    public static final int OUTPUT_GRAY = 1;
    public static final int OUTPUT_COLOR = 2;

    public ImageFiler(Component win) {
        super(win);
    }

    @Override
    protected void handleNewDocument() {
        // Do nothing
    }

    @Override
    protected boolean handleSaveDocument(OutputStream outputstream) {
        return true;
    }

    @Override
    protected boolean handleOpenDocument(InputStream inputstream) {
        return true;
    }

    public static boolean writeJPG(File file, RenderedImage source, float quality) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try (OutputStream os = new FileOutputStream(file)) {
            writeJPG(os, source, quality);
        } catch (OutOfMemoryError | IOException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public static boolean writeJPG(OutputStream outputStream, RenderedImage source, float quality) {
        ImageWriter writer = null;
        try {
            Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("JPEG"); //$NON-NLS-1$
            if (iter.hasNext()) {
                writer = iter.next();
                try (ImageOutputStream os = ImageIO.createImageOutputStream(outputStream)) {
                    writer.setOutput(os);
                    JPEGImageWriteParam iwp = new JPEGImageWriteParam(null);
                    iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    iwp.setCompressionQuality(quality);
                    writer.write(null, new IIOImage(source, null, null), iwp);
                }
            }
        } catch (OutOfMemoryError | IOException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        } finally {
            if (writer != null) {
                writer.dispose();
            }
        }
        return true;
    }

    public static RenderedImage getReadableImage(RenderedImage source) {
        if (source != null && source.getSampleModel() != null) {
            int numBands = source.getSampleModel().getNumBands();
            if (ImageConversion.isBinary(source.getSampleModel())) {
                return ImageConversion.convertTo(source, BufferedImage.TYPE_BYTE_GRAY);
            }

            if (source.getColorModel() instanceof IndexColorModel || numBands == 2 || numBands > 3
                || (source.getSampleModel() instanceof BandedSampleModel && numBands > 1)) {
                int imageType = numBands >= 3 ? BufferedImage.TYPE_3BYTE_BGR : BufferedImage.TYPE_BYTE_GRAY;
                return ImageConversion.convertTo(source, imageType);
            }
        }
        return source;
    }

    public static String changeExtension(String filename, String ext) {
        if (filename == null) {
            return ""; //$NON-NLS-1$
        }
        // replace extension after the last point
        int pointPos = filename.lastIndexOf('.');
        if (pointPos == -1) {
            pointPos = filename.length();
        }
        return filename.substring(0, pointPos) + ext;
    }

}
