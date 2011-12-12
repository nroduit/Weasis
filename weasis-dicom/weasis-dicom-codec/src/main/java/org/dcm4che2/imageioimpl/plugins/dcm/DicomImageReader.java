/*
 * ***** BEGIN LICENSE BLOCK ***** Version: MPL 1.1/GPL 2.0/LGPL 2.1
 * 
 * The contents of this file are subject to the Mozilla Public License Version 1.1 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * 
 * Software distributed under the License is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either
 * express or implied. See the License for the specific language governing rights and limitations under the License.
 * 
 * The Original Code is part of dcm4che, an implementation of DICOM(TM) in Java(TM), hosted at
 * http://sourceforge.net/projects/dcm4che.
 * 
 * The Initial Developer of the Original Code is Gunter Zeilinger, Huetteldorferstr. 24/10, 1150 Vienna/Austria/Europe.
 * Portions created by the Initial Developer are Copyright (C) 2002-2005 the Initial Developer. All Rights Reserved.
 * 
 * Contributor(s): See listed authors below.
 * 
 * Alternatively, the contents of this file may be used under the terms of either the GNU General Public License Version
 * 2 or later (the "GPL"), or the GNU Lesser General Public License Version 2.1 or later (the "LGPL"), in which case the
 * provisions of the GPL or the LGPL are applicable instead of those above. If you wish to allow use of your version of
 * this file only under the terms of either the GPL or the LGPL, and not to allow others to use your version of this
 * file under the terms of the MPL, indicate your decision by deleting the provisions above and replace them with the
 * notice and other provisions required by the GPL or the LGPL. If you do not delete the provisions above, a recipient
 * may use your version of this file under the terms of any one of the MPL, the GPL or the LGPL.
 * 
 * ***** END LICENSE BLOCK *****
 */

package org.dcm4che2.imageioimpl.plugins.dcm;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RasterFormatException;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.plugins.jpeg.JPEGImageWriteParam;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.media.jai.RenderedOp;

import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;
import org.dcm4che2.data.VR;
import org.dcm4che2.image.LookupTable;
import org.dcm4che2.image.OverlayUtils;
import org.dcm4che2.image.PartialComponentSampleModel;
import org.dcm4che2.image.VOIUtils;
import org.dcm4che2.imageio.ImageReaderFactory;
import org.dcm4che2.imageio.ItemParser;
import org.dcm4che2.imageio.plugins.dcm.DicomImageReadParam;
import org.dcm4che2.imageio.plugins.dcm.DicomStreamMetaData;
import org.dcm4che2.io.DicomInputStream;
import org.dcm4che2.io.StopTagInputHandler;
import org.dcm4che2.util.ByteUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractProperties;
import org.weasis.core.api.image.op.RectifySignedShortDataDescriptor;
import org.weasis.core.api.util.FileUtil;
import org.weasis.dicom.codec.ColorModelFactory;

import com.sun.media.imageio.stream.RawImageInputStream;
import com.sun.media.imageio.stream.SegmentedImageInputStream;

/**
 * @author Gunter Zeilinger <gunterze@gmail.com>
 * @version $Revision$ $Date$
 * @since Sep 2, 2006
 */
public class DicomImageReader extends ImageReader {

