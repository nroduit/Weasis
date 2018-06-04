/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda. (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Just static image-makers for now.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 27 Dec.
 */
public final class RenderSupport {

    private static final int BITS_FOR_GRAY = 16;

    private RenderSupport() {
    }

    /**
     * Creates a buffered image from a ByteBuffer (gray scale).
     *
     * @param asByteBuffer the buffer.
     * @param canvasBounds Canvas size (to define image dimensions."
     * @return the image.
     */
    public static RenderedImage makeBufferedImage(final ByteBuffer asByteBuffer, final Rectangle canvasBounds) {
        //dimensoes da imagem
        int width = canvasBounds.width;
        int height = canvasBounds.height;

        // Note: For other data types this may have to be calculated diferently (see DicomImageIO).
        DataBufferShort dbuffer = new DataBufferShort(width * height);

        for (int i = 0; i < dbuffer.getSize(); i++) {
            int value = (int) asByteBuffer.getChar();
            dbuffer.setElem(i, value);
        }

        int dataType = DataBuffer.TYPE_SHORT;

        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);

        int[] bits = new int[1];
        Arrays.fill(bits, BITS_FOR_GRAY);
        ComponentColorModel cm = new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, dataType);

        SampleModel sm = new PixelInterleavedSampleModel(dataType, width, height, 1, width, new int[]{0});

        WritableRaster raster = Raster.createWritableRaster(sm, dbuffer, new Point(0, 0));
        BufferedImage pimg = new BufferedImage(cm, raster, cm.isAlphaPremultiplied(), null);

        return pimg;

    }

    /**
     * Creates a buffered image from a ByteBuffer (color).
     *
     * @param asByteBuffer the buffer.
     * @param canvasBounds Canvas size (to define image dimensions."
     * @return the image.
     */
    public static RenderedImage make8BitsImage(final ByteBuffer asByteBuffer, final Rectangle canvasBounds) {
        //dimensoes da imagem
        int width = canvasBounds.width;
        int height = canvasBounds.height;

        DataBufferByte dbuffer = new DataBufferByte(asByteBuffer.array(), width * height);

        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        img.setData(Raster.createRaster(img.getSampleModel(), dbuffer, new Point()));

        return img;

    }

}
