/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandContext;

public class GuiExecutor {
  private static final Logger LOGGER = LoggerFactory.getLogger(GuiExecutor.class);

  private GuiExecutor() {}

  public static void execute(Runnable r) {
    if (r == null) {
      return;
    }
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      SwingUtilities.invokeLater(r);
    }
  }

  /**
   * Like {@link #invokeAndWait} but an unchecked exception thrown by the task reaches the caller,
   * and the caller's {@link CommandContext} (origin, produced files) is bound while the task runs.
   */
  public static void invokeAndRethrow(Runnable r) {
    if (r != null) {
      invokeAndRethrow(
          () -> {
            r.run();
            return null;
          });
    }
  }

  /** Same as {@link #invokeAndRethrow(Runnable)} for a task that computes a value. */
  public static <T> T invokeAndRethrow(Supplier<T> task) {
    if (SwingUtilities.isEventDispatchThread()) {
      return task.get();
    }
    CommandContext context = CommandContext.current();
    AtomicReference<T> result = new AtomicReference<>();
    try {
      SwingUtilities.invokeAndWait(
          () ->
              CommandContext.runWith(
                  context,
                  () -> {
                    result.set(task.get());
                    return null;
                  }));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the EDT", e);
    } catch (InvocationTargetException e) {
      switch (e.getCause()) {
        case RuntimeException re -> throw re;
        case Error err -> throw err;
        default -> throw new IllegalStateException(e.getCause());
      }
    }
    return result.get();
  }

  public static void invokeAndWait(Runnable r) {
    if (r == null) {
      return;
    }
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
    } else {
      try {
        SwingUtilities.invokeAndWait(r);
      } catch (InterruptedException e) {
        LOGGER.warn("Interrupted Exception of {}", r);
        Thread.currentThread().interrupt();
      } catch (InvocationTargetException e) {
        LOGGER.error("EDT invokeAndWait()", e);
      }
    }
  }
}
