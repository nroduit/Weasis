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
import com.jogamp.opengl.GL2ES2;
import java.nio.IntBuffer;

/**
 * The linear RGBA32F textures the path tracer averages into: a colour pair and a feature pair
 * (first-hit normal and depth, the denoiser's guides). Each frame reads the history from one
 * texture of a pair, on units {@value #HISTORY_UNIT} and {@value #FEATURE_UNIT}, and writes the new
 * average into the other, then they swap. Full float precision matters, since late frames add
 * increments a half float would round away.
 */
public class AccumulationBuffers {

  public static final int HISTORY_UNIT = 7;
  public static final int FEATURE_UNIT = 8;

  private final int[] ids = new int[2];
  private final int[] featureIds = new int[2];
  private int current;
  private int width;
  private int height;

  private void allocate(GL2ES2 gl, int w, int h) {
    if (ids[0] <= 0) {
      IntBuffer buf = IntBuffer.allocate(4);
      gl.glGenTextures(4, buf);
      ids[0] = buf.get(0);
      ids[1] = buf.get(1);
      featureIds[0] = buf.get(2);
      featureIds[1] = buf.get(3);
    }
    for (int id : new int[] {ids[0], ids[1], featureIds[0], featureIds[1]}) {
      gl.glBindTexture(GL.GL_TEXTURE_2D, id);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
      gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
      gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA32F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
    }
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    width = w;
    height = h;
  }

  /** Sizes the pair to the render target and binds the history texture on its unit. */
  public void prepare(GL2ES2 gl, int w, int h) {
    if (ids[0] <= 0 || width != w || height != h) {
      allocate(gl, Math.max(w, 1), Math.max(h, 1));
    }
    bindHistory(gl);
  }

  /** Binds whatever the pairs hold so the samplers are complete even when history is not read. */
  public void bindHistory(GL2ES2 gl) {
    if (ids[0] <= 0) {
      allocate(gl, 1, 1);
    }
    gl.glActiveTexture(GL.GL_TEXTURE0 + HISTORY_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, ids[current]);
    gl.glActiveTexture(GL.GL_TEXTURE0 + FEATURE_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, featureIds[current]);
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  /** Texture the current frame writes its colour average into. */
  public int getWriteId() {
    return ids[1 - current];
  }

  /** Texture the current frame writes its feature average into. */
  public int getFeatureWriteId() {
    return featureIds[1 - current];
  }

  /** Makes the frame just written the history of the next one. */
  public void swap() {
    current = 1 - current;
  }

  public void destroy(GL2ES2 gl) {
    if (ids[0] > 0) {
      gl.glDeleteTextures(2, ids, 0);
      gl.glDeleteTextures(2, featureIds, 0);
      ids[0] = 0;
      ids[1] = 0;
      featureIds[0] = 0;
      featureIds[1] = 0;
      width = 0;
      height = 0;
    }
  }
}
