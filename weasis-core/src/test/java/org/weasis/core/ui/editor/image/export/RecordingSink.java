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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A sink that remembers what it was asked, for tests of the pipeline around it. */
final class RecordingSink implements FrameSink {

  final List<Integer> durations = new ArrayList<>();
  boolean opened;
  boolean closed;
  boolean aborted;
  int fullAfter = Integer.MAX_VALUE;
  int failAt = -1;

  @Override
  public void open(int width, int height) {
    opened = true;
  }

  @Override
  public boolean isFull() {
    return durations.size() >= fullAfter;
  }

  @Override
  public void write(BufferedImage frame, int durationMs) throws IOException {
    if (durations.size() == failAt) {
      throw new IOException("disk full");
    }
    durations.add(durationMs);
  }

  @Override
  public void close() {
    closed = true;
  }

  @Override
  public void abort() {
    aborted = true;
  }
}
