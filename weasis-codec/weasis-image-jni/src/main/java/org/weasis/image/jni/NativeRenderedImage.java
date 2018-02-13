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
import java.awt.Rectangle;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.IOException;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageTypeSpecifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.media.imageioimpl.common.SimpleRenderedImage;

public class NativeRenderedImage extends SimpleRenderedImage {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeRenderedImage.class);

    private Raster currentTile;
    private Point currentTileGrid;

    private NativeImageReader reader;
    private ImageReadParam param = null;
    private int imageIndex;

    public NativeRenderedImage(NativeImageReader reader, ImageReadParam param, int imageIndex) throws IOException {
        this.reader = reader;
        this.param = param;
        this.imageIndex = imageIndex;

        ImageParameters params = reader.getInfoImage(imageIndex, param);

        this.tileWidth = params.getTileWidth();
        this.tileHeight = params.getTileHeight();
        this.tileGridXOffset = params.getTileGridXOffset();
        this.tileGridYOffset = params.getTileGridYOffset();

        this.width = params.getWidth();
        this.height = params.getHeight();
        this.minX = 0;
        this.minY = 0;

        if (tileWidth < 64 || tileHeight < 64) {
            this.tileWidth = this.width;
            this.tileHeight = this.height;
        }

        ImageTypeSpecifier type = NativeImageReader.createImageType(params, null, null, null, null, null);
        colorModel = type.getColorModel();
        sampleModel = type.getSampleModel().createCompatibleSampleModel(tileWidth, tileHeight);
    }

    @Override
    public synchronized Raster getTile(int tileX, int tileY) {
        if (currentTile != null && currentTileGrid != null && currentTileGrid.x == tileX
            && currentTileGrid.y == tileY) {
            return currentTile;
        }

        try {
            long start = System.currentTimeMillis();

            boolean tiled = width != tileWidth || height != tileHeight;
            int x = tileXToX(tileX);
            int y = tileYToY(tileY);

            if (tiled) {
                param.setSourceRegion(new Rectangle(x, y, tileWidth, tileHeight));
            }
            // param.setDestinationType(new ImageTypeSpecifier(colorModel, sampleModel));

            NativeImage img = reader.getImage(imageIndex, param);

            if (img == null) {
                return null;
            }

            DataBuffer db = NativeImageReader.createDataBuffer(img);
            if (db == null) {
                return null;
            }

            currentTile = Raster.createWritableRaster(sampleModel, db, new Point(x, y));
            long stop = System.currentTimeMillis();
            LOGGER.debug("Building BufferedImage time: {} ms", (stop - start)); //$NON-NLS-1$
        } catch (IOException e) {
            LOGGER.error("Cannot retrieve tile: {}", e.getMessage());
        }

        int originalNumXTiles = getNumXTiles();
        int originalNumYTiles = getNumYTiles();
        if (tileX >= originalNumXTiles || tileY >= originalNumYTiles) {
            throw new IllegalArgumentException();
        }

        if (currentTileGrid == null) {
            currentTileGrid = new Point(tileX, tileY);
        } else {
            currentTileGrid.x = tileX;
            currentTileGrid.y = tileY;
        }

        return currentTile;
    }

    public void readAsRaster(WritableRaster raster) throws java.io.IOException {
        readSubsampledRaster(raster);
    }

    private Raster readSubsampledRaster(WritableRaster raster) throws java.io.IOException {
        throw new IOException("Subsampled not supported");
    }

}
