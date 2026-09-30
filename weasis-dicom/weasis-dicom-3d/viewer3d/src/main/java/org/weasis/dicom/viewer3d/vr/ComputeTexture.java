/*
 * Copyright (c) 2023 Weasis Team and other contributors.
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

public class ComputeTexture extends TextureData {
  public static final int COMPUTE_LOCAL_SIZE = 16;
  private final View3d view3d;
  private final int localSize;

  /**
   * Textures bound on image units 1 and 2 for the path tracer's averages, 0 when not accumulating.
   */
  private int accumulationId;

  private int featureId;
  private int momentId;

  /**
   * @param view3d the 3d view
   * @param localSize the size of the shader block (must match to localSize in compute glsl)
   */
  public ComputeTexture(View3d view3d, int localSize) {
    super(view3d.getSurfaceWidth(), view3d.getSurfaceHeight(), PixelFormat.RGBA32F);
    this.view3d = view3d;
    this.localSize = localSize;
  }

  @Override
  public void init(Gl gl) {
    super.init(gl);
    this.width = view3d.getSurfaceWidth();
    this.height = view3d.getSurfaceHeight();

    gl.glActiveTexture(Gl.GL_TEXTURE0);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexImage2D(
        Gl.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, MemorySegment.NULL);
    gl.glBindImageTexture(0, getId(), 0, false, 0, Gl.GL_WRITE_ONLY, internalFormat);

    // Force to render next time
    this.width = 1;
    this.height = 1;
  }

  private static int nextPowerOfTwo(int size) {
    size--;
    size |= size >> 1;
    size |= size >> 2;
    size |= size >> 4;
    size |= size >> 8;
    size |= size >> 16;
    size++;
    return size;
  }

  public void setAccumulationTargets(
      int colorTextureId, int featureTextureId, int momentTextureId) {
    this.accumulationId = colorTextureId;
    this.featureId = featureTextureId;
    this.momentId = momentTextureId;
  }

  @Override
  public void render(Gl gl) {
    if (gl == null) {
      return;
    }
    if (getId() <= 0) {
      init(gl);
    }
    if (accumulationId > 0) {
      gl.glBindImageTexture(1, accumulationId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RGBA32F);
      gl.glBindImageTexture(2, featureId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RGBA32F);
      gl.glBindImageTexture(3, momentId, 0, false, 0, Gl.GL_WRITE_ONLY, Gl.GL_RG32F);
    }
    dispatch(gl);
  }

  /** Runs the active compute program over the output image. */
  public void dispatch(Gl gl) {
    if (width != view3d.getSurfaceWidth() || height != view3d.getSurfaceHeight()) {
      gl.glActiveTexture(Gl.GL_TEXTURE0);
      gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
      this.width = view3d.getSurfaceWidth();
      this.height = view3d.getSurfaceHeight();
      gl.glTexImage2D(
          Gl.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, MemorySegment.NULL);
    }

    int workSizeX = Math.max(localSize, nextPowerOfTwo(width));
    int workSizeY = Math.max(localSize, nextPowerOfTwo(height));
    gl.glDispatchCompute(workSizeX / localSize, workSizeY / localSize, 1);
    gl.glMemoryBarrier(Gl.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  }
}
