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

import java.lang.foreign.MemorySegment;
import org.weasis.dicom.viewer3d.gl.Gl;

/**
 * The linear float textures the path tracer averages into: a colour pair, a feature pair (first-hit
 * normal and depth, the denoiser's guides) and a moment pair (squared-luminance mean and sample
 * count of each pixel, its convergence). Each frame reads the history from one texture of a pair,
 * on units {@value #HISTORY_UNIT}, {@value #FEATURE_UNIT} and {@value #MOMENT_UNIT}, and writes the
 * new average into the other, then they swap. Full float precision matters, since late frames add
 * increments a half float would round away.
 */
public class AccumulationBuffers {

  public static final int HISTORY_UNIT = 7;
  public static final int FEATURE_UNIT = 8;
  public static final int MOMENT_UNIT = 12;

  private final int[] ids = new int[2];
  private final int[] featureIds = new int[2];
  private final int[] momentIds = new int[2];
  private int current;
  private int width;
  private int height;

  private void allocate(Gl gl, int w, int h) {
    if (ids[0] <= 0) {
      int[] names = gl.glGenTextures(6);
      ids[0] = names[0];
      ids[1] = names[1];
      featureIds[0] = names[2];
      featureIds[1] = names[3];
      momentIds[0] = names[4];
      momentIds[1] = names[5];
    }
    for (int id : new int[] {ids[0], ids[1], featureIds[0], featureIds[1]}) {
      allocate(gl, id, Gl.GL_RGBA32F, Gl.GL_RGBA, w, h);
    }
    for (int id : momentIds) {
      allocate(gl, id, Gl.GL_RG32F, Gl.GL_RG, w, h);
    }
    gl.glBindTexture(Gl.GL_TEXTURE_2D, 0);
    width = w;
    height = h;
  }

  private static void allocate(Gl gl, int id, int internalFormat, int format, int w, int h) {
    gl.glBindTexture(Gl.GL_TEXTURE_2D, id);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexImage2D(
        Gl.GL_TEXTURE_2D, 0, internalFormat, w, h, 0, format, Gl.GL_FLOAT, MemorySegment.NULL);
  }

  /** Sizes the pairs to the render target and binds the history textures on their units. */
  public void prepare(Gl gl, int w, int h) {
    if (ids[0] <= 0 || width != w || height != h) {
      allocate(gl, Math.max(w, 1), Math.max(h, 1));
    }
    bindHistory(gl);
  }

  /** Binds whatever the pairs hold so the samplers are complete even when history is not read. */
  public void bindHistory(Gl gl) {
    if (ids[0] <= 0) {
      allocate(gl, 1, 1);
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0 + HISTORY_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, ids[current]);
    gl.glActiveTexture(Gl.GL_TEXTURE0 + FEATURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, featureIds[current]);
    gl.glActiveTexture(Gl.GL_TEXTURE0 + MOMENT_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, momentIds[current]);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  /** Texture holding the colour average read this frame. */
  public int getHistoryId() {
    return ids[current];
  }

  /** Texture holding the moments read this frame. */
  public int getMomentHistoryId() {
    return momentIds[current];
  }

  /** Texture the current frame writes its colour average into. */
  public int getWriteId() {
    return ids[1 - current];
  }

  /** Texture the current frame writes its feature average into. */
  public int getFeatureWriteId() {
    return featureIds[1 - current];
  }

  /** Texture the current frame writes its moments into. */
  public int getMomentWriteId() {
    return momentIds[1 - current];
  }

  /** Makes the frame just written the history of the next one. */
  public void swap() {
    current = 1 - current;
  }

  public void destroy(Gl gl) {
    if (ids[0] > 0) {
      gl.glDeleteTextures(ids);
      gl.glDeleteTextures(featureIds);
      gl.glDeleteTextures(momentIds);
      ids[0] = 0;
      ids[1] = 0;
      featureIds[0] = 0;
      featureIds[1] = 0;
      momentIds[0] = 0;
      momentIds[1] = 0;
      width = 0;
      height = 0;
    }
  }
}
