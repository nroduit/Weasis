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
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
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
        // Not a good practice (should be instanceof) but necessary to remove the dependency with dcm4che3 lib
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
            try {
                Field f_stream = SegmentedImageInputStream.class.getDeclaredField("stream");
                Field f_mapper = SegmentedImageInputStream.class.getDeclaredField("mapper");
                if (f_mapper != null && f_stream != null) {
                    f_mapper.setAccessible(true);
                    f_stream.setAccessible(true);
                    Object mapperObject = f_mapper.get(iis);
                    // Not a good practice (should be instanceof) but necessary to remove the dependency with dcm4che14 lib
                    if ("org.dcm4cheri.image.ItemParser".equals(mapperObject.getClass().getName())) {
                        FileImageInputStream fstream = (FileImageInputStream) f_stream.get(iis);
                        Field f_raf = FileImageInputStream.class.getDeclaredField("raf");
                        if (f_raf != null) {
                            f_raf.setAccessible(true);

                            Field f_items = mapperObject.getClass().getDeclaredField("items");
                            Method m_numberOfDataFragments = mapperObject.getClass().getMethod("getNumberOfDataFragments");
                            //Needs to be called so that all segments are added to List "items"
                            int nbrOfDataFragments = (int) m_numberOfDataFragments.invoke(mapperObject, new Object[]{});
                            if (f_items != null) {
                                f_items.setAccessible(true);
                                ArrayList items = (ArrayList) f_items.get(mapperObject);
                                if (items.size() > 0) {
                                    Field f_startPos = items.get(0).getClass().getDeclaredField("startPos");
                                    Field f_length = items.get(0).getClass().getDeclaredField("length");
                                    Field f_offset = items.get(0).getClass().getDeclaredField("offset");
                                    if (f_startPos != null && f_length != null) {
                                        f_startPos.setAccessible(true);
                                        f_length.setAccessible(true);
                                        long[] segmentStartPositions = new long[items.size()];
                                        int[] segmentLengths = new int[items.size()];
                                        int startIndex = -1;
                                        for (int i = 0; i < items.size(); i++) {

                                            if(startIndex == -1 && (long) f_offset.get(items.get(i)) == iis.getStreamPosition()) {
                                                startIndex = i;
                                            }
                                            segmentStartPositions[i] = (long) f_startPos.get(items.get(i));
                                            segmentLengths[i] = (int) f_length.get(items.get(i));
                                        }
                                        if(startIndex != -1) {
                                            segmentStartPositions = Arrays.copyOfRange(segmentStartPositions, startIndex, segmentStartPositions.length);
                                            segmentLengths = Arrays.copyOfRange(segmentLengths, startIndex, segmentLengths.length);
                                            RandomAccessFile raf = (RandomAccessFile) f_raf.get(fstream);
                                            /*
                                             * PS 3.5.8.2 Though a fragment may not contain encoded data from more than one frame,
                                             * the encoded data from one frame may span multiple fragments. See note in Section 8.2.
                                             */
                                            FileStreamSegment segment = new FileStreamSegment(raf, FileStreamSegment.getFileIDfromFileDescriptor(raf.getFD()), segmentStartPositions, segmentLengths);
                                            mlImage.setStreamSegment(segment);
                                            return;
                                        }
                                    }
                                }
                            }

                        }
                    }
                }
                throw new IllegalArgumentException("No adaptor implemented for this type of SegmentedImageInputStream");
            } catch (Exception e) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.error("getFileDescriptor from SegmentedInputImageStream", e);
                } else {
                    LOGGER.error(e.getMessage());
                }
            }
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