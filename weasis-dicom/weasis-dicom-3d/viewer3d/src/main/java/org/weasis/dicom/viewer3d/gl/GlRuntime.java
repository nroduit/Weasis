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

import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application's one OpenGL thread. Every view renders through it, into its own framebuffer
 * objects, so nothing is shared between contexts and a texture name means the same thing
 * everywhere.
 */
public final class GlRuntime {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlRuntime.class);

  private static GlThread thread;

  private GlRuntime() {}

  /**
   * The thread, started on first use.
   *
   * @throws GlException when no context can be created; 3D is then unavailable
   */
  public static synchronized GlThread thread() {
    if (thread == null) {
      warnIfNativeAccessIsRestricted();
      thread = new GlThread();
      LOGGER.info("3D rendering on {}", thread.context().description());
    }
    return thread;
  }

  /**
   * The FFM binding calls the graphics driver, which a launcher that forgot {@code
   * --enable-native-access} only warns about today and will refuse later. The application options
   * carry the flag; a development run configuration may not.
   */
  private static void warnIfNativeAccessIsRestricted() {
    if (!GlRuntime.class.getModule().isNativeAccessEnabled()) {
      LOGGER.warn(
          "Native access is not enabled for this launcher; add --enable-native-access=ALL-UNNAMED");
    }
  }

  /** The thread if it is already running, without starting it. */
  public static synchronized GlThread threadIfStarted() {
    return thread;
  }

  /**
   * Queues GL work when the thread is running, and does nothing when it is not — which is what the
   * disposal paths need, since a view can be closed before or after the renderer ever started.
   */
  public static void submit(Consumer<Gl> task) {
    GlThread current = threadIfStarted();
    if (current != null) {
      current.submit(task);
    }
  }

  /** Stops the thread and destroys the context. Idempotent. */
  public static synchronized void shutdown() {
    if (thread != null) {
      thread.close();
      thread = null;
    }
  }
}
