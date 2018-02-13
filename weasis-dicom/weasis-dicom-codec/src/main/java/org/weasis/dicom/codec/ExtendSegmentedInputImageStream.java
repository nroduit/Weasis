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
package org.weasis.dicom.codec;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import javax.imageio.stream.ImageInputStream;

import org.dcm4che3.data.Fragments;
import org.dcm4che3.imageio.stream.SegmentedInputImageStream;

public class ExtendSegmentedInputImageStream extends SegmentedInputImageStream {
    private final File file;
    private final long[] segmentPositions;
    private final long[] segmentLengths;

    public ExtendSegmentedInputImageStream(ImageInputStream stream, Fragments pixeldataFragments, int frameIndex)
        throws IOException {
        super(stream, pixeldataFragments, frameIndex);
        this.file = null;
        this.segmentPositions = null;
        this.segmentLengths = null;
    }

    public ExtendSegmentedInputImageStream(ImageInputStream stream, File file, long[] segmentPositions,
        int[] segmentLengths) throws IOException {
        super(stream, segmentPositions, segmentLengths);
        this.file = file;
        this.segmentPositions = segmentPositions;
        this.segmentLengths = segmentLengths == null ? null : Arrays.stream(segmentLengths).asLongStream().toArray();
    }

    public long[] getSegmentPositions() {
        return segmentPositions;
    }

    public long[] getSegmentLengths() {
        return segmentLengths;
    }

    public File getFile() {
        return file;
    }
}
