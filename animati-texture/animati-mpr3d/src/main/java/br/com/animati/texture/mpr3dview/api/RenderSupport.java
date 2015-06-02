/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */

package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.mpr3dview.ViewTexture;
import br.com.animati.texturedicom.TextureData;
import br.com.animati.texturedicom.rendering.RenderHelper;
import br.com.animati.texturedicom.rendering.RenderResult;
import br.com.animati.texturedicom.rendering.RenderResultListener;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.nio.ByteBuffer;
import java.util.Arrays;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.TiledImage;
import org.weasis.core.api.image.util.ImageFiler;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2013, 27 Dec.
 */
public class RenderSupport {
    
    private final ViewTexture owner;
    
    private RenderedImage renderedAsSource;
    private volatile boolean dirty = true;
    
    private BufferedImage bufferedImage;
    private volatile boolean bufferedDirty;
    
    public RenderSupport(ViewTexture view) {
        owner = view;
    }

    /**
     * @return the rendered
     */
    public RenderedImage getRenderedAsSource() {
        if (dirty) {
            return null;
        }
        return renderedAsSource;
    }
    
    public BufferedImage getBufferedImage() {
        if (bufferedDirty) {
            return null;
        }
        return bufferedImage;
    }

    /**
     * @param dirty the dirty to set
     */
    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        bufferedDirty = dirty;
    }
    
    public void startRendering(final ActionListener listener) {
        startRendering(listener, true);
    }

    public void startRendering(final ActionListener listener, final boolean renderAsRaw) {
        final String msg = renderAsRaw ? "renderedAsSource" : "renderedAsBuffered";

        final TextureData.Format format = owner.getParentImageSeries().getTextureData().getFormat();
        RenderHelper helper = new RenderHelper(owner,
                new RenderResultListener() {
            @Override
            public void onRenderResultReceived(RenderResult renderResult) {
                try {
                    if (renderAsRaw) {
                        ByteBuffer asByteBuffer = renderResult.asByteBuffer();
                        if (TextureData.Format.Byte.equals(format)) {
                            renderedAsSource = make8BitsImage(asByteBuffer, owner.getBounds());
                        } else {
                            renderedAsSource = makeBufferedImage(asByteBuffer,
                                    owner.getBounds());
                        }
                        dirty = false;
                        
                    } else {
                        BufferedImage asBuff = renderResult.asBufferedImage();
                        bufferedImage = asBuff;
                        
                        bufferedDirty = false;
                    }
                    
                    listener.actionPerformed(new ActionEvent(renderedAsSource, 0, msg));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }

            }
        }, renderAsRaw);

        helper.getParametersCanvas().setRotationOffset(0);
        helper.getParametersCanvas().flippedHorizontally = false;
        helper.getParametersCanvas().flippedVertically = false;
        helper.renderFrame();
    }
    
    private static RenderedImage makeBufferedImage(final ByteBuffer asByteBuffer,
            final Rectangle canvasBounds) {
        //dimensoes da imagem
        int width = canvasBounds.width;
        int height = canvasBounds.height;
        

        //TODO: make work for all datatypes possible (see DicomImageIO
//        dataType =
//                allocated <= 8 ? DataBuffer.TYPE_BYTE : ds.getInt(Tag.PixelRepresentation) != 0 ? DataBuffer.TYPE_SHORT
//                    : DataBuffer.TYPE_USHORT;
//            if (allocated > 16 && samples == 1) {
//                dataType = DataBuffer.TYPE_INT;
//            }
        
        //DataBufferInt dbuffer = new DataBufferInt(width * height);
            DataBufferShort dbuffer = new DataBufferShort(width * height);
            
            //asByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < dbuffer.getSize(); i++) {
                    int value = (int) asByteBuffer.getChar();
                    dbuffer.setElem(i, value);
                }
        


//        SampleModel sm = new PixelInterleavedSampleModel(
//                DataBuffer.TYPE_USHORT, width, height, 1, width, new int[] {0});
//        
//        WritableRaster wr = Raster.createWritableRaster(sm, dbuffer, new Point());
// 
//        final BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);
//        img.setData(wr);
        
        
        //        SampleModel sampleModel = RasterFactory.createBandedSampleModel(
//                DataBuffer.TYPE_BYTE,width,height,1);
 
           int dataType = DataBuffer.TYPE_SHORT;
   
        ColorSpace cs = ColorSpace.getInstance(ColorSpace.CS_GRAY);
   
        int[] bits = new int[1];
        Arrays.fill(bits, 16);
        ComponentColorModel cm = new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, dataType);

       SampleModel sm = new PixelInterleavedSampleModel(dataType, width, height, 1, width, new int[] {0});

        Raster raster = RasterFactory.createWritableRaster(sm, dbuffer, new Point(0, 0));
        // cria uma TiledImage usando o SampleModel e o ColorModel
        final TiledImage img = new TiledImage(0, 0, width, height, 0, 0, sm, cm);
        // seta o Raster que contém o dataBuffer na tiledImage
        img.setData(raster);
        
        final PlanarImage pimg = ImageFiler.tileImage(img);

         return pimg;
         
    }
    
    private RenderedImage make8BitsImage(ByteBuffer asByteBuffer, Rectangle canvasBounds) {
        //dimensoes da imagem
        int width = canvasBounds.width;
        int height = canvasBounds.height;
        
        DataBufferByte dbuffer = new DataBufferByte(asByteBuffer.array(), width * height);
        
        // cria o SampleModel com o tipo byte
        SampleModel sampleModel = RasterFactory.createBandedSampleModel(
        DataBuffer.TYPE_BYTE,width,height,1);
        // cria o ColorModel a partir do sampleModel
        ColorModel colorModel = PlanarImage.createColorModel(sampleModel);
        // cria o Raster
        Raster raster = RasterFactory.createWritableRaster(sampleModel,
        dbuffer,new Point(0,0));
        // cria uma TiledImage usando o SampleModel e o ColorModel
        TiledImage tiledImage = new TiledImage(0,0,width,height,0,0,
        sampleModel,colorModel);
        // seta o Raster que contém o dataBuffer na tiledImage
        tiledImage.setData(raster);
        
        return tiledImage;
        
    }
    
    //TODO: make work for all datatypes
//    ByteBuffer bb = renderResult.asByteBuffer();
//            int i;
//
//            DataBufferUShort dbus = new DataBufferUShort(512*512);
//            i = 0;
//            while(bb.hasRemaining()) {
//                dbus.setElem(i++, (int)bb.getChar());
//            }
//
//            DataBufferShort dbs = new DataBufferShort(512*512);
//            i = 0;
//            while(bb.hasRemaining()) {
//                dbus.setElem(i++, (int)bb.getChar() - 32768);
//            }
//
//            DataBufferByte dbb = new DataBufferByte(512*512);
//            i = 0;
//            while(bb.hasRemaining()) {
//                dbus.setElem(i++, bb.get());
//            }

    

    
}
