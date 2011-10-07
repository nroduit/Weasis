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
package org.weasis.core.ui.graphic;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.util.ArrayList;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

import org.weasis.core.api.image.OperationsManager;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.graphic.model.Layer;

/**
 * The Class RenderedImageLayer.
 * 
 * @author Nicolas Roduit
 */
public class RenderedImageLayer<E extends ImageElement> implements Layer, ImageLayer<E> {

    private final OperationsManager operations;
    private OperationsManager preprocessing;
    private E sourceImage;
    private RandomIter readIterator;
    private ArrayList<ImageLayerChangeListener<E>> listenerList;
    private boolean buildIterator = false;
    private RenderedImage displayImage;
    private boolean visible = true;

    // private final Image2DViewer view2DPane;

    public RenderedImageLayer(OperationsManager manager, boolean buildIterator) {
        this(manager, null, null, buildIterator);
    }

    public RenderedImageLayer(OperationsManager manager, E image, OperationsManager preprocessing, boolean buildIterator) {
        if (manager == null) {
            throw new IllegalArgumentException("OperationsManager argument cannot be null"); //$NON-NLS-1$
        }
        this.operations = manager;
        // tileListener = new TileListener();
        this.sourceImage = image;
        this.preprocessing = preprocessing;
        this.buildIterator = buildIterator;
        // this.view2DPane = view2DPane;
        this.listenerList = new ArrayList<ImageLayerChangeListener<E>>();
        if (image != null) {
            // cache(operations.updateAllOperations());
            displayImage = operations.updateAllOperations();
            fireImageChanged();
        }
    }

    @Override
    public RandomIter getReadIterator() {
        return readIterator;
    }

    @Override
    public E getSourceImage() {
        return sourceImage;
    }

    @Override
    public RenderedImage getDisplayImage() {
        return displayImage;
    }

    public OperationsManager getPreprocessing() {
        return preprocessing;
    }

    @Override
    public void setImage(E image, OperationsManager preprocessing) {
        boolean init = image != null && !image.equals(this.sourceImage);
        this.sourceImage = image;
        this.preprocessing = preprocessing;
        if (init) {
            displayImage = operations.updateAllOperations();
            // cache(operations.updateAllOperations());
            fireImageChanged();
        } else if (image == null) {
            displayImage = null;
            fireImageChanged();
        }
    }

