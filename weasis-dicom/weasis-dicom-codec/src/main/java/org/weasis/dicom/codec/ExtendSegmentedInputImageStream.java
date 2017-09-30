package org.weasis.dicom.codec;

import java.io.File;
import java.util.Arrays;

public class ExtendSegmentedInputImageStream {
    private final File file;
    private final long[] segmentPositions;
    private final long[] segmentLengths;

    public ExtendSegmentedInputImageStream(File file, long[] segmentPositions, int[] segmentLengths) {
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
