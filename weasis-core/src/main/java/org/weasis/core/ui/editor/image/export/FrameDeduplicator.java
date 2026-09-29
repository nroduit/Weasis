/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

/**
 * Turns timed samples into sink frames. A sample identical to the frame before it is not stored:
 * that frame simply lasts until the next sample that differs, so an idle viewer costs one
 * comparison per sample and nothing in the file. Durations come from the sample times, which makes
 * a skipped sample a longer frame rather than a lost one.
 *
 * <p>Not thread-safe: one thread feeds it, and the sink is called from that thread.
 */
final class FrameDeduplicator {

  private final FrameSink sink;
  private final int minDurationMs;
  private final Consumer<BufferedImage> recycler;

  private BufferedImage pending;
  private byte[] pendingData;
  private long pendingTimeNanos;
  private int writtenFrames;

  /**
   * @param minDurationMs the shortest frame a sink can represent
   * @param recycler receives every buffer once it is no longer held
   */
  FrameDeduplicator(FrameSink sink, int minDurationMs, Consumer<BufferedImage> recycler) {
    this.sink = sink;
    this.minDurationMs = Math.max(1, minDurationMs);
    this.recycler = recycler;
  }

  /** Whether {@code frame}, sampled at {@code timeNanos}, changed anything since the last one. */
  void accept(BufferedImage frame, long timeNanos) throws IOException {
    byte[] data = pixels(frame);
    if (pending != null && Arrays.equals(pendingData, data)) {
      recycler.accept(frame);
      return;
    }
    flush(timeNanos);
    pending = frame;
    pendingData = data;
    pendingTimeNanos = timeNanos;
  }

  /** Writes the frame still pending, which lasts until {@code endNanos}. */
  void finish(long endNanos) throws IOException {
    flush(endNanos);
  }

  int writtenFrames() {
    return writtenFrames;
  }

  private void flush(long untilNanos) throws IOException {
    if (pending == null) {
      return;
    }
    BufferedImage frame = pending;
    pending = null;
    pendingData = null;
    try {
      int durationMs = (int) Math.max(minDurationMs, (untilNanos - pendingTimeNanos) / 1_000_000L);
      sink.write(frame, durationMs);
      writtenFrames++;
    } finally {
      recycler.accept(frame);
    }
  }

  private static byte[] pixels(BufferedImage image) {
    return ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
  }
}
