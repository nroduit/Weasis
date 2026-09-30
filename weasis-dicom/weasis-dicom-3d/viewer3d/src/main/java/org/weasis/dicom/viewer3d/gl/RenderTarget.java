/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.gl;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * What one view draws into and what Swing paints from: an RGBA8 colour texture with its framebuffer
 * object, a pixel pack buffer, and a pair of images the frames alternate between.
 *
 * <p>The GL thread fills the back image and swaps; the Swing thread, which is blocked while that
 * happens, then paints the front one. An expose or an overlay change repaints that image again
 * without any GL work.
 *
 * <p>The image is {@link BufferedImage#TYPE_INT_RGB}: the quad pass blends the ray-cast output over
 * the opaque background colour, so alpha carries nothing, and it samples upside down, so the rows
 * arrive in the order the image wants them.
 */
public final class RenderTarget {

  private static final Logger LOGGER = LoggerFactory.getLogger(RenderTarget.class);

  private int width;
  private int height;
  private int texture;
  private int framebuffer;
  private int packBuffer;

  private BufferedImage back;
  private int[] backPixels;
  private volatile BufferedImage front;

  /** Sizes the target to the surface, reallocating only when it actually changed. */
  public void ensureSize(Gl gl, int surfaceWidth, int surfaceHeight) {
    int w = Math.max(surfaceWidth, 1);
    int h = Math.max(surfaceHeight, 1);
    if (w == width && h == height && texture > 0) {
      return;
    }
    width = w;
    height = h;
    if (texture <= 0) {
      texture = gl.glGenTextures(1)[0];
      framebuffer = gl.glGenFramebuffers(1)[0];
      packBuffer = gl.glGenBuffers(1)[0];
    }

    gl.glBindTexture(Gl.GL_TEXTURE_2D, texture);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexImage2D(
        Gl.GL_TEXTURE_2D,
        0,
        Gl.GL_RGBA8,
        width,
        height,
        0,
        Gl.GL_RGBA,
        Gl.GL_UNSIGNED_BYTE,
        MemorySegment.NULL);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, 0);

    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, framebuffer);
    gl.glFramebufferTexture2D(
        Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT0, Gl.GL_TEXTURE_2D, texture, 0);
    int status = gl.glCheckFramebufferStatus(Gl.GL_FRAMEBUFFER);
    if (status != Gl.GL_FRAMEBUFFER_COMPLETE) {
      LOGGER.error("The render target is not complete, status 0x{}", Integer.toHexString(status));
    }
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);

    gl.glBindBuffer(Gl.GL_PIXEL_PACK_BUFFER, packBuffer);
    gl.glBufferData(
        Gl.GL_PIXEL_PACK_BUFFER,
        (long) width * height * Integer.BYTES,
        MemorySegment.NULL,
        Gl.GL_STREAM_READ);
    gl.glBindBuffer(Gl.GL_PIXEL_PACK_BUFFER, 0);

    back = newImage();
    backPixels = ((DataBufferInt) back.getRaster().getDataBuffer()).getData();
    front = newImage();
  }

  private BufferedImage newImage() {
    return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
  }

  /** Makes the target the destination of the passes that follow. */
  public void bind(Gl gl) {
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, framebuffer);
    gl.glDrawBuffers(Gl.GL_COLOR_ATTACHMENT0);
    gl.glViewport(0, 0, width, height);
  }

  public void unbind(Gl gl) {
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);
  }

  /**
   * Brings the frame back into the image Swing paints. {@code BGRA} with {@code
   * UNSIGNED_INT_8_8_8_8_REV} is the driver's fast path and lands as the packed ARGB a {@code
   * TYPE_INT_RGB} raster holds.
   */
  public void read(Gl gl) {
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, framebuffer);
    gl.glBindBuffer(Gl.GL_PIXEL_PACK_BUFFER, packBuffer);
    gl.glPixelStorei(Gl.GL_PACK_ALIGNMENT, 4);
    gl.glReadPixels(
        0, 0, width, height, Gl.GL_BGRA, Gl.GL_UNSIGNED_INT_8_8_8_8_REV, MemorySegment.NULL);

    long bytes = (long) width * height * Integer.BYTES;
    MemorySegment mapped =
        gl.glMapBufferRange(Gl.GL_PIXEL_PACK_BUFFER, 0, bytes, Gl.GL_MAP_READ_BIT);
    if (!MemorySegment.NULL.equals(mapped)) {
      MemorySegment.copy(mapped, ValueLayout.JAVA_INT, 0, backPixels, 0, backPixels.length);
      gl.glUnmapBuffer(Gl.GL_PIXEL_PACK_BUFFER);
      swap();
    } else {
      LOGGER.warn("The driver refused to map the pixel pack buffer; the frame is dropped");
    }
    gl.glBindBuffer(Gl.GL_PIXEL_PACK_BUFFER, 0);
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);
  }

  private void swap() {
    BufferedImage rendered = back;
    back = front;
    backPixels = ((DataBufferInt) back.getRaster().getDataBuffer()).getData();
    front = rendered;
  }

  /** The last frame, for the Swing thread. Null until the first one has been read back. */
  public BufferedImage image() {
    return front;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  /** Releases the GL objects. Must run on the GL thread. */
  public void destroy(Gl gl) {
    if (texture > 0) {
      gl.glDeleteTextures(texture);
      gl.glDeleteFramebuffers(framebuffer);
      gl.glDeleteBuffers(packBuffer);
      texture = 0;
      framebuffer = 0;
      packBuffer = 0;
      width = 0;
      height = 0;
    }
    back = null;
    backPixels = null;
    front = null;
  }
}
