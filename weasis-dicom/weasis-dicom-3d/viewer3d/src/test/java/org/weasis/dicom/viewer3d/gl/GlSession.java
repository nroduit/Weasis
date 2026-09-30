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

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;

/**
 * A current OpenGL context for the tests that really draw, on either back-end, aborting the test
 * instead of failing it when the machine has no usable driver.
 *
 * <p>Each back-end owns a process-wide context, so a test that holds a session must not run beside
 * another: they share the GL state. Surefire runs this module with {@code parallel=all}, hence the
 * {@code @Isolated} the callers carry.
 */
abstract sealed class GlSession implements AutoCloseable {

  abstract Gl gl();

  /** The driver's own renderer name, the identity two back-ends must share to be comparable. */
  String renderer() {
    return gl().glGetString(Gl.GL_RENDERER) + " " + gl().glGetString(Gl.GL_VERSION);
  }

  String describe() {
    return gl().glGetString(Gl.GL_RENDERER) + " — OpenGL " + gl().glGetString(Gl.GL_VERSION);
  }

  @Override
  public abstract void close();

  /** The back-ends this machine can actually run, in a stable order. */
  static List<GlBackend> available() {
    List<GlBackend> backends = new ArrayList<>();
    for (GlBackend backend : GlBackend.values()) {
      try (GlSession probe = open(backend)) {
        backends.add(backend);
      } catch (RuntimeException | UnsatisfiedLinkError e) {
        // Not available here; the tests that need it are skipped.
      }
    }
    return backends;
  }

  static GlSession openOrAbort(GlBackend backend) {
    try {
      return open(backend);
    } catch (RuntimeException | UnsatisfiedLinkError e) {
      Assumptions.abort("No " + backend + " OpenGL context on this machine: " + e.getMessage());
      throw new IllegalStateException(e); // unreachable
    }
  }

  /** The back-end named by {@value GlBackend#P_BACKEND}, for the tests that measure one of them. */
  static GlSession openOrAbort() {
    return openOrAbort(GlBackend.selected());
  }

  private static GlSession open(GlBackend backend) {
    return new Offscreen(backend);
  }

  /**
   * The context of one back-end, made current on the calling thread. The tests drive it directly
   * rather than through {@link GlThread}, so that a failure points at the GL call and not at a task
   * wrapper.
   */
  private static final class Offscreen extends GlSession {
    private final GlContext context;

    Offscreen(GlBackend backend) {
      this.context = GlContext.create(backend);
      // A core profile has no default vertex array object, as GlThread also arranges.
      Gl gl = context.gl();
      gl.glBindVertexArray(gl.glGenVertexArray());
    }

    @Override
    Gl gl() {
      return context.gl();
    }

    @Override
    String describe() {
      return context.description();
    }

    @Override
    public void close() {
      context.destroy();
    }
  }
}
