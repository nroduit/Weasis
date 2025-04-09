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

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2GL3;
import com.jogamp.opengl.GL4;
import java.nio.IntBuffer;

public abstract class TextureData {
  public enum PixelFormat {
    BYTE,
    UNSIGNED_SHORT,
    SIGNED_SHORT,
    FLOAT,
    RGB8,
    RGBA32F,
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
        this.internalFormat = GL.GL_R8;
        this.type = GL.GL_UNSIGNED_BYTE;
        this.format = GL2ES2.GL_RED;
      }
      case SIGNED_SHORT, UNSIGNED_SHORT -> {
        this.internalFormat = GL2GL3.GL_R16;
        this.type = GL.GL_UNSIGNED_SHORT;
        this.format = GL2ES2.GL_RED;
      }
      case FLOAT -> {
        this.internalFormat = GL2GL3.GL_R32F;
        this.type = GL.GL_FLOAT;
        this.format = GL2ES2.GL_RED;
      }
      case RGB8 -> {
        this.internalFormat = GL.GL_RGB8;
        this.type = GL.GL_UNSIGNED_BYTE;
        this.format = GL.GL_RGB;
      }
      case RGBA8 -> {
        this.internalFormat = GL.GL_RGBA8;
        this.type = GL.GL_UNSIGNED_BYTE;
        this.format = GL.GL_RGBA;
      }
      default -> {
        this.internalFormat = GL.GL_RGBA32F;
        this.type = GL.GL_FLOAT;
        this.format = GL.GL_RGBA;
      }
    }
  }

  public void init(GL4 gl4) {
    if (id <= 0) {
      IntBuffer intBuffer = IntBuffer.allocate(1);
      gl4.glGenTextures(1, intBuffer);
      id = intBuffer.get(0);
    }
  }

  public abstract void render(GL4 gl4);

  public void destroy(GL4 gl4) {
    if (id != 0) {
      gl4.glDeleteTextures(1, new int[] {id}, 0);
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
      case RGBA32F -> 5;
      case FLOAT -> 6;
    };
  }
}
