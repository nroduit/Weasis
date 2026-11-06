/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

/**
 * Read access to the samples of one multiplex group. Implementations may hold only a window of the
 * recording in memory: callers announce the range they are about to draw with {@link #prefetch(int,
 * int)} and read ranges with {@link #readRaw(int, int, ChannelDefinition, int[])} or {@link
 * #rawMinMax(int, int, ChannelDefinition, int[])} rather than sample by sample when they can.
 */
public interface WaveDataReadable extends AutoCloseable {

  /** The sample value in the channel unit: stored value times the sensitivity plus the baseline. */
  double getSample(int index, ChannelDefinition channel);

  /** The sample value as stored. */
  int getRawSample(int index, ChannelDefinition channel);

  /**
   * Copies {@code count} stored values of a channel from {@code start} into {@code out}; positions
   * outside the recording read as 0.
   */
  default void readRaw(int start, int count, ChannelDefinition channel, int[] out) {
    for (int i = 0; i < count; i++) {
      out[i] = getRawSample(start + i, channel);
    }
  }

  /**
   * The exact minimum and maximum stored values of a channel over {@code count} samples from {@code
   * start}, written to {@code out[0]} and {@code out[1]}; the range is clipped to the recording.
   */
  void rawMinMax(int start, int count, ChannelDefinition channel, int[] out);

  /** The mean stored value of a channel over a range clipped to the recording, 0 when empty. */
  default double rawMean(int start, int count, ChannelDefinition channel) {
    int first = Math.max(0, start);
    int last = (int) Math.min(getNbSamplesPerChannel() - 1L, (long) start + count - 1);
    if (last < first) {
      return 0;
    }
    double sum = 0;
    for (int i = first; i <= last; i++) {
      sum += getRawSample(i, channel);
    }
    return sum / (last - first + 1);
  }

  /**
   * The block size of the min / max summary when one covers the range, 0 otherwise. Ranges of a few
   * blocks or more are then cheap to measure with {@link #summaryMinMax}.
   */
  default int summaryBlock(int start, int count) {
    return 0;
  }

  /**
   * The minimum and maximum stored values over a range from the summary: the range is rounded
   * outward to whole blocks of at most a quarter of its length. Exact when no summary covers it.
   */
  default void summaryMinMax(int start, int count, ChannelDefinition channel, int[] out) {
    rawMinMax(start, count, channel, out);
  }

  /** Announces a range that will be read soon; implementations may load it in the background. */
  default void prefetch(int start, int count) {}

  int getNbOfChannels();

  int getNbSamplesPerChannel();

  @Override
  default void close() {}
}
