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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/** The thread that owns the context: who runs what, in which order, and what a failure does. */
@Isolated("creates the process-wide OpenGL context")
@DisplayNameGeneration(ReplaceUnderscores.class)
class GlThreadTest {

  private static GlThread open() {
    try {
      return new GlThread();
    } catch (GlException | UnsatisfiedLinkError e) {
      Assumptions.abort("No OpenGL context on this machine: " + e.getMessage());
      throw new IllegalStateException(e); // unreachable
    }
  }

  @Test
  @DisplayName("Every task runs on the thread that owns the context, never on the caller's")
  void tasks_run_on_the_gl_thread() {
    try (GlThread glThread = open()) {
      assertFalse(glThread.isCurrentThread(), "the test thread is not the GL thread");
      assertTrue(glThread.<Boolean>invokeAndWait(gl -> glThread.isCurrentThread()));
      assertEquals(
          Gl.GL_NO_ERROR, (int) glThread.<Integer>invokeAndWait(Gl::glGetError), "a clean context");
      assertFalse(glThread.context().description().isBlank());
    }
  }

  @Test
  @DisplayName("A frame is served before the uploads already queued behind it")
  void frames_overtake_queued_work() throws InterruptedException {
    try (GlThread glThread = open()) {
      List<String> order = new CopyOnWriteArrayList<>();
      CountDownLatch blocked = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);

      // Hold the thread so the rest of the work is certainly queued before it is taken.
      glThread.submit(
          gl -> {
            blocked.countDown();
            try {
              release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
          });
      assertTrue(blocked.await(5, TimeUnit.SECONDS), "the GL thread took the first task");

      glThread.submit(gl -> order.add("upload")); // NON-NLS
      Thread frame = new Thread(() -> glThread.invokeAndWait(gl -> order.add("frame"))); // NON-NLS
      frame.start();
      // Let the frame reach the head of the queue before the thread is released.
      Thread.sleep(100);
      release.countDown();
      frame.join(5000);
      glThread.drain();

      assertEquals(List.of("frame", "upload"), order); // NON-NLS
    }
  }

  @Test
  @DisplayName("A task that throws is reported to its caller and leaves the thread running")
  void a_failed_task_does_not_end_the_thread() {
    try (GlThread glThread = open()) {
      GlException failure =
          assertThrows(
              GlException.class,
              () ->
                  glThread.invokeAndWait(
                      gl -> {
                        throw new IllegalStateException("broken frame"); // NON-NLS
                      }));
      assertEquals("broken frame", failure.getCause().getMessage());
      assertTrue(
          glThread.<Boolean>invokeAndWait(gl -> Boolean.TRUE), "the thread still serves frames");
    }
  }

  @Test
  @DisplayName("Closing twice is harmless")
  void close_is_idempotent() {
    GlThread glThread = open();
    glThread.close();
    glThread.close();
  }
}
