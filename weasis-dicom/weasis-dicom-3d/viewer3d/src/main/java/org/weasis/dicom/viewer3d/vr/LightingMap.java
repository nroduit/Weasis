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

import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import org.weasis.dicom.viewer3d.gl.Gl;

public class LightingMap extends TextureData {

  final float[] map;

  public LightingMap(int width) {
    super(width, PixelFormat.RGBA32F);
    map = new float[width * 4];
  }

  @Override
  public void init(Gl gl) {
    super.init(gl);
    gl.glActiveTexture(Gl.GL_TEXTURE2);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_LINEAR);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_LINEAR);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    upload(gl);
  }

  @Override
  public void render(Gl gl) {
    update(gl);
  }

  void update(Gl gl) {
    if (gl != null) {
      if (getId() <= 0) {
        init(gl);
        return;
      }
      gl.glActiveTexture(Gl.GL_TEXTURE2);
      gl.glBindTexture(Gl.GL_TEXTURE_2D, getId());
      upload(gl);
    }
  }

  private void upload(Gl gl) {
    try (Arena arena = Arena.ofConfined()) {
      gl.glTexImage2D(
          Gl.GL_TEXTURE_2D,
          0,
          internalFormat,
          width,
          height,
          0,
          format,
          type,
          arena.allocateFrom(ValueLayout.JAVA_FLOAT, map));
    }
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
