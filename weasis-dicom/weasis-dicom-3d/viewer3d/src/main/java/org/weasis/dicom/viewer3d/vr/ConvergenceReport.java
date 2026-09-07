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

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2GL3;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the path tracer's averages back and logs how converged the image is: the share of covered
 * pixels that stopped sampling and the distribution of their standard error. A profiling aid to set
 * the convergence target; it stalls the pipeline and copies the frame, so it only runs at a few
 * sample counts of a refinement and only while profiling.
 */
final class ConvergenceReport {
  private static final Logger LOGGER = LoggerFactory.getLogger(ConvergenceReport.class);

  /** Sample counts of a refinement at which a report is logged. */
  static final int[] MILESTONES = {32, 128, 256, View3d.PATH_TRACING_FRAMES};

  private static final float[] LUMA = {0.2126f, 0.7152f, 0.0722f};

  private ConvergenceReport() {}

  /** Whether a frame that took the count from {@code before} to {@code after} crosses one. */
  static boolean crosses(int before, int after) {
    for (int m : MILESTONES) {
      if (before < m && after >= m) {
        return true;
      }
    }
    return false;
  }

  static void log(
      GL2ES2 gl, int historyId, int momentId, int width, int height, int samples, String name) {
    if (!gl.isGL2GL3() || width <= 0 || height <= 0) {
      return;
    }
    GL2GL3 gl3 = gl.getGL2GL3();
    int pixels = width * height;
    FloatBuffer colour = Buffers.newDirectFloatBuffer(pixels * 4);
    FloatBuffer moment = Buffers.newDirectFloatBuffer(pixels * 2);
    gl3.glActiveTexture(GL.GL_TEXTURE0);
    gl3.glBindTexture(GL.GL_TEXTURE_2D, historyId);
    gl3.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA, GL.GL_FLOAT, colour);
    gl3.glBindTexture(GL.GL_TEXTURE_2D, momentId);
    gl3.glGetTexImage(GL.GL_TEXTURE_2D, 0, GL2ES2.GL_RG, GL.GL_FLOAT, moment);
    gl3.glBindTexture(GL.GL_TEXTURE_2D, 0);

    float[] errors = new float[pixels];
    int covered = 0;
    int stopped = 0;
    double countSum = 0;
    for (int i = 0; i < pixels; i++) {
      float alpha = colour.get(i * 4 + 3);
      if (alpha <= 0f) {
        continue;
      }
      float mean =
          colour.get(i * 4) * LUMA[0]
              + colour.get(i * 4 + 1) * LUMA[1]
              + colour.get(i * 4 + 2) * LUMA[2];
      float squares = moment.get(i * 2);
      float count = moment.get(i * 2 + 1);
      float variance = Math.max(squares - mean * mean, 0f);
      errors[covered++] = (float) Math.sqrt(variance / Math.max(count, 1f));
      countSum += count;
      if (count < samples) {
        stopped++;
      }
    }
    if (covered == 0) {
      LOGGER.info("3D {} path tracing at {} samples: no covered pixel", name, samples);
      return;
    }
    Arrays.sort(errors, 0, covered);
    LOGGER.info(
        "3D {} path tracing at {} samples: {}% of pixels covered, {}% of them stopped, {} samples/pixel avg, standard error p50 {} p75 {} p90 {} p99 {}", // NON-NLS
        name,
        samples,
        Math.round(100.0 * covered / pixels),
        Math.round(100.0 * stopped / covered),
        Math.round(countSum / covered),
        format(errors[covered / 2]),
        format(errors[covered * 3 / 4]),
        format(errors[covered * 9 / 10]),
        format(errors[Math.min(covered - 1, covered * 99 / 100)]));
  }

  private static String format(float value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
