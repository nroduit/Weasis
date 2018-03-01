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

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageOutputStream;

public abstract class NativeImage {

    // Descriptor of file fragment corresponding to an image
    private StreamSegment streamSegment;
    private String filepath;
    protected ImageParameters imageParameters;

    protected Buffer inputBuffer;
    protected Buffer outputBuffer;

    public NativeImage() {
    }

    public NativeImage(String filepath) {
        setFilepath(filepath);
    }

    public NativeImage(StreamSegment streamSegment) {
        setStreamSegment(streamSegment);
    }

    public ImageParameters getImageParameters() {
        return imageParameters;
    }

    public String getFilepath() {
        return filepath;
    }

    public void setFilepath(String filepath) {
        this.filepath = filepath;
    }

    public StreamSegment getStreamSegment() {
        return streamSegment;
    }

    public void setStreamSegment(StreamSegment streamSegment) {
        this.streamSegment = streamSegment;
    }

    public Buffer getInputBuffer() {
        return inputBuffer;
    }

    public Buffer getOutputBuffer() {
        return outputBuffer;
    }

    public void setInputBuffer(Buffer inputBuffer) {
        this.inputBuffer = inputBuffer;
    }

    public void setOutputBuffer(Buffer outputBuffer) {
        this.outputBuffer = outputBuffer;
    }

    public void fillInputBuffer(Object array, int offset, int length) {
        inputBuffer = getBuffer(array, offset, length);
    }

    public void fillOutputBuffer(Object array, int offset, int length) {
        outputBuffer = getBuffer(array, offset, length);
    }

    private static Buffer getBuffer(Object array, int offset, int length) {
        Buffer buffer = null;
        if (array instanceof byte[]) {
            buffer = ByteBuffer.wrap((byte[]) array, offset, length);
        } else if (array instanceof short[]) {
            buffer = ShortBuffer.wrap((short[]) array, offset, length);
        } else if (array instanceof int[]) {
            buffer = IntBuffer.wrap((int[]) array, offset, length);
        } else if (array instanceof float[]) {
            buffer = FloatBuffer.wrap((float[]) array, offset, length);
        } else if (array instanceof double[]) {
            buffer = DoubleBuffer.wrap((double[]) array, offset, length);
        }
        return buffer;
    }

    public ByteBuffer allocateDirectByteBuffer(int size) {
        // For large buffer, It is slower due to the way of the JVM allocates this kind of memory (fragmented).
        outputBuffer = ByteBuffer.allocateDirect(size);
        return (ByteBuffer) outputBuffer;
    }

    public static void writeByteBuffer(ImageOutputStream ouputStream, ByteBuffer outBuf, int bytesWritten)
        throws IOException {

        if (ouputStream instanceof FileImageOutputStream) {
            try (RandomAccessFile raf = FileStreamSegment.getRandomAccessFile((FileImageOutputStream) ouputStream)) {
                raf.getChannel().write(outBuf);
            }
        } else {
            if (outBuf.hasArray()) {
                ouputStream.write(outBuf.array(), 0, bytesWritten);
            } else {
                int limit = outBuf.limit() - outBuf.position();
                if (limit > bytesWritten) {
                    limit = bytesWritten;
                }
                for (int i = 0; i < limit; i++) {
                    ouputStream.write(outBuf.get());
                }
            }
        }
    }
}
