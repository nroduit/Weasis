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
package org.weasis.opencv.op.tile;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

// Our algorithm need three intermediate buffers: a,b,c that
// we want to store close to each other
class MyAlgorithm extends TiledAlgorithm {
    private Mat mBuffer;
    private Mat a;
    private Mat b;
    private Mat c;

    public MyAlgorithm(int tileSize, int padding) {
        super(tileSize, padding, Core.BORDER_DEFAULT);
        int size = tileSize + padding * 2;

        // Allocate all buffer as continuous array
        mBuffer.create(size * 3, size, CvType.CV_8UC1);

        // Create views to sub-regions of mBuffer
        a = mBuffer.rowRange(0, size);
        b = mBuffer.rowRange(size, 2 * size);
        c = mBuffer.rowRange(2 * size, 3 * size);
    }

}