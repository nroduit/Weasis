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

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The single thread that owns the OpenGL context. Everything that touches GL runs here: the context
 * is created, used and destroyed on this thread and is never made current anywhere else, so no
 * texture name, program or buffer is ever shared between contexts.
 *
 * <p>A frame is a {@link #invokeAndWait} task and jumps ahead of the queue; an upload or a deletion
 * is a {@link #submit} task and waits its turn, which is what keeps a volume loading in the
 * background from delaying the display.
 *
 * <p><b>The one rule:</b> a task must never block on another thread, and in particular must never
 * call the EDT synchronously. The EDT waits for frames, so a task that waited for the EDT would
 * deadlock the application; {@link #isCurrentThread()} is what the code that crosses that boundary
 * checks itself against.
 */
public final class GlThread implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlThread.class);

  private static final long SHUTDOWN_TIMEOUT_MS = 2000;

  private final LinkedBlockingDeque<FutureTask<?>> queue = new LinkedBlockingDeque<>();
  private final Thread thread;
  private volatile GlContext context;
  private volatile boolean running = true;

  /**
   * Starts the thread and creates the context on it.
   *
   * @throws GlException when no context can be created; the thread is then already stopped
   */
  public GlThread() {
    FutureTask<GlContext> creation = new FutureTask<>(GlThread::createContext);
    this.thread = Thread.ofPlatform().name("Weasis OpenGL").daemon(true).unstarted(this::run);
    queue.addFirst(creation);
    thread.start();
    try {
      this.context = creation.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GlException("Interrupted while creating the OpenGL context", e);
    } catch (ExecutionException e) {
      running = false;
      Throwable cause = e.getCause();
      throw cause instanceof GlException gl
          ? gl
          : new GlException("The OpenGL context could not be created", cause);
    }
  }

  // A core profile has no default vertex array object and every draw needs one bound; JOGL used
  // to create it silently for its own drawables.
  private static GlContext createContext() {
    GlContext created = GlContext.create();
    Gl gl = created.gl();
    gl.glBindVertexArray(gl.glGenVertexArray());
    LOGGER.info(
        "OpenGL parallel shader compile: {}",
        gl.enableParallelShaderCompile() ? "on" : "unavailable"); // NON-NLS
    return created;
  }

  private void run() {
    try {
      while (running) {
        FutureTask<?> task = queue.take();
        task.run();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      GlContext current = context;
      if (current != null) {
        current.destroy();
      }
    }
  }

  /** {@code true} when the caller is the thread that owns the context. */
  public boolean isCurrentThread() {
    return Thread.currentThread() == thread;
  }

  /** The context, for the callers that only need to describe it. */
  public GlContext context() {
    return context;
  }

  /**
   * Runs {@code frame} on the GL thread ahead of the queued work and waits for its result. Called
   * from the EDT for every frame.
   */
  public <T> T invokeAndWait(Function<Gl, T> frame) {
    if (isCurrentThread()) {
      return frame.apply(context.gl());
    }
    FutureTask<T> task = new FutureTask<>(callable(frame));
    queue.addFirst(task);
    try {
      return task.get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new GlException("Interrupted while waiting for a frame", e);
    } catch (ExecutionException e) {
      throw new GlException("The frame could not be rendered", e.getCause());
    } catch (CancellationException e) {
      throw new GlException("The GL thread was stopped while a frame was pending", e);
    }
  }

  /**
   * Queues {@code task} behind the frames and returns at once. Used for the slab uploads and every
   * deletion, which is what finally gives them a current context.
   */
  public void submit(Consumer<Gl> task) {
    queue.addLast(
        new FutureTask<Void>(
            callable(
                gl -> {
                  task.accept(gl);
                  return null;
                })));
  }

  private <T> Callable<T> callable(Function<Gl, T> body) {
    return () -> {
      try {
        return body.apply(context.gl());
      } catch (RuntimeException e) {
        // The thread outlives a failed task: a driver hiccup skips one frame, it does not end 3D.
        LOGGER.error("An OpenGL task failed", e);
        throw e;
      }
    };
  }

  /** Stops the thread and destroys the context on it. Idempotent. */
  @Override
  public void close() {
    if (!running) {
      return;
    }
    running = false;
    // A no-op wakes the loop so it sees the flag and runs its finally.
    queue.addLast(new FutureTask<>(() -> null));
    try {
      thread.join(SHUTDOWN_TIMEOUT_MS);
      if (thread.isAlive()) {
        LOGGER.warn("The OpenGL thread did not stop within {} ms", SHUTDOWN_TIMEOUT_MS);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    context = null;
  }

  /** Waits for everything queued so far, for the tests and the shutdown paths. */
  public void drain() {
    FutureTask<?> barrier = new FutureTask<>(() -> null);
    queue.addLast(barrier);
    try {
      barrier.get(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | java.util.concurrent.TimeoutException e) {
      LOGGER.warn("Waiting for the queued OpenGL work", e);
    }
  }
}
