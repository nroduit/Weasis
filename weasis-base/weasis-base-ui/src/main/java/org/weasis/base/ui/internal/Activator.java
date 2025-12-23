/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.internal;

import java.lang.management.ManagementFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.base.ui.gui.WeasisWinListener;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.AuditLog;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  private WeasisWin mainWindow;
  private WeasisWinListener mainWindowListener;

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    try {
      // WeasisWin must be instantiated in the EDT but required to end before the plugin startup
      GuiExecutor.invokeAndWait(
          () -> {
            try {
              mainWindow = new WeasisWin();
              mainWindow.createMainPanel();
              mainWindow.showWindow();
              LOGGER.info(
                  "{} Global starting, type:INIT time:{}",
                  AuditLog.MARKER_PERF,
                  (System.currentTimeMillis()
                      - ManagementFactory.getRuntimeMXBean().getStartTime()));

              // Initialize the main window listener
              mainWindowListener = new WeasisWinListener(mainWindow);
              mainWindowListener.activate();

              LOGGER.info("Weasis base-ui plugin started");
            } catch (Exception ex) {
              // It is better to exit than to let run a zombie process
              LOGGER.error("Cannot start GUI", ex);
              System.exit(-1);
            }
          });
    } catch (Exception e) {
      LOGGER.error("Error starting Weasis base-ui plugin", e);
    }
  }

  @Override
  public void stop() {
    try {
      if (mainWindowListener != null) {
        mainWindowListener.deactivate();
      }
      LOGGER.info("Weasis base-ui plugin stopped");
    } catch (Exception e) {
      LOGGER.error("Error stopping Weasis base-ui plugin", e);
    }
  }
}
