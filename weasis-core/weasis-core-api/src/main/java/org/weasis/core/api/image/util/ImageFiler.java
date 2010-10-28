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
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.ParameterBlockJAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.TiledImage;

import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.AbstractBufferHandler;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.util.FileUtil;

import com.sun.media.jai.codec.ImageCodec;
import com.sun.media.jai.codec.ImageEncoder;
import com.sun.media.jai.codec.JPEGEncodeParam;
import com.sun.media.jai.codec.PNGEncodeParam;
import com.sun.media.jai.codec.TIFFEncodeParam;
import com.sun.media.jai.util.ImageUtil;

/**
 * The Class ImageFiler.
 * 
 * @author Nicolas Roduit
 */
public class ImageFiler extends AbstractBufferHandler {

    public final static String[] OUTPUT_TYPE = { "Binary", "Gray Levels", "Color" }; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
    public final static int TILESIZE = 512;
    public final static int LIMIT_TO_TILE = 768;
    public int saveMode = 0;
    public final static int SAVE_TILED = 0;
    public final static int SAVE_MULTI = 3;
    public final static int SAVE_CANVAS = 1;
    public final static int SAVE_SVG = 4;
    public final static int OUTPUT_BINARY = 0;
    public final static int OUTPUT_GRAY = 1;
    public final static int OUTPUT_COLOR = 2;

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

    public static PlanarImage getImage(String filename) {
        PlanarImage src = null;
        if (filename != null) {
            File f = new File(filename);
            if (!f.exists() || !f.canRead()) {
                // JOptionPane.showMessageDialog(JMVisionWin.getInstance(), "Unable to find or read " + f.getName(),
                // "ImageJai
                // Path", 0);
            } else {
                src = JAI.create("LoadImage", f, null); //$NON-NLS-1$
            }
            /*
             * try { ImageInputStream in = new FileImageInputStream(new RandomAccessFile(filename, "r")); // Tile image
             * while reading to handle large images ImageLayout layout = new ImageLayout();
             * layout.setTileWidth(TILESIZE); layout.setTileHeight(TILESIZE); RenderingHints hints = new
             * RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout); ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead");
             * pb.setParameter("Input", in); src = JAI.create("ImageRead", pb, hints); //to avoid problem with alpha
             * channel and png encoded in 24 and 32 bits src = getReadableImage(src); } catch (Exception ex) { }
             */
        }
        return src;
    }

    /**
     * Sauvegarde d'une image au format TIFF.
     * 
     * @param fichier
     *            le nom du fichier
     */
    public static boolean writeOptimizedTIFF(File file, PlanarImage source) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            writeOptimizedTIFF(os, source);
        } catch (OutOfMemoryError e) {
            // JMVisionWin.setOutOfMemoryMessage();
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

    public static PlanarImage loadImage2(InputStream stream) {
        PlanarImage src = null;
        if (stream != null) {
            try {
                ImageInputStream in = new FileCacheImageInputStream(stream, AbstractProperties.APP_TEMP_DIR);
                // Tile image while reading to handle large images
                ImageLayout layout = new ImageLayout();
                layout.setTileWidth(TILESIZE);
                layout.setTileHeight(TILESIZE);
                RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
                ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
                pb.setParameter("Input", in); //$NON-NLS-1$
                src = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
            } catch (Exception ex) {
                src = null;
            }
            if (src == null) {
                try {
                    stream.close();
                } catch (IOException ex1) {
                }
                // JMVisionWin.getInstance().setStatusMessage("Unable to load " + file.getName() + ".\nThis image format
                // is
                // not supported");
            }
            return src;
        }
        return null;
    }

    public static boolean writeOptimizedTIFF(String fichier, PlanarImage source) throws Exception {
        return writeOptimizedTIFF(new File(fichier), source);
    }

    private static void writeOptimizedTIFF(OutputStream os, PlanarImage source) throws Exception {
        TIFFEncodeParam param = new TIFFEncodeParam();
        param.setWriteTiled(true);
        param.setTileSize(TILESIZE, TILESIZE);
        if (ImageUtil.isBinary(source.getSampleModel())) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_GROUP4);
        } else {
            param.setCompression(TIFFEncodeParam.COMPRESSION_JPEG_TTN2);
            JPEGEncodeParam wparam = new JPEGEncodeParam();
            wparam.setQuality(1.0f);
            param.setJPEGEncodeParam(wparam);
        }
        ImageEncoder enc = ImageCodec.createImageEncoder("TIFF", os, param); //$NON-NLS-1$
        enc.encode(source);
    }

    public static boolean writeTIFF(File file, PlanarImage source) {
        if (file.exists() && !file.canWrite()) {
            return false;
        }
        OutputStream os = null;
        try {
            os = new FileOutputStream(file);
            writeTIFF(os, source);
        } catch (OutOfMemoryError e) {
            // JMVisionWin.setOutOfMemoryMessage();
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

    public static boolean writeTIFF(String fichier, PlanarImage source) throws Exception {
        File file = new File(fichier);
        return writeTIFF(file, source);
    }

    private static void writeTIFF(OutputStream os, PlanarImage source) throws Exception {
        TIFFEncodeParam param = new TIFFEncodeParam();
        param.setWriteTiled(false);
        if (ImageUtil.isBinary(source.getSampleModel())) {
            param.setCompression(TIFFEncodeParam.COMPRESSION_GROUP4);
        }
        ImageEncoder enc = ImageCodec.createImageEncoder("TIFF", os, param); //$NON-NLS-1$
        enc.encode(source);
    }

    public static boolean writePNG(File file, PlanarImage source) {
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

    public static boolean writePNG(String fichier, PlanarImage source) throws Exception {
        File file = new File(fichier);
        return writePNG(file, source);
    }

    private static void writePNG(OutputStream os, PlanarImage source) throws Exception {
        PNGEncodeParam param = new PNGEncodeParam.Palette();
        ImageEncoder enc = ImageCodec.createImageEncoder("PNG", os, param); //$NON-NLS-1$
        enc.encode(source);
    }

    public static boolean writeJPG(File file, PlanarImage source, float quality) {
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

    public static PlanarImage getReadableImage(PlanarImage src) {
        PlanarImage dst = null;
        if (src != null && src.getSampleModel() != null) {
            if (ImageUtil.isBinary(src.getSampleModel())) {
                dst = src;
                if (src.getColorModel() instanceof IndexColorModel) {
                    IndexColorModel icm = (IndexColorModel) src.getColorModel();
                    byte[] table_data = new byte[icm.getMapSize()];
                    icm.getReds(table_data);
                    if (table_data[0] != (byte) 0x00) {
                        ImageLayout layout = new ImageLayout();
                        layout.setSampleModel(LayoutUtil.createBinarySampelModel(src.getTileWidth(), src
                            .getTileHeight()));
                        layout.setColorModel(LayoutUtil.createBinaryIndexColorModel());
                        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
                        hints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
                        ParameterBlock pb = new ParameterBlock();
                        pb.addSource(src);
                        return JAI.create("NotBinary", pb, hints); //$NON-NLS-1$
                    }
                }
            } else if (src.getColorModel() instanceof IndexColorModel) {
                dst = ImageToolkit.convertIndexColorToRGBColor(src);
            } else {
                dst = src;
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
        Byte[] bandValues = new Byte[3];
        bandValues[0] = (byte) color.getRed();
        bandValues[1] = (byte) color.getGreen();
        bandValues[2] = (byte) color.getBlue();
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

    public static PlanarImage tileImage(PlanarImage img) {
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
}
