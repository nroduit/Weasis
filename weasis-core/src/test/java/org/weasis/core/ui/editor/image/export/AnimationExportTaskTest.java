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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The task needs an event thread but no display: every step runs through {@code invokeAndWait}. */
@DisplayName("Animation export task")
class AnimationExportTaskTest {

  /** A source of {@code count} tiny frames; frame {@code blank} yields no image. */
  private static final class FakeSource implements FrameSource {
    final List<Integer> shown = new ArrayList<>();
    final int count;
    final int blank;
    Runnable onShow;
    boolean closed;

    FakeSource(int count, int blank) {
      this.count = count;
      this.blank = blank;
    }

    @Override
    public int frameCount() {
      return count;
    }

    @Override
    public void showFrame(int index) {
      shown.add(index);
      if (onShow != null) {
        onShow.run();
      }
    }

    @Override
    public BufferedImage captureFrame() {
      int index = shown.getLast();
      return index == blank ? null : new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
    }

    @Override
    public int durationMs(int index) {
      return 40 + index;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  private static void run(AnimationExportTask task) throws Exception {
    task.execute();
    try {
      task.get();
    } finally {
      SwingUtilities.invokeAndWait(() -> {}); // the source closes on the EDT after the task
    }
  }

  private static AnimationExportTask task(FakeSource source, RecordingSink sink) {
    return new AnimationExportTask(source, sink, new Dimension(2, 2));
  }

  @Test
  @DisplayName("writes every frame with its own duration, then closes the sink and the source")
  void completes() throws Exception {
    FakeSource source = new FakeSource(3, -1);
    RecordingSink sink = new RecordingSink();
    run(task(source, sink));
    assertAll(
        () -> assertEquals(List.of(0, 1, 2), source.shown),
        () -> assertEquals(List.of(40, 41, 42), sink.durations),
        () -> assertTrue(sink.opened),
        () -> assertTrue(sink.closed),
        () -> assertFalse(sink.aborted),
        () -> assertTrue(source.closed));
  }

  @Test
  @DisplayName("skips a frame the source could not capture")
  void skipsBlankFrame() throws Exception {
    FakeSource source = new FakeSource(3, 1);
    RecordingSink sink = new RecordingSink();
    run(task(source, sink));
    assertEquals(List.of(40, 42), sink.durations);
  }

  @Test
  @DisplayName("a stop aborts the output at the next frame and still restores the source")
  void stopAborts() throws Exception {
    FakeSource source = new FakeSource(5, -1);
    RecordingSink sink = new RecordingSink();
    AnimationExportTask task = task(source, sink);
    source.onShow = () -> task.stop();
    run(task);
    assertAll(
        () -> assertEquals(List.of(0), source.shown),
        () -> assertTrue(sink.durations.isEmpty()),
        () -> assertTrue(sink.aborted),
        () -> assertFalse(sink.closed),
        () -> assertTrue(source.closed));
  }

  @Test
  @DisplayName("a full sink ends the export early with a complete file")
  void fullSinkEndsEarly() throws Exception {
    FakeSource source = new FakeSource(10, -1);
    RecordingSink sink = new RecordingSink();
    sink.fullAfter = 2;
    run(task(source, sink));
    assertAll(
        () -> assertEquals(2, sink.durations.size()),
        () -> assertTrue(sink.closed),
        () -> assertFalse(sink.aborted));
  }

  @Test
  @DisplayName("a failing write aborts the output and reports the failure")
  void failingWriteAborts() throws Exception {
    FakeSource source = new FakeSource(3, -1);
    RecordingSink sink = new RecordingSink();
    sink.failAt = 1;
    AnimationExportTask task = task(source, sink);
    assertThrows(ExecutionException.class, () -> run(task));
    assertAll(
        () -> assertTrue(sink.aborted),
        () -> assertFalse(sink.closed),
        () -> assertTrue(source.closed));
  }
}
