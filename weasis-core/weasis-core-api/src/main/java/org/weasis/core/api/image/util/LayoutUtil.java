/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.Rectangle;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.IndexColorModel;
import java.awt.image.MultiPixelPackedSampleModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;

/**
 * The Class LayoutUtil.
 *
 * @author Nicolas Roduit
 */
public class LayoutUtil {

    private LayoutUtil() {
    }

    public static SampleModel createBinarySampelModel(int tileWidth, int tileHeight) {
        return new MultiPixelPackedSampleModel(DataBuffer.TYPE_BYTE, tileWidth, tileHeight, 1);
    }

    public static SampleModel createBinarySampelModel() {
        return createBinarySampelModel(ImageFiler.TILESIZE, ImageFiler.TILESIZE);
    }

    public static WritableRaster createCompatibleRaster(RenderedImage img, Rectangle region) {
        Rectangle rect;
        if (region == null) { // copy the entire image
            rect = ImageToolkit.getBounds(img);
        } else {
            rect = region.intersection(ImageToolkit.getBounds(img));
            if (rect.isEmpty()) {
                return null;
            }
        }

        SampleModel sm = img.getSampleModel();
        if (sm.getWidth() != rect.width || sm.getHeight() != rect.height) {
            sm = sm.createCompatibleSampleModel(rect.width, rect.height);
        }

        WritableRaster raster = Raster.createWritableRaster(sm, rect.getLocation());

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
