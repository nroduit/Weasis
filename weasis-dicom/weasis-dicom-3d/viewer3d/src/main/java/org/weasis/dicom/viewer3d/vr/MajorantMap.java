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
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import org.weasis.dicom.viewer3d.gl.Gl;

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
      Gl gl,
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
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, fboId);
    for (int lvl : new int[] {0, COARSE_LEVEL}) {
      level = lvl;
      int w = width >> lvl;
      int h = height >> lvl;
      int d = depth >> lvl;
      gl.glViewport(0, 0, w, h);
      for (int z = 0; z < d; z++) {
        layer = z;
        gl.glFramebufferTextureLayer(Gl.GL_FRAMEBUFFER, Gl.GL_COLOR_ATTACHMENT0, id, lvl, z);
        program.setUniforms(gl);
        gl.glDrawArrays(Gl.GL_TRIANGLES, 0, View3d.vertexBufferData.length / 2);
      }
    }
    gl.glBindFramebuffer(Gl.GL_FRAMEBUFFER, 0);
    gl.glViewport(0, 0, surfaceWidth, surfaceHeight);
    bakedGrid = grid;
    bakedFingerprint = fingerprint;
  }

  // (Re)creates the texture for the grid's dimensions, with the mip levels the walk reads.
  private void allocate(Gl gl, MajorantGrid grid) {
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
    id = gl.glGenTextures(1)[0];
    gl.glActiveTexture(Gl.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, id);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_BASE_LEVEL, 0);
    gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAX_LEVEL, COARSE_LEVEL);
    for (int lvl = 0; lvl <= COARSE_LEVEL; lvl++) {
      gl.glTexImage3D(
          Gl.GL_TEXTURE_3D,
          lvl,
          Gl.GL_R32F,
          width >> lvl,
          height >> lvl,
          depth >> lvl,
          0,
          Gl.GL_RED,
          Gl.GL_FLOAT,
          MemorySegment.NULL);
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0);
    fboId = gl.glGenFramebuffers(1)[0];
  }

  /** Binds the map on its unit; a caller must have baked it first. */
  void bind(Gl gl) {
    gl.glActiveTexture(Gl.GL_TEXTURE0 + TEXTURE_UNIT);
    gl.glBindTexture(Gl.GL_TEXTURE_3D, id);
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  /**
   * Binds a single empty block on the unit while the grid of the volume is incomplete, so the
   * sampler is complete; the tracer then applies its global bound over a one-block walk.
   */
  void bindPlaceholder(Gl gl) {
    gl.glActiveTexture(Gl.GL_TEXTURE0 + TEXTURE_UNIT);
    if (placeholderId <= 0) {
      placeholderId = gl.glGenTextures(1)[0];
      gl.glBindTexture(Gl.GL_TEXTURE_3D, placeholderId);
      gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
      gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
      try (Arena arena = Arena.ofConfined()) {
        gl.glTexImage3D(
            Gl.GL_TEXTURE_3D,
            0,
            Gl.GL_R32F,
            1,
            1,
            1,
            0,
            Gl.GL_RED,
            Gl.GL_FLOAT,
            arena.allocateFrom(ValueLayout.JAVA_FLOAT, 0f));
      }
    } else {
      gl.glBindTexture(Gl.GL_TEXTURE_3D, placeholderId);
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  void destroy(Gl gl) {
    if (fboId >= 0) {
      gl.glDeleteFramebuffers(fboId);
      fboId = -1;
    }
    if (id > 0) {
      gl.glDeleteTextures(id);
      id = 0;
    }
    bakedGrid = null;
  }

  /** Releases the placeholder and the program too; for the end of the view. */
  void dispose(Gl gl) {
    destroy(gl);
    if (placeholderId > 0) {
      gl.glDeleteTextures(placeholderId);
      placeholderId = 0;
    }
    program.destroy(gl);
    uniformsShared = false;
  }
}
