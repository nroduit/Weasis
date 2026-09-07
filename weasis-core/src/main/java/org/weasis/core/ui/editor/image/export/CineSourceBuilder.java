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

import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.opencv.data.PlanarImage;

/**
 * Exports the frames a view already holds. The defaults follow the series rather than a fixed
 * value: the whole range, the cine speed configured for playback, and GIF unless the source is in
 * colour — a 256-entry palette is effectively lossless on 8-bit grayscale, and the encode is the
 * fastest of the two, which is what long loops need.
 */
public class CineSourceBuilder extends PullSourceBuilder {

  private final ViewCanvas<?> view;
  private final SliderCineListener scroll;
  private final JSpinner firstSpinner;
  private final JSpinner lastSpinner;
  private final JSpinner strideSpinner;

  public CineSourceBuilder(ViewCanvas<?> view, SliderCineListener scroll) {
    this.view = view;
    this.scroll = scroll;
    int min = scroll.getSliderMin();
    int max = scroll.getSliderMax();
    this.firstSpinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
    this.lastSpinner = new JSpinner(new SpinnerNumberModel(max, min, max, 1));
    this.strideSpinner = new JSpinner(new SpinnerNumberModel(1, 1, Math.max(1, max - min), 1));
  }

  @Override
  public String title() {
    return Messages.getString("animation.cine");
  }

  @Override
  public int frameCount(int frameDurationMs) {
    return Math.max(0, (last() - first()) / stride() + 1);
  }

  @Override
  public void addOptions(AnimationForm form, Runnable onChange) {
    firstSpinner.getModel().addChangeListener(_ -> onChange.run());
    lastSpinner.getModel().addChangeListener(_ -> onChange.run());
    strideSpinner.getModel().addChangeListener(_ -> onChange.run());
    firstSpinner.setToolTipText(Messages.getString("animation.first.frame"));
    lastSpinner.setToolTipText(Messages.getString("animation.last.frame"));
    strideSpinner.setToolTipText(Messages.getString("animation.stride.tip"));
    form.row(
        Messages.getString("animation.frame.range"),
        firstSpinner,
        new JLabel("\u2013"),
        lastSpinner,
        new JLabel(Messages.getString("animation.stride")),
        strideSpinner);
  }

  @Override
  protected FrameSource build(FrameGrabber grabber, int frameDurationMs) {
    return new CineFrameSource(grabber, scroll, first(), last(), stride(), frameDurationMs);
  }

  @Override
  public String defaultFileName() {
    return "cine"; // NON-NLS
  }

  @Override
  public AnimationFormat preferredFormat() {
    return isColorSource() ? AnimationFormat.APNG : AnimationFormat.GIF;
  }

  @Override
  public double defaultFrameRate() {
    double speed = scroll.getSpeed();
    return speed > 0 ? speed : super.defaultFrameRate();
  }

  private boolean isColorSource() {
    PlanarImage image = view.getSourceImage();
    return image != null && image.channels() > 1;
  }

  private int first() {
    return (Integer) firstSpinner.getValue();
  }

  private int last() {
    return Math.max(first(), (Integer) lastSpinner.getValue());
  }

  private int stride() {
    return (Integer) strideSpinner.getValue();
  }
}
