/*******************************************************************************
 * Copyright (c) 2015 Weasis Team.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.image.jni;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.media.imageio.stream.SegmentedImageInputStream;

public class FileStreamSegment {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileStreamSegment.class);

    private final RandomAccessFile file;
    private final int fileID;
    private final long[] segPosition;
    private final int[] segLength;

    private FileStreamSegment(RandomAccessFile fdes, int fileID, long[] startPos, int[] length) {
        this.file = fdes;
        this.fileID = fileID;
        this.segPosition = startPos;
        this.segLength = length;
    }

    public RandomAccessFile getFile() {
        return file;
    }

    public int getFileID() {
        return fileID;
    }

    public long[] getSegPosition() {
        return segPosition;
    }

    public int[] getSegLength() {
        return segLength;
    }

    public MappedByteBuffer getDirectByteBuffer(int segment) throws IOException {
        if (segPosition == null || segPosition.length <= segment || segLength == null || segLength.length <= segment) {
            throw new IllegalArgumentException("Invalid position of the file to read!");
        }
        MappedByteBuffer buffer =
            file.getChannel().map(FileChannel.MapMode.READ_ONLY, segPosition[segment], segLength[segment]);
        // For performance reason, native order is preferred.
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    public ByteBuffer getDirectByteBuffer(int startSeg, int endSeg) throws IOException {
        if (segPosition == null || segPosition.length <= endSeg || segLength == null || segLength.length <= endSeg) {
            throw new IllegalArgumentException("Invalid position of the file to read!");
        }
        int length = segLength[startSeg];

        // DICOM PS 3.5.8.2 Handle frame with multiple fragments
        if (startSeg < endSeg) {
            for (int i = startSeg + 1; i <= endSeg; i++) {
                length += segLength[i];
            }
            ByteBuffer buffer = ByteBuffer.allocateDirect(length);
            buffer.order(ByteOrder.nativeOrder());
            for (int i = startSeg; i <= endSeg; i++) {
                buffer.put(file.getChannel().map(FileChannel.MapMode.READ_ONLY, segPosition[i], segLength[i]));
            }
            buffer.rewind();
            return buffer;
        }

        MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_ONLY, segPosition[startSeg], length);
        buffer.order(ByteOrder.nativeOrder());
        return buffer;
    }

    public static int getFileIDfromFileDescriptor(FileDescriptor fileDS) {
        if (fileDS != null && fileDS.valid()) {
            try {
                Field f_fileID = FileDescriptor.class.getDeclaredField("fd");
                if (f_fileID != null) {
                    f_fileID.setAccessible(true);
                    return f_fileID.getInt(fileDS);
                }
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("getFileIDfromFileDescriptor exception", e); //$NON-NLS-1$
                } else {
                    LOGGER.error(e.getMessage());
                }
            }
        }
        // not valid id
        return -1;
    }

    public static void adaptParametersFromStream(ImageInputStream iis, NativeImage mlImage) throws IOException {
        if (mlImage == null) {
            return;
        }

        if (iis instanceof org.dcm4che3.imageio.stream.SegmentedImageInputStream) {
            try {
                Field f_stream = org.dcm4che3.imageio.stream.SegmentedImageInputStream.class.getDeclaredField("stream");
                Field f_curSegment =
                    org.dcm4che3.imageio.stream.SegmentedImageInputStream.class.getDeclaredField("curSegment");
                if (f_curSegment != null && f_stream != null) {
                    f_curSegment.setAccessible(true);
                    f_stream.setAccessible(true);
                    // Current implementation

                    FileImageInputStream fstream = (FileImageInputStream) f_stream.get(iis);
                    Field f_raf = FileImageInputStream.class.getDeclaredField("raf");
                    if (f_raf != null) {
                        f_raf.setAccessible(true);
                        Integer curSegment = (Integer) f_curSegment.get(iis);
                        if (curSegment != null && curSegment >= 0) {
                            Field f_segmentPositionsList =
                                org.dcm4che3.imageio.stream.SegmentedImageInputStream.class
                                    .getDeclaredField("segmentPositionsList");
                            Field f_segmentLengths =
                                org.dcm4che3.imageio.stream.SegmentedImageInputStream.class
                                    .getDeclaredField("segmentLengths");
                            if (f_segmentPositionsList != null && f_segmentLengths != null) {
                                f_segmentPositionsList.setAccessible(true);
                                f_segmentLengths.setAccessible(true);
                                long[] segmentPositionsList = (long[]) f_segmentPositionsList.get(iis);
                                int[] segmentLengths = (int[]) f_segmentLengths.get(iis);
                                RandomAccessFile raf = (RandomAccessFile) f_raf.get(fstream);
                                /*
                                 * PS 3.5.8.2 Though a fragment may not contain encoded data from more than one frame,
                                 * the encoded data from one frame may span multiple fragments. See note in Section 8.2.
                                 */
                                mlImage.setStreamSegment(new FileStreamSegment(raf, FileStreamSegment
                                    .getFileIDfromFileDescriptor(raf.getFD()), Arrays.copyOfRange(segmentPositionsList,
                                    curSegment, segmentPositionsList.length), Arrays.copyOfRange(segmentLengths,
                                    curSegment, segmentLengths.length)));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("getFileDescriptor from SegmentedInputImageStream", e); //$NON-NLS-1$
                } else {
                    LOGGER.error(e.getMessage());
                }
            }
        } else if (iis instanceof FileImageInputStream) {
            RandomAccessFile raf = getRandomAccessFile((FileImageInputStream) iis);
            if (raf != null) {
                mlImage.setStreamSegment(new FileStreamSegment(raf, FileStreamSegment.getFileIDfromFileDescriptor(raf
                    .getFD()), new long[] { 0 }, new int[] { (int) raf.length() }));
            }
        } else if (iis instanceof SegmentedImageInputStream) {
            throw new IllegalArgumentException("No adaptor implemented yet for SegmentedImageInputStream");
            // try {
            // Field f_stream = SegmentedImageInputStream.class.getDeclaredField("stream");
            // Field f_mapper = SegmentedImageInputStream.class.getDeclaredField("mapper");
            // if (f_mapper != null && f_stream != null) {
            // f_mapper.setAccessible(true);
            // f_stream.setAccessible(true);
            // FileImageInputStream fstream = (FileImageInputStream) f_stream.get(iis);
            // Field f_raf = FileImageInputStream.class.getDeclaredField("raf");
            // if (f_raf != null) {
            // f_raf.setAccessible(true);
            // ItemParser mapper = (ItemParser) f_mapper.get(iis);
            // Field f_parser = ItemParser.class.getDeclaredField("firstItemOfFrame");
            // if (f_parser != null) {
            // f_parser.setAccessible(true);
            // ArrayList<ItemParser.Item> items = (ArrayList<ItemParser.Item>) f_parser.get(mapper);
            // Item item = items.get(0);
            //
            // RandomAccessFile raf = (RandomAccessFile) f_raf.get(fstream);
            // segment =
            // new FileStreamSegment(FileStreamSegment.getFileIDfromFileDescriptor(raf.getFD()),
            // item.startPos, item.length, item.offset);
            // }
            // }
            // }
            // } catch (Exception e) {
            // // TODO Auto-generated catch block
            // e.printStackTrace();
            // }
        } else if (iis instanceof FileCacheImageInputStream) {
            throw new IllegalArgumentException("No adaptor implemented yet for FileCacheImageInputStream");
        } else if (iis instanceof MemoryCacheImageInputStream) {
            // TODO load in inputBuffer
            throw new IllegalArgumentException("No adaptor implemented yet for MemoryCacheImageInputStream");
        }
    }

    public static RandomAccessFile getRandomAccessFile(FileImageInputStream fstream) {
        try {
            Field f_raf = FileImageInputStream.class.getDeclaredField("raf");
            if (f_raf != null) {
                f_raf.setAccessible(true);
                return (RandomAccessFile) f_raf.get(fstream);
            }

        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("getFileDescriptor from FileImageInputStream", e); //$NON-NLS-1$
            } else {
                LOGGER.error(e.getMessage());
            }
        }
        return null;
    }

    public static RandomAccessFile getRandomAccessFile(FileImageOutputStream fstream) {
        try {
            Field f_raf = FileImageOutputStream.class.getDeclaredField("raf");
            if (f_raf != null) {
                f_raf.setAccessible(true);
                return (RandomAccessFile) f_raf.get(fstream);
            }

        } catch (Exception e) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.error("getFileDescriptor from FileImageOutputStream", e); //$NON-NLS-1$
            } else {
                LOGGER.error(e.getMessage());
            }
        }
        return null;
    }

}
