/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.management.ThreadMXBean;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.management.ManagementFactory;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.stream.IntStream;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.weasis.opencv.data.ImageCV;
import org.weasis.opencv.natives.NativeLibrary;

/**
 * Baseline of the MPR resampling kernel, the per-pixel image write of the curved MPR and the slice
 * staging of the 3D texture upload, on a synthetic 16-bit volume. Not part of the normal test run
 * (the class name does not match the Surefire pattern).
 *
 * <p>Run with {@code mvn -pl weasis-dicom/weasis-dicom-viewer2d test -Dtest=MprBenchmark
 * -Dbench.run=true -Dbench.opencv=<path of libopencv_java>}. Optional: {@code -Dbench.out=<markdown
 * file>}.
 */
class MprBenchmark {
  private static final ThreadMXBean THREADS = (ThreadMXBean) ManagementFactory.getThreadMXBean();
  private static final int WARMUP = Integer.getInteger("bench.warmup", 3);
  private static final int ITERATIONS = Integer.getInteger("bench.iterations", 10);
  private static final int SIZE_XY = 512;
  private static final int SIZE_Z = 256;

  private final List<String> rows = new ArrayList<>();

  @BeforeAll
  static void loadNativeLibrary() {
    String library = System.getProperty("bench.opencv");
    if (library == null) {
      NativeLibrary.loadLibraryFromLibraryName();
    } else {
      System.load(library);
    }
  }

  @FunctionalInterface
  private interface Step {
    void run() throws Exception;
  }

  @Test
  void baseline() throws Exception {
    assumeTrue(Boolean.getBoolean("bench.run"), "bench.run is not set");

    var volume = new VolumeShort(SIZE_XY, SIZE_XY, SIZE_Z, false, 1, null);
    var random = new Random(7);
    for (int c = 0; c < volume.data.chunkCount(); c++) {
      short[] chunk = volume.data.getChunk(c);
      for (int i = 0; i < chunk.length; i++) {
        chunk[i] = (short) random.nextInt(4096); // above the Short cache, as real CT values are
      }
    }

    rows.add("| Measure | Median ms | Max ms | Heap MB/call | Note |");
    rows.add("|---|---|---|---|---|");
    benchReslice(volume);
    benchImageWrite();
    benchSliceStaging(volume);

    String report =
        "Volume %d × %d × %d, 16-bit; %d threads; warm-up %d, iterations %d, Java %s%n%n"
                .formatted(
                    SIZE_XY,
                    SIZE_XY,
                    SIZE_Z,
                    Runtime.getRuntime().availableProcessors(),
                    WARMUP,
                    ITERATIONS,
                    Runtime.version())
            + String.join(System.lineSeparator(), rows)
            + System.lineSeparator();
    System.out.println(report);
    String out = System.getProperty("bench.out");
    if (out != null) {
      Files.writeString(Path.of(out), report);
    }
  }

  // Oblique plane through the volume center, the size Volume.getVolumeSlice renders
  private void benchReslice(VolumeShort volume) throws Exception {
    int size = volume.getSliceSize();
    var transform =
        new Matrix4d()
            .translate(SIZE_XY / 2.0, SIZE_XY / 2.0, SIZE_Z / 2.0)
            .rotateX(Math.toRadians(30))
            .rotateY(Math.toRadians(20))
            .translate(-size / 2.0, -size / 2.0, 0);
    short[] out = new short[size * size];
    short[] voxels = volume.data.getChunk(0);
    String note = "%d × %d output".formatted(size, size);

    add(
        "reslice, current kernel (boxed `getInterpolatedValueFromSource`), 1 thread",
        () -> IntStream.range(0, size).forEach(y -> boxedRow(volume, transform, size, y, out)),
        note);
    add(
        "reslice, current kernel, all threads",
        () ->
            IntStream.range(0, size)
                .parallel()
                .forEach(y -> boxedRow(volume, transform, size, y, out)),
        "heap is measured on the calling thread only");
    add(
        "reslice, candidate primitive kernel on `short[]`, 1 thread",
        () -> IntStream.range(0, size).forEach(y -> primitiveRow(voxels, transform, size, y, out)),
        note);
    add(
        "reslice, candidate primitive kernel, all threads",
        () ->
            IntStream.range(0, size)
                .parallel()
                .forEach(y -> primitiveRow(voxels, transform, size, y, out)),
        "");
  }

  private static void boxedRow(
      VolumeShort volume, Matrix4d transform, int size, int y, short[] out) {
    var point = new Vector3d();
    for (int x = 0; x < size; x++) {
      transform.transformPosition(point.set(x, y, 0));
      Short value = volume.getInterpolatedValueFromSource(point.x, point.y, point.z, 0);
      if (value != null) {
        out[y * size + x] = value;
      }
    }
  }