    public void drawImage(Graphics2D g2d) {
        // Get the clipping rectangle
        if (!visible || displayImage == null) {
            return;
        }
        Rectangle clipBounds = g2d.getClipBounds();
        if (clipBounds == null) {
            clipBounds =
                new Rectangle(displayImage.getMinX(), displayImage.getMinY(), displayImage.getWidth(),
                    displayImage.getHeight());
        }
        Shape clip = g2d.getClip();
        if (clip instanceof Rectangle2D) {
            Rectangle2D rect =
                new Rectangle2D.Double(displayImage.getMinX(), displayImage.getMinY(), displayImage.getWidth() - 1,
                    displayImage.getHeight() - 1);
            rect = rect.createIntersection((Rectangle2D) clip);
            if (rect.isEmpty()) {
                return;
            }
            g2d.setClip(rect);
        }

        // final Rectangle vr = computeRect(displayImage.getImage(), clipBounds, translateX, translateY);
        // boolean tilesMissing = false;
        // for (int y = 0; y < vr.height; y++) {
        // for (int x = 0; x < vr.width; x++) {
        // final Raster r = displayImage.getTile(vr.x + x, vr.y + y);
        // final int x0 = (vr.x + x) * tileWidth;
        // final int y0 = (vr.y + y) * tileHeight;
        // if (r != null) {
        // Raster raster = r;
        // if (r.getMinX() != 0 || r.getMinY() != 0) {
        // raster = r.createTranslatedChild(0, 0);
        // }
        // final ColorModel srcCM = displayImage.getColorModel();
        // final BufferedImage bi = new BufferedImage(srcCM, (WritableRaster) raster, false, null);
        // g2d.drawImage(bi, x0 + translateX, y0 + translateY, null);
        // }
        // else {
        // tilesMissing = true;
        // }
        // }
        // }
        // g2d.setClip(clip);
        // if (tilesMissing) {
        // orderTiles();
        // }

        int txmin = XtoTileX(clipBounds.x);
        txmin = Math.max(txmin, displayImage.getMinTileX());
        txmin = Math.min(txmin, displayImage.getMinTileX() + displayImage.getNumXTiles() - 1);
        int txmax = XtoTileX(clipBounds.x + clipBounds.width - 1);
        txmax = Math.max(txmax, displayImage.getMinTileX());
        txmax = Math.min(txmax, displayImage.getMinTileX() + displayImage.getNumXTiles() - 1);
        int tymin = YtoTileY(clipBounds.y);
        tymin = Math.max(tymin, displayImage.getMinTileY());
        tymin = Math.min(tymin, displayImage.getMinTileY() + displayImage.getNumYTiles() - 1);
        int tymax = YtoTileY(clipBounds.y + clipBounds.height - 1);
        tymax = Math.max(tymax, displayImage.getMinTileY());
        tymax = Math.min(tymax, displayImage.getMinTileY() + displayImage.getNumYTiles() - 1);
        final ColorModel cm = displayImage.getColorModel();
        final SampleModel sm = displayImage.getSampleModel();
        if (sm != null && cm != null) {
            // Loop over tiles within the clipping region
            for (int tj = tymin; tj <= tymax; tj++) {
                for (int ti = txmin; ti <= txmax; ti++) {
                    int tx = TileXtoX(ti);
                    int ty = TileYtoY(tj);
                    Raster tile = null;
                    try {
                        tile = displayImage.getTile(ti, tj);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    if (tile != null) {
                        WritableRaster wr = Raster.createWritableRaster(sm, tile.getDataBuffer(), null);
                        BufferedImage bi = new BufferedImage(cm, wr, cm.isAlphaPremultiplied(), null);
                        // AffineTransform at = AffineTransform.getTranslateInstance(0, 0);
                        // if (_transform != null) {
                        // at.concatenate(_transform);
                        // }
                        g2d.drawImage(bi, tx, ty, null);
                    }
                }
            }
        }
        g2d.setClip(clip);

    }

    // public void drawImage(Graphics2D g2d) {
    // // Get the clipping rectangle
    // if (image == null || image.getImageResult() == null) {
    // return;
    // }
    // Rectangle clipBounds = g2d.getClipBounds();
    // if (clipBounds == null) {
    // clipBounds = new Rectangle(image.getMinX(), image.getMinY(), image.getWidth(), image.getHeight());
    // }
    // Shape clip = g2d.getClip();
    // if (clip instanceof Rectangle2D) {
    // // efface le débordement des derniers tiles en x et y
    // Rectangle2D rect = new Rectangle2D.Double(image.getMinX(), image.getMinY(), image.getWidth() - 1, image
    // .getHeight() - 1);
    // rect = rect.createIntersection((Rectangle2D) clip);
    // if (rect.isEmpty()) {
    // return;
    // }
    // g2d.setClip(rect);
    // }
    // // Determine the extent of the clipping region in tile coordinates.
    // int txmin, txmax, tymin, tymax;
    // int ti, tj;
    // txmin = XtoTileX(clipBounds.x);
    // txmin = Math.max(txmin, image.getMinTileX());
    // txmin = Math.min(txmin, image.getMinTileX() + image.getNumXTiles() - 1);
    // txmax = XtoTileX(clipBounds.x + clipBounds.width - 1);
    // txmax = Math.max(txmax, image.getMinTileX());
    // txmax = Math.min(txmax, image.getMinTileX() + image.getNumXTiles() - 1);
    // tymin = YtoTileY(clipBounds.y);
    // tymin = Math.max(tymin, image.getMinTileY());
    // tymin = Math.min(tymin, image.getMinTileY() + image.getNumYTiles() - 1);
    // tymax = YtoTileY(clipBounds.y + clipBounds.height - 1);
    // tymax = Math.max(tymax, image.getMinTileY());
    // tymax = Math.min(tymax, image.getMinTileY() + image.getNumYTiles() - 1);
    // final ColorModel cm = image.getColorModel();
    // final SampleModel sm = image.getSampleModel();
    // // Loop over tiles within the clipping region
    // for (tj = tymin; tj <= tymax; tj++) {
    // for (ti = txmin; ti <= txmax; ti++) {
    // int tx = TileXtoX(ti);
    // int ty = TileYtoY(tj);
    // Raster tile = null;
    // try {
    // tile = image.getTile(ti, tj);
    // }
    // catch (Exception ex) {
    // ex.printStackTrace();
    // }
    // if (tile != null) {
    // WritableRaster wr = Raster.createWritableRaster(sm, tile.getDataBuffer(), null);
    // BufferedImage bi = new BufferedImage(cm, wr, cm.isAlphaPremultiplied(), null);
    // AffineTransform at = AffineTransform.getTranslateInstance(0, 0);
    // if (_transform != null) {
    // at.concatenate(_transform);
    // }
    // g2d.drawRenderedImage(bi, at);
    // }
    // }
    // }
    // g2d.setClip(clip);
    // }

    private int XtoTileX(int x) {
        if (displayImage == null) {
            return 0;
        }
        return PlanarImage.XToTileX(x, displayImage.getTileGridXOffset(), displayImage.getTileWidth());
    }

    private int YtoTileY(int y) {
        if (displayImage == null) {
            return 0;
        }
        return PlanarImage.YToTileY(y, displayImage.getTileGridYOffset(), displayImage.getTileHeight());
    }

    private int TileXtoX(int tx) {
        if (displayImage == null) {
            return 0;
        }
        return tx * displayImage.getTileWidth() + displayImage.getTileGridXOffset();
    }

    private int TileYtoY(int ty) {
        if (displayImage == null) {
            return 0;
        }
        return ty * displayImage.getTileHeight() + displayImage.getTileGridYOffset();
    }

    public void dispose() {
        sourceImage = null;
        listenerList.clear();
        listenerList = null;
    }

    public void addLayerChangeListener(ImageLayerChangeListener listener) {
        if (listener != null && !listenerList.contains(listener)) {
            listenerList.add(listener);
        }
    }

    public void fireImageChanged() {
        if (displayImage == null) {
            operations.clearCacheNodes();
        }
        PlanarImage img = null;
        if (buildIterator && sourceImage != null) {
            img = sourceImage.getImage(preprocessing);
        }
        readIterator = (img == null) ? null : RandomIterFactory.create(img, null);
        fireLayerChanged();
    }

    public void fireLayerChanged() {
        for (int i = 0; i < listenerList.size(); i++) {
            listenerList.get(i).handleLayerChanged(this);
        }
    }

    /**
     * Removes a layer manager listener from this layer.
     */
    public void removeLayerChangeListener(ImageLayerChangeListener listener) {
        if (listener != null) {
            listenerList.remove(listener);
        }
    }

    @Override
    public void updateImageOperation(String operation) {
        displayImage = operations.updateOperation(operation);
        fireImageChanged();
    }

    public void updateAllImageOperations() {
        displayImage = operations.updateAllOperations();
        fireImageChanged();
    }

    public OperationsManager getOperationsManager() {
        return operations;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    @Override
    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    @Override
    public int getLevel() {
        return 0;
    }

    @Override
    public void setLevel(int i) {
        // TODO Auto-generated method stub

    }

    @Override
    public AffineTransform getTransform() {
        return null;
    }

    @Override
    public void setTransform(AffineTransform transform) {
        // Does handle affine transform for image, already in operation manager
    }

}
