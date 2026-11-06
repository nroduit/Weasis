/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.util.Arrays;

/**
 * Minimum and maximum of every channel over blocks of samples, at block sizes doubling from a base
 * block up to the whole recording. Built once by feeding the samples in order, it answers the min /
 * max of a long range with a handful of array reads, so painting a recording zoomed out costs a few
 * reads per pixel column whatever its length instead of one read per sample.
 *
 * <p>The summary can be queried while it is being built: {@link #covers(int, int)} tells whether a
 * range is already summarised. Ranges are rounded outward to whole blocks of at most a quarter of
 * their length, an error below a pixel when the range is a pixel column.
 */
final class MinMaxSummary {

  /** Smallest base block: below it reading the samples is as cheap as reading the summary. */
  static final int MIN_BLOCK = 16;

  private final int channels;
  private final int samples;
  private final int baseShift;
  private final int baseBlock;
  private final int levels;
  private final int[][] min;
  private final int[][] max;

  private final int[] currentMin;
  private final int[] currentMax;
  private int inBlock;
  private int blockIndex;
  private int fed;
  private volatile int covered;
  private volatile boolean finished;

  /**
   * @param channels channels of the recording
   * @param samples samples per channel
   * @param targetBlocks the number of base blocks the summary should not exceed much, bounding its
   *     memory: the base block is the smallest power of two giving at most that many blocks
   */
  MinMaxSummary(int channels, int samples, int targetBlocks) {
    if (channels <= 0 || samples <= 0 || targetBlocks <= 0) {
      throw new IllegalArgumentException("Empty summary");
    }
    this.channels = channels;
    this.samples = samples;
    int shift = 0;
    while ((1 << shift) < MIN_BLOCK || ((samples - 1) >> shift) + 1 > targetBlocks) {
      shift++;
    }
    this.baseShift = shift;
    this.baseBlock = 1 << shift;
    int blocks = ((samples - 1) >> shift) + 1;
    int count = 1;
    for (int b = blocks; b > 1; b = (b + 1) >> 1) {
      count++;
    }
    this.levels = count;
    this.min = new int[count][];
    this.max = new int[count][];
    for (int level = 0, b = blocks; level < count; level++, b = (b + 1) >> 1) {
      min[level] = new int[b * channels];
      max[level] = new int[b * channels];
    }
    this.currentMin = new int[channels];
    this.currentMax = new int[channels];
    Arrays.fill(currentMin, Integer.MAX_VALUE);
    Arrays.fill(currentMax, Integer.MIN_VALUE);
  }

  /** The base block in samples: the resolution of the summary. */
  int baseBlock() {
    return baseBlock;
  }

  int levels() {
    return levels;
  }

  /** The number of samples (from the start of the recording) the summary covers so far. */
  int covered() {
    return finished ? samples : covered;
  }

  boolean isFinished() {
    return finished;
  }

  /** Whether the whole range is summarised already (ranges outside the recording are clipped). */
  boolean covers(int start, int count) {
    int end = (int) Math.min(samples, (long) Math.max(0, start) + count);
    return end <= covered();
  }

  /**
   * Feeds the next samples of the recording, interleaved by channel as in Waveform Data ({@code
   * count * channels} values).
   */
  void append(int[] values, int count) {
    if (finished) {
      throw new IllegalStateException("Summary finished");
    }
    int limit = Math.min(count, samples - fed);
    int offset = 0;
    for (int s = 0; s < limit; s++) {
      for (int c = 0; c < channels; c++, offset++) {
        int v = values[offset];
        if (v < currentMin[c]) {
          currentMin[c] = v;
        }
        if (v > currentMax[c]) {
          currentMax[c] = v;
        }
      }
      if (++inBlock == baseBlock) {
        flushBlock();
      }
    }
    fed += limit;
  }

  /** Stores the last (partial) block and completes the upper levels. */
  void finish() {
    if (finished) {
      return;
    }
    if (inBlock > 0) {
      flushBlock();
    }
    for (int level = 1; level < levels; level++) {
      int blocks = min[level].length / channels;
      int last = blocks - 1;
      int children = min[level - 1].length / channels;
      combine(level, last, Math.min(children - 1, 2 * last + 1));
    }
    finished = true;
  }

  private void flushBlock() {
    int base = blockIndex * channels;
    System.arraycopy(currentMin, 0, min[0], base, channels);
    System.arraycopy(currentMax, 0, max[0], base, channels);
    Arrays.fill(currentMin, Integer.MAX_VALUE);
    Arrays.fill(currentMax, Integer.MIN_VALUE);
    inBlock = 0;
    int index = blockIndex++;
    // A completed second child completes its parent, up to the top
    for (int level = 1; level < levels && (index & 1) == 1; level++) {
      index >>= 1;
      combine(level, index, 2 * index + 1);
    }
    covered = Math.min(samples, blockIndex << baseShift);
  }

  /**
   * Stores block {@code index} of {@code level} from its children {@code 2 * index .. lastChild}.
   */
  private void combine(int level, int index, int lastChild) {
    int[] childMin = min[level - 1];
    int[] childMax = max[level - 1];
    int first = 2 * index * channels;
    int target = index * channels;
    System.arraycopy(childMin, first, min[level], target, channels);
    System.arraycopy(childMax, first, max[level], target, channels);
    if (lastChild > 2 * index) {
      int second = lastChild * channels;
      for (int c = 0; c < channels; c++) {
        min[level][target + c] = Math.min(min[level][target + c], childMin[second + c]);
        max[level][target + c] = Math.max(max[level][target + c], childMax[second + c]);
      }
    }
  }

  /**
   * The minimum and maximum of a channel over a range, from blocks of at most a quarter of the
   * range (or the base block), the range rounded outward to whole blocks.
   *
   * @return false when the range is not covered yet or empty; {@code out} is then untouched
   */
  boolean minMax(int start, int count, int channel, int[] out) {
    int first = Math.max(0, start);
    int end = (int) Math.min(samples, (long) start + count);
    if (end <= first || channel < 0 || channel >= channels) {
      return false;
    }
    int limit = covered();
    if (end > limit) {
      return false;
    }
    int quarter = (end - first) >> 2;
    int level = 0;
    while (level + 1 < levels && (baseBlock << (level + 1)) <= quarter) {
      level++;
    }
    int shift = baseShift + level;
    int i1 = (end - 1) >> shift;
    // During the build the last block of an upper level may not be complete yet
    while (level > 0 && !finished && ((long) (i1 + 1) << shift) > limit) {
      level--;
      shift--;
      i1 = (end - 1) >> shift;
    }
    int i0 = first >> shift;
    int[] levelMin = min[level];
    int[] levelMax = max[level];
    int lo = Integer.MAX_VALUE;
    int hi = Integer.MIN_VALUE;
    for (int i = i0 * channels + channel, stop = i1 * channels + channel;
        i <= stop;
        i += channels) {
      int a = levelMin[i];
      int b = levelMax[i];
      if (a < lo) {
        lo = a;
      }
      if (b > hi) {
        hi = b;
      }
    }
    out[0] = lo;
    out[1] = hi;
    return true;
  }
}
