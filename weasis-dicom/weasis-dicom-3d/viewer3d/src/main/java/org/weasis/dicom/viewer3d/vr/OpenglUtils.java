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

import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.GLDrawable;
import com.jogamp.opengl.GLDrawableFactory;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenglUtils {
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenglUtils.class);

  static {
    GLProfile.initSingleton();
  }

  private static GLContext glContext;

  private OpenglUtils() {}

  public static GLContext getDefaultGlContext() {
    synchronized (OpenglUtils.class) {
      if (glContext == null) {
        try {
          Threading.invoke(
              true,
              () -> {
                final GLProfile glProfile = GLProfile.get(GLProfile.GL4);
                GLDrawable glDrawable =
                    GLDrawableFactory.getFactory(glProfile)
                        .createOffscreenDrawable(null, new GLCapabilities(glProfile), null, 1, 1);
                glDrawable.setRealized(true);
                glContext = glDrawable.createContext(null);
                //                glContext.makeCurrent();
                //                initCubeIcons(glContext.getGL().getGL4());
                //                glContext.getGL().glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
                //                glContext.release();
                //                TextureIO.setTexRectEnabled(false);
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

  public static GL4 getGL4() {
    return getDefaultGlContext().getGL().getGL4();
  }
}
