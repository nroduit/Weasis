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
package org.weasis.jpeg.internal;

import java.nio.ByteBuffer;

import org.weasis.jpeg.cpp.libijg.RETURN_MSG;
import org.weasis.jpeg.cpp.libijg.jpeg_decompress_struct;

public interface DecoderIJG extends AutoCloseable {

    jpeg_decompress_struct getJpeg_DecompressStruct();

    int getDecompressedColorModel();

    int bytesPerSample();

    RETURN_MSG init(boolean isYBR);

    RETURN_MSG readHeader(ByteBuffer compressedFrameBuffer, long compressedFrameBufferSize, boolean force2RGB);

    RETURN_MSG decode(ByteBuffer compressedFrameBuffer, long compressedFrameBufferSize,
        ByteBuffer uncompressedFrameBuffer, long uncompressedFrameBufferSize);

    void deallocate();

    @Override
    void close();
}
