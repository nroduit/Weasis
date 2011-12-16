package org.dcm4che2.imageioimpl.plugins.dcm;

import java.awt.Transparency;
import java.awt.color.ColorSpace;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.SampleModel;

public class PartialComponentColorModel extends ColorModel {

    int subsampleX, subsampleY;

    public PartialComponentColorModel(ColorSpace cspace, int subsampleX, int subsampleY) {
        super(24, new int[] { 8, 8, 8 }, cspace, false, false, Transparency.OPAQUE, DataBuffer.TYPE_BYTE);
        this.subsampleX = subsampleX;
        this.subsampleY = subsampleY;
    }

    @Override
    public boolean isCompatibleRaster(Raster raster) {
        return isCompatibleSampleModel(raster.getSampleModel());
    }

    @Override
    public boolean isCompatibleSampleModel(SampleModel sm) {
        return sm instanceof PartialComponentSampleModel;
    }

    @Override
    public SampleModel createCompatibleSampleModel(int w, int h) {
        return new PartialComponentSampleModel(w, h, subsampleX, subsampleY);
    }

    @Override
    public int getAlpha(int pixel) {
        return 255;
    }

    @Override
    public int getBlue(int pixel) {
        return pixel & 0xFF;
    }

    @Override
    public int getGreen(int pixel) {
        return pixel & 0xFF00;
    }

    @Override
    public int getRed(int pixel) {
        return pixel & 0xFF0000;
    }

    @Override
    public int getAlpha(Object inData) {
        return 255;
    }

    @Override
    public int getBlue(Object inData) {
        return getRGB(inData) & 0xFF;
    }

    @Override
    public int getGreen(Object inData) {
        return (getRGB(inData) >> 8) & 0xFF;
    }

    @Override
    public int getRed(Object inData) {
        return getRGB(inData) >> 16;
    }

    @Override
    public int getRGB(Object inData) {
        byte[] ba = (byte[]) inData;
        ColorSpace cs = getColorSpace();
        float[] fba = new float[] { (ba[0] & 0xFF) / 255f, (ba[1] & 0xFF) / 255f, (ba[2] & 0xFF) / 255f };
        float[] rgb = cs.toRGB(fba);
        int ret = (((int) (rgb[0] * 255)) << 16) | (((int) (rgb[1] * 255)) << 8) | (((int) (rgb[2] * 255)));
        return ret;
    }

    public String toString(float[] rgb) {
        return "" + rgb[0] + "," + rgb[1] + "," + rgb[2];
    }

    public String toString(byte[] rgb) {
        return "" + rgb[0] + "," + rgb[1] + "," + rgb[2];
    }

    @Override
    public int[] getComponents(int pixel, int[] components, int offset) {
        if (components == null) {
            components = new int[offset + 1];
        }

        components[offset + 0] = (pixel & ((1 << 8) - 1));
        return components;
    }
}
