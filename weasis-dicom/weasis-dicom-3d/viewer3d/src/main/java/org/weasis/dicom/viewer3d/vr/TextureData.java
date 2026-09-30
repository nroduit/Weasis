/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import org.weasis.dicom.viewer3d.gl.Gl;

public abstract class TextureData {
  public enum PixelFormat {
    BYTE,
    UNSIGNED_SHORT,
    SIGNED_SHORT,
    FLOAT,
    RGB8,
    RGBA32F,
    RGBA16F,
    RGBA8;
  }

  protected final PixelFormat pixelFormat;
  protected int width;
  protected int height;
  protected int depth;
  protected final int type;
  protected final int internalFormat;
  protected final int format;
  private int id;

  protected TextureData(int width, PixelFormat pixelFormat) {
    this(width, 1, 1, pixelFormat);
  }

  protected TextureData(int width, int height, PixelFormat pixelFormat) {
    this(width, height, 1, pixelFormat);
  }

  protected TextureData(int width, int height, int depth, PixelFormat pixelFormat) {
    this.width = width;
    this.height = height;
    this.depth = depth;
    this.pixelFormat = pixelFormat;
    switch (pixelFormat) {
      case BYTE -> {
        this.internalFormat = Gl.GL_R8;
        this.type = Gl.GL_UNSIGNED_BYTE;
        this.format = Gl.GL_RED;
      }
      case SIGNED_SHORT, UNSIGNED_SHORT -> {
        this.internalFormat = Gl.GL_R16;
        this.type = Gl.GL_UNSIGNED_SHORT;
        this.format = Gl.GL_RED;
      }
      case FLOAT -> {
        this.internalFormat = Gl.GL_R32F;
        this.type = Gl.GL_FLOAT;
        this.format = Gl.GL_RED;
      }
      case RGB8 -> {
        this.internalFormat = Gl.GL_RGB8;
        this.type = Gl.GL_UNSIGNED_BYTE;
        this.format = Gl.GL_RGB;
      }
      case RGBA8 -> {
        this.internalFormat = Gl.GL_RGBA8;
        this.type = Gl.GL_UNSIGNED_BYTE;
        this.format = Gl.GL_RGBA;
      }
      case RGBA16F -> {
        // Half-float: 8 bytes/pixel vs 16 for RGBA32F — halves GPU memory bandwidth on Retina.
        this.internalFormat = Gl.GL_RGBA16F;
        this.type = Gl.GL_FLOAT;
        this.format = Gl.GL_RGBA;
      }
      default -> {
        this.internalFormat = Gl.GL_RGBA32F;
        this.type = Gl.GL_FLOAT;
        this.format = Gl.GL_RGBA;
      }
    }
  }

  public void init(Gl gl) {
    if (id <= 0) {
      id = gl.glGenTextures(1)[0];
    }
  }

  public abstract void render(Gl gl);

  public void destroy(Gl gl) {
    if (id != 0) {
      gl.glDeleteTextures(id);
      id = 0;
    }
  }

  public PixelFormat getPixelFormat() {
    return pixelFormat;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public int getDepth() {
    return depth;
  }

  public int getType() {
    return type;
  }

  public int getInternalFormat() {
    return internalFormat;
  }

  public int getFormat() {
    return format;
  }

  public int getId() {
    return id;
  }

  public static int getDataType(PixelFormat format) {
    return switch (format) {
      case BYTE -> 0;
      case SIGNED_SHORT -> 1;
      case UNSIGNED_SHORT -> 2;
      case RGB8 -> 3;
      case RGBA8 -> 4;
      case RGBA32F, RGBA16F -> 5;
      case FLOAT -> 6;
    };
  }
}
