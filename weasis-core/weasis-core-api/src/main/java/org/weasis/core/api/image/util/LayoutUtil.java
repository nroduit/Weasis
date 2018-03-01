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

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.PlanarImage;
import javax.media.jai.RasterFactory;
import javax.media.jai.TileCache;

/**
 * The Class LayoutUtil.
 *
 * @author Nicolas Roduit
 */
public class LayoutUtil {

    public static RenderingHints BORDER_COPY =
        new RenderingHints(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

    private LayoutUtil() {
    }

    public static ImageLayout getImageLayoutHint(RenderingHints renderinghints) {
        if (renderinghints == null) {
            return null;
        } else {
            return (ImageLayout) renderinghints.get(JAI.KEY_IMAGE_LAYOUT);
        }
    }

    public static TileCache getTileCacheHint(RenderingHints renderinghints) {
        if (renderinghints == null) {
            return null;
        } else {
            return (TileCache) renderinghints.get(JAI.KEY_TILE_CACHE);
        }
    }

    public static BorderExtender getBorderExtenderHint(RenderingHints renderinghints) {
        if (renderinghints == null) {
            return null;
        } else {
            return (BorderExtender) renderinghints.get(JAI.KEY_BORDER_EXTENDER);
        }
    }

    public static RenderingHints createTiledLayoutHints() {
        ImageLayout layout = new ImageLayout();
        layout.setTileWidth(ImageFiler.TILESIZE);
        layout.setTileHeight(ImageFiler.TILESIZE);
        return new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
    }

    public static RenderingHints createTiledLayoutHints(RenderedImage source) {
        ImageLayout layout = new ImageLayout(source);
        layout.setTileWidth(ImageFiler.TILESIZE);
        layout.setTileHeight(ImageFiler.TILESIZE);
        return new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
    }

    private static RenderingHints createRenderedImage(ColorSpace cs, int[] bits) {
        ColorModel colorModel =
            new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        ImageLayout layout = new ImageLayout();
        layout.setColorModel(colorModel);
        layout.setSampleModel(colorModel.createCompatibleSampleModel(ImageFiler.TILESIZE, ImageFiler.TILESIZE));
        return new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
    }

    public static RenderingHints createGrayRenderedImage() {
        return createRenderedImage(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 });
    }

    public static RenderingHints createColorRenderedImage() {
        return createRenderedImage(ColorSpace.getInstance(ColorSpace.CS_sRGB), new int[] { 8, 8, 8 });
    }

    public static RenderingHints createBinaryRenderedImage(RenderingHints oldhints) {
        // zero to zero and unity to 255; force SampleModel to be bilevel.
        ImageLayout layout = getImageLayoutHint(oldhints);
        if (layout == null) {
            layout = new ImageLayout();
        }
        layout.setColorModel(new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 }, false,
            false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE));
        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
        oldhints.add(hints);
        oldhints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
        return hints;
    }

    public static RenderingHints createBinaryRenderedImage() {
        // zero to zero and unity to 255; force SampleModel to be bilevel.
        ImageLayout layout = new ImageLayout();
        layout.setSampleModel(createBinarySampelModel());
        layout.setColorModel(createBinaryIndexColorModel());
        RenderingHints hints = new RenderingHints(JAI.KEY_TRANSFORM_ON_COLORMAP, Boolean.FALSE);
        hints.add(new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout));
        return hints;
    }

    public static SampleModel createBinarySampelModel(int tileWidth, int tileHeight) {
        return new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 1);
    }

    public static SampleModel createBinarySampelModel() {
        return createBinarySampelModel(ImageFiler.TILESIZE, ImageFiler.TILESIZE);
    }

    public static WritableRaster createCompatibleRaster(PlanarImage img, Rectangle region) {
        Rectangle rect;
        if (region == null) { // copy the entire image
            rect = img.getBounds();
        } else {
            rect = region.intersection(img.getBounds());
            if (rect.isEmpty()) {
                return null;
            }
        }

        SampleModel sm = img.getSampleModel();
        if (sm.getWidth() != rect.width || sm.getHeight() != rect.height) {
            sm = sm.createCompatibleSampleModel(rect.width, rect.height);
        }

        WritableRaster raster = RasterFactory.createWritableRaster(sm, rect.getLocation());

        // int startTileX = img.XToTileX(rect.x);
        // int startTileY = img.YToTileY(rect.y);
        // int endTileX = img.XToTileX(rect.x + rect.width - 1);
        // int endTileY = img.YToTileY(rect.y + rect.height - 1);
        //
        // SampleModel[] sampleModels = { img.getSampleModel() };
        // int tagID = RasterAccessor.findCompatibleTag(sampleModels, raster.getSampleModel());
        //
        // RasterFormatTag srcTag = new RasterFormatTag(img.getSampleModel(), tagID);
        // RasterFormatTag dstTag = new RasterFormatTag(raster.getSampleModel(), tagID);
        //
        // for (int ty = startTileY; ty <= endTileY; ty++) {
        // for (int tx = startTileX; tx <= endTileX; tx++) {
        // Raster tile = img.getTile(tx, ty);
        // Rectangle subRegion = rect.intersection(tile.getBounds());
        //
        // RasterAccessor s = new RasterAccessor(tile, subRegion, srcTag, img.getColorModel());
        // RasterAccessor d = new RasterAccessor(raster, subRegion, dstTag, null);
        // ImageUtil.copyRaster(s, d);
        // }
        // }
        return raster;
    }

    public static ColorModel createBinaryIndexColorModel() {
        // 0 -> 0x00 (black), 1 -> 0xff (white)
        byte[] comp = new byte[] { (byte) 0x00, (byte) 0xFF };
        return new IndexColorModel(1, 2, comp, comp, comp);
    }
}
