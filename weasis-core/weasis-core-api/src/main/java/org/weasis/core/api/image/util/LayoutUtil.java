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

import javax.media.jai.BorderExtender;
import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.TileCache;

/**
 * The Class LayoutUtil.
 * 
 * @author Nicolas Roduit
 */
public class LayoutUtil {

    public static RenderingHints BORDER_COPY =
        new RenderingHints(JAI.KEY_BORDER_EXTENDER, BorderExtender.createInstance(BorderExtender.BORDER_COPY));

    public LayoutUtil() {
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

    private static RenderingHints createRenderedImage(ColorSpace cs, int bits[]) {
        ColorModel colorModel =
            new ComponentColorModel(cs, bits, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        ImageLayout layout = new ImageLayout();
        layout.setColorModel(colorModel);
        layout.setSampleModel(colorModel.createCompatibleSampleModel(ImageFiler.TILESIZE, ImageFiler.TILESIZE));
        RenderingHints hints = new RenderingHints(JAI.KEY_IMAGE_LAYOUT, layout);
        return hints;
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
        layout.setColorModel(new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_GRAY), new int[] { 8 },
            false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE));
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

    public static ColorModel createBinaryIndexColorModel() {
        // 0xffffff -> white, 0x000000 -> black
        byte[] comp = new byte[] { (byte) 0x00, (byte) 0xFF };
        return new IndexColorModel(1, 2, comp, comp, comp);
    }
}
