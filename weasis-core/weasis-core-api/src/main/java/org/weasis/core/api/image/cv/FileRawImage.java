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
package org.weasis.core.api.image.cv;

import java.awt.image.DataBuffer;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

import org.opencv.core.Mat;
import org.weasis.core.api.media.data.PlanarImage;

public class FileRawImage {
    public static final int HEADER_LENGTH = 50;

    private final File file;

    public FileRawImage(File file) {
        this.file = Objects.requireNonNull(file);
    }

    public File getFile() {
        return file;
    }

    public ImageCV read() throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
            FileChannel fc = raf.getChannel();

            // header: fixed size of 50 bytes
            ByteBuffer buffer = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size()).order(ByteOrder.LITTLE_ENDIAN);
            // 0-4: cvType
            int cvType = buffer.getInt();
            // 4-8: width
            int width = buffer.getInt();
            // 8-12: height
            int height = buffer.getInt();

            buffer.position(HEADER_LENGTH);

            ImageCV mat = new ImageCV(height, width, cvType);

            int dataType = ImageProcessor.convertToDataType(cvType);
            if (dataType == DataBuffer.TYPE_BYTE) {
                byte[] data;
                if (buffer.hasArray()) {
                    data = buffer.array();
                } else {
                    data = new byte[buffer.remaining()];
                    for (int i = 0; i < data.length; i++) {
                        data[i] = buffer.get();
                    }
                }
                mat.put(0, 0, data);
            } else if (dataType == DataBuffer.TYPE_SHORT || dataType == DataBuffer.TYPE_USHORT) {
                ShortBuffer b = buffer.asShortBuffer();
                short[] data = new short[b.remaining()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = b.get();
                }
                mat.put(0, 0, data);
            } else if (dataType == DataBuffer.TYPE_INT) {
                IntBuffer b = buffer.asIntBuffer();
                int[] data = new int[b.remaining()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = b.get();
                }
                mat.put(0, 0, data);
            } else if (dataType == DataBuffer.TYPE_FLOAT) {
                FloatBuffer b = buffer.asFloatBuffer();
                float[] data = new float[b.remaining()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = b.get();
                }
                mat.put(0, 0, data);
            } else if (dataType == DataBuffer.TYPE_DOUBLE) {
                DoubleBuffer b = buffer.asDoubleBuffer();
                double[] data = new double[b.remaining()];
                for (int i = 0; i < data.length; i++) {
                    data[i] = b.get();
                }
                mat.put(0, 0, data);
            }
            return mat;
        }
    }

    public void write(PlanarImage mat) throws IOException {
        int cvType = mat.type();
        int dataType = ImageProcessor.convertToDataType(cvType);
        int width = mat.width();
        int height = mat.height();
        int size = width * height * mat.channels();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            FileChannel fc = raf.getChannel();
            // header: fixed size of 50 bytes
            ByteBuffer header = fc.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
            // 0-4: cvType
            header.putInt(cvType);
            // 4-8: width
            header.putInt(width);
            // 8-12: height
            header.putInt(height);

            if (dataType == DataBuffer.TYPE_BYTE) {
                byte[] data = new byte[size];
                mat.get(0, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, HEADER_LENGTH, data.length);
                buf.put(data);
            } else if (dataType == DataBuffer.TYPE_SHORT || dataType == DataBuffer.TYPE_USHORT) {
                short[] data = new short[size];
                mat.get(0, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, HEADER_LENGTH, 2L * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (short i : data) {
                    buf.putShort(i);
                }
            } else if (dataType == DataBuffer.TYPE_INT) {
                int[] data = new int[size];
                mat.get(0, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, HEADER_LENGTH, 4L * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (int i : data) {
                    buf.putInt(i);
                }
            } else if (dataType == DataBuffer.TYPE_FLOAT) {
                float[] data = new float[size];
                mat.get(0, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, HEADER_LENGTH, 4L * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (float i : data) {
                    buf.putFloat(i);
                }
            } else if (dataType == DataBuffer.TYPE_DOUBLE) {
                double[] data = new double[size];
                mat.get(0, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, HEADER_LENGTH, 8L * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (double i : data) {
                    buf.putDouble(i);
                }
            }
        }
    }

    public void writeHeader(int cvType, int width, int height) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            FileChannel fc = raf.getChannel();
            // header: fixed size of 50 bytes
            ByteBuffer header = fc.map(FileChannel.MapMode.READ_WRITE, 0, HEADER_LENGTH).order(ByteOrder.LITTLE_ENDIAN);
            // 0-4: cvType
            header.putInt(cvType);
            // 4-8: width
            header.putInt(width);
            // 8-12: height
            header.putInt(height);
        }
    }

    public void writeHeader(PlanarImage mat) throws IOException {
        writeHeader(mat.type(), mat.width(), mat.height());
    }

    public void writeRow(Mat mat, int row) throws IOException {
        int cvType = mat.type();
        int dataType = ImageProcessor.convertToDataType(cvType);
        int size = mat.width() * mat.channels();

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            FileChannel fc = raf.getChannel();

            if (dataType == DataBuffer.TYPE_BYTE) {
                byte[] data = new byte[size];
                mat.get(row, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, fc.size() , data.length);
                buf.put(data);
            } else if (dataType == DataBuffer.TYPE_SHORT || dataType == DataBuffer.TYPE_USHORT) {
                short[] data = new short[size];
                mat.get(row, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, fc.size(), 2 * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (short i : data) {
                    buf.putShort(i);
                }
            } else if (dataType == DataBuffer.TYPE_INT) {
                int[] data = new int[size];
                mat.get(row, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, fc.size(), 4 * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (int i : data) {
                    buf.putInt(i);
                }
            } else if (dataType == DataBuffer.TYPE_FLOAT) {
                float[] data = new float[size];
                mat.get(row, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, fc.size(), 4 * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (float i : data) {
                    buf.putFloat(i);
                }
            } else if (dataType == DataBuffer.TYPE_DOUBLE) {
                double[] data = new double[size];
                mat.get(row, 0, data);
                ByteBuffer buf = fc.map(FileChannel.MapMode.READ_WRITE, fc.size(), 8 * data.length).order(ByteOrder.LITTLE_ENDIAN);
                for (double i : data) {
                    buf.putDouble(i);
                }
            }
        }
    }

}