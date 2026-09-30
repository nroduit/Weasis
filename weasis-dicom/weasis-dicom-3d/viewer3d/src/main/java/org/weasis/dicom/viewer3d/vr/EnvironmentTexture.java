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

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.Objects;
import org.weasis.dicom.viewer3d.gl.Gl;

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
  public void init(Gl gl) {
    super.init(gl);
    gl.glActiveTexture(Gl.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_LINEAR_MIPMAP_LINEAR);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_LINEAR);
    // Longitude wraps, latitude clamps at the poles.
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_REPEAT);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAX_LEVEL, EnvironmentMap.LEVELS - 1);
    try (Arena arena = Arena.ofConfined()) {
      for (int level = 0; level < EnvironmentMap.LEVELS; level++) {
        gl.glTexImage2D(
            Gl.GL_TEXTURE_2D,
            level,
            Gl.GL_RGB32F,
            baked.width(level),
            baked.height(level),
            0,
            Gl.GL_RGB,
            Gl.GL_FLOAT,
            arena.allocateFrom(ValueLayout.JAVA_FLOAT, baked.levels().get(level)));
      }
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  /** Binds the texture on its unit, uploading it on first use. */
  @Override
  public void render(Gl gl) {
    if (getId() <= 0) {
      init(gl);
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }
}