    public static final File DICOM_CACHE_DIR = new File(AbstractProperties.APP_TEMP_DIR, "cache"); //$NON-NLS-1$
    static {
        try {
            DICOM_CACHE_DIR.mkdirs();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static final Logger log = LoggerFactory.getLogger(DicomImageReader.class);
    private static final String J2KIMAGE_READER = "com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReader";
    private static final int[] OFFSETS_0 = { 0 };
    private static final int[] OFFSETS_0_0_0 = { 0, 0, 0 };
    private static final int[] OFFSETS_0_1_2 = { 0, 1, 2 };

    private ImageInputStream iis;

    private DicomInputStream dis;

    private DicomObject ds;

    private int width;

    private int height;

    private int frames;

    private int allocated;

    private int dataType;

    private int samples;

    private boolean monochrome;

    private boolean paletteColor;

    private boolean banded;

    private boolean bigEndian;

    private boolean swapByteOrder;

    private long pixelDataPos;

    private int pixelDataLen;

    protected boolean compressed;

    private DicomStreamMetaData streamMetaData;

    protected ImageReader reader;

    private ItemParser itemParser;

    private SegmentedImageInputStream siis;

    private String pmi;

    private Float autoWindowCenter;
    private Float autoWindowWidth;

    /**
     * Store the transfer syntax locally in case it gets modified to re-write the image
     */
    protected String tsuid;

    protected DicomImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);

        resetLocal();

        if (input != null) {
            if (!(input instanceof ImageInputStream)) {
                throw new IllegalArgumentException("Input not an ImageInputStream!");
            }
            this.iis = (ImageInputStream) input;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        resetLocal();
    }

    @Override
    public void reset() {
        super.reset();
        resetLocal();
    }

    private void resetLocal() {
        iis = null;
        dis = null;
        ds = null;
        streamMetaData = null;
        width = 0;
        height = 0;
        frames = 0;
        allocated = 0;
        dataType = 0;
        samples = 0;
        banded = false;
        bigEndian = false;
        swapByteOrder = false;
        pixelDataPos = 0L;
        pixelDataLen = 0;
        tsuid = null;
        pmi = null;
        compressed = false;
        if (reader != null) {
            reader.dispose();
            reader = null;
        }
        itemParser = null;
        siis = null;
        autoWindowCenter = null;
        autoWindowWidth = null;
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new DicomImageReadParam();
    }

    /**
     * Return a DicomStreamMetaData object that includes the DICOM header. <b>WARNING:</b> If this class is used to read
     * directly from a cache or other location that contains uncorrected data, the DICOM header will have the
     * uncorrected data as well. That is, assume the DB has some fixes to patient demographics. These will not usually
     * be applied to the DICOM files directly, so you can get the wrong information from the header. This is not an
     * issue if you know the DICOM is up to date, or if you use the DB information as authoritative.
     */
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        readMetaData();
        return streamMetaData;
    }

