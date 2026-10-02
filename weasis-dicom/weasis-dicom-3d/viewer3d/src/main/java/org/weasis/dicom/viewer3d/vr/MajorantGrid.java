/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.stream.IntStream;

/**
 * Coarse bounds of the voxel values for the path tracer: the lowest and highest sampler value of
 * each block of {@value #BLOCK} voxels, widened by one voxel so a trilinear sample taken inside the
 * block stays within its range. The tracer turns each range into an extinction bound through the
 * window and the preset's {@link AlphaRangeTable}, tracks each block at its own bound and skips the
 * blocks whose bound is zero, instead of stepping through empty space at the bound of the densest
 * voxel of the volume.
 *
 * <p>Filled slice by slice on the loading thread from the staging buffers, then uploaded once on
 * the shared context as a two-channel float 3D texture on unit {@value #TEXTURE_UNIT}. A volume
 * whose upload was cut short never completes, and the tracer keeps its global bound.
 */
public final class MajorantGrid {

  public static final int TEXTURE_UNIT = 9;

  /** Edge of a block, in voxels. */
  static final int BLOCK = 8;

  private final int volumeWidth;
  private final int volumeHeight;
  private final int volumeDepth;
  private final int width;
  private final int height;
  private final int depth;

  /** Interleaved minimum and maximum of each block, in sampler units. */
  private final float[] range;

  private volatile boolean complete;
  private boolean supported = true;
  private volatile int id;

  MajorantGrid(int volumeWidth, int volumeHeight, int volumeDepth) {
    this.volumeWidth = volumeWidth;
    this.volumeHeight = volumeHeight;
    this.volumeDepth = volumeDepth;
    this.width = blocks(volumeWidth);
    this.height = blocks(volumeHeight);
    this.depth = blocks(volumeDepth);
    this.range = new float[width * height * depth * 2];
    for (int i = 0; i < range.length; i += 2) {
      range[i] = Float.POSITIVE_INFINITY;
      range[i + 1] = Float.NEGATIVE_INFINITY;
    }
  }

