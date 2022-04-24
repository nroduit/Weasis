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

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.ui.gui.MainWindowListener;
import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  @Override
  public void start(final BundleContext bundleContext) throws Exception {
    // Starts core bundles for initialization before calling UI components
    Bundle bundle = FrameworkUtil.getBundle(BundleTools.class);
    if (bundle != null) {
      bundle.start();
    }
    bundle = FrameworkUtil.getBundle(UIManager.class);
    if (bundle != null) {
      bundle.start();
    }

    // WeasisWin must be instantiated in the EDT but required to end before the bundle startup
    GuiExecutor.instance()
        .invokeAndWait(
            () -> {
              final WeasisWin mainWindow = new WeasisWin();
              // Register "weasis" command
              Dictionary<String, Object> dict = new Hashtable<>();
              dict.put(CommandProcessor.COMMAND_SCOPE, "weasis"); // NON-NLS
              dict.put(
                  CommandProcessor.COMMAND_FUNCTION, WeasisWin.functions.toArray(new String[0]));
              bundleContext.registerService(WeasisWin.class.getName(), mainWindow, dict);
              try {
                mainWindow.createMainPanel();
                mainWindow.showWindow();
              } catch (Exception ex) {
                // It is better to exit than to let run a zombie process
                LOGGER.error("Cannot start GUI", ex);
                System.exit(-1);
              }
              MainWindowListener listener =
                  BundlePreferences.getService(bundleContext, MainWindowListener.class);
              if (listener != null) {
                listener.setMainWindow(mainWindow);
              }
            });
  }

  @Override
  public void stop(BundleContext bundleContext) {
    // Let osgi services doing their job
  }
}
