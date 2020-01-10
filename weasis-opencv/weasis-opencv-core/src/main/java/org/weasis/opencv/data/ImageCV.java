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
import org.opencv.core.Range;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;

public class ImageCV extends Mat implements PlanarImage {

    public ImageCV() {
        super();
    }

    public ImageCV(int rows, int cols, int type) {
        super(rows, cols, type);
    }

    public ImageCV(Size size, int type, Scalar s) {
        super(size, type, s);
    }

    public ImageCV(int rows, int cols, int type, Scalar s) {
        super(rows, cols, type, s);
    }

    public ImageCV(Mat m, Range rowRange, Range colRange) {
        super(m, rowRange, colRange);
    }

    public ImageCV(Mat m, Range rowRange) {
        super(m, rowRange);
    }

    public ImageCV(Mat m, Rect roi) {
        super(m, roi);
    }

    public ImageCV(Size size, int type) {
        super(size, type);
    }

    @Override
    public long physicalBytes() {
        return total() * elemSize();
    }

    public static Mat toMat(PlanarImage source) {
        if (source instanceof Mat) {
            return (Mat) source;
        } else {
            throw new IllegalAccessError("Not implemented yet");
        }
    }

    public static ImageCV toImageCV(Mat source) {
        if (source instanceof ImageCV) {
            return (ImageCV) source;
        }
        ImageCV dstImg = new ImageCV();
        source.assignTo(dstImg);
        return dstImg;
    }

    // TODO remove for Java 8
    @Override
    public Mat toMat() {
        if (this instanceof Mat) {
            return this;
        } else {
            throw new IllegalAccessError("Not implemented yet");
        }
    }

    @Override
    public ImageCV toImageCV() {
        if (this instanceof Mat) {
            if (this instanceof ImageCV) {
                return this;
            }
            ImageCV dstImg = new ImageCV();
            this.assignTo(dstImg);
            return dstImg;
        } else {
            throw new IllegalAccessError("Not implemented yet");
        }
    }

    @Override
    public void close() {
        this.release();
    }

}