  static int blocks(int voxels) {
    return Math.max(1, (voxels + BLOCK - 1) / BLOCK);
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getDepth() {
    return depth;
  }

  /** Whether every slice has been folded in, so the ranges bound the whole volume. */
  public boolean isComplete() {
    return complete;
  }

  /**
   * Folds {@code sliceCount} slices starting at texture slice {@code firstSlice} into the grid. The
   * buffer holds them tightly packed as the GPU upload does: one byte, unsigned short or float per
   * channel, in sampler units once normalized. A colour volume contributes its luminance, as the
   * shader reads it. Unsupported layouts leave the grid incomplete.
   */
  void accumulate(Buffer buffer, int channels, int firstSlice, int sliceCount) {
    if (!supported || sliceCount <= 0) {
      return;
    }
    SampleReader reader = SampleReader.of(buffer, channels);
    if (reader == null) {
      supported = false;
      return;
    }
    int sliceLength = volumeWidth * volumeHeight * channels;
    IntStream.range(0, sliceCount)
        .parallel()
        .forEach(
            s -> {
              float[] sliceRange = sliceRange(reader, s * sliceLength);
              foldSlice(sliceRange, firstSlice + s);
            });
  }

  /** Marks the grid as covering the whole volume; blocks no slice reached bound nothing. */
  void complete() {
    if (!supported) {
      return;
    }
    for (int i = 0; i < range.length; i += 2) {
      if (range[i] > range[i + 1]) {
        range[i] = 0f;
        range[i + 1] = 0f;
      }
    }
    complete = true;
  }

  // Block ranges of one slice, widened by one voxel across the block edges within the slice.
  private float[] sliceRange(SampleReader reader, int base) {
    float[] slice = new float[width * height * 2];
    for (int i = 0; i < slice.length; i += 2) {
      slice[i] = Float.POSITIVE_INFINITY;
      slice[i + 1] = Float.NEGATIVE_INFINITY;
    }
    for (int y = 0; y < volumeHeight; y++) {
      int by = y / BLOCK;
      int ny = neighbourBlock(y, by, volumeHeight);
      int row = base + y * volumeWidth * reader.channels();
      for (int x = 0; x < volumeWidth; x++) {
        float v = reader.read(row + x * reader.channels());
        int bx = x / BLOCK;
        int nx = neighbourBlock(x, bx, volumeWidth);
        fold(slice, (by * width + bx) * 2, v);
        if (nx >= 0) {
          fold(slice, (by * width + nx) * 2, v);
        }
        if (ny >= 0) {
          fold(slice, (ny * width + bx) * 2, v);
          if (nx >= 0) {
            fold(slice, (ny * width + nx) * 2, v);
          }
        }
      }
    }
    return slice;
  }

  // Folds a slice's block ranges into its own block layer and, on an edge, into the adjacent one.
  private synchronized void foldSlice(float[] slice, int z) {
    int bz = z / BLOCK;
    int nz = neighbourBlock(z, bz, volumeDepth);
    int layer = width * height * 2;
    for (int i = 0; i < slice.length; i += 2) {
      fold(range, bz * layer + i, slice[i]);
      fold(range, bz * layer + i, slice[i + 1]);
      if (nz >= 0) {
        fold(range, nz * layer + i, slice[i]);
        fold(range, nz * layer + i, slice[i + 1]);
      }
    }
  }

  private static void fold(float[] target, int offset, float value) {
    if (value < target[offset]) {
      target[offset] = value;
    }
    if (value > target[offset + 1]) {
      target[offset + 1] = value;
    }
  }

  /** The block next to {@code block} that voxel {@code i} borders, or -1 when it borders none. */
  static int neighbourBlock(int i, int block, int voxels) {
    int inBlock = i % BLOCK;
    if (inBlock == 0 && block > 0) {
      return block - 1;
    }
    if (inBlock == BLOCK - 1 && i < voxels - 1) {
      return block + 1;
    }
    return -1;
  }

  /** Range of a block, for tests: {min, max}. */
  float[] rangeAt(int bx, int by, int bz) {
    int i = ((bz * height + by) * width + bx) * 2;
    return new float[] {range[i], range[i + 1]};
  }

  /** Whether the complete grid has been uploaded, so a view can bind it. */
  public boolean isUploaded() {
    return complete && id > 0;
  }

  /**
   * Uploads the complete grid. Must run on the shared context: the texture is sampled by every view
   * of the volume and must not die with one of them.
   */
  void upload(GL2ES2 gl) {
    if (!complete || id > 0) {
      return;
    }
    IntBuffer buf = IntBuffer.allocate(1);
    gl.glGenTextures(1, buf);
    int texture = buf.get(0);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, texture);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES2.GL_TEXTURE_WRAP_R, GL.GL_CLAMP_TO_EDGE);
    gl.glTexImage3D(
        GL2ES2.GL_TEXTURE_3D,
        0,
        GL.GL_RG32F,
        width,
        height,
        depth,
        0,
        GL2ES2.GL_RG,
        GL.GL_FLOAT,
        Buffers.newDirectFloatBuffer(range).rewind());
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, 0);
    gl.glFinish();
    id = texture;
  }

  /** Binds the grid on its unit. Only valid once uploaded. */
  public void render(GL2ES2 gl) {
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, id);
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  public void destroy(GL2ES2 gl) {
    if (id > 0) {
      gl.glDeleteTextures(1, new int[] {id}, 0);
      id = 0;
    }
  }

  /** Reads one voxel of a staging buffer as the sampler value the shader sees. */
  private sealed interface SampleReader {
    int channels();

    float read(int index);

    static SampleReader of(Buffer buffer, int channels) {
      return switch (buffer) {
        case ByteBuffer b when channels == 1 -> new Bytes(b);
        case ByteBuffer b when channels == 3 || channels == 4 -> new Luminance(b, channels);
        case ShortBuffer s when channels == 1 -> new Shorts(s);
        case FloatBuffer f when channels == 1 -> new Floats(f);
        default -> null;
      };
    }

    record Bytes(ByteBuffer buffer) implements SampleReader {
      @Override
      public int channels() {
        return 1;
      }

      @Override
      public float read(int index) {
        return (buffer.get(index) & 0xFF) / 255f;
      }
    }

    record Luminance(ByteBuffer buffer, int channels) implements SampleReader {
      @Override
      public float read(int index) {
        float r = buffer.get(index) & 0xFF;
        float g = buffer.get(index + 1) & 0xFF;
        float b = buffer.get(index + 2) & 0xFF;
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f;
      }
    }

    record Shorts(ShortBuffer buffer) implements SampleReader {
      @Override
      public int channels() {
        return 1;
      }

      @Override
      public float read(int index) {
        return (buffer.get(index) & 0xFFFF) / 65535f;
      }
    }

    record Floats(FloatBuffer buffer) implements SampleReader {
      @Override
      public int channels() {
        return 1;
      }

      @Override
      public float read(int index) {
        return buffer.get(index);
      }
    }
  }
}
