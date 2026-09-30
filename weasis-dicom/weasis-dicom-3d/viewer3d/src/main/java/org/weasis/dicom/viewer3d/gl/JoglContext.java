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

import com.jogamp.nativewindow.AbstractGraphicsDevice;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The same offscreen context as before, on JOGL's own 1 × 1 drawable, behind the interface the
 * renderer now uses everywhere. It keeps the binding selectable while the FFM one settles; it is
 * removed with JOGL.
 */
public final class JoglContext implements GlContext {

  private static final Logger LOGGER = LoggerFactory.getLogger(JoglContext.class);

  private final GLDrawable drawable;
  private final GLContext context;
  private final JoglGl gl;
  private final String description;
  private boolean destroyed;

  private JoglContext(GLDrawable drawable, GLContext context) {
    this.drawable = drawable;
    this.context = context;
    context.makeCurrent();
    this.gl = new JoglGl(context.getGL());
    this.description =
        "%s — OpenGL %s (%s, JOGL %s)"
            .formatted(
                gl.glGetString(Gl.GL_RENDERER),
                gl.glGetString(Gl.GL_VERSION),
                gl.glGetString(Gl.GL_VENDOR),
                context.getGLVersion());
  }

  static JoglContext create() {
    try {
      GLProfile.initSingleton();
      // GL4 first for the compute path; GL3 is the OpenGL 3.3 minimum of the FBO path. On macOS
      // GL4 resolves to the highest core profile the driver has, typically 4.1.
      GLProfile profile;
      try {
        profile = GLProfile.get(GLProfile.GL4);
      } catch (Exception e) {
        LOGGER.info("GL4 profile unavailable, falling back to GL3: {}", e.getMessage());
        profile = GLProfile.get(GLProfile.GL3);
      }
      GLDrawable drawable =
          GLDrawableFactory.getFactory(profile)
              .createOffscreenDrawable(null, new GLCapabilities(profile), null, 1, 1);
      drawable.setRealized(true);
      return new JoglContext(drawable, drawable.createContext(null));
    } catch (GlException e) {
      throw e;
    } catch (Throwable t) {
      throw new GlException("JOGL cannot create an offscreen OpenGL context", t);
    }
  }

  @Override
  public void makeCurrent() {
    context.makeCurrent();
  }

  @Override
  public void release() {
    context.release();
  }

  /**
   * The drawable is created with a null device, so JOGL opens a display of its own that this class
   * owns. Leaving it open makes JOGL force-close it at JVM shutdown while the driver still holds a
   * live context, which segfaults the driver on Linux.
   */
  @Override
  public void destroy() {
    if (destroyed) {
      return;
    }
    destroyed = true;
    try {
      context.destroy();
      AbstractGraphicsDevice device =
          drawable.getNativeSurface().getGraphicsConfiguration().getScreen().getDevice();
      drawable.setRealized(false);
      device.close();
    } catch (Exception e) {
      LOGGER.error("Releasing the OpenGL context", e);
    }
  }

  @Override
  public String description() {
    return description;
  }

  @Override
  public String name() {
    return "JOGL"; // NON-NLS
  }

  @Override
  public Gl gl() {
    return gl;
  }
}
