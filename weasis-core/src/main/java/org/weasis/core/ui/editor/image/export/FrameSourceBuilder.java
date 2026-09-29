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

/**
 * Contributes what is specific to one kind of animation: its own controls, its defaults, and how
 * the export runs. Everything else — scope, size, format, destination — is common and lives in
 * {@link AnimationExportDialog}.
 *
 * <p>Sources that know their frames in advance extend {@link PullSourceBuilder}; a recorder, which
 * is driven by the clock and by what the user does, implements {@link #run} itself.
 */
public interface FrameSourceBuilder {

  String title();

  /** Frames the current settings would produce, or the cap when the source is open-ended. */
  int frameCount(int frameDurationMs);

  /**
   * Adds the rows specific to this source to the form, if any.
   *
   * @param onChange to be run whenever a control changes the frame count or the timing
   */
  void addOptions(AnimationForm form, Runnable onChange);

  /**
   * Produces the animation. The dialog has already closed and {@code grabber} is prepared;
   * releasing it once the export is over belongs to the implementation.
   */
  void run(Window parent, FrameGrabber grabber, int frameDurationMs, FrameSink sink);

  /** Base name proposed in the file chooser, without an extension. */
  String defaultFileName();

  default AnimationFormat preferredFormat() {
    return AnimationFormat.APNG;
  }

  default CaptureScope defaultScope() {
    return CaptureScope.VIEW;
  }

  default double defaultFrameRate() {
    return 15.0;
  }

  /** Output size as a percentage of the captured area. */
  default int defaultSizeRatio() {
    return 100;
  }
}
