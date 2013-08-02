/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.RenderingHints;
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
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.util.FileUtil;

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

    public static final String[] OUTPUT_TYPE = { "Binary", "Gray Levels", "Color" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    public static final int TILESIZE = 512;
    public static final int LIMIT_TO_TILE = 768;
    public int saveMode = 0;
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
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            writeTIFF(os, source, tiled, addThumb, jpegCompression);
        } catch (OutOfMemoryError e) {
            LOGGER.error(e.getMessage());
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            try {
                os.flush();
                os.close();
            } catch (IOException ex1) {
            }
        }
        return true;
    }

    public static RenderedImage loadImage(String filename) {
        RenderedImage src = null;
        if (filename != null) {
            try {
                ImageInputStream in = new FileImageInputStream(new RandomAccessFile(filename, "r"));
                ImageLayout layout = new ImageLayout();
                layout.setTileWidth(TILESIZE);
                layout.setTileHeight(TILESIZE);
                RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
                ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead");
                pb.setParameter("Input", in);
                src = JAI.create("ImageRead", pb, hints);
                src = getReadableImage(src);
            } catch (Exception ex) {
            }
        }
        return src;
    }

    // public static PlanarImage loadImage2(InputStream stream) {
    // PlanarImage src = null;
    // if (stream != null) {
    // try {
    // ImageInputStream in = new FileCacheImageInputStream(stream, AbstractProperties.APP_TEMP_DIR);
    // // Tile image while reading to handle large images
    // ImageLayout layout = new ImageLayout();
    // layout.setTileWidth(TILESIZE);
    // layout.setTileHeight(TILESIZE);
    // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
    //                ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
    //                pb.setParameter("Input", in); //$NON-NLS-1$
    //                src = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
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
        boolean jpegCompression) throws Exception {
        return writeTIFF(new File(fichier), source, tiled, addThumb, jpegCompression);
    }

    private static void writeTIFF(OutputStream os, RenderedImage source, boolean tiled, boolean addThumb,
        boolean jpegCompression) throws Exception {
        TIFFEncodeParam param = new TIFFEncodeParam();
        if (tiled) {
            param.setWriteTiled(true);
            param.setTileSize(TILESIZE, TILESIZE);
        }
        if (ImageUtil.isBinary(source.getSampleModel())) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_GROUP4);
        } else if (jpegCompression) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_JPEG_TTN2);
            JPEGEncodeParam wparam = new JPEGEncodeParam();
            wparam.setQuality(1.0f);
            param.setJPEGEncodeParam(wparam);
        }
        if (addThumb) {
            ArrayList<TIFFField> extraFields = new ArrayList<TIFFField>(6);
            int fileVal = getResolutionInDpi(source);
            if (fileVal > 0) {
                TIFFDirectory dir = (TIFFDirectory) source.getProperty("tiff_directory");
                TIFFField f;
                f = dir.getField(282);
                long[][] l_xRes = f.getAsRationals();
                f = dir.getField(283);
                long[][] l_yRes = f.getAsRationals();
                f = dir.getField(296);
                char[] l_resUnit = f.getAsChars();
                f = dir.getField(271);
                if (f != null) {
                    extraFields.add(new TIFFField(271, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
                }
                f = dir.getField(272);
                if (f != null) {
                    extraFields.add(new TIFFField(272, TIFFField.TIFF_ASCII, 1, new String[] { f.getAsString(0) }));
                }
                extraFields.add(new TIFFField(282, TIFFField.TIFF_RATIONAL, l_xRes.length, l_xRes));
                extraFields.add(new TIFFField(283, TIFFField.TIFF_RATIONAL, l_yRes.length, l_yRes));
                extraFields.add(new TIFFField(296, TIFFField.TIFF_SHORT, l_resUnit.length, l_resUnit));

            }
            extraFields
                .add(new TIFFField(305, TIFFField.TIFF_ASCII, 1, new String[] { AbstractProperties.WEASIS_NAME }));
            param.setExtraFields(extraFields.toArray(new TIFFField[extraFields.size()]));

            ArrayList list = new ArrayList();
            list.add(Thumbnail.createThumbnail(getReadableImage(source)));

            param.setExtraImages(list.iterator());
        }

        ImageEncoder enc = ImageCodec.createImageEncoder("TIFF", os, param);
        enc.encode(source);
    }

    public static RenderedImage getThumbnailInTiff(ImageElement img) {
        RenderedImage thumbnail = null;
        if (img != null && img.getFile() != null) {
            try {
                String mime = img.getMimeType();

                if ("image/tiff".equals(mime) || "image/x-tiff".equals(mime)) {
                    ImageDecoder dec =
                        ImageCodec.createImageDecoder("tiff", new FileSeekableStream(img.getFile()), null);
                    int count = dec.getNumPages();
                    if (count == 2) {
                        RenderedImage src2 = dec.decodeAsRenderedImage(1);
                        if (src2.getWidth() <= Thumbnail.MAX_SIZE) {
                            thumbnail = src2;
                        }
                    }
                }

            } catch (IOException ex) {
                ex.printStackTrace();
                return null;
            }
        }
        return thumbnail;
    }

    public static RenderedImage getThumbnailInTiff(File file) {
        RenderedImage thumbnail = null;
        try {
            String mimeType = MimeInspector.getMimeType(file);
            if (mimeType != null && (mimeType.equals("image/tiff") || mimeType.equals("image/x-tiff"))) {
                ImageDecoder dec = ImageCodec.createImageDecoder("tiff", new FileSeekableStream(file), null);
                int count = dec.getNumPages();
                if (count == 2) {
                    RenderedImage src2 = dec.decodeAsRenderedImage(1);
                    if (src2.getWidth() <= Thumbnail.MAX_SIZE) {
                        thumbnail = src2;
                    }
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
        return thumbnail;
    }

    private static int getResolutionInDpi(RenderedImage source) {
        if (source.getProperty("tiff_directory") instanceof TIFFDirectory) {
            TIFFDirectory dir = (TIFFDirectory) source.getProperty("tiff_directory");
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
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            writePNG(os, source);
        } catch (OutOfMemoryError e) {
            // JMVisionWin.setOutOfMemoryMessage();
            return false;
        } catch (Exception ex) {
            ex.printStackTrace();
            return false;
        } finally {
            FileUtil.safeClose(os);
        }
        return true;
    }

    public static boolean writePNG(String fichier, RenderedImage source) throws Exception {
        File file = new File(fichier);
        return writePNG(file, source);
    }

    private static void writePNG(OutputStream os, RenderedImage source) throws Exception {
        PNGEncodeParam param = new PNGEncodeParam.Palette();
        ImageEncoder enc = ImageCodec.createImageEncoder("PNG", os, param); //$NON-NLS-1$
        enc.encode(source);
    }

    public static boolean writeJPG(File file, RenderedImage source, float quality) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        ImageOutputStream os = null;
        ImageWriter writer = null;
        try {
            Iterator iter = ImageIO.getImageWritersByFormatName("JPEG"); //$NON-NLS-1$
            if (iter.hasNext()) {
                writer = (ImageWriter) iter.next();
                os = ImageIO.createImageOutputStream(file);
                writer.setOutput(os);
                JPEGImageWriteParam iwp = new JPEGImageWriteParam(null);
                iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                iwp.setCompressionQuality(quality);
                writer.write(null, new IIOImage(source, null, null), iwp);
            }
        } catch (OutOfMemoryError e) {
            // JMVisionWin.setOutOfMemoryMessage();
            return false;
        } catch (IOException ex) {
            ex.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) {
                    os.flush();
                    writer.dispose();
                    os.close();
                }
            } catch (IOException e) {
                // Do nothing
            }

        }
        return true;
    }

    public static boolean writeJPG(String fichier, PlanarImage source) throws Exception {
        File file = new File(fichier);
        return writePNG(file, source);
    }

    public static RenderedImage getReadableImage(RenderedImage source) {
        RenderedImage dst = null;
        if (source != null && source.getSampleModel() != null) {
            if (ImageUtil.isBinary(source.getSampleModel())) {
                dst = source;
                if (source.getColorModel() instanceof IndexColorModel) {
                    IndexColorModel icm = (IndexColorModel) source.getColorModel();
                    byte[] table_data = new byte[icm.getMapSize()];
                    icm.getReds(table_data);
                    if (table_data[0] != (byte) 0x00) {
                        ImageLayout layout = new ImageLayout();
                        layout.setSampleModel(LayoutUtil.createBinarySampelModel(source.getTileWidth(),
                            source.getTileHeight()));
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
            int numBands = dst.getSampleModel().getNumBands();
            if (numBands == 2) {
                ParameterBlockJAI pb = new ParameterBlockJAI("bandSelect"); //$NON-NLS-1$
                pb.addSource(dst);
                pb.setParameter("bandIndices", new int[] { 0, 1, 0 }); //$NON-NLS-1$
                dst = JAI.create("bandSelect", pb); //$NON-NLS-1$
            }
            // for image with alpha channel
            else if (numBands > 3) {
                ParameterBlockJAI pb = new ParameterBlockJAI("bandSelect"); //$NON-NLS-1$
                pb.addSource(dst);
                pb.setParameter("bandIndices", new int[] { 0, 1, 2 }); //$NON-NLS-1$
                dst = JAI.create("bandSelect", pb); //$NON-NLS-1$
            }
        }
        return dst;
    }

    public static String changeExtension(String filename, String ext) {
        if (filename == null) {
            return ""; //$NON-NLS-1$
        }
        // récupère dans le nom tous ce qu'il y a avant le dernier point et on ajoute l'extension
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

    public static File cacheTiledImage(RenderedImage img, MediaElement<?> media) {
        if ((img.getWidth() > 2 * ImageFiler.TILESIZE || img.getHeight() > 2 * ImageFiler.TILESIZE)) {
            File imgCacheFile = null;
            try {
                imgCacheFile = File.createTempFile("tiled_", ".tif", AbstractProperties.FILE_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
            } catch (IOException e) {
                e.printStackTrace();
            }

            if (ImageFiler.writeTIFF(imgCacheFile, img, true, false, false)) {
                media.setTag(TagW.TiledImagePath, imgCacheFile.getPath());
                return imgCacheFile;
            }
        }
        return null;
    }

    public static RenderedImage readTiledCacheImage(File file) {
        try {
            ImageDecoder dec = ImageCodec.createImageDecoder("tiff", new FileSeekableStream(file), null);
            return dec.decodeAsRenderedImage();

        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static RenderedImage readThumbnailCacheImage(File file) {
        try {
            ImageDecoder dec = ImageCodec.createImageDecoder("tiff", new FileSeekableStream(file), null);
            int count = dec.getNumPages();
            if (count == 2) {
                RenderedImage src2 = dec.decodeAsRenderedImage(1);
                if (src2.getWidth() <= Thumbnail.MAX_SIZE) {
                    return src2;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }
}
