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
package org.weasis.image.jni;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.imageio.stream.MemoryCacheImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class MemoryStreamSegment extends StreamSegment {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryStreamSegment.class);

    private final byte[] inputStream;

    MemoryStreamSegment(byte[] b) {
        super(new long[] { 0 }, new long[] { b.length });
        this.inputStream = b;
    }

    @Override
    public ByteBuffer getDirectByteBuffer(int segment) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocateDirect((int) segLength[segment]);
        buffer.order(ByteOrder.nativeOrder());
        buffer.put(inputStream);
        return buffer;
    }

    @Override
    public ByteBuffer getDirectByteBuffer(int startSeg, int endSeg) throws IOException {
        return getDirectByteBuffer(startSeg);
    }

    public static ByteArrayInputStream getByteArrayInputStream(MemoryCacheImageInputStream inputStream) {
        if (inputStream != null) {
            try {
                Field fid = MemoryCacheImageInputStream.class.getDeclaredField("stream");
                if (fid != null) {
                    fid.setAccessible(true);
                    return (ByteArrayInputStream) fid.get(inputStream);
                }
            } catch (Exception e) {
                LOGGER.error("Cannot get inputstream", e);
            }
        }
        return null;
    }
}
