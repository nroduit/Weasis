/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.image.jni;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Arrays;

import javax.imageio.stream.FileCacheImageInputStream;
import javax.imageio.stream.FileImageInputStream;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.media.imageio.stream.SegmentedImageInputStream;

public abstract class StreamSegment {
    private static final Logger LOGGER = LoggerFactory.getLogger(StreamSegment.class);

    protected final long[] segPosition;
    protected final int[] segLength;

    StreamSegment(long[] startPos, int[] length) {
        this.segPosition = startPos;
        this.segLength = length;
    }

    public static void adaptParametersFromStream(ImageInputStream iis, NativeImage mlImage) throws IOException {
        if (mlImage == null) {
            return;
        }
        // Not a good practice (should be instanceof) but necessary to remove the dependency with dcm4che lib
        if ("org.dcm4che3.imageio.stream.SegmentedInputImageStream".equals(iis.getClass().getName())) {
            try {
                Class<? extends ImageInputStream> clazz = iis.getClass();
                Field fStream = clazz.getDeclaredField("stream");
                Field fCurSegment = clazz.getDeclaredField("curSegment");
                if (fCurSegment != null && fStream != null) {
                    fCurSegment.setAccessible(true);
                    fStream.setAccessible(true);

                    FileImageInputStream fstream = (FileImageInputStream) fStream.get(iis);
                    Field fRaf = FileImageInputStream.class.getDeclaredField("raf");
                    if (fRaf != null) {
                        fRaf.setAccessible(true);
                        Integer curSegment = (Integer) fCurSegment.get(iis);
                        if (curSegment != null && curSegment >= 0) {
                            Field fSegmentPositionsList = clazz.getDeclaredField("segmentPositionsList");
                            Field fSegmentLengths = clazz.getDeclaredField("segmentLengths");
                            if (fSegmentPositionsList != null && fSegmentLengths != null) {
                                fSegmentPositionsList.setAccessible(true);
                                fSegmentLengths.setAccessible(true);
                                long[] segmentPositionsList = (long[]) fSegmentPositionsList.get(iis);
                                int[] segmentLengths = (int[]) fSegmentLengths.get(iis);
                                RandomAccessFile raf = (RandomAccessFile) fRaf.get(fstream);
                                /*
                                 * PS 3.5.8.2 Though a fragment may not contain encoded data from more than one frame,
                                 * the encoded data from one frame may span multiple fragments. See note in Section 8.2.
                                 */
                                mlImage.setStreamSegment(new FileStreamSegment(raf,
                                    FileStreamSegment.getFileIDfromFileDescriptor(raf.getFD()),
                                    Arrays.copyOfRange(segmentPositionsList, curSegment, segmentPositionsList.length),
                                    Arrays.copyOfRange(segmentLengths, curSegment, segmentLengths.length)));
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
            RandomAccessFile raf = FileStreamSegment.getRandomAccessFile((FileImageInputStream) iis);
            if (raf != null) {
                mlImage.setStreamSegment(
                    new FileStreamSegment(raf, FileStreamSegment.getFileIDfromFileDescriptor(raf.getFD()),
                        new long[] { 0 }, new int[] { (int) raf.length() }));
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
            ByteArrayInputStream stream =
                MemoryStreamSegment.getByteArrayInputStream((MemoryCacheImageInputStream) iis);
            if (stream != null) {
                byte[] b = getByte(stream);
                if (b != null) {
                    mlImage.setStreamSegment(new MemoryStreamSegment(b));
                }
                return;
            }
            throw new IllegalArgumentException("No adaptor implemented for this type of MemoryCacheImageInputStream");
        } else {
            throw new IllegalArgumentException("No stream adaptor found for " + iis.getClass().getName() + "!");
        }
    }

    public long[] getSegPosition() {
        return segPosition;
    }

    public int[] getSegLength() {
        return segLength;
    }

    public static byte[] getByte(ByteArrayInputStream inputStream) {
        if (inputStream != null) {
            try {
                Field fid = ByteArrayInputStream.class.getDeclaredField("buf");
                if (fid != null) {
                    fid.setAccessible(true);
                    return (byte[]) fid.get(inputStream);
                }
            } catch (Exception e) {
                LOGGER.error("Cannot get bytes from inputstream", e);
            }
        }
        return null;
    }

    public abstract ByteBuffer getDirectByteBuffer(int segment) throws IOException;

    public abstract ByteBuffer getDirectByteBuffer(int startSeg, int endSeg) throws IOException;

}