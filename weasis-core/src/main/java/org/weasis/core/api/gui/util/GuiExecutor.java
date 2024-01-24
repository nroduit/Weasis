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
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
