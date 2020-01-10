/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.opencv.data;

import org.opencv.core.Mat;
import org.opencv.core.Size;

public interface PlanarImage extends ImageSize, AutoCloseable {

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

    double[] get(int row, int column);

    int get(int i, int j, byte[] pixelData);

    int get(int i, int j, short[] data);

    int get(int i, int j, int[] data);

    int get(int i, int j, float[] data);

    int get(int i, int j, double[] data);

    void assignTo(Mat dstImg);

    Mat toMat();

    ImageCV toImageCV();
    
    @Override
    void close();
    // TODO required Java 8

    // default Mat toMat() {
    // if (this instanceof Mat) {
    // return (Mat) this;
    // } else {
    // throw new IllegalAccessError("Not implemented yet");
    // }
    // }
    //
    // default ImageCV toImageCV() {
    // if (this instanceof Mat) {
    // if (this instanceof ImageCV) {
    // return (ImageCV) this;
    // }
    // ImageCV dstImg = new ImageCV();
    // this.assignTo(dstImg);
    // return dstImg;
    // } else {
    // throw new IllegalAccessError("Not implemented yet");
    // }
    // }

}