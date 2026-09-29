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

/** Consumes the frames of an animation and produces one output file. */
public interface FrameSink extends AutoCloseable {

  void open(int width, int height) throws IOException;

  /** Whether the sink has reached its own capacity and no further frame can be accepted. */
  default boolean isFull() {
    return false;
  }

  void write(BufferedImage frame, int durationMs) throws IOException;

  /** Finishes the output file. */
  @Override
  void close() throws IOException;

  /** Releases the resources and deletes the partial output after a cancellation or a failure. */
  void abort();
}
