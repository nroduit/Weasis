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
import com.jogamp.opengl.GL2ES3;
import java.nio.IntBuffer;

/**
 * The extinction bound of every block of a {@link MajorantGrid}, baked for the current preset and
 * window so the path tracer reads one texel per block instead of windowing the block's value range
 * and looking the result up at every step of its walk. Level 0 of the 3D texture holds the alpha
 * bound of each block; level 2 holds the highest bound of each 4×4×4 group of blocks, which lets
 * the walk leap over empty regions. The map is padded to a multiple of four so level 2 covers it
 * exactly; padded texels bound nothing.
 *
 * <p>Baked by a fragment pass over each layer of both levels, which runs on the compute and the FBO
 * paths alike, whenever the inputs of the bound change.
 */
final class MajorantMap {

  static final int TEXTURE_UNIT = 11;

  /** Mip level holding the coarse bounds, and the number of blocks a coarse cell spans per axis. */
  static final int COARSE_LEVEL = 2;

  static final int COARSE_SPAN = 1 << COARSE_LEVEL;

  private final Program program =
      new Program(
          "majorantBake", // NON-NLS
          ShaderManager.FBO_VERTEX_SHADER,
          ShaderManager.MAJORANT_BAKE_FRAGMENT_SHADER);

  private int id;
  private int placeholderId;
  private int fboId = -1;
  private int width;
  private int height;
  private int depth;
  private int level;
  private int layer;
  private boolean uniformsShared;
  private long bakedFingerprint;
  private MajorantGrid bakedGrid;

  private static int padded(int blocks) {
    return (blocks + COARSE_SPAN - 1) / COARSE_SPAN * COARSE_SPAN;
  }

  /** Whether the map holds the bounds of {@code grid} under the inputs {@code fingerprint}. */
  boolean isBaked(MajorantGrid grid, long fingerprint) {
    return bakedGrid == grid && bakedFingerprint == fingerprint && id > 0;
  }

  /**
   * Bakes the bounds of {@code grid}, bound on its unit together with the alpha range table of the
   * preset. The window uniforms come from {@code source}; the vertex array of the quad must be set
   * up, and the framebuffer and viewport are restored to the given surface afterwards.
   */
  void bake(
      GL2ES2 gl,
      Program source,
      MajorantGrid grid,
      long fingerprint,
      int surfaceWidth,
      int surfaceHeight) {
    allocate(gl, grid);
    if (!uniformsShared) {
      program.shareAllUniforms(gl, source);
      program.allocateUniform(
          gl, "majorantGrid", (g, loc) -> g.glUniform1i(loc, MajorantGrid.TEXTURE_UNIT));
      program.allocateUniform(
          gl, "alphaRangeMap", (g, loc) -> g.glUniform1i(loc, Preset.ALPHA_RANGE_UNIT));
      program.allocateUniform(gl, "bakeLevel", (g, loc) -> g.glUniform1i(loc, level));
      program.allocateUniform(gl, "bakeLayer", (g, loc) -> g.glUniform1i(loc, layer));
      uniformsShared = true;
    }
    program.use(gl);
    GL2ES3 gl3 = gl.getGL2ES3();
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboId);
    for (int lvl : new int[] {0, COARSE_LEVEL}) {
      level = lvl;
      int w = width >> lvl;
      int h = height >> lvl;
      int d = depth >> lvl;
      gl.glViewport(0, 0, w, h);
      for (int z = 0; z < d; z++) {
        layer = z;
        gl3.glFramebufferTextureLayer(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, id, lvl, z);
        program.setUniforms(gl);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, View3d.vertexBufferData.length / 2);
      }
    }
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    gl.glViewport(0, 0, surfaceWidth, surfaceHeight);
    bakedGrid = grid;
    bakedFingerprint = fingerprint;
  }

  // (Re)creates the texture for the grid's dimensions, with the mip levels the walk reads.
  private void allocate(GL2ES2 gl, MajorantGrid grid) {
    int w = padded(grid.getWidth());
    int h = padded(grid.getHeight());
    int d = padded(grid.getDepth());
    if (id > 0 && w == width && h == height && d == depth) {
      return;
    }
    destroy(gl);
    width = w;
    height = h;
    depth = d;
    IntBuffer buf = IntBuffer.allocate(1);
    gl.glGenTextures(1, buf);
    id = buf.get(0);
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, id);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES3.GL_TEXTURE_BASE_LEVEL, 0);
    gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL2ES3.GL_TEXTURE_MAX_LEVEL, COARSE_LEVEL);
    for (int lvl = 0; lvl <= COARSE_LEVEL; lvl++) {
      gl.glTexImage3D(
          GL2ES2.GL_TEXTURE_3D,
          lvl,
          GL.GL_R32F,
          width >> lvl,
          height >> lvl,
          depth >> lvl,
          0,
          GL2ES2.GL_RED,
          GL.GL_FLOAT,
          null);
    }
    gl.glActiveTexture(GL.GL_TEXTURE0);
    gl.glGenFramebuffers(1, buf);
    fboId = buf.get(0);
  }

  /** Binds the map on its unit; a caller must have baked it first. */
  void bind(GL2ES2 gl) {
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, id);
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  /**
   * Binds a single empty block on the unit while the grid of the volume is incomplete, so the
   * sampler is complete; the tracer then applies its global bound over a one-block walk.
   */
  void bindPlaceholder(GL2ES2 gl) {
    gl.glActiveTexture(GL.GL_TEXTURE0 + TEXTURE_UNIT);
    if (placeholderId <= 0) {
      IntBuffer buf = IntBuffer.allocate(1);
      gl.glGenTextures(1, buf);
      placeholderId = buf.get(0);
      gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, placeholderId);
      gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
      gl.glTexParameteri(GL2ES2.GL_TEXTURE_3D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
      gl.glTexImage3D(
          GL2ES2.GL_TEXTURE_3D,
          0,
          GL.GL_R32F,
          1,
          1,
          1,
          0,
          GL2ES2.GL_RED,
          GL.GL_FLOAT,
          Buffers.newDirectFloatBuffer(new float[] {0f}).rewind());
    } else {
      gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, placeholderId);
    }
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  void destroy(GL2ES2 gl) {
    if (fboId >= 0) {
      gl.glDeleteFramebuffers(1, new int[] {fboId}, 0);
      fboId = -1;
    }
    if (id > 0) {
      gl.glDeleteTextures(1, new int[] {id}, 0);
      id = 0;
    }
    bakedGrid = null;
  }

  /** Releases the placeholder and the program too; for the end of the view. */
  void dispose(GL2ES2 gl) {
    destroy(gl);
    if (placeholderId > 0) {
      gl.glDeleteTextures(1, new int[] {placeholderId}, 0);
      placeholderId = 0;
    }
    program.destroy(gl);
    uniformsShared = false;
  }
}