    /**
     * Gets any image specific meta data. This should return the image specific blocks for enhanced multi-frame, but
     * currently it merely returns null.
     */
    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    /**
     * Returns the number of regular images in the study. This excludes overlays.
     */
    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        readMetaData();
        return frames;
    }

    /**
     * Reads the DICOM header meta-data, up to, but not including pixel data.
     * 
     * @throws IOException
     */
    private void readMetaData() throws IOException {
        if (iis == null) {
            throw new IllegalStateException("Input not set!");
        }
        if (ds != null) {
            return;
        }
        dis = new DicomInputStream(iis);
        dis.setHandler(new StopTagInputHandler(Tag.PixelData));
        ds = dis.readDicomObject();
        while (dis.tag() == 0xFFFCFFFC) {
            dis.readBytes(dis.valueLength());
            dis.readDicomObject(ds, -1);
        }
        streamMetaData = new DicomStreamMetaData();
        streamMetaData.setDicomObject(ds);
        bigEndian = dis.getTransferSyntax().bigEndian();
        tsuid = ds.getString(Tag.TransferSyntaxUID);
        width = ds.getInt(Tag.Columns);
        height = ds.getInt(Tag.Rows);
        frames = ds.getInt(Tag.NumberOfFrames);
        allocated = ds.getInt(Tag.BitsAllocated, 8);
        samples = ds.getInt(Tag.SamplesPerPixel, 1);
        banded = ds.getInt(Tag.PlanarConfiguration) != 0;
        // dataType = allocated <= 8 ? DataBuffer.TYPE_BYTE : DataBuffer.TYPE_USHORT;
        dataType =
            allocated <= 8 ? DataBuffer.TYPE_BYTE : ds.getInt(Tag.PixelRepresentation) != 0 ? DataBuffer.TYPE_SHORT
                : DataBuffer.TYPE_USHORT;
        if (allocated > 16 && samples == 1) {
            dataType = DataBuffer.TYPE_INT;
        }
        paletteColor = ColorModelFactory.isPaletteColor(ds);
        monochrome = ColorModelFactory.isMonochrome(ds);
        pmi = ds.getString(Tag.PhotometricInterpretation);

        if (dis.tag() == Tag.PixelData) {
            if (frames == 0) {
                frames = 1;
            }
            swapByteOrder = bigEndian && dis.vr() == VR.OW && dataType == DataBuffer.TYPE_BYTE;
            if (swapByteOrder && banded) {
                throw new UnsupportedOperationException(
                    "Big Endian color-by-plane with Pixel Data VR=OW not implemented");
            }
            pixelDataPos = dis.getStreamPosition();
            pixelDataLen = dis.valueLength();
            compressed = pixelDataLen == -1;
            if (compressed) {
                ImageReaderFactory f = ImageReaderFactory.getInstance();
                log.debug("Transfer syntax for image is " + tsuid + " with image reader class " + f.getClass());
                f.adjustDatasetForTransferSyntax(ds, tsuid);
            }
        } else if (ds.getString(Tag.PixelDataProviderURL) != null) {
            if (frames == 0) {
                frames = 1;
                compressed = true;
            }
        }
    }

    /**
     * Sets the input for the image reader.
     * 
     * @param imageIndex
     *            The Dicom frame index, or overlay number
     * @throws IOException
     */
    protected void initImageReader(int imageIndex) throws IOException {
        readMetaData();
        if (reader == null) {
            if (compressed) {
                initCompressedImageReader(imageIndex);
            } else {
                initRawImageReader();
            }
        }
        // Reset the input stream location if required, and reset the reader if required
        if (compressed && itemParser != null) {
            itemParser.seekFrame(siis, imageIndex);
            reader.setInput(siis, false);
        }
    }

    private void initCompressedImageReader(int imageIndex) throws IOException {
        ImageReaderFactory f = ImageReaderFactory.getInstance();
        this.reader = f.getReaderForTransferSyntax(tsuid);
        if (!"1.2.840.10008.1.2.4.94".equals(tsuid)) {
            this.itemParser = new ItemParser(dis, iis, frames, tsuid);
            this.siis = new SegmentedImageInputStream(iis, itemParser);
        }
    }

    private void initRawImageReader() {
        long[] frameOffsets = new long[frames];
        int frameLen = width * height * samples * (allocated >> 3);
        frameOffsets[0] = pixelDataPos;
        for (int i = 1; i < frameOffsets.length; i++) {
            frameOffsets[i] = frameOffsets[i - 1] + frameLen;
        }
        Dimension[] imageDimensions = new Dimension[frames];
        Arrays.fill(imageDimensions, new Dimension(width, height));
        RawImageInputStream riis =
            new RawImageInputStream(iis, createImageTypeSpecifier(), frameOffsets, imageDimensions);
        riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        reader = ImageIO.getImageReadersByFormatName("RAW").next();
        reader.setInput(riis);
    }

    /** Create an image type specifier for the entire image */
    protected ImageTypeSpecifier createImageTypeSpecifier() {
        ColorModel cm = ColorModelFactory.createColorModel(ds);
        SampleModel sm = createSampleModel();
        return new ImageTypeSpecifier(cm, sm);
    }

    private SampleModel createSampleModel() {
        if (samples == 1) {
            return new PixelInterleavedSampleModel(dataType, width, height, 1, width, OFFSETS_0);
        }

        // samples == 3
        if (banded) {
            return new BandedSampleModel(dataType, width, height, width, OFFSETS_0_1_2, OFFSETS_0_0_0);
        }

        if ((!compressed) && pmi.endsWith("422")) {
            return new PartialComponentSampleModel(width, height, 2, 1) {
                @Override
                public SampleModel createSubsetSampleModel(int[] bands) {
                    if (bands.length != 3) {
                        throw new RasterFormatException("Accept only 3 bands");
                    }
                    return this;
                }
            };
        }

        if ((!compressed) && pmi.endsWith("420")) {
            return new PartialComponentSampleModel(width, height, 2, 2) {
                @Override
                public SampleModel createSubsetSampleModel(int[] bands) {
                    if (bands.length != 3) {
                        throw new RasterFormatException("Accept only 3 bands");
                    }
                    return this;
                }
            };
        }

        return new PixelInterleavedSampleModel(dataType, width, height, 3, width * 3, OFFSETS_0_1_2);
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        readMetaData();
        if (OverlayUtils.isOverlay(imageIndex)) {
            return OverlayUtils.getOverlayHeight(ds, imageIndex);
        }
        return height;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        readMetaData();
        if (OverlayUtils.isOverlay(imageIndex)) {
            return OverlayUtils.getOverlayWidth(ds, imageIndex);
        }
        return width;
    }

    /**
     * Exposes the window center value that was determined during the last {@link #read(int, ImageReadParam)} call.
     * 
     * @return the window center value, or <code>null</code> if auto windowing was not applied.
     */
    public Float getAutoWindowCenter() {
        return autoWindowCenter;
    }

    /**
     * Exposes the window width value that was determined during the last {@link #read(int, ImageReadParam)} call.
     * 
     * @return the window width value, or <code>null</code> if auto windowing was not applied.
     */
    public Float getAutoWindowWidth() {
        return autoWindowWidth;
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        // Index changes from 1 to 0 as the Dicom frames start to count at 1
        // ImageReader expects the first frame to be 0.
        initImageReader(0);
        return reader.getImageTypes(0);
    }

    @Override
    public boolean canReadRaster() {
        return true;
    }

    /**
     * Read the raw raster data from the image, without any LUTs being applied. Cannot read overlay data, as it isn't
     * clear what the raster format should be for those.
     */
    @Override
    public Raster readRaster(int imageIndex, ImageReadParam param) throws IOException {
        initImageReader(imageIndex);
        if (param == null) {
            param = getDefaultReadParam();
        }
        if (compressed) {
            ImageReadParam param1 = reader.getDefaultReadParam();
            copyReadParam(param, param1);
            return decompressRaster(imageIndex, param1);
        }
        if (pmi.endsWith("422") || pmi.endsWith("420")) {
            log.debug("Using a 422/420 partial component image reader.");
            if (param.getSourceXSubsampling() != 1 || param.getSourceYSubsampling() != 1
                || param.getSourceRegion() != null) {
                log.warn("YBR_*_422 and 420 reader does not support source sub-sampling or source region.");
                throw new UnsupportedOperationException("Implement sub-sampling/soure region.");
            }
            SampleModel sm = createSampleModel();
            WritableRaster wr = Raster.createWritableRaster(sm, new Point());
            DataBufferByte dbb = (DataBufferByte) wr.getDataBuffer();
            byte[] data = dbb.getData();
            log.debug("Seeking to " + (pixelDataPos + imageIndex * data.length) + " and reading " + data.length
                + " bytes.");
            iis.seek(pixelDataPos + imageIndex * data.length);
            iis.read(data);
            if (swapByteOrder) {
                ByteUtils.toggleShortEndian(data);
            }
            return wr;
        }
        Raster raster = reader.readRaster(imageIndex, param);
        if (swapByteOrder) {
            ByteUtils.toggleShortEndian(((DataBufferByte) raster.getDataBuffer()).getData());
        }
        return raster;
    }

    /**
     * Reads the provided image as a buffered image. It is possible to read image overlays by providing the 0x60000000
     * number associated with the overlay. Otherwise, the imageIndex must be in the range 0..numberOfFrames-1, or 0 for
     * a single frame image. Overlays can be read from PR objects or other types of objects in addition to image
     * objects. param can be used to sepecify GSPS to apply to the image, or to override the default window level
     * values, or to return the raw image.
     */
    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        if (OverlayUtils.isOverlay(imageIndex)) {
            readMetaData();
            String rgbs = (param != null) ? ((DicomImageReadParam) param).getOverlayRGB() : null;
            return OverlayUtils.extractOverlay(ds, imageIndex, this, rgbs);
        }
        initImageReader(imageIndex);
        if (param == null) {
            param = getDefaultReadParam();
        }
        BufferedImage bi;
        if (compressed) {
            ImageReadParam param1 = reader.getDefaultReadParam();
            copyReadParam(param, param1);
            bi = reader.read(0, param1);
            postDecompress();
            if (paletteColor && bi.getColorModel().getNumComponents() == 1) {
                bi = new BufferedImage(ColorModelFactory.createColorModel(ds), bi.getRaster(), false, null);
            }
        } else if (pmi.endsWith("422") || pmi.endsWith("420")) {
            bi = readYbr400(imageIndex, param);
        } else {
            bi = reader.read(imageIndex, param);
            if (swapByteOrder) {
                ByteUtils.toggleShortEndian(((DataBufferByte) bi.getRaster().getDataBuffer()).getData());
            }
        }
        // if (monochrome) {
        // WritableRaster raster = bi.getRaster();
        // LookupTable lut = createLut((DicomImageReadParam) param, imageIndex + 1, raster);
        // if (lut != null) {
        // WritableRaster dest = raster;
        // if (dest.getDataBuffer().getDataType() != DataBuffer.TYPE_BYTE && (lut instanceof ByteLookupTable)) {
        // BufferedImage ret = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        // dest = ret.getRaster();
        // bi = ret;
        // }
        // DataBuffer destData = dest.getDataBuffer();
        // lut.lookup(raster, dest);
        // if (destData.getDataType() == DataBuffer.TYPE_SHORT) {
        // ColorModel cm = bi.getColorModel();
        // short[] ss = ((DataBufferShort) destData).getData();
        // return new BufferedImage(cm, Raster.createWritableRaster(raster.getSampleModel(), new DataBufferUShort(
        // ss, ss.length), null), cm.isAlphaPremultiplied(), new Hashtable<Object, Object>());
        // }
        // }
        // }
        RenderedImage img = validateSignedShortDataBuffer(bi);
        return (img instanceof BufferedImage ? (BufferedImage) img : ((RenderedOp) img).getAsBufferedImage());
    }

    /**
     * Reads the Ybr 422 and 420 type images. Sub-samples after reading as necessary.
     * 
     * @param imageIndex
     * @param param
     * @return
     * @throws IOException
     */
    private BufferedImage readYbr400(int imageIndex, ImageReadParam param) throws IOException {
        ImageReadParam useParam = param;
        Rectangle sourceRegion = param.getSourceRegion();
        if (param.getSourceXSubsampling() != 1 || param.getSourceYSubsampling() != 1 || sourceRegion != null) {
            useParam = getDefaultReadParam();
        }
        BufferedImage bi;
        WritableRaster wr = (WritableRaster) readRaster(imageIndex, useParam);
        bi = new BufferedImage(ColorModelFactory.createColorModel(ds), wr, false, null);
        if (useParam == param) {
            return bi;
        }
        return subsampleRGB(bi, sourceRegion, param.getSourceXSubsampling(), param.getSourceYSubsampling());
    }

    /** Sub-samples RGB buffered images when the reader doesn't support it. */
    public static BufferedImage subsampleRGB(BufferedImage src, Rectangle sourceRegion, int subSampleX, int subSampleY) {
        if (sourceRegion == null) {
            sourceRegion = new Rectangle(src.getWidth(), src.getHeight());
        }
        int dWidth = (int) Math.ceil((double) sourceRegion.width / subSampleX);
        int dHeight = (int) Math.ceil((double) sourceRegion.height / subSampleY);

        BufferedImage dest = createRGBBufferedImage(dWidth, dHeight);

        int[] srcRgb = new int[src.getWidth()];
        int[] destRgb = new int[dWidth];
        int maxY = sourceRegion.y + sourceRegion.height;
        int maxX = sourceRegion.x + sourceRegion.width;
        int destY = 0;
        for (int iy = sourceRegion.y; iy < maxY; iy += subSampleY, destY++) {
            srcRgb = src.getRGB(sourceRegion.x, iy, sourceRegion.width, 1, srcRgb, 0, src.getWidth());
            if (subSampleX == 1) {
                dest.setRGB(0, destY, dWidth, 1, srcRgb, 0, src.getWidth());
            } else {
                int destX = 0;
                for (int ix = sourceRegion.x; ix < maxX; ix += subSampleX) {
                    destRgb[destX++] = srcRgb[ix];
                }
                dest.setRGB(0, destY, dWidth, 1, destRgb, 0, dWidth);
            }
        }
        return dest;
    }

    /**
     * Creates a BufferedImage with a custom color model that can be used to store 3 channel RGB data in a byte array
     * data buffer
     */
    public static BufferedImage createRGBBufferedImage(int destWidth, int destHeight) {
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_sRGB);
        ColorModel cm = new ComponentColorModel(cs, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        WritableRaster r = cm.createCompatibleWritableRaster(destWidth, destHeight);
        BufferedImage dest = new BufferedImage(cm, r, false, null);

        return dest;
    }

    @Override
    public RenderedImage readAsRenderedImage(int imageIndex, ImageReadParam param) throws IOException {
        initImageReader(imageIndex);
        if (param == null) {
            param = getDefaultReadParam();
        }
        RenderedImage bi;
        if (compressed) {
            ImageReadParam param1 = reader.getDefaultReadParam();
            copyReadParam(param, param1);
            bi = reader.readAsRenderedImage(0, param1);
            postDecompress();

            // // TEST cache compressed images. Reading is slow with pure java raw reader! Issue W/L shifted.
            // if (reader.getClass().getName().equals("com.sun.media.imageioimpl.plugins.jpeg2000.J2KImageReader")) {
            // Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("RAW");
            // ImageWriter writer = null;
            // while (iter.hasNext()) {
            // writer = iter.next();
            // }
            // if (writer != null) {
            //                    File outFile = File.createTempFile("raw_", ".raw", DICOM_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
            // ImageOutputStream out = null;
            // out = ImageIO.createImageOutputStream(outFile);
            // writer.setOutput(out);
            //
            // ImageWriteParam iwp = writer.getDefaultWriteParam();
            // iwp.setTilingMode(ImageWriteParam.MODE_EXPLICIT);
            // iwp.setTiling(ImageFiler.TILESIZE, ImageFiler.TILESIZE, 0, 0);
            // writer.write(null, new IIOImage(bi, null, null), iwp);
            //
            // // Read image, no need to cache because image would accessible
            // long[] frameOffsets = new long[frames];
            // int frameLen = width * height * samples * (allocated >> 3);
            // frameOffsets[0] = 0;
            // for (int i = 1; i < frameOffsets.length; i++) {
            // frameOffsets[i] = frameOffsets[i - 1] + frameLen;
            // }
            // Dimension[] imageDimensions = new Dimension[frames];
            // Arrays.fill(imageDimensions, new Dimension(width, height));
            // ColorModel cm = ColorModelFactory.createColorModel(ds);
            // SampleModel sm = cm.createCompatibleSampleModel(width, height);
            // RawImageInputStream riis =
            // new RawImageInputStream(ImageIO.createImageInputStream(outFile),
            // new ImageTypeSpecifier(cm, sm), frameOffsets, imageDimensions);
            // riis.setByteOrder(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
            // // ImageReader readerRaw = ImageIO.getImageReadersByFormatName("RAW").next();
            // // readerRaw.setInput(riis);
            // // Tile image while reading to handle large images
            // ImageLayout layout = new ImageLayout();
            // layout.setTileWidth(ImageFiler.TILESIZE);
            // layout.setTileHeight(ImageFiler.TILESIZE);
            // RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
            //                    ParameterBlockJAI pb = new ParameterBlockJAI("ImageRead"); //$NON-NLS-1$
            //                    pb.setParameter("Input", riis); //$NON-NLS-1$
            //                    bi = JAI.create("ImageRead", pb, hints); //$NON-NLS-1$
            // }
            // }
        } else if (pmi.endsWith("422") || pmi.endsWith("420")) {
            bi = readYbr400(imageIndex, param);
            if (bi != null) {
                File outFile = File.createTempFile("img_", ".jpg", DICOM_CACHE_DIR); //$NON-NLS-1$ //$NON-NLS-2$
                ImageOutputStream out = null;
                try {
                    Iterator<ImageWriter> iter = ImageIO.getImageWritersByFormatName("JPEG");
                    ImageWriter writer = null;
                    while (iter.hasNext()) {
                        ImageWriter w = iter.next();
                        // Other encoder do not write it correctly
                        // Workaround: overrides createSubsetSampleModel() in constructor of PartialComponentSampleModel
                        if (w.getClass().getName().equals("com.sun.imageio.plugins.jpeg.JPEGImageWriter")) {
                            writer = w;
                            break;
                        }
                    }
                    if (writer != null) {
                        out = ImageIO.createImageOutputStream(outFile);
                        writer.setOutput(out);
                        JPEGImageWriteParam iwp = new JPEGImageWriteParam(null);
                        iwp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        iwp.setCompressionQuality(1.0f);

                        writer.write(null, new IIOImage(bi, null, null), iwp);
                        // Read image. No need to cache the filename because it is contained in the rendered image.
                        bi = ImageIO.read(outFile);
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    FileUtil.safeClose(out);
                }
            }

        } else {
            bi = reader.readAsRenderedImage(imageIndex, param);
            if (swapByteOrder) {
                // TODO handle this case for big image, find an example
                ByteUtils.toggleShortEndian(((DataBufferByte) bi.getData().getDataBuffer()).getData());
            }
        }
        return validateSignedShortDataBuffer(bi);
    }

    public RenderedImage validateSignedShortDataBuffer(RenderedImage source) {
        // Issue in ComponentColorModel when signed short DataBuffer, only 16 bits is supported
        // see http://java.sun.com/javase/6/docs/api/java/awt/image/ComponentColorModel.html
        // Instances of ComponentColorModel created with transfer types DataBuffer.TYPE_SHORT, DataBuffer.TYPE_FLOAT,
        // and
        // DataBuffer.TYPE_DOUBLE use all the bits of all sample values. Thus all color/alpha components have 16 bits
        // when
        // using DataBuffer.TYPE_SHORT, 32 bits when using DataBuffer.TYPE_FLOAT, and 64 bits when using
        // DataBuffer.TYPE_DOUBLE. When the ComponentColorModel(ColorSpace, int[], boolean, boolean, int, int) form of
        // constructor is used with one of these transfer types, the bits array argument is ignored.

        // Bits Allocated = 16 (Bits alloués )
        // Bits Stored = 12 (Bits enregistrés )
        // High Bit = 11 (Bit le plus significatif)
        // |<------------------ pixel ----------------->|
        // ______________ ______________ ______________ ______________
        // |XXXXXXXXXXXXXX| | | |
        // |______________|______________|______________|______________|
        // 15 12 11 8 7 4 3 0
        //
        // ---------------------------
        //
        // Bits Allocated = 16
        // Bits Stored = 12
        // High Bit = 15
        // |<------------------ pixel ----------------->|
        // ______________ ______________ ______________ ______________
        // | | | |XXXXXXXXXXXXXX|
        // |______________|______________|______________|______________|
        // 15 12 11 8 7 4 3 0
        int highBit;
        // TODO test with all decoders (works with raw decoder)
        if (source != null && dataType == DataBuffer.TYPE_SHORT
            && source.getSampleModel().getDataType() == DataBuffer.TYPE_SHORT
            && (highBit = ds.getInt(Tag.HighBit, allocated) + 1) < allocated) {
            source = RectifySignedShortDataDescriptor.create(source, new int[] { highBit }, null);
        }
        return source;
    }

    public int getDataType() {
        return dataType;
    }

    @Override
    public BufferedImage readTile(int imageIndex, int tileX, int tileY) throws IOException {
        return super.readTile(imageIndex, tileX, tileY);
    }

    @Override
    public Raster readTileRaster(int imageIndex, int tileX, int tileY) throws IOException {
        return super.readTileRaster(imageIndex, tileX, tileY);
    }

    // TODO add to original dcm4che class
    public void readPixelData() throws Exception {
        readMetaData();
        if (dis.tag() == Tag.PixelData) {
            // Remove the StopHandler on the tag 'Pixel Data'
            dis.setHandler(dis);
            // Set the position at the beginning of the tag 'Pixel Data'
            dis.reset();
            // Read the Pixel Data (compressed is a sequence attribute)
            dis.readDicomObject(ds, -1);
        } else {
            throw new Exception("Cannot read pixel data");
        }
    }

    public long getPixelDataPos() {
        return pixelDataPos;
    }

    /**
     * Reads the bytes for the given image as raw data. Useful when copying the image data unchanged to a new
     * file/location etc, but some values are being changed in the header, or some images are being excluded.
     */
    public byte[] readBytes(int imageIndex, ImageReadParam param) throws IOException {
        initImageReader(imageIndex);
        if (compressed) {
            return itemParser.readFrame(siis, imageIndex);
        }

        int frameLen = width * height * samples * (allocated >> 3);
        byte[] ret = new byte[frameLen];
        long offset = pixelDataPos + imageIndex * (long) frameLen;
        iis.seek(offset);
        iis.read(ret);
        return ret;
    }

    protected void copyReadParam(ImageReadParam src, ImageReadParam dst) {
        dst.setDestination(src.getDestination());
        dst.setSourceRegion(src.getSourceRegion());
        dst.setSourceSubsampling(src.getSourceXSubsampling(), src.getSourceYSubsampling(), src.getSubsamplingXOffset(),
            src.getSubsamplingYOffset());
        dst.setDestinationOffset(src.getDestinationOffset());
        if (ImageReaderFactory.getInstance().needsImageTypeSpecifier(tsuid)) {
            dst.setDestinationType(createImageTypeSpecifier());
        }

    }

    private Raster decompressRaster(int imageIndex, ImageReadParam param) throws IOException {
        if (!reader.canReadRaster()) {
            BufferedImage bi = reader.read(0, param);
            postDecompress();
            return bi.getRaster();
        }
        Raster raster = reader.readRaster(0, param);
        postDecompress();
        return raster;
    }

    protected void postDecompress() {
        // workaround for Bug in J2KImageReader and
        // J2KImageReaderCodecLib.setInput()
        if (reader.getClass().getName().startsWith(J2KIMAGE_READER)) {
            reader.dispose();
            ImageReaderFactory f = ImageReaderFactory.getInstance();
            reader = f.getReaderForTransferSyntax(tsuid);
        } else {
            reader.reset();
        }
    }

    /**
     * Return the complete lookup table to apply to the image data. This comprises the Modality LUT, VOI LUT and
     * Presentation LUT. The Modality LUT can be represented as rescale slope/intercept, and the VOI LUT can be
     * represented as window width/center/type. Presentation LUT will come from the specified values in the image read
     * parameter, and is designed to turn p-values into DDLs (digital driving levels).
     * 
     * @param param
     * @param raster
     * @return Complete lookup table to apply to the image.
     */
    private LookupTable createLut(DicomImageReadParam param, int frame, Raster raster) {
        short[] pval2gray = param.getPValue2Gray();
        DicomObject pr = param.getPresentationState();
        float c = param.getWindowCenter();
        float w = param.getWindowWidth();
        String vlutFct = param.getVoiLutFunction();
        if (param.isAutoWindowing()) {
            DicomObject voiObj = VOIUtils.selectVoiObject(ds, pr, frame);
            if (voiObj == null) {
                float[] cw = VOIUtils.getMinMaxWindowCenterWidth(ds, pr, frame, raster);
                c = cw[0];
                w = cw[1];
                vlutFct = LookupTable.LINEAR;
                this.autoWindowCenter = Float.valueOf(c);
                this.autoWindowWidth = Float.valueOf(w);
            }
        }

        return LookupTable.createLutForImageWithPR(ds, pr, frame, c, w, vlutFct, 8, pval2gray);
    }
}
