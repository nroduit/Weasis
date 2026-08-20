/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import com.jogamp.opengl.GL;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;

/**
 * Splits a 3D frame into the three stages it actually spends time in: the GL ray-cast and blit, the
 * {@code GLJPanel} read-back that hands the result to the Java2D pipeline, and the Swing overlays
 * painted on top. Only the first stage scales with the rendering resolution and the sample count,
 * so this is what tells whether tuning the shader can help at all.
 *
 * <p>Off by default; enable it with {@code -Dweasis.3d.profile=true} or the matching local
 * persistence preference, which is read once per frame so it can be toggled on a running instance.
 * It is not free: timing the GL stage requires a {@code glFinish()}, which serialises CPU and GPU
 * and therefore costs frame rate on its own. Idle and dragging frames are accumulated separately
 * and reported every five seconds in which something was rendered.
 */
final class RenderProfiler {
  private static final Logger LOGGER = LoggerFactory.getLogger(RenderProfiler.class);

  static final String P_PROFILE = "weasis.3d.profile"; // NON-NLS

  private static final long REPORT_INTERVAL_NS = TimeUnit.SECONDS.toNanos(5);

  private final String name;
  private final Stage idle = new Stage("idle"); // NON-NLS
  private final Stage drag = new Stage("drag"); // NON-NLS

  private boolean enabled;
  private long gpuNanos;
  private long lastReport;

  RenderProfiler(Object view) {
    this.name = "view-" + Integer.toHexString(System.identityHashCode(view)); // NON-NLS
  }

  /** Samples the preference once per frame so the render pass and the report agree on it. */
  void beginFrame() {
    enabled =
        Boolean.getBoolean(P_PROFILE)
            || GuiUtils.getUICore().getLocalPersistence().getBooleanProperty(P_PROFILE, false);
    gpuNanos = 0L;
  }

  long start() {
    return enabled ? System.nanoTime() : 0L;
  }

  /** Drains the GL pipeline so the elapsed time covers the work itself, not just its submission. */
  void endGpu(GL gl, long start) {
    if (enabled) {
      gl.glFinish();
      gpuNanos = System.nanoTime() - start;
    }
  }

  void endFrame(
      long panelStart, long overlayStart, boolean adjusting, int passWidth, int passHeight) {
    if (!enabled) {
      return;
    }
    long now = System.nanoTime();
    Stage stage = adjusting ? drag : idle;
    stage.add(gpuNanos, overlayStart - panelStart - gpuNanos, now - overlayStart);
    stage.pass = passWidth + "x" + passHeight; // NON-NLS

    if (lastReport == 0L) {
      lastReport = now;
    } else if (now - lastReport >= REPORT_INTERVAL_NS) {
      lastReport = now;
      idle.log(name);
      drag.log(name);
    }
  }

  /** Accumulates the per-stage timings of one rendering mode over a reporting interval. */
  private static final class Stage {
    private final String mode;
    private String pass = "?"; // NON-NLS
    private long frames;
    private long glSum;
    private long glMax;
    private long readSum;
    private long readMax;
    private long drawSum;
    private long drawMax;

    Stage(String mode) {
      this.mode = mode;
    }

    void add(long gl, long readback, long overlay) {
      frames++;
      glSum += gl;
      glMax = Math.max(glMax, gl);
      readSum += readback;
      readMax = Math.max(readMax, readback);
      drawSum += overlay;
      drawMax = Math.max(drawMax, overlay);
    }

    void log(String name) {
      if (frames == 0) {
        return;
      }
      double total = (glSum + readSum + drawSum) / (double) frames / 1_000_000.0;
      LOGGER.info(
          "3D {} {}: {} frames at {} — gl {} ms, readback {} ms, overlay {} ms, total {} ms avg (≈{} fps)", // NON-NLS
          name,
          mode,
          frames,
          pass,
          avgMax(glSum, glMax),
          avgMax(readSum, readMax),
          avgMax(drawSum, drawMax),
          format(total),
          format(total > 0 ? 1000.0 / total : 0));
      reset();
    }

    private String avgMax(long sum, long max) {
      return format(sum / (double) frames / 1_000_000.0) + "/" + format(max / 1_000_000.0);
    }

    private static String format(double value) {
      return String.format(Locale.ROOT, "%.1f", value);
    }

    private void reset() {
      frames = 0;
      glSum = 0;
      glMax = 0;
      readSum = 0;
      readMax = 0;
      drawSum = 0;
      drawMax = 0;
    }
  }
}
