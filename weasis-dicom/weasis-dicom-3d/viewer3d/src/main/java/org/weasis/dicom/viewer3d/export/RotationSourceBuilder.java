/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.export;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import org.weasis.core.ui.editor.image.export.AnimationForm;
import org.weasis.core.ui.editor.image.export.AnimationFormat;
import org.weasis.core.ui.editor.image.export.FrameGrabber;
import org.weasis.core.ui.editor.image.export.FrameSource;
import org.weasis.core.ui.editor.image.export.PullSourceBuilder;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.geometry.Axis;
import org.weasis.dicom.viewer3d.vr.View3d;

/**
 * A turn of the volume-rendered view. APNG is the default: a volume render is a smooth synthetic
 * gradient on a static background, which is where the GIF palette bands and where the inter-frame
 * delta of APNG pays for itself.
 */
public class RotationSourceBuilder extends PullSourceBuilder {

  /** 5 degrees per frame on a full turn: the step below which a turn reads as continuous. */
  private static final int DEFAULT_FRAMES = 72;

  private static final double DEFAULT_FRAME_RATE = 24.0;

  private final View3d view;
  private final JComboBox<Axis> axisCombo = new JComboBox<>(Axis.values());
  private final JSpinner frameSpinner =
      new JSpinner(new SpinnerNumberModel(DEFAULT_FRAMES, 4, 360, 1));
  private final JCheckBox pingPongCheckBox =
      new JCheckBox(Messages.getString("rotation.ping.pong"));
  private final JSpinner angleSpinner = new JSpinner(new SpinnerNumberModel(360, 10, 360, 10));
  private final JSpinner samplesSpinner =
      new JSpinner(
          new SpinnerNumberModel(View3d.PATH_TRACING_FRAMES, 16, View3d.PATH_TRACING_FRAMES, 16));

  public RotationSourceBuilder(View3d view) {
    this.view = view;
    axisCombo.setSelectedItem(view.getCamera().getRotationAxis());
    samplesSpinner.setToolTipText(Messages.getString("rotation.samples.tip"));
    angleSpinner.setToolTipText(Messages.getString("rotation.angle.tip"));
    // A partial sweep jumps back at the seam unless it comes back by itself
    angleSpinner.addChangeListener(_ -> pingPongCheckBox.setSelected(angle() < 360));
  }

  @Override
  public String title() {
    return Messages.getString("rotation.export");
  }

  @Override
  public int frameCount(int frameDurationMs) {
    return (Integer) frameSpinner.getValue();
  }

  @Override
  public void addOptions(AnimationForm form, Runnable onChange) {
    frameSpinner.getModel().addChangeListener(_ -> onChange.run());
    form.row(Messages.getString("axis"), axisCombo);
    form.row(Messages.getString("rotation.angle"), angleSpinner, new JLabel("\u00b0"));
    form.row(Messages.getString("rotation.frames"), frameSpinner, pingPongCheckBox);
    if (view.isPathTracing()) {
      form.row(Messages.getString("rotation.samples"), samplesSpinner);
    }
  }

  @Override
  protected FrameSource build(FrameGrabber grabber, int frameDurationMs) {
    return new RotationFrameSource(
        view,
        grabber,
        (Axis) axisCombo.getSelectedItem(),
        frameCount(frameDurationMs),
        frameDurationMs,
        pingPongCheckBox.isSelected(),
        angle(),
        (Integer) samplesSpinner.getValue());
  }

  private int angle() {
    return (Integer) angleSpinner.getValue();
  }

  @Override
  public double defaultFrameRate() {
    return DEFAULT_FRAME_RATE;
  }

  @Override
  public String defaultFileName() {
    return "rotation"; // NON-NLS
  }

  @Override
  public AnimationFormat preferredFormat() {
    return AnimationFormat.APNG;
  }
}
