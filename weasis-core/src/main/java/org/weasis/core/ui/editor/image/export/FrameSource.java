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

/**
 * Supplies the frames of an animation in order. A source knows what advances a frame — a camera
 * sweep, a series index — and delegates the pixels themselves to a {@link FrameGrabber}.
 *
 * <p>Each frame goes through three steps, all called on the EDT: {@link #showFrame(int)} puts the
 * view in the state of the frame, {@link #renderProgress()} is polled until the rendering is
 * complete, and {@link #captureFrame()} grabs it. The EDT stays free between polls, so a rendering
 * that completes over several repaints — a loading volume, a progressive path tracer — can finish.
 */
public interface FrameSource extends AutoCloseable {

  int frameCount();

  /** Puts the view in the state of frame {@code index}. */
  void showFrame(int index);

  /** How far the rendering of the shown frame has come, from 0 to 1; it is captured at 1. */
  default double renderProgress() {
    return 1.0;
  }

  /** Captures the shown frame. */
  BufferedImage captureFrame();

  /** Display time of frame {@code index}, in milliseconds. */
  int durationMs(int index);

  /** Restores whatever state the rendering changed. Called on the EDT, even after a failure. */
  @Override
  void close();
}
