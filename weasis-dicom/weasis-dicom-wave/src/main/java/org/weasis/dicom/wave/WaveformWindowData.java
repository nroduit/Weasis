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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.opencv.waveform.WaveformReader;

/**
 * Samples served from a {@link WaveformReader} one window at a time: native Waveform Data is
 * memory-mapped per window, a T.261 stream is decoded per frame sequence. A small cache of decoded
 * windows (the visible range and its neighbours) keeps scrolling at the cost of a copy, and a jump
 * elsewhere costs one window of decoding. {@link #prefetch(int, int)} fills the cache on a
 * background thread so painting rarely waits for the decoder.
 *
 * <p>A {@link MinMaxSummary} of the whole recording is built on another background thread as soon
 * as the data is opened: once a range is summarised, painting it zoomed out no longer reads its
 * samples. {@link #setSummaryListener(Runnable)} is told when more of the recording is covered.
 */
public final class WaveformWindowData implements WaveDataReadable {
  private static final Logger LOGGER = LoggerFactory.getLogger(WaveformWindowData.class);

  /** Windows kept in memory: the visible range, its neighbours and the prefetch margin. */
  private static final int CACHE_WINDOWS = 8;

  private static final int MIN_WINDOW = 1024;

  /** Base blocks of the summary at most (about 12 MB for 12 channels at this count). */
  private static final int SUMMARY_BLOCKS = 1 << 16;

  /** Windows summarised between two notifications of the listener. */
  private static final int SUMMARY_NOTIFY_WINDOWS = 64;

  private final WaveformReader reader;
  private final int channels;
  private final int samples;
  private final int window;
  private final int windowCount;
  private final Map<Integer, int[]> cache;
  private final ExecutorService prefetcher;
  private final ExecutorService summarizer;
  private final MinMaxSummary summary;
  private volatile Runnable summaryListener;
  private volatile boolean closed;

