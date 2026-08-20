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
import java.nio.IntBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * OpenGL 3.3-compatible FBO-based render target for volume rendering.
 *
 * <p>This class replaces {@link ComputeTexture} on platforms that do not support compute shaders
 * (OpenGL &lt; 4.3), notably macOS which is limited to OpenGL 4.1. Instead of dispatching a compute
 * shader that writes to an image unit via {@code imageStore()}, it renders a full-screen quad into
 * an FBO whose color attachment is the output texture. The fragment shader ({@code volumeFbo.frag})
 * contains identical ray-casting logic to {@code volume.comp}.
 *
 * <p>Minimum requirement: OpenGL 3.3 / GLSL 3.30. All features used (FBOs, {@code sampler3D},
 * {@code uint} uniforms, {@code layout(location)} on vertex inputs and fragment outputs, {@code
 * textureSize}) are core since OpenGL 3.3. No 4.x-specific features are used.
 *
 * <p>The rendering flow is:
 *
 * <ol>
 *   <li>Bind the FBO.
 *   <li>Draw the full-screen quad using the FBO ray-casting program.
 *   <li>Unbind the FBO and restore the viewport to the physical surface size.
 *   <li>The FBO color-attachment texture is then blitted to the screen by the quad program.
 * </ol>
 *
 * <p><b>Adaptive resolution.</b> The color attachment is always allocated at the physical surface
 * size, but only a sub-rectangle of it is ray-cast while the camera is being dragged: the pass then
 * runs at the logical (AWT) resolution, which on a Retina display is a quarter of the fragments.
 * The blit scales its texture coordinates by {@link #getRenderScaleX()} so the partially filled
 * target is upscaled with {@code GL_LINEAR} for the duration of the gesture only, and the frame
 * that follows the release is ray-cast at full physical resolution. Keeping one full-size
 * allocation means no texture reallocation on every press and release.
 */
public class FboRenderTexture extends TextureData {
  private static final Logger LOGGER = LoggerFactory.getLogger(FboRenderTexture.class);

  /**
   * Dedicated texture unit for the FBO colour-attachment (output) texture. Units 0-2 are already
   * used: 0=volTexture (3D), 1=colorMap, 2=lightingMap.
   */
  public static final int OUTPUT_TEXTURE_UNIT = 3;

  private final View3d view3d;
  private int fboId = -1;

  public FboRenderTexture(View3d view3d) {
    // Use RGBA16F: half-float is sufficient for volume rendering output and halves GPU memory
    // bandwidth compared to RGBA32F.
    super(
        Math.max(view3d.getSurfaceWidth(), 1),
        Math.max(view3d.getSurfaceHeight(), 1),
        PixelFormat.RGBA16F);
    this.view3d = view3d;
  }

  /** Allocation width: the physical surface, which is what an idle frame is ray-cast at. */
  private int surfaceWidth() {
    int w = view3d.getSurfaceWidth();
    return w > 0 ? w : Math.max(view3d.getWidth(), 1);
  }

  private int surfaceHeight() {
    int h = view3d.getSurfaceHeight();
    return h > 0 ? h : Math.max(view3d.getHeight(), 1);
  }

  /**
   * Width of the ray-cast pass: the logical (AWT) resolution while the camera is being dragged, the
   * full physical surface otherwise. Pure function of the current state, so the blit and the
   * overlay uniforms can call it without depending on when the pass ran.
   */
  private int passWidth() {
    return view3d.camera.isAdjusting() ? Math.clamp(view3d.getWidth(), 1, width) : width;
  }

  private int passHeight() {
    return view3d.camera.isAdjusting() ? Math.clamp(view3d.getHeight(), 1, height) : height;
  }

  /** Fraction of the target ray-cast this frame; the blit scales its texture coordinates by it. */
  public float getRenderScaleX() {
    return width > 0 ? passWidth() / (float) width : 1f;
  }

  public float getRenderScaleY() {
    return height > 0 ? passHeight() / (float) height : 1f;
  }

  @Override
  public void init(GL2ES2 gl) {
    super.init(gl);
    this.width = surfaceWidth();
    this.height = surfaceHeight();

    // --- Colour-attachment texture (bound on unit 3 to avoid disturbing units 0-2) ---
    gl.glActiveTexture(GL.GL_TEXTURE0 + OUTPUT_TEXTURE_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, getId());
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
    // GL_LINEAR: the blit quad upscales the reduced-resolution pass rendered while interacting.
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
    gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
    gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);

    // --- FBO ---
    IntBuffer buf = IntBuffer.allocate(1);
    gl.glGenFramebuffers(1, buf);
    fboId = buf.get(0);
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboId);
    gl.glFramebufferTexture2D(
        GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, getId(), 0);

    int status = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
    if (status != GL.GL_FRAMEBUFFER_COMPLETE) {
      LOGGER.error("FBO not complete, status: 0x{}", Integer.toHexString(status));
    }
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);

    // Restore active texture unit to 0
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  private void resizeTexture(GL2ES2 gl) {
    gl.glActiveTexture(GL.GL_TEXTURE0 + OUTPUT_TEXTURE_UNIT);
    gl.glBindTexture(GL.GL_TEXTURE_2D, getId());
    this.width = surfaceWidth();
    this.height = surfaceHeight();
    gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
    gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
    gl.glActiveTexture(GL.GL_TEXTURE0);
  }

  /**
   * Renders the volume into the FBO. The FBO ray-casting program must already be active (bound via
   * {@code program.use(gl4)}) and uniforms set before this method is called.
   *
   * @param gl the current OpenGL 4 context
   */
  @Override
  public void render(GL2ES2 gl) {
    if (gl == null) {
      return;
    }
    if (getId() <= 0 || fboId < 0) {
      init(gl);
    }

    if (width != surfaceWidth() || height != surfaceHeight()) {
      resizeTexture(gl);
    }

    // Ray-cast into the FBO, over the whole target when idle and over the logical-resolution
    // sub-rectangle while interacting. glClear covers the full attachment, so the region outside
    // the sub-rectangle never holds a stale frame.
    // Units 0 (volTexture 3D), 1 (colorMap), 2 (lightingMap) are already bound by the caller.
    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fboId);
    gl.glViewport(0, 0, passWidth(), passHeight());
    gl.glClear(GL.GL_COLOR_BUFFER_BIT);

    gl.glDrawArrays(GL.GL_TRIANGLES, 0, View3d.vertexBufferData.length / 2);

    gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    // Restore viewport to the full physical surface size
    gl.glViewport(0, 0, surfaceWidth(), surfaceHeight());
  }

  @Override
  public void destroy(GL2ES2 gl) {
    if (fboId >= 0) {
      gl.glDeleteFramebuffers(1, Buffers.newDirectIntBuffer(new int[] {fboId}));
      fboId = -1;
    }
    super.destroy(gl);
  }
}
