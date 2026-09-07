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

import java.awt.image.BufferedImage;
import java.util.Objects;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.weasis.core.ui.editor.image.export.FrameGrabber;
import org.weasis.core.ui.editor.image.export.FrameSource;
import org.weasis.dicom.viewer3d.geometry.Axis;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.vr.View3d;

/**
 * Turns the volume around one axis and captures each step.
 *
 * <p>The sweep rotates the start pose by a fixed delta about the chosen axis of the volume, the
 * same axis the rotation slider turns about, rather than going through {@link
 * Camera#setRotation(int)}, whose integer degrees would quantize the turn. The camera is also kept
 * out of its adjusting state for every captured frame, since while adjusting the renderer ray-casts
 * a sub-rectangle and upscales it, which is visibly soft.
 *
 * <p>Nothing is captured before the volume has finished loading: a partial volume would show, and
 * the end of the load resets the camera and the preset of the view. The start pose is therefore
 * taken only then. With path tracing, each frame is captured once its running average holds the
 * requested number of samples, instead of the one-sample noise the first pass produces.
 *
 * <p><b>The rendering quality is deliberately left alone.</b> Raising it for the capture is
 * tempting — an export can afford to be slower than an interactive frame — but {@code
 * rayCastingComposite} in {@code vrFunctions.glsl} does not correct the per-sample opacity for the
 * sampling rate: {@code depthSampleNumber} sets how many samples a ray takes and each one
 * contributes the same alpha, so more samples means a more opaque and more saturated image. The
 * opacity slider is calibrated against the quality in force, and the export must show what the
 * display shows. The segmentation overlay a few lines above in the same shader does correct for the
 * rate, which is why it would have kept its colours while the anatomy shifted.
 */
public class RotationFrameSource implements FrameSource {

  private final View3d view;
  private final FrameGrabber grabber;
  private final Axis axis;
  private final int frameCount;
  private final int durationMs;
  private final boolean pingPong;
  private final double sweep;
  private final int samples;

  private CameraPose start;
  private int shownIndex;
  private boolean posed;

  /**
   * @param sweepDegrees the angle covered, 360 for a full turn; less spreads the same frames over a
   *     smaller arc, which is what makes the motion smooth
   * @param samples path-traced samples each frame waits for, capped at what the view accumulates
   */
  public RotationFrameSource(
      View3d view,
      FrameGrabber grabber,
      Axis axis,
      int frameCount,
      int durationMs,
      boolean pingPong,
      double sweepDegrees,
      int samples) {
    this.view = Objects.requireNonNull(view);
    this.grabber = Objects.requireNonNull(grabber);
    this.axis = axis;
    this.frameCount = Math.max(1, frameCount);
    this.durationMs = durationMs;
    this.pingPong = pingPong;
    this.sweep = Math.toRadians(Math.clamp(sweepDegrees, 1.0, 360.0));
    this.samples = Math.clamp(samples, 1, View3d.PATH_TRACING_FRAMES);
  }

  @Override
  public int frameCount() {
    return frameCount;
  }

  @Override
  public void showFrame(int index) {
    shownIndex = index;
    posed = false;
  }

  @Override
  public double renderProgress() {
    if (!view.isLoadFinished()) {
      return 0.0;
    }
    if (start == null) {
      start = CameraPose.of(view.getCamera());
      view.getCamera().setAdjusting(false);
      view.setCapturing(true);
    }
    if (!posed) {
      pose(shownIndex);
      posed = true;
    }
    return view.isPathTracing() ? convergence(view.getAccumulatedSamples(), samples) : 1.0;
  }

  static double convergence(int accumulated, int target) {
    return Math.min(1.0, accumulated / (double) target);
  }

  private void pose(int index) {
    view.getCamera()
        .set(
            start.position(),
            new Quaterniond(start.rotation())
                .rotateAxis(sweepAngle(index, frameCount, pingPong, sweep), axis.direction()),
            start.zoom(),
            false);
    view.display();
  }

  @Override
  public BufferedImage captureFrame() {
    return grabber.grab();
  }

  /**
   * The angle of frame {@code index}. A full turn never repeats its first frame at the seam; a
   * ping-pong goes out to the sweep and back, half a turn when the sweep is a full one, since a
   * full turn and back would show every pose twice; a partial one-way sweep ends exactly on it.
   */
  static double sweepAngle(int index, int frameCount, boolean pingPong, double sweep) {
    double phase = index / (double) frameCount;
    boolean fullTurn = sweep >= 2 * Math.PI - 1.0e-9;
    if (pingPong) {
      double arc = fullTurn ? Math.PI : sweep;
      return arc * (phase <= 0.5 ? phase * 2 : (1 - phase) * 2);
    }
    if (fullTurn) {
      return sweep * phase;
    }
    return frameCount == 1 ? 0.0 : sweep * index / (frameCount - 1);
  }

  @Override
  public int durationMs(int index) {
    return durationMs;
  }

  @Override
  public void close() {
    view.setCapturing(false);
    if (start != null) {
      start.restore(view.getCamera());
    }
  }

  private record CameraPose(
      Vector3d position, Quaterniond rotation, double zoom, boolean adjusting) {

    static CameraPose of(Camera camera) {
      return new CameraPose(
          new Vector3d(camera.getPosition()),
          new Quaterniond(camera.getRotation()),
          camera.getZoomFactor(),
          camera.isAdjusting());
    }

    void restore(Camera camera) {
      camera.setAdjusting(adjusting);
      camera.set(position, rotation, zoom);
      camera.updateRotationAction();
    }
  }
}
