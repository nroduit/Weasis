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

import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.util.ThreadUtil;

/**
 * Records what happens in the interface while the user works. Sampling runs on a timer rather than
 * per repaint, because repaint frequency is unbounded and would produce thousands of near-identical
 * frames.
 *
 * <p>Only the grab runs on the EDT. Comparing a sample with the previous frame and writing it go to
 * one encoder thread through a short queue, so the recording does not slow down what it records;
 * when the encoder falls behind, a sample is skipped and the frame on screen simply lasts longer.
 * Frames travel in a small pool of reused buffers, never allocated per sample.
 *
 * <p>Recording stops at the chosen duration. Identical samples are merged into one longer frame, so
 * an idle screen writes almost nothing and the frame cap alone would never end it; the cap still
 * bounds a busy recording, which stops cleanly there rather than failing at the encode. Menus and
 * tooltips shown in their own native window are not children of the application frame and so do not
 * appear in a window-scope recording.
 */
public class AnimationRecorder {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnimationRecorder.class);

  /** Samples the encoder may hold before the timer skips one. */
  private static final int MAX_QUEUED = 2;

  /** One pending frame, the queued samples, one being compared, one being grabbed. */
  private static final int POOL_SIZE = MAX_QUEUED + 2;

  private final Window parent;
  private final String title;
  private final FrameGrabber grabber;
  private final FrameSink sink;
  private final int maxFrames;
  private final long maxDurationMs;
  private final Timer timer;
  private final ExecutorService encoder = ThreadUtil.newSingleThreadExecutor("AnimationRecorder");
  private final BlockingQueue<BufferedImage> free = new ArrayBlockingQueue<>(POOL_SIZE);
  private final AtomicInteger queued = new AtomicInteger();
  private final AtomicInteger writtenFrames = new AtomicInteger();

  private FrameDeduplicator frames;
  private volatile boolean sinkFull;
  private volatile IOException failure;
  private long startTime;
  private boolean stopped;

  public AnimationRecorder(
      Window parent,
      String title,
      FrameGrabber grabber,
      FrameSink sink,
      int intervalMs,
      long maxDurationMs,
      int maxFrames) {
    this.parent = parent;
    this.title = title;
    this.grabber = grabber;
    this.sink = sink;
    this.maxDurationMs = Math.max(intervalMs, maxDurationMs);
    this.maxFrames = Math.max(1, maxFrames);
    this.timer = new Timer(Math.max(1, intervalMs), _ -> sample());
  }

  /** Starts sampling; {@code false} when another recording is already running. */
  public boolean start() {
    if (!RecordingState.start(this)) {
      return false;
    }
    try {
      sink.open(grabber.getFrameSize().width, grabber.getFrameSize().height);
    } catch (IOException e) {
      LOGGER.error("Cannot start the recording", e);
      grabber.release();
      encoder.shutdown();
      RecordingState.stop(this);
      return false;
    }
    for (int i = 0; i < POOL_SIZE; i++) {
      free.add(grabber.newFrameBuffer());
    }
    frames = new FrameDeduplicator(sink, 1, free::add);
    startTime = System.currentTimeMillis();
    LOGGER.info(
        "Recording started: {} at {}x{}, {} ms between samples, {} s and {} frames at most",
        grabber.getScope(),
        grabber.getFrameSize().width,
        grabber.getFrameSize().height,
        timer.getDelay(),
        maxDurationMs / 1000L,
        maxFrames);
    timer.start();
    return true;
  }

  /** Elapsed time against the maximum duration, for the indicator in the menu bar. */
  public String status() {
    long elapsed = elapsedMs() / 1000L;
    long max = maxDurationMs / 1000L;
    return String.format(
        Messages.getString("animation.recording.status"),
        elapsed / 60,
        elapsed % 60,
        max / 60,
        max % 60);
  }

  private long elapsedMs() {
    return System.currentTimeMillis() - startTime;
  }

  /** One timer tick: grab on the EDT, hand the sample to the encoder. */
  private void sample() {
    if (failure != null) {
      stop(null);
      return;
    }
    if (sinkFull) {
      stop(Messages.getString("animation.memory.limit"));
      return;
    }
    if (elapsedMs() >= maxDurationMs || writtenFrames.get() >= maxFrames) {
      stop(null);
      return;
    }
    if (queued.get() >= MAX_QUEUED) {
      return;
    }
    BufferedImage buffer = free.poll();
    if (buffer == null) {
      return;
    }
    if (grabber.grab(buffer) == null) {
      free.add(buffer);
      return;
    }
    long time = System.nanoTime();
    queued.incrementAndGet();
    encoder.execute(() -> encode(buffer, time));
  }

  private void encode(BufferedImage frame, long timeNanos) {
    try {
      frames.accept(frame, timeNanos);
      writtenFrames.set(frames.writtenFrames());
      if (sink.isFull()) {
        sinkFull = true;
      }
    } catch (IOException e) {
      LOGGER.error("Cannot record a frame", e);
      failure = e;
    } finally {
      queued.decrementAndGet();
    }
  }

  /** Ends the recording and writes the file; safe to call more than once. */
  public void stop() {
    stop(null);
  }

  /** Ends the recording, writes the file and, when {@code reason} is given, tells the user why. */
  private void stop(String reason) {
    if (stopped) {
      return;
    }
    stopped = true;
    timer.stop();
    grabber.release();
    RecordingState.stop(this);
    long elapsedMs = elapsedMs();
    long end = System.nanoTime();
    encoder.execute(() -> finish(end));
    encoder.shutdown();
    AnimationProgressDialog.run(
        parent,
        title,
        new SwingWorker<Void, Void>() {
          @Override
          protected Void doInBackground() throws Exception {
            encoder.awaitTermination(1, TimeUnit.MINUTES);
            if (failure != null) {
              sink.abort();
              throw failure;
            }
            if (writtenFrames.get() == 0) {
              sink.abort();
            } else {
              sink.close();
            }
            return null;
          }
        },
        null);
    if (failure != null) {
      return;
    }
    LOGGER.info("Recording ended: {} frames over {} s", writtenFrames.get(), elapsedMs / 1000L);
    String saved =
        String.format(
            Messages.getString("animation.recording.saved"),
            writtenFrames.get(),
            elapsedMs / 1000L);
    String message =
        reason == null
            ? saved
            : String.format(Messages.getString("animation.recording.stopped"), reason)
                + "\n"
                + saved;
    JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
  }

  private void finish(long endNanos) {
    try {
      frames.finish(endNanos);
      writtenFrames.set(frames.writtenFrames());
    } catch (IOException e) {
      LOGGER.error("Cannot record the last frame", e);
      failure = e;
    }
  }
}
