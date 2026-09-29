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
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.Messages;

/**
 * Records the viewer while the user works. The container is the default scope: an MPR crosshair
 * drag is meaningless without the other panes. The maximum duration bounds an otherwise open-ended
 * capture, and with it the memory the animated sinks need.
 */
public class RecorderSourceBuilder implements FrameSourceBuilder {

  private static final int DEFAULT_MAX_SECONDS = 60;

  private final boolean containerAvailable;
  private final JSpinner durationSpinner =
      new JSpinner(new SpinnerNumberModel(DEFAULT_MAX_SECONDS, 5, 600, 5));
  private static final double DEFAULT_FRAME_RATE = 12.0;
  private final JCheckBox pointerCheckBox =
      new JCheckBox(Messages.getString("animation.pointer"), true);

  public RecorderSourceBuilder(boolean containerAvailable) {
    this.containerAvailable = containerAvailable;
  }

  @Override
  public String title() {
    return Messages.getString("animation.recording");
  }

  @Override
  public int frameCount(int frameDurationMs) {
    return (int) Math.ceil(maxSeconds() * 1000.0 / Math.max(1, frameDurationMs));
  }

  @Override
  public void addOptions(AnimationForm form, Runnable onChange) {
    durationSpinner.getModel().addChangeListener(_ -> onChange.run());
    form.row(
        Messages.getString("animation.max.duration"),
        durationSpinner,
        new JLabel(Messages.getString("animation.seconds")),
        pointerCheckBox);
  }

  @Override
  public void run(Window parent, FrameGrabber grabber, int frameDurationMs, FrameSink sink) {
    grabber.setPointerVisible(pointerCheckBox.isSelected());
    grabber.setFollowSelection(true);
    boolean started =
        new AnimationRecorder(
                parent,
                title(),
                grabber,
                sink,
                frameDurationMs,
                maxSeconds() * 1000L,
                frameCount(frameDurationMs))
            .start();
    if (!started) {
      sink.abort();
      JOptionPane.showMessageDialog(
          parent,
          Messages.getString("animation.recording.busy"),
          title(),
          JOptionPane.WARNING_MESSAGE);
    }
  }

  @Override
  public String defaultFileName() {
    return "recording"; // NON-NLS
  }

  @Override
  public CaptureScope defaultScope() {
    return containerAvailable ? CaptureScope.CONTAINER : CaptureScope.APPLICATION_WINDOW;
  }

  @Override
  public double defaultFrameRate() {
    return DEFAULT_FRAME_RATE;
  }

  /** Half size keeps a minute of a full-HD layout under the memory cap of the animated sinks. */
  @Override
  public int defaultSizeRatio() {
    return 50;
  }

  private int maxSeconds() {
    return (Integer) durationSpinner.getValue();
  }
}
