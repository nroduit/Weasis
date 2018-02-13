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
package org.weasis.core.api.image.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.ParameterBlock;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractBufferHandler;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Thumbnail;

import com.sun.media.jai.codec.FileSeekableStream;
import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageDecoder;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;
import com.sun.media.jai.codec.PNGEncodeParam;
import com.sun.media.jai.codec.TIFFDirectory;
import com.sun.media.jai.codec.TIFFEncodeParam;
import com.sun.media.jai.codec.TIFFField;
import com.sun.media.jai.util.ImageUtil;

/**
 * The Class ImageFiler.
 *
 * @author Nicolas Roduit
 */
public class ImageFiler extends AbstractBufferHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImageFiler.class);
    private static final String TIFF_TAG = "tiff_directory"; //$NON-NLS-1$

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

    public static boolean writeTIFF(File file, RenderedImage source, boolean tiled, boolean addThumb,
        boolean jpegCompression) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try (OutputStream os = new FileOutputStream(file)) {
            writeTIFF(os, source, tiled, addThumb, jpegCompression);
        } catch (OutOfMemoryError | IOException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public static RenderedImage loadImage(String filename) {
        RenderedImage src = null;
        if (filename != null) {
            try {
                ImageInputStream in = new FileImageInputStream(new RandomAccessFile(filename, "r")); //$NON-NLS-1$
                ImageLayout layout = new ImageLayout();
                layout.setTileWidth(TILESIZE);
                layout.setTileHeight(TILESIZE);
                RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
                ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
                pb.setParameter("Input", in); //$NON-NLS-1$
                src = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
                src = getReadableImage(src);
            } catch (Exception e) {
                LOGGER.error("Cannot load image", e); //$NON-NLS-1$
            }
        }
        return src;
    }

    // public static PlanarImage loadImage2(InputStream stream) {
    // PlanarImage src = null;
    // if (stream != null) {
    // try {
    // ImageInputStream in = new FileCacheImageInputStream(stream, AppProperties.APP_TEMP_DIR);
    // // Tile image while reading to handle large images
    // ImageLayout layout = new ImageLayout();
    // layout.setTileWidth(TILESIZE);
    // layout.setTileHeight(TILESIZE);
    // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
    // ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
    // pb.setParameter("Input", in); //$NON-NLS-1$
    // src = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
    // } catch (Exception ex) {
    // src = null;
    // }
    // if (src == null) {
    // try {
    // stream.close();
    // } catch (IOException ex1) {
    // }
    // // JMVisionWin.getInstance().setStatusMessage("Unable to load " + file.getName() + ".\nThis image format
    // // is
    // // not supported");
    // }
    // return src;
    // }
    // return null;
    // }

    public static boolean writeTIFF(String fichier, RenderedImage source, boolean tiled, boolean addThumb,
        boolean jpegCompression) throws IOException {
        return writeTIFF(new File(fichier), source, tiled, addThumb, jpegCompression);
    }

    private static void writeTIFF(OutputStream os, RenderedImage source, boolean tiled, boolean addThumb,
        boolean jpegCompression) throws IOException {
        TIFFEncodeParam param = new TIFFEncodeParam();
        if (tiled) {
            param.setWriteTiled(true);
            param.setTileSize(TILESIZE, TILESIZE);
        }
        boolean binary = ImageUtil.isBinary(source.getSampleModel());
        if (binary) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_GROUP4);
        } else if (jpegCompression) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_JPEG_TTN2);
            JPEGEncodeParam wparam = new JPEGEncodeParam();
            wparam.setQuality(1.0f);
            param.setJPEGEncodeParam(wparam);
        }
        if (addThumb) {
            ArrayList<TIFFField> extraFields = new ArrayList<>(6);
            int fileVal = getResolutionInDpi(source);
            if (fileVal > 0) {
                TIFFDirectory dir = (TIFFDirectory) source.getProperty(TIFF_TAG); // $NON-NLS-1$
                TIFFField f;
                f = dir.getField(282);
                long[][] xRes = f.getAsRationals();
                f = dir.getField(283);
                long[][] yRes = f.getAsRationals();
                f = dir.getField(296);
                char[] resUnit = f.getAsChars();
                f = dir.getField(271);
                if (f != null) {
                    extraFields.add(new TIFFField(271, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
                }
                f = dir.getField(272);
                if (f != null) {
                    extraFields.add(new TIFFField(272, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
                }
                extraFields.add(new TIFFField(282, TIFFField.TIFF_RATIONAL, xRes.length, xRes));
                extraFields.add(new TIFFField(283, TIFFField.TIFF_RATIONAL, yRes.length, yRes));
                extraFields.add(new TIFFField(296, TIFFField.TIFF_SHORT, resUnit.length, resUnit));

            }
            extraFields.add(new TIFFField(305, TIFFField.TIFF_ASCII, 1, new String[] { AppProperties.WEASIS_NAME }));
            param.setExtraFields(extraFields.toArray(new TIFFField[extraFields.size()]));

            if (!binary) {
                // Doesn't support bilevel image (or binary to grayscale).
                ArrayList<RenderedImage> list = new ArrayList<>();
                list.add(Thumbnail.createThumbnail(source));
                param.setExtraImages(list.iterator());
            }
        }

        ImageEncoder enc = ImageCodec.createImageEncoder("TIFF", os, param); //$NON-NLS-1$
        enc.encode(source);
    }

    public static RenderedImage getThumbnailInTiff(ImageElement img) {
        return getThumbnailInTiff(img.getFile());
    }

    public static BufferedImage getThumbnailInTiff(File file) {
        BufferedImage thumbnail = null;
        if (file != null) {
            String mimeType = MimeInspector.getMimeType(file);
            if (mimeType != null && ("image/tiff".equals(mimeType) || "image/x-tiff".equals(mimeType))) { //$NON-NLS-1$ //$NON-NLS-2$
                try (FileSeekableStream inputStream = new FileSeekableStream(file)) {
                    ImageDecoder dec = ImageCodec.createImageDecoder("tiff", inputStream, null); //$NON-NLS-1$
                    int count = dec.getNumPages();
                    if (count == 2) {
                        RenderedImage src2 = dec.decodeAsRenderedImage(1);
                        if (src2.getWidth() <= Thumbnail.MAX_SIZE) {
                            thumbnail = PlanarImage.wrapRenderedImage(src2).getAsBufferedImage();
                        }
                    }

                } catch (IOException ex) {
                    LOGGER.error("Cannot read thumbnail", ex); //$NON-NLS-1$
                    return null;
                }
            }
        }
        return thumbnail;
    }

    private static int getResolutionInDpi(RenderedImage source) {
        if (source.getProperty(TIFF_TAG) instanceof TIFFDirectory) { // $NON-NLS-1$
            TIFFDirectory dir = (TIFFDirectory) source.getProperty(TIFF_TAG); // $NON-NLS-1$
            TIFFField fieldx = dir.getField(282); // 282 is X_resolution
            TIFFField fieldy = dir.getField(283); // 283 is Y_resolution
            TIFFField fieldUnit = dir.getField(296); // 296 is unit
            if (fieldx != null && fieldUnit != null) {
                char c = fieldUnit.getAsChars()[0];
                if (c == '2' || c == '\u0002') {
                    int resolutionx = (int) fieldx.getAsDouble(0); // this is the magic step, no idea why it is needed,
                                                                   // but numbers are wrong otherwise
                    int resolutiony = (int) fieldy.getAsDouble(0);
                    if (resolutionx == resolutiony) {
                        return resolutionx;
                    }
                }
            }
        }
        return 0;
    }

    public static boolean writePNG(File file, RenderedImage source) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }

        try (OutputStream os = new FileOutputStream(file)) {
            writePNG(os, source);
        } catch (OutOfMemoryError | IOException e) {
            LOGGER.error("", e); //$NON-NLS-1$
            return false;
        }
        return true;
    }

    public static void writePNG(OutputStream os, RenderedImage source) throws IOException {
        PNGEncodeParam param = new PNGEncodeParam.Palette();
        ImageEncoder enc = ImageCodec.createImageEncoder("PNG", os, param); //$NON-NLS-1$
        enc.encode(source);
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
        RenderedImage dst = null;
        if (source != null && source.getSampleModel() != null) {
            if (ImageUtil.isBinary(source.getSampleModel())) {
                dst = source;
                if (source.getColorModel() instanceof IndexColorModel) {
                    IndexColorModel icm = (IndexColorModel) source.getColorModel();
                    byte[] tableData = new byte[icm.getMapSize()];
                    icm.getReds(tableData);
                    if (tableData[0] != (byte) 0x00) {
                        ImageLayout layout = new ImageLayout();
                        layout.setSampleModel(
                            LayoutUtil.createBinarySampelModel(source.getTileWidth(), source.getTileHeight()));
                        layout.setColorModel(LayoutUtil.createBinaryIndexColorModel());
                        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
                        hints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(source);
                        return JAI.create("NotBinary", pb, hints); //$NON-NLS-1$
                    }
                }
            } else if (source.getColorModel() instanceof IndexColorModel) {
                dst = PlanarImage.wrapRenderedImage(ImageToolkit.convertIndexColorToRGBColor(source));
            } else {
                dst = source;
            }

            String bdSel = "bandSelect"; //$NON-NLS-1$
            int numBands = dst.getSampleModel().getNumBands();
            if (numBands == 2) {
                ParameterBlockJAI pb = new ParameterBlockJAI(bdSel); // $NON-NLS-1$
                pb.addSource(dst);
                pb.setParameter("bandIndices", new int[] { 0, 1, 0 }); //$NON-NLS-1$
                dst = JAI.create(bdSel, pb, null); // $NON-NLS-1$
            }
            // for image with alpha channel
            else if (numBands > 3) {
                ParameterBlockJAI pb = new ParameterBlockJAI(bdSel); // $NON-NLS-1$
                pb.addSource(dst);
                pb.setParameter("bandIndices", new int[] { 0, 1, 2 }); //$NON-NLS-1$
                dst = JAI.create(bdSel, pb, null); // $NON-NLS-1$
            }
        }
        return dst;
    }

    public static String changeExtension(String filename, String ext) {
        if (filename == null) {
            return ""; //$NON-NLS-1$
        }
        // replace extension after the last point
        int pointPos = filename.lastIndexOf("."); //$NON-NLS-1$
        if (pointPos == -1) {
            pointPos = filename.length();
        }
        return filename.substring(0, pointPos) + ext;
    }

    public static PlanarImage getEmptyImage(Byte[] bandValues, float width, float height) {
        ParameterBlock pb = new ParameterBlock();
        pb.add(width);
        pb.add(height);
        pb.add(bandValues);
        return JAI.create("constant", pb, null); //$NON-NLS-1$
    }

    public static PlanarImage getEmptyImage(Color color, float width, float height) {
        Byte[] bandValues = { (byte) color.getRed(), (byte) color.getGreen(), (byte) color.getBlue() };
        return getEmptyImage(bandValues, width, height);
    }

    public static TiledImage getEmptyTiledImage(Color color, float width, float height) {
        return new TiledImage(getEmptyImage(color, width, height), ImageFiler.TILESIZE, ImageFiler.TILESIZE);
    }

    public static TiledImage getEmptyTiledImage(Byte[] bandValues, float width, float height) {
        return new TiledImage(getEmptyImage(bandValues, width, height), ImageFiler.TILESIZE, ImageFiler.TILESIZE);
    }

    public static void encodeImagePng(RenderedImage image, FileOutputStream fileStream) throws IOException {
        PNGEncodeParam param = new PNGEncodeParam.Palette();
        ImageEncoder enc = ImageCodec.createImageEncoder("PNG", fileStream, param); //$NON-NLS-1$
        enc.encode(image);
    }

    public static PlanarImage tileImage(RenderedImage img) {
        // Tile image while reading to handle large images
        ImageLayout layout = new ImageLayout();
        layout.setTileWidth(TILESIZE);
        layout.setTileHeight(TILESIZE);
        RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
        ParameterBlock pb = new ParameterBlock();
        pb.addSource(img);
        pb.add(img.getSampleModel().getDataType());
        return JAI.create("format", pb, hints); //$NON-NLS-1$
    }

    public static File cacheTiledImage(RenderedImage img, MediaElement media) {
        if (img.getWidth() > 2 * ImageFiler.TILESIZE || img.getHeight() > 2 * ImageFiler.TILESIZE) {
            File imgCacheFile = null;
            try {
                imgCacheFile = File.createTempFile("tiled_", ".tif", AppProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (IOException e) {
                LOGGER.error("Creating cache image", e); //$NON-NLS-1$
            }

            if (ImageFiler.writeTIFF(imgCacheFile, img, true, false, false)) {
                return imgCacheFile;
            }
        }
        return null;
    }
}
