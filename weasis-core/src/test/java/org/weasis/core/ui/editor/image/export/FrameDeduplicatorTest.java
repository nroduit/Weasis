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

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Frame deduplicator")
class FrameDeduplicatorTest {

  private static final long MS = 1_000_000L;

  private static BufferedImage frame(int gray) {
    BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_3BYTE_BGR);
    for (int y = 0; y < 2; y++) {
      for (int x = 0; x < 2; x++) {
        image.setRGB(x, y, gray << 16 | gray << 8 | gray);
      }
    }
    return image;
  }

  @Test
  @DisplayName("merges identical samples into one frame that lasts until the next change")
  void mergesIdenticalSamples() throws IOException {
    RecordingSink sink = new RecordingSink();
    List<BufferedImage> recycled = new ArrayList<>();
    FrameDeduplicator frames = new FrameDeduplicator(sink, 10, recycled::add);
    frames.accept(frame(10), 0);
    frames.accept(frame(10), 100 * MS);
    frames.accept(frame(10), 200 * MS);
    frames.accept(frame(20), 250 * MS);
    frames.finish(300 * MS);
    assertAll(
        () -> assertEquals(List.of(250, 50), sink.durations),
        () -> assertEquals(2, frames.writtenFrames()),
        () -> assertEquals(4, recycled.size()));
  }

  @Test
  @DisplayName("never writes a frame shorter than the sink can represent")
  void floorsTheDuration() throws IOException {
    RecordingSink sink = new RecordingSink();
    FrameDeduplicator frames = new FrameDeduplicator(sink, 10, _ -> {});
    frames.accept(frame(10), 0);
    frames.accept(frame(20), 2 * MS);
    frames.finish(2 * MS);
    assertEquals(List.of(10, 10), sink.durations);
  }

  @Test
  @DisplayName("writes nothing when nothing was sampled")
  void empty() throws IOException {
    RecordingSink sink = new RecordingSink();
    new FrameDeduplicator(sink, 10, _ -> {}).finish(MS);
    assertEquals(List.of(), sink.durations);
  }
}
