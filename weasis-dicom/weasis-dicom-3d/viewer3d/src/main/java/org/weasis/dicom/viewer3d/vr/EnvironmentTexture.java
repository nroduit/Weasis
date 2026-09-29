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
import java.util.Objects;

/**
 * GL form of a baked {@link EnvironmentMap}: an RGB float equirectangular texture whose mip chain
 * holds the roughness-prefiltered levels, bound on unit {@value #TEXTURE_UNIT}.
 */
public class EnvironmentTexture extends TextureData {

  /**
   * Above the volume (0), colour map (1), lighting map (2), FBO output (3) and segmentation (4, 5).
   */
  public static final int TEXTURE_UNIT = 6;

  private final EnvironmentMap.Baked baked;

  public EnvironmentTexture(EnvironmentMap.Baked baked) {
    super(EnvironmentMap.BASE_WIDTH, EnvironmentMap.BASE_WIDTH / 2, PixelFormat.RGBA32F);
    this.baked = Objects.requireNonNull(baked);
  }

  public float[] getIrradianceSh() {
    return baked.irradianceSh();
  }

  public static float maxLod() {
    return EnvironmentMap.LEVELS - 1f;
  }

  @Override
  public void init(GL2ES2 gl) {
    super.init(gl);
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, getId());
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR_MIPMAP_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    // Longitude wraps, latitude clamps at the poles.
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_REPEAT);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL2GL3.GL_TEXTURE_MAX_LEVEL, EnvironmentMap.LEVELS - 1);
    for (int level = 0; level < EnvironmentMap.LEVELS; level++) {
      gl.glTexImage2D(
          GL.GL_TEXTURE_2D,
          level,
          GL2GL3.GL_RGB32F,
          baked.width(level),
          baked.height(level),
          0,
          GL.GL_RGB,
          GL.GL_FLOAT,
          Buffers.newDirectFloatBuffer(baked.levels().get(level)).rewind());
    }
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  /** Binds the texture on its unit, uploading it on first use. */
  @Override
  public void render(GL2ES2 gl) {
    if (getId() <= 0) {
      init(gl);
    }
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, getId());
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }
}
