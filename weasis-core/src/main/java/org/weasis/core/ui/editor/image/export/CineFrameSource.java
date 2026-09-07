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
import java.util.Objects;
import org.weasis.core.api.gui.util.SliderChangeListener;

/**
 * Plays a series out frame by frame through the view itself, so window level, zoom and overlays in
 * the export match what is on screen. The slider is put back where it was when the export ends.
 */
public class CineFrameSource implements FrameSource {

  private final FrameGrabber grabber;
  private final SliderChangeListener scroll;
  private final int first;
  private final int stride;
  private final int count;
  private final int durationMs;
  private final int initialValue;

  public CineFrameSource(
      FrameGrabber grabber,
      SliderChangeListener scroll,
      int first,
      int last,
      int stride,
      int durationMs) {
    this.grabber = Objects.requireNonNull(grabber);
    this.scroll = Objects.requireNonNull(scroll);
    this.first = first;
    this.stride = Math.max(1, stride);
    this.count = Math.max(0, (last - first) / this.stride + 1);
    this.durationMs = durationMs;
    this.initialValue = scroll.getSliderValue();
  }

  @Override
  public int frameCount() {
    return count;
  }

  @Override
  public void showFrame(int index) {
    scroll.setSliderValue(first + index * stride);
  }

  @Override
  public BufferedImage captureFrame() {
    return grabber.grab();
  }

  @Override
  public int durationMs(int index) {
    return durationMs;
  }

  @Override
  public void close() {
    scroll.setSliderValue(initialValue);
  }
}
