package org.weasis.core.api.image.cv;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.SampleModel;
import java.awt.image.TileObserver;
import java.awt.image.WritableRaster;
import java.awt.image.WritableRenderedImage;
import java.util.Hashtable;
import java.util.Objects;
import java.util.Set;
import java.util.Vector;

public class CvImage implements WritableRenderedImage {
    private int imageType = BufferedImage.TYPE_CUSTOM;
    private ColorModel colorModel;
    private final WritableRaster raster;
    private Hashtable<String, Object> properties;

    public CvImage(ColorModel colorModel, WritableRaster raster) {
        super();
        this.colorModel = colorModel;
        this.raster = raster;
    }

    @Override
    public Vector<RenderedImage> getSources() {
        return null;
    }

    @Override
    public Object getProperty(String name) {
        if (properties == null) {
            return null;
        }
        return properties.get(Objects.requireNonNull(name, "null property"));
    }

    @Override
    public String[] getPropertyNames() {
        if (properties == null || properties.isEmpty()) {
            return null;
        }
        final Set<String> keys = properties.keySet();
        return keys.toArray(new String[keys.size()]);
    }

    @Override
    public ColorModel getColorModel() {
        return colorModel;
    }

    @Override
    public SampleModel getSampleModel() {
        return raster.getSampleModel();
    }

    @Override
    public int getWidth() {
        return raster.getWidth();
    }

    @Override
    public int getHeight() {
        return raster.getHeight();
    }

    @Override
    public int getMinX() {
        return raster.getMinX();
    }

    @Override
    public int getMinY() {
        return raster.getMinY();
    }

    @Override
    public int getNumXTiles() {
        return 1;
    }

    @Override
    public int getNumYTiles() {
        return 1;
    }

    @Override
    public int getMinTileX() {
        return 0;
    }

    @Override
    public int getMinTileY() {
        return 0;
    }

    @Override
    public int getTileWidth() {
        return raster.getWidth();
    }

    @Override
    public int getTileHeight() {
        return raster.getHeight();
    }

    @Override
    public int getTileGridXOffset() {
        return raster.getSampleModelTranslateX();
    }

    @Override
    public int getTileGridYOffset() {
        return raster.getSampleModelTranslateY();
    }

    @Override
    public Raster getTile(int tileX, int tileY) {
        if (tileX == 0 && tileY == 0) {
            return raster;
        }
        throw new ArrayIndexOutOfBoundsException("BufferedImages only have" + " one tile with index 0,0");
    }

    @Override
    public Raster getData() {

        // REMIND : this allocates a whole new tile if raster is a
        // subtile. (It only copies in the requested area)
        // We should do something smarter.
        int width = raster.getWidth();
        int height = raster.getHeight();
        int startX = raster.getMinX();
        int startY = raster.getMinY();
        WritableRaster wr = Raster.createWritableRaster(raster.getSampleModel(),
            new Point(raster.getSampleModelTranslateX(), raster.getSampleModelTranslateY()));

        Object tdata = null;

        for (int i = startY; i < startY + height; i++) {
            tdata = raster.getDataElements(startX, i, width, 1, tdata);
            wr.setDataElements(startX, i, width, 1, tdata);
        }
        return wr;
    }

    @Override
    public Raster getData(Rectangle rect) {
        SampleModel sm = raster.getSampleModel();
        SampleModel nsm = sm.createCompatibleSampleModel(rect.width, rect.height);
        WritableRaster wr = Raster.createWritableRaster(nsm, rect.getLocation());
        int width = rect.width;
        int height = rect.height;
        int startX = rect.x;
        int startY = rect.y;

        Object tdata = null;

        for (int i = startY; i < startY + height; i++) {
            tdata = raster.getDataElements(startX, i, width, 1, tdata);
            wr.setDataElements(startX, i, width, 1, tdata);
        }
        return wr;
    }

    @Override
    public WritableRaster copyData(WritableRaster outRaster) {
        if (outRaster == null) {
            return (WritableRaster) getData();
        }
        int width = outRaster.getWidth();
        int height = outRaster.getHeight();
        int startX = outRaster.getMinX();
        int startY = outRaster.getMinY();

        Object tdata = null;

        for (int i = startY; i < startY + height; i++) {
            tdata = raster.getDataElements(startX, i, width, 1, tdata);
            outRaster.setDataElements(startX, i, width, 1, tdata);
        }

        return outRaster;
    }

    @Override
    public void addTileObserver(TileObserver to) {
    }

    @Override
    public void removeTileObserver(TileObserver to) {
    }

    @Override
    public WritableRaster getWritableTile(int tileX, int tileY) {
        return raster;
    }

    @Override
    public void releaseWritableTile(int tileX, int tileY) {
    }

    @Override
    public boolean isTileWritable(int tileX, int tileY) {
        if (tileX == 0 && tileY == 0) {
            return true;
        }
        throw new IllegalArgumentException("Only 1 tile in image");
    }

    @Override
    public Point[] getWritableTileIndices() {
        return new Point[] { new Point(0, 0) };
    }

    @Override
    public boolean hasTileWriters() {
        return true;
    }

    @Override
    public void setData(Raster r) {
        int width = r.getWidth();
        int height = r.getHeight();
        int startX = r.getMinX();
        int startY = r.getMinY();

        int[] tdata = null;

        // Clip to the current Raster
        Rectangle rclip = new Rectangle(startX, startY, width, height);
        Rectangle bclip = new Rectangle(0, 0, raster.getWidth(), raster.getHeight());
        Rectangle intersect = rclip.intersection(bclip);
        if (intersect.isEmpty()) {
            return;
        }
        width = intersect.width;
        height = intersect.height;
        startX = intersect.x;
        startY = intersect.y;

        // remind use get/setDataElements for speed if Rasters are
        // compatible
        for (int i = startY; i < startY + height; i++) {
            tdata = r.getPixels(startX, i, width, 1, tdata);
            raster.setPixels(startX, i, width, 1, tdata);
        }
    }
}