  public WaveformWindowData(WaveformReader reader) {
    this.reader = Objects.requireNonNull(reader);
    this.channels = reader.channels();
    this.samples = (int) Math.min(Integer.MAX_VALUE, reader.length());
    this.window = Math.max(MIN_WINDOW, reader.preferredWindow());
    this.windowCount = (samples + window - 1) / window;
    this.cache =
        new LinkedHashMap<>(CACHE_WINDOWS, 0.75f, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Integer, int[]> eldest) {
            return size() > CACHE_WINDOWS;
          }
        };
    this.prefetcher = ThreadUtil.newSingleThreadDaemonExecutor("Waveform prefetch"); // NON-NLS
    this.summary =
        channels > 0 && samples > 0 ? new MinMaxSummary(channels, samples, SUMMARY_BLOCKS) : null;
    if (summary != null) {
      this.summarizer = ThreadUtil.newSingleThreadDaemonExecutor("Waveform summary"); // NON-NLS
      summarizer.execute(this::buildSummary);
    } else {
      this.summarizer = null;
    }
  }

  /** The window length in samples: the unit of decoding and of the cache. */
  public int getWindow() {
    return window;
  }

  /** Called (on the summary thread) when more of the recording is summarised, and at the end. */
  public void setSummaryListener(Runnable listener) {
    this.summaryListener = listener;
  }

  /** Whether the min / max summary of the whole recording is built. */
  public boolean isSummaryComplete() {
    return summary != null && summary.isFinished();
  }

  @Override
  public int getNbOfChannels() {
    return channels;
  }

  @Override
  public int getNbSamplesPerChannel() {
    return samples;
  }

  @Override
  public double getSample(int index, ChannelDefinition channel) {
    return getRawSample(index, channel) * channel.getAmplitudeUnitScalingFactor()
        + channel.getBaseline();
  }

  @Override
  public int getRawSample(int index, ChannelDefinition channel) {
    if (index < 0 || index >= samples || channel.getPosition() >= channels) {
      return 0;
    }
    int k = index / window;
    int[] values = window(k);
    return values[(index - k * window) * channels + channel.getPosition()];
  }

  @Override
  public void readRaw(int start, int count, ChannelDefinition channel, int[] out) {
    int position = channel.getPosition();
    if (count <= 0) {
      return;
    }
    if (position >= channels) {
      Arrays.fill(out, 0, count, 0);
      return;
    }
    int i = 0;
    while (i < count && start + i < 0) {
      out[i++] = 0;
    }
    int last = (int) Math.min(samples - 1L, (long) start + count - 1);
    for (int index = start + i; index <= last; ) {
      int k = index / window;
      int[] values = window(k);
      int end = Math.min(last, (k + 1) * window - 1);
      int offset = (index - k * window) * channels + position;
      for (; index <= end; index++, offset += channels) {
        out[i++] = values[offset];
      }
    }
    while (i < count) {
      out[i++] = 0;
    }
  }

  @Override
  public void rawMinMax(int start, int count, ChannelDefinition channel, int[] out) {
    int first = Math.max(0, start);
    int last = (int) Math.min(samples - 1L, (long) start + count - 1);
    int min = Integer.MAX_VALUE;
    int max = Integer.MIN_VALUE;
    int position = channel.getPosition();
    if (position >= channels) {
      last = first - 1;
    }
    for (int index = first; index <= last; ) {
      int k = index / window;
      int[] values = window(k);
      int end = Math.min(last, (k + 1) * window - 1);
      int offset = (index - k * window) * channels + position;
      for (int i = index; i <= end; i++, offset += channels) {
        int value = values[offset];
        if (value < min) {
          min = value;
        }
        if (value > max) {
          max = value;
        }
      }
      index = end + 1;
    }
    if (min > max) {
      min = 0;
      max = 0;
    }
    out[0] = min;
    out[1] = max;
  }

  @Override
  public double rawMean(int start, int count, ChannelDefinition channel) {
    int first = Math.max(0, start);
    int last = (int) Math.min(samples - 1L, (long) start + count - 1);
    int position = channel.getPosition();
    if (last < first || position >= channels) {
      return 0;
    }
    double sum = 0;
    for (int index = first; index <= last; ) {
      int k = index / window;
      int[] values = window(k);
      int end = Math.min(last, (k + 1) * window - 1);
      int offset = (index - k * window) * channels + position;
      long partial = 0;
      for (int i = index; i <= end; i++, offset += channels) {
        partial += values[offset];
      }
      sum += partial;
      index = end + 1;
    }
    return sum / (last - first + 1);
  }

  @Override
  public int summaryBlock(int start, int count) {
    return summary != null && summary.covers(start, count) ? summary.baseBlock() : 0;
  }

  @Override
  public void summaryMinMax(int start, int count, ChannelDefinition channel, int[] out) {
    if (summary == null || !summary.minMax(start, count, channel.getPosition(), out)) {
      rawMinMax(start, count, channel, out);
    }
  }

  /**
   * Loads the windows of a range in the background, unless the range spans more windows than the
   * cache can hold: such a range is painted from the summary, and loading it would only evict the
   * windows in use.
   */
  @Override
  public void prefetch(int start, int count) {
    if (closed || count <= 0) {
      return;
    }
    int firstWindow = Math.max(0, start) / window;
    int lastWindow = (int) Math.min(windowCount - 1L, ((long) start + count - 1) / window);
    if (lastWindow - firstWindow >= CACHE_WINDOWS) {
      return;
    }
    List<Integer> missing = new ArrayList<>();
    synchronized (cache) {
      for (int k = firstWindow; k <= lastWindow; k++) {
        if (!cache.containsKey(k)) {
          missing.add(k);
        }
      }
    }
    for (Integer k : missing) {
      prefetcher.execute(
          () -> {
            if (!closed) {
              try {
                window(k);
              } catch (RuntimeException e) {
                LOGGER.warn("Prefetching waveform window {}", k, e);
              }
            }
          });
    }
  }

  /**
   * Feeds the summary window by window, decoding outside the cache so that the scan does not evict
   * the windows being displayed.
   */
  private void buildSummary() {
    try {
      for (int k = 0; k < windowCount && !closed; k++) {
        int[] values;
        synchronized (cache) {
          values = cache.get(k);
        }
        if (values == null) {
          values = decode(k);
        }
        summary.append(values, values.length / channels);
        if ((k + 1) % SUMMARY_NOTIFY_WINDOWS == 0) {
          notifySummary();
        }
      }
      if (!closed) {
        summary.finish();
        notifySummary();
      }
    } catch (RuntimeException e) {
      LOGGER.warn("Building the waveform summary", e);
    }
  }

  private void notifySummary() {
    Runnable listener = summaryListener;
    if (listener != null) {
      listener.run();
    }
  }

  private int[] window(int k) {
    synchronized (cache) {
      int[] values = cache.get(k);
      if (values != null) {
        return values;
      }
    }
    int[] values = decode(k);
    synchronized (cache) {
      cache.putIfAbsent(k, values);
      return cache.get(k);
    }
  }

  private int[] decode(int k) {
    int start = k * window;
    int count = Math.min(window, samples - start);
    Mat mat;
    synchronized (reader) {
      if (closed) {
        return new int[count * channels];
      }
      mat = reader.read(start, count);
    }
    try {
      return toInts(mat, count * channels);
    } finally {
      mat.release();
    }
  }

  private static int[] toInts(Mat mat, int expected) {
    int total = (int) Math.min(expected, mat.total());
    int[] values = new int[expected];
    switch (CvType.depth(mat.type())) {
      case CvType.CV_8S, CvType.CV_8U -> {
        byte[] bytes = new byte[total];
        mat.get(0, 0, bytes);
        boolean signed = CvType.depth(mat.type()) == CvType.CV_8S;
        for (int i = 0; i < total; i++) {
          values[i] = signed ? bytes[i] : bytes[i] & 0xFF;
        }
      }
      case CvType.CV_16S, CvType.CV_16U -> {
        short[] shorts = new short[total];
        mat.get(0, 0, shorts);
        boolean signed = CvType.depth(mat.type()) == CvType.CV_16S;
        for (int i = 0; i < total; i++) {
          values[i] = signed ? shorts[i] : shorts[i] & 0xFFFF;
        }
      }
      case CvType.CV_32S -> mat.get(0, 0, values);
      default -> throw new IllegalStateException("Unsupported sample type " + mat.type());
    }
    return values;
  }

  @Override
  public void close() {
    closed = true;
    summaryListener = null;
    prefetcher.shutdownNow();
    if (summarizer != null) {
      summarizer.shutdownNow();
    }
    synchronized (reader) {
      try {
        reader.close();
      } catch (Exception e) {
        LOGGER.warn("Closing the waveform reader", e);
      }
    }
    synchronized (cache) {
      cache.clear();
    }
  }
}
