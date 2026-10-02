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

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;

/**
 * Lighting lookup table of a {@link Preset}. The GL texture name is owned by the preset, one per
 * context, so this class never generates or deletes names itself.
 */
public class LightingMap extends TextureData {

  final float[] map;

  public LightingMap(int width) {
    super(width, PixelFormat.RGBA32F);
    map = new float[width * 4];
  }

  /** Sets the sampling parameters of {@code textureId} on unit 2 and uploads the map. */
  void init(GL2ES2 gl, int textureId) {
    gl.glActiveTexture(GL.GL_TEXTURE2);
    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    upload(gl);
  }

  /** Binds {@code textureId} on unit 2 and uploads the map. */
  void update(GL2ES2 gl, int textureId) {
    gl.glActiveTexture(GL.GL_TEXTURE2);
    gl.glBindTexture(GL.GL_TEXTURE_2D, textureId);
    upload(gl);
  }

  private void upload(GL2ES2 gl) {
    gl.glTexImage2D(
        GL.GL_TEXTURE_2D,
        0,
        internalFormat,
        width,
        height,
        0,
        format,
        type,
        Buffers.newDirectFloatBuffer(map).rewind());
  }

  @Override
  public void init(GL2ES2 gl) {
    throw new UnsupportedOperationException("The texture name is owned by the preset");
  }

  @Override
  public void render(GL2ES2 gl) {
    // Uploaded by Preset.render(GL2ES2, boolean) with the texture name of the current context.
  }

  public void setAmbient(int index, float value) {
    int i = index * 4;
    if (i < map.length) {
      map[i] = value;
    }
  }

  public void setDiffuse(int index, float value) {
    int i = index * 4 + 1;
    if (i < map.length) {
      map[i] = value;
    }
  }

  public void setSpecular(int index, float value) {
    int i = index * 4 + 2;
    if (i < map.length) {
      map[i] = value;
    }
  }
}
