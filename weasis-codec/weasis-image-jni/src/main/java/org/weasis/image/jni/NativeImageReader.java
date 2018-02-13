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
package org.weasis.image.jni;

import java.awt.Point;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.imageio.IIOException;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.media.imageioimpl.common.ExtendImageParam;

public abstract class NativeImageReader extends ImageReader {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeImageReader.class);

    // The position of the byte after the last byte read so far.
    protected long highMark = Long.MIN_VALUE;

    // Indicating the stream positions of the start of each image. Entries are added as needed.
    protected final ArrayList<Long> imageStartPosition = new ArrayList<>();

    // The number of images in the stream, if known, otherwise -1.
    private int numImages = -1;

    protected HashMap<Integer, ArrayList<ImageTypeSpecifier>> imageTypes = new HashMap<>();
    protected HashMap<Integer, NativeImage> nativeImages = new HashMap<>();

    protected NativeImageReader(ImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    protected abstract NativeCodec getCodec();

    /**
     * Creates a <code>ImageTypeSpecifier</code> from the <code>ImageParameters</code>. The default sample model is
     * pixel interleaved and the default color model is CS_GRAY or CS_sRGB and IndexColorModel with palettes.
     */
    protected static final ImageTypeSpecifier createImageType(ImageParameters params, ColorSpace colorSpace,
        byte[] redPalette, byte[] greenPalette, byte[] bluePalette, byte[] alphaPalette) throws IOException {
        return createImageType(params,
            createColorModel(params, colorSpace, redPalette, greenPalette, bluePalette, alphaPalette));
    }

    protected static final ImageTypeSpecifier createImageType(ImageParameters params, ColorModel colorModel)
        throws IOException {

        int nType = params.getDataType();
        int nWidth = params.getWidth();
        int nHeight = params.getHeight();
        int nBands = params.getSamplesPerPixel();
        int nBitDepth = params.getBitsPerSample();
        int nScanlineStride = params.getBytesPerLine() / ((nBitDepth + 7) / 8);

        // TODO should handle all types.
        if (nType < 0 || (nType > 2 && nType != ImageParameters.TYPE_BIT)) {
            throw new UnsupportedOperationException("Unsupported data type" + " " + nType);
        }

        SampleModel sampleModel;
        if (nType == ImageParameters.TYPE_BIT) {
            sampleModel =
                new MultiPixelPackedSampleModel(nType, nWidth, nHeight, 1, nScanlineStride, params.getBitOffset());
        } else {
            int[] bandOffsets = new int[nBands];
            for (int i = 0; i < nBands; i++) {
                bandOffsets[i] = i;
            }
            sampleModel = new PixelInterleavedSampleModel(nType, nWidth, nHeight, nBands, nScanlineStride, bandOffsets);
        }
        return new ImageTypeSpecifier(colorModel, sampleModel);
    }

    private static ColorModel createColorModel(ImageParameters params, ColorSpace colorSpace, byte[] redPalette,
        byte[] greenPalette, byte[] bluePalette, byte[] alphaPalette) {
        int nType = params.getDataType();
        int nBands = params.getSamplesPerPixel();
        int nBitDepth = params.getBitsPerSample();

        ColorModel colorModel;
        if (nBands == 1 && redPalette != null && greenPalette != null && bluePalette != null
            && redPalette.length == greenPalette.length && redPalette.length == bluePalette.length) {

            // Build IndexColorModel
            int paletteLength = redPalette.length;
            if (alphaPalette != null) {
                byte[] alphaTmp = alphaPalette;
                if (alphaPalette.length != paletteLength) {
                    alphaTmp = new byte[paletteLength];
                    if (alphaPalette.length > paletteLength) {
                        System.arraycopy(alphaPalette, 0, alphaTmp, 0, paletteLength);
                    } else {
                        System.arraycopy(alphaPalette, 0, alphaTmp, 0, alphaPalette.length);
                        for (int i = alphaPalette.length; i < paletteLength; i++) {
                            alphaTmp[i] = (byte) 255; // Opaque.
                        }
                    }
                }
                colorModel =
                    new IndexColorModel(nBitDepth, paletteLength, redPalette, greenPalette, bluePalette, alphaTmp);
            } else {
                colorModel = new IndexColorModel(nBitDepth, paletteLength, redPalette, greenPalette, bluePalette);
            }
        } else if (nType == ImageParameters.TYPE_BIT) {
            // 0 -> 0x00 (black), 1 -> 0xff (white)
            byte[] comp = new byte[] { (byte) 0x00, (byte) 0xFF };
            colorModel = new IndexColorModel(1, 2, comp, comp, comp);
        } else {
            ColorSpace cs;
            boolean hasAlpha;
            if (colorSpace != null
                && (colorSpace.getNumComponents() == nBands || colorSpace.getNumComponents() == nBands - 1)) {
                cs = colorSpace;
                hasAlpha = colorSpace.getNumComponents() + 1 == nBands;
            } else {
                cs = ColorSpace.getInstance(nBands < 3 ? ColorSpace.CS_GRAY : ColorSpace.CS_sRGB);
                hasAlpha = nBands % 2 == 0;
            }

            int[] bits = new int[nBands];
            for (int i = 0; i < nBands; i++) {
                bits[i] = nBitDepth;
            }
            colorModel = new ComponentColorModel(cs, bits, hasAlpha, false,
                hasAlpha ? Transparency.TRANSLUCENT : Transparency.OPAQUE, nType);
        }
        return colorModel;
    }

    /**
     * Stores the location of the image at the specified index in the imageStartPosition List.
     *
     * @param imageIndex
     * @return the image index
     * @throws IIOException
     */
    private int locateImage(int imageIndex) throws IIOException {
        if (imageIndex < 0) {
            throw new IndexOutOfBoundsException();
        }

        try {
            // Find closest known index which can be -1 if none read before
            int index = Math.min(imageIndex, imageStartPosition.size() - 1);

            ImageInputStream stream = (ImageInputStream) input;

            // Seek to find the beginning of stream
            if (index >= 0) {
                if (index == imageIndex) {
                    // Seek to previously identified position and return.
                    stream.seek(imageStartPosition.get(index));
                    return imageIndex;
                } else if (highMark >= 0) {
                    // Position not yet identify, so seek to first unread byte.
                    stream.seek(highMark);
                }
            }

            ImageReaderSpi provider = getOriginatingProvider();

            // Search images until at desired index or last image found.
            do {
                try {
                    if (provider.canDecodeInput(stream)) {
                        // Add the image position when the beginning stream is identify as an image.
                        imageStartPosition.add(stream.getStreamPosition());
                    } else {
                        return index;
                    }
                } catch (IOException e) {
                    // Ignore it.
                    return index;
                }

                index++;
                if (index == imageIndex) {
                    break;
                }

                if (!skipImage(index)) {
                    return index - 1;
                }
            } while (true);
        } catch (IOException e) {
            throw new IIOException("Cannot locate image index", e);
        }
        return imageIndex;
    }

    /**
     * Verify that imageIndex is in bounds and find the image position.
     *
     * @param imageIndex
     * @throws IIOException
     */
    protected void seekToImage(int imageIndex) throws IIOException {
        if (imageIndex < minIndex) {
            throw new IndexOutOfBoundsException("imageIndex less than minIndex!");
        }

        // Update minIndex if cannot seek back.
        if (seekForwardOnly) {
            minIndex = imageIndex;
        }

        int index = locateImage(imageIndex);
        if (index != imageIndex) {
            throw new IndexOutOfBoundsException("imageIndex out of bounds!");
        }
    }

    /**
     * Skip the current image. If possible subclasses should override this method with a more efficient implementation.
     *
     * @param index
     *
     * @return Whether the image was successfully skipped.
     */
    protected boolean skipImage(int index) throws IOException {
        boolean retval;

        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        InputStream stream;
        if (input instanceof ImageInputStream) {
            stream = new InputStreamAdapter((ImageInputStream) input);
        } else {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }
        // FIXME skip stream!
        retval = nativeDecode(stream, null, index) != null;

        if (retval) {
            long pos = ((ImageInputStream) input).getStreamPosition();
            if (pos > highMark) {
                highMark = pos;
            }
        }

        return retval;
    }

    /**
     * Decodes an image from the supplied <code>InputStream</code>.
     *
     * @param stream
     *            an input stream
     * @param param
     * @param imageIndex
     * @return NativeImage
     * @throws IOException
     */
    protected abstract NativeImage nativeDecode(InputStream stream, ImageReadParam param, int imageIndex)
        throws IOException;

    protected synchronized NativeImage getImage(int imageIndex, ImageReadParam param) throws IOException {
        NativeImage nativeImage = nativeImages.get(imageIndex);
        if (nativeImage != null && nativeImage.getOutputBuffer() != null) {
            return nativeImage;
        }
        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        seekToImage(imageIndex);
        InputStreamAdapter stream;
        if (input instanceof ImageInputStream) {
            stream = new InputStreamAdapter((ImageInputStream) input);
        } else {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }

        nativeImage = nativeDecode(stream, param, imageIndex);
        if (nativeImage != null) {
            checkParameters(nativeImage.getImageParameters(), param);
            nativeImages.put(imageIndex, nativeImage);
            long pos = ((ImageInputStream) input).getStreamPosition();
            if (pos > highMark) {
                highMark = pos;
            }
        }
        return nativeImage;
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        if (seekForwardOnly && allowSearch) {
            throw new IllegalStateException("can only seek forward!");
        }

        if (numImages > 0) {
            return numImages;
        }
        if (allowSearch) {
            this.numImages = locateImage(Integer.MAX_VALUE) + 1;
        }
        return numImages;
    }

    protected synchronized ImageParameters getInfoImage(int imageIndex, ImageReadParam param) throws IOException {

        NativeImage nativeImage = nativeImages.get(imageIndex);
        if (nativeImage != null) {
            // Get parameters in cache
            ImageParameters p = nativeImage.getImageParameters();
            if (!p.isInitSignedData() && param != null) {
                // Adapt parameters (for signed or unsigned data) when ImageReadParam was null in previous calls.
                checkParameters(p, param);
            }
            return p;
        }

        if (input == null) {
            throw new IllegalStateException("input cannot be null");
        }
        if (!(input instanceof ImageInputStream)) {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }

        ImageInputStream iis = (ImageInputStream) input;
        seekToImage(imageIndex);
        // Mark the input.
        iis.mark();

        ImageParameters infoImage;
        try {
            long start = System.currentTimeMillis();

            NativeCodec decoder = getCodec();
            NativeImage mlImage = decoder.buildImage(iis);
            infoImage = mlImage.getImageParameters();
            if (infoImage == null) {
                throw new IIOException("Null ImageParameters!");
            }
            if (infoImage.getBytesPerLine() <= 0) {
                // TODO handle ICC profile
                FileStreamSegment.adaptParametersFromStream(iis, mlImage, param);
                String error = decoder.readHeader(mlImage);
                if (error != null) {
                    throw new IIOException("Native JPEG codec error: " + error);
                }
            }

            long stop = System.currentTimeMillis();
            LOGGER.debug("Reading header time: {} ms", (stop - start)); //$NON-NLS-1$
            LOGGER.debug("Parameters => {}", infoImage.toString());

            checkParameters(infoImage, param);
            nativeImages.put(imageIndex, mlImage);

            // Free native resources.
            decoder.dispose();
        } catch (Throwable t) {
            throw new IIOException("native JPEG lib error", t);
        }

        // Reset the marked position.
        iis.reset();

        return infoImage;
    }

    private void checkParameters(ImageParameters p, ImageReadParam param) {
        if (param instanceof ExtendImageParam) {
            Boolean signed = ((ExtendImageParam) param).getSignedData();
            if (signed != null) {
                p.setSignedData(signed);
            }
            p.setInitSignedData(true);
        }
        int bps = p.getBitsPerSample();
        int spp = p.getSamplesPerPixel();
        int dataType =
            bps <= 8 ? DataBuffer.TYPE_BYTE : p.isSignedData() ? DataBuffer.TYPE_SHORT : DataBuffer.TYPE_USHORT;
        if (bps > 16 && spp == 1) {
            dataType = DataBuffer.TYPE_INT;
        }
        if (bps == 1 && spp == 1) {
            dataType = ImageParameters.TYPE_BIT;
        }
        p.setDataType(dataType);
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        return getInfoImage(imageIndex, null).getWidth();
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        return getInfoImage(imageIndex, null).getHeight();
    }

    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
        seekToImage(imageIndex);

        return null;
    }

    protected static DataBuffer createDataBuffer(NativeImage img) {
        DataBuffer db = null;
        if (img != null) {
            ImageParameters p = img.getImageParameters();
            if (p != null && img.getOutputBuffer() != null) {
                int dataOffset = p.getDataOffset();
                Buffer buf = img.getOutputBuffer();
                buf.rewind();
                int limit = buf.limit();

                if (buf instanceof ByteBuffer) {
                    // int spp = p.getSamplesPerPixel();
                    // if (spp == 1) {
                    byte[] byteData;
                    if (buf.hasArray()) {
                        byteData = (byte[]) buf.array();
                    } else {
                        ByteBuffer byteBuffer = (ByteBuffer) buf;
                        byteData = new byte[limit];
                        for (int i = 0; i < byteData.length; i++) {
                            byteData[i] = byteBuffer.get();
                        }
                    }
                    db = new DataBufferByte(byteData, byteData.length - dataOffset, dataOffset);
                    // } else {
                    // if ((limit % spp) == 0) {
                    // ByteBuffer byteBuffer = (ByteBuffer) buf;
                    // byte[][] byteData = new byte[spp][limit / 3];
                    // int pix = 0;
                    // while (byteBuffer.hasRemaining()) {
                    // for (int i = 0; i < spp; i++) {
                    // byteData[i][pix] = byteBuffer.get();
                    // }
                    // pix++;
                    // }
                    // db = new DataBufferByte(byteData, limit / 3);
                    // }
                    // }
                } else if (buf instanceof ShortBuffer) {
                    short[] shortData;
                    if (buf.hasArray()) {
                        shortData = (short[]) buf.array();

                    } else {
                        ShortBuffer byteBuffer = (ShortBuffer) buf;
                        shortData = new short[limit];
                        for (int i = 0; i < shortData.length; i++) {
                            shortData[i] = byteBuffer.get();
                        }
                    }
                    // By default short buffer is unsigned, must be explicitly set before to be signed short.
                    // If not, RectifyUShortToShortDataDescriptor will fix this issue
                    if (p.isSignedData()) {
                        db = new DataBufferShort(shortData, shortData.length - dataOffset, dataOffset);
                    } else {
                        db = new DataBufferUShort(shortData, shortData.length - dataOffset, dataOffset);
                    }
                }
                img.outputBuffer = null;
            }
        }
        return db;
    }

    @Override
    public synchronized RenderedImage readAsRenderedImage(int imageIndex, ImageReadParam param) throws IOException {
        return read(imageIndex, param);
        // TODO must be validated as the image reading concurrency is outside the pool thread
        // return new NativeRenderedImage(this, param, imageIndex);
    }

    @Override
    public synchronized BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        long start = System.currentTimeMillis();
        NativeImage img = getImage(imageIndex, param);
        if (img == null) {
            return null;
        }

        DataBuffer db = createDataBuffer(img);
        if (db == null) {
            return null;
        }

        ColorModel cm = createColorModel(img.getImageParameters(), null, null, null, null, null);
        ImageTypeSpecifier type = createImageType(img.getImageParameters(), cm);
        SampleModel sm = type.getSampleModel();
        Point offset = null;
        if (param != null) {
            offset = param.getDestinationOffset();
            if (param.getDestination() != null && param.getDestination().getColorModel() != null) {
                cm = param.getDestination().getColorModel();
                sm = cm.createCompatibleSampleModel(img.getImageParameters().getWidth(),
                    img.getImageParameters().getHeight());
            }
        }
        // Create a new raster and copy the data.
        WritableRaster raster = Raster.createWritableRaster(sm, db, offset);

        long stop = System.currentTimeMillis();
        LOGGER.debug("Building BufferedImage time: {} ms", stop - start); //$NON-NLS-1$
        return new BufferedImage(cm, raster, false, null);
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {

        seekToImage(imageIndex);

        ArrayList<ImageTypeSpecifier> types;
        synchronized (imageTypes) {
            if (imageTypes.containsKey(imageIndex)) {
                types = imageTypes.get(imageIndex);
            } else {
                types = new ArrayList<>();
                ImageParameters info = getInfoImage(imageIndex, null);
                types.add(createImageType(info, null, null, null, null, null));
                imageTypes.put(imageIndex, types);
            }
        }
        return types.iterator();
    }

    @Override
    public void reset() {
        resetLocal();
        super.reset();
    }

    protected void resetLocal() {
        highMark = Long.MIN_VALUE;
        imageStartPosition.clear();
        nativeImages.clear();
        imageTypes.clear();
        numImages = -1;
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        if (input != null && !(input instanceof ImageInputStream)) {
            throw new IllegalArgumentException("input is not an ImageInputStream!");
        }
        resetLocal();
    }

    @Override
    public ImageReadParam getDefaultReadParam() {
        return new NativeImageReadParam();
    }

}
