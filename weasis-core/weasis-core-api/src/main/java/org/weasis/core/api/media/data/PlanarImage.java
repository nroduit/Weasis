package org.weasis.core.api.media.data;

import org.opencv.core.Size;

public interface PlanarImage extends ImageSize {

    // javadoc: Mat::channels()
    int channels();

    // javadoc: Mat::dims()
    int dims();

    // javadoc: Mat::depth()
    int depth();

    // javadoc: Mat::elemSize()
    long elemSize();

    // javadoc: Mat::elemSize1()
    long elemSize1();

    // javadoc: Mat::release()
    void release();

    // javadoc: Mat::size()
    Size size();

    // javadoc: Mat::type()
    int type();

    // javadoc:Mat::height()
    int height();

    // javadoc:Mat::width()
    int width();

    double[] get(int x, int y);

    int get(int i, int j, byte[] pixelData);

    int get(int i, int j, short[] data);
    
    int get(int i, int j, int[] data);

    int get(int i, int j, float[] data);
    
    int get(int i, int j, double[] data);
}