  private static void primitiveRow(
      short[] voxels, Matrix4d transform, int size, int y, short[] out) {
    var point = new Vector3d();
    int plane = SIZE_XY * SIZE_XY;
    for (int x = 0; x < size; x++) {
      transform.transformPosition(point.set(x, y, 0));
      double px = point.x;
      double py = point.y;
      double pz = point.z;
      if (px < 0
          || px >= SIZE_XY - 1
          || py < 0
          || py >= SIZE_XY - 1
          || pz < 0
          || pz >= SIZE_Z - 1) {
        continue;
      }
      int x0 = (int) px;
      int y0 = (int) py;
      int z0 = (int) pz;
      double fx = px - x0;
      double fy = py - y0;
      double fz = pz - z0;
      int base = z0 * plane + y0 * SIZE_XY + x0;
      double v00 = lerp(voxels[base], voxels[base + 1], fx);
      double v10 = lerp(voxels[base + SIZE_XY], voxels[base + SIZE_XY + 1], fx);
      double v01 = lerp(voxels[base + plane], voxels[base + plane + 1], fx);
      double v11 = lerp(voxels[base + plane + SIZE_XY], voxels[base + plane + SIZE_XY + 1], fx);
      double v0 = v00 + (v10 - v00) * fy;
      double v1 = v01 + (v11 - v01) * fy;
      out[y * size + x] = (short) Math.round(v0 + (v1 - v0) * fz);
    }
  }

  private static double lerp(short a, short b, double f) {
    int ua = a & 0xFFFF;
    return ua + ((b & 0xFFFF) - ua) * f;
  }

  // The write pattern of CurvedMprImageIO.setPixelValue against one bulk put
  private void benchImageWrite() throws Exception {
    int width = 1024;
    int height = 256;
    short[] values = new short[width * height];
    var dst = new ImageCV(height, width, CvType.CV_16UC1);
    String note = "%d × %d panoramic".formatted(width, height);
    add(
        "image write, current: `Mat.put(row, col, short)` per pixel",
        () -> {
          for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
              dst.put(j, i, values[j * width + i]); // as CurvedMprImageIO.setPixelValue does
            }
          }
        },
        note);
    add("image write, candidate: one bulk `put`", () -> dst.put(0, 0, values), note);
    dst.release();
  }

  // The staging of TextureSliceDataBuffer.toImageData(List<Mat>) against a direct segment copy
  private void benchSliceStaging(VolumeShort volume) throws Exception {
    int count = 64;
    List<Mat> slices = new ArrayList<>();
    for (int z = 0; z < count; z++) {
      slices.add(volume.getAxialSlice(z).toMat());
    }
    int pixels = SIZE_XY * SIZE_XY;
    long bytes = (long) pixels * Short.BYTES;
    String note =
        "%d slices of %d × %d, %d MB".formatted(count, SIZE_XY, SIZE_XY, count * bytes >> 20);

    try (Arena arena = Arena.ofShared()) {
      // Allocated once: the zero-filling of a new segment would hide the copies being compared
      MemorySegment segment = arena.allocate(count * bytes);
      add(
          "3D staging, current: `Mat.get` → `short[]` → segment buffer",
          () -> {
            ShortBuffer buffer =
                segment.asByteBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();
            short[] data = new short[pixels];
            for (Mat slice : slices) {
              slice.get(0, 0, data);
              buffer.put(data);
            }
          },
          note);
      add(
          "3D staging, candidate: `MemorySegment.copy` from `Mat.dataAddr()`",
          () -> {
            long offset = 0;
            for (Mat slice : slices) {
              MemorySegment source = MemorySegment.ofAddress(slice.dataAddr()).reinterpret(bytes);
              MemorySegment.copy(source, 0, segment, offset, bytes);
              offset += bytes;
            }
          },
          note);
      add(
          "3D staging: allocation of the segment alone (`Arena.ofShared`, zero-filled)",
          () -> {
            try (Arena scratch = Arena.ofShared()) {
              scratch.allocate(count * bytes);
            }
          },
          note);
    }
    add(
        "volume → image: `getAxialSlice`",
        () -> volume.getAxialSlice(100).release(),
        "%d × %d".formatted(SIZE_XY, SIZE_XY));
    slices.forEach(Mat::release);
  }

  private void add(String measure, Step step, String note) throws Exception {
    for (int i = 0; i < WARMUP; i++) {
      step.run();
    }
    long thread = Thread.currentThread().threadId();
    double[] times = new double[ITERATIONS];
    long allocated = 0;
    for (int i = 0; i < ITERATIONS; i++) {
      long heap = THREADS.getThreadAllocatedBytes(thread);
      long start = System.nanoTime();
      step.run();
      times[i] = (System.nanoTime() - start) / 1e6;
      allocated += THREADS.getThreadAllocatedBytes(thread) - heap;
    }
    Arrays.sort(times);
    rows.add(
        String.format(
            Locale.ROOT,
            "| %s | %.2f | %.2f | %.2f | %s |",
            measure,
            times[ITERATIONS / 2],
            times[ITERATIONS - 1],
            allocated / (double) ITERATIONS / (1 << 20),
            note));
  }
}
