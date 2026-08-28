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

import com.jogamp.nativewindow.AbstractGraphicsDevice;
import com.jogamp.nativewindow.NativeWindowFactory;
import com.jogamp.opengl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenglUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenglUtils.class);

  private static GLContext glContext;

  /** The offscreen drawable backing {@link #glContext}, kept only to release it on shutdown. */
  private static GLDrawable glDrawable;

  /** The profile that was actually negotiated when the shared context was created. */
  private static GLProfile glProfile;

  private OpenglUtils() {}

  public static GLContext getDefaultGlContext() {
    synchronized (OpenglUtils.class) {
      if (glContext == null) {
        try {
          GLProfile.initSingleton();
          // Last resort if the plugin was never stopped: runs before JOGL tears down the native
          // windowing layer, so the display is released before X11Util force-closes it.
          NativeWindowFactory.addCustomShutdownHook(false, OpenglUtils::destroy);
          Threading.invoke(
              true,
              () -> {
                // Try GL4 first (needed for compute shaders on OpenGL >= 4.3).
                // If GL4 is unavailable, fall back to GL3 (OpenGL 3.3), which is the minimum
                // required for the FBO fragment-shader path (GLSL 3.30).
                // On macOS, GL4 resolves to the highest Core profile available (typically 4.1),
                // which also satisfies the FBO path and disables compute shaders.
                GLProfile profile;
                try {
                  profile = GLProfile.get(GLProfile.GL4);
                } catch (Exception e) {
                  LOGGER.info("GL4 profile unavailable, falling back to GL3: {}", e.getMessage());
                  profile = GLProfile.get(GLProfile.GL3);
                }
                glProfile = profile;
                GLDrawable drawable =
                    GLDrawableFactory.getFactory(glProfile)
                        .createOffscreenDrawable(null, new GLCapabilities(glProfile), null, 1, 1);
                drawable.setRealized(true);
                glDrawable = drawable;
                glContext = drawable.createContext(null);
                LOGGER.info("OpenGL context created with profile: {}", glProfile.getName());
              },
              null);
        } catch (Throwable t) {
          LOGGER.error("Unable to initialize an OpenGL context", t);
        }
      }
    }

    if (glContext == null) {
      throw new IllegalArgumentException("Unable to initialize an OpenGL context");
    }
    return glContext;
  }

  /**
   * Releases the shared context, its offscreen drawable and the display opened for it. Idempotent.
   *
   * <p>The drawable is created with a null device, so JOGL opens a dedicated display that the
   * caller owns. Leaving it open makes JOGL force-close it at JVM shutdown while the GL driver
   * still holds a live context, which segfaults the driver on Linux.
   */
  public static void destroy() {
    synchronized (OpenglUtils.class) {
      GLContext context = glContext;
      GLDrawable drawable = glDrawable;
      glContext = null;
      glDrawable = null;
      glProfile = null;
      if (context == null) {
        return;
      }
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
  }

  /**
   * Returns the {@link GLCapabilities} that should be used when constructing on-screen {@link
   * com.jogamp.opengl.awt.GLJPanel} or {@link com.jogamp.opengl.awt.GLCanvas} instances.
   *
   * <p>Using the same {@link GLProfile} here as in {@link #getDefaultGlContext()} ensures the
   * on-screen drawable negotiates the same OpenGL Core profile as the shared context. This is
   * critical on macOS: without an explicit profile, AWT-based drawables default to a
   * legacy/compatibility context that cannot share data with the Core-profile shared context,
   * causing JOGL to silently skip {@code GLEventListener} callbacks ({@code init()}, {@code
   * display()}, …).
   */
  public static GLCapabilities getDefaultCapabilities() {
    // Ensure the profile has been resolved by initialising the shared context first.
    getDefaultGlContext();
    return new GLCapabilities(glProfile);
  }

  /**
   * Returns a {@link GL4} interface for the shared context. On a GL4 context this is a direct cast.
   * On a GL3-only context this returns {@code null} — callers that only need the FBO path should
   * use {@link #getGL()} instead.
   */
  public static GL4 getGL4() {
    GL gl = getDefaultGlContext().getGL();
    return gl.isGL4() ? gl.getGL4() : null;
  }

  /**
   * Returns a {@link com.jogamp.opengl.GL2ES2} interface that works for both GL3 and GL4 contexts.
   * This covers all API calls needed by the FBO fragment-shader path.
   */
  public static GL2ES2 getGL() {
    return getDefaultGlContext().getGL().getGL2ES2();
  }
}
