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

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;

public class ComputeTexture extends TextureData {
  public static final int COMPUTE_LOCAL_SIZE = 16;
  private final View3d view3d;
  private final int localSize;

  /**
   * @param view3d the 3d view
   * @param localSize the size of the shader block (must match to localSize in compute glsl)
   */
  public ComputeTexture(View3d view3d, int localSize) {
    super(view3d.getWidth(), view3d.getHeight(), PixelFormat.RGBA32F);
    this.view3d = view3d;
    this.localSize = localSize;
  }

  @Override
  public void init(GL4 gl4) {
    super.init(gl4);
    this.width = view3d.getWidth();
    this.height = view3d.getHeight();

    gl4.glActiveTexture(GL.GL_TEXTURE0);
    gl4.glBindTexture(GL.GL_TEXTURE_2D, getId());
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    gl4.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
    gl4.glTexImage2D(GL.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
    gl4.glBindImageTexture(0, getId(), 0, false, 0, GL.GL_WRITE_ONLY, internalFormat);

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

  @Override
  public void render(GL4 gl4) {
    if (gl4 == null) {
      return;
    }
    if (getId() <= 0) {
      init(gl4);
    }

    if (width != view3d.getWidth() || height != view3d.getHeight()) {
      gl4.glActiveTexture(GL.GL_TEXTURE0);
      gl4.glBindTexture(GL.GL_TEXTURE_2D, getId());
      this.width = view3d.getWidth();
      this.height = view3d.getHeight();
      gl4.glTexImage2D(GL.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
    }

    int workSizeX = Math.max(localSize, nextPowerOfTwo(width));
    int workSizeY = Math.max(localSize, nextPowerOfTwo(height));
    gl4.glDispatchCompute(workSizeX / localSize, workSizeY / localSize, 1);
    gl4.glMemoryBarrier(GL2ES3.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
  }
}
