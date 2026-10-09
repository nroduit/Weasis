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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.awt.image.BufferedImage;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.weasis.dicom.viewer3d.vr.Program;
import org.weasis.dicom.viewer3d.vr.ShaderManager;

/**
 * What replaces the panel's own drawable: the quad pass into the view's target and the read-back
 * into the image Swing paints. The orientation is what this checks above all — the quad samples
 * upside down so that the rows {@code glReadPixels} returns, which start at the bottom of the
 * framebuffer, land in a {@link BufferedImage} that starts at the top.
 */
@Isolated("holds a process-wide OpenGL context")
@DisplayNameGeneration(ReplaceUnderscores.class)
class RenderTargetTest {

  private static final int RED = 0xFFFF0000;
  private static final int GREEN = 0xFF00FF00;
  private static final int BLUE = 0xFF0000FF;
  private static final int WHITE = 0xFFFFFFFF;

  /** The four texels of the source, in GL order: the first row is the bottom one on screen. */
  private static final byte[] SOURCE = {
    (byte) 255,
    0,
    0,
    (byte) 255,
    0,
    (byte) 255,
    0,
    (byte) 255,
    0,
    0,
    (byte) 255,
    (byte) 255,
    (byte) 255,
    (byte) 255,
    (byte) 255,
    (byte) 255
  };

  private static final float[] QUAD = {
    -1f, -1f, 1f, -1f, -1f, 1f,
    -1f, 1f, 1f, -1f, 1f, 1f
  };

  @Test
  @DisplayName("The frame reaches the image the right way up")
  void the_frame_reaches_the_image_the_right_way_up() {
    try (GlSession session = GlSession.openOrAbort()) {
      Gl gl = session.gl();
      RenderTarget target = new RenderTarget();
      target.ensureSize(gl, 2, 2);
      BufferedImage first = target.image();

      int texture = uploadSource(gl);
      int buffer = uploadQuad(gl);
      Program quad =
          new Program(
              "basic", ShaderManager.VERTEX_SHADER, ShaderManager.FRAGMENT_SHADER); // NON-NLS
      quad.allocateUniform(gl, "compute", (g, loc) -> g.glUniform1i(loc, 0));
      quad.allocateUniform(gl, "texScale", (g, loc) -> g.glUniform2f(loc, 1f, 1f));

      target.bind(gl);
      quad.use(gl);
      quad.setUniforms(gl);
      gl.glActiveTexture(Gl.GL_TEXTURE0);
      gl.glBindTexture(Gl.GL_TEXTURE_2D, texture);
      gl.glEnableVertexAttribArray(0);
      gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, buffer);
      gl.glVertexAttribPointer(0, 2, Gl.GL_FLOAT, false, 0, 0);
      gl.glDrawArrays(Gl.GL_TRIANGLES, 0, QUAD.length / 2);
      gl.glDisableVertexAttribArray(0);
      target.unbind(gl);
      target.read(gl);

      BufferedImage image = target.image();
      assertNotNull(image, "a frame was read back");
      assertNotSame(first, image, "the images swap");
      assertAll(
          "the source lands upright and unmirrored",
          // The image starts at the top, which is the last row of the texture.
          () -> assertEquals(BLUE, image.getRGB(0, 0), "top left"), // NON-NLS
          () -> assertEquals(WHITE, image.getRGB(1, 0), "top right"), // NON-NLS
          () -> assertEquals(RED, image.getRGB(0, 1), "bottom left"), // NON-NLS
          () -> assertEquals(GREEN, image.getRGB(1, 1), "bottom right")); // NON-NLS
      assertEquals(Gl.GL_NO_ERROR, gl.glGetError(), "a clean frame");

      quad.destroy(gl);
      gl.glDeleteTextures(texture);
      gl.glDeleteBuffers(buffer);
      target.destroy(gl);
    }
  }

  private static int uploadSource(Gl gl) {
    int texture = gl.glGenTextures(1)[0];
    gl.glActiveTexture(Gl.GL_TEXTURE0);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, texture);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_S, Gl.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_WRAP_T, Gl.GL_CLAMP_TO_EDGE);
    try (Arena arena = Arena.ofConfined()) {
      gl.glTexImage2D(
          Gl.GL_TEXTURE_2D,
          0,
          Gl.GL_RGBA8,
          2,
          2,
          0,
          Gl.GL_RGBA,
          Gl.GL_UNSIGNED_BYTE,
          arena.allocateFrom(ValueLayout.JAVA_BYTE, SOURCE));
    }
    return texture;
  }

  private static int uploadQuad(Gl gl) {
    int buffer = gl.glGenBuffers(1)[0];
    gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, buffer);
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment vertices = arena.allocateFrom(ValueLayout.JAVA_FLOAT, QUAD);
      gl.glBufferData(Gl.GL_ARRAY_BUFFER, vertices.byteSize(), vertices, Gl.GL_STATIC_DRAW);
    }
    return buffer;
  }
}
