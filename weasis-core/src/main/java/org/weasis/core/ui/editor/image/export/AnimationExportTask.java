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

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Drives a pull source into a sink off the EDT: each frame is rendered on the EDT and encoded on
 * the worker thread, so the interface stays responsive and the progress bar keeps moving. While a
 * frame is still rendering, the worker polls the source instead of blocking the EDT, which lets a
 * progressive rendering refine itself through its own repaints.
 *
 * <p>A cancellation is cooperative rather than an interrupt, so the source always gets its chance
 * to restore the state it changed and the partial file is always removed.
 */
public class AnimationExportTask extends SwingWorker<Void, Void> {

  private static final long RENDER_POLL_MS = 15;

  private final FrameSource source;
  private final FrameSink sink;
  private final Dimension frameSize;
  private volatile boolean stopped;

  public AnimationExportTask(FrameSource source, FrameSink sink, Dimension frameSize) {
    this.source = source;
    this.sink = sink;
    this.frameSize = frameSize;
  }

  /** Stops at the next frame boundary, without interrupting the encode in progress. */
  public void stop() {
    stopped = true;
  }

  @Override
  protected Void doInBackground() throws Exception {
    boolean complete = false;
    try {
      sink.open(frameSize.width, frameSize.height);
      int count = source.frameCount();
      for (int i = 0; i < count && !stopped && !sink.isFull(); i++) {
        int index = i;
        SwingUtilities.invokeAndWait(() -> source.showFrame(index));
        if (!awaitRendering(i, count)) {
          break;
        }
        BufferedImage frame = callOnEventThread(source::captureFrame);
        if (frame != null) {
          sink.write(frame, source.durationMs(i));
        }
        publishProgress(i + 1.0, count);
      }
      if (!stopped) {
        sink.close();
        complete = true;
      }
    } finally {
      if (!complete) {
        sink.abort();
      }
      SwingUtilities.invokeLater(source::close);
    }
    return null;
  }

  /** Waits for the shown frame to finish rendering; false when the export was stopped meanwhile. */
  private boolean awaitRendering(int index, int count)
      throws InterruptedException, InvocationTargetException {
    double done;
    while ((done = callOnEventThread(source::renderProgress)) < 1.0) {
      if (stopped) {
        return false;
      }
      publishProgress(index + done, count);
      Thread.sleep(RENDER_POLL_MS);
    }
    return !stopped;
  }

  private void publishProgress(double frames, int count) {
    setProgress(Math.clamp((int) (frames * 100 / Math.max(1, count)), 0, 100));
  }

  private static <T> T callOnEventThread(Supplier<T> task)
      throws InterruptedException, InvocationTargetException {
    AtomicReference<T> holder = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> holder.set(task.get()));
    return holder.get();
  }
}
