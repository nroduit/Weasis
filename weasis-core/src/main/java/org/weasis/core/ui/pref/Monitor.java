/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.pref;

import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.Rectangle;
import java.util.List;
import org.weasis.core.ui.editor.image.dockable.MeasureTool;

public class Monitor {
  private final GraphicsDevice graphicsDevice;
  private double realScaleFactor;

  public Monitor(GraphicsDevice graphicsDevice) {
    this.realScaleFactor = 0.0;
    this.graphicsDevice = graphicsDevice;
  }

  public synchronized double getRealScaleFactor() {
    return realScaleFactor;
  }

  public synchronized void setRealScaleFactor(double realScaleFactor) {
    this.realScaleFactor = realScaleFactor;
  }

  public String getMonitorID() {
    return graphicsDevice.getIDstring();
  }

  public Rectangle getBounds() {
    return graphicsDevice.getDefaultConfiguration().getBounds();
  }

  public GraphicsConfiguration getGraphicsConfiguration() {
    return graphicsDevice.getDefaultConfiguration();
  }

  public GraphicsDevice getGraphicsDevice() {
    return graphicsDevice;
  }

  public static Monitor getMonitor(GraphicsConfiguration gconfig) {
    if (gconfig != null) {
      List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
      for (Monitor monitor : monitors) {
        if (gconfig.equals(monitor.getGraphicsConfiguration())) {
          return monitor;
        }
      }
    }
    return null;
  }

  public static Monitor getDefaultMonitor() {
    int defIndex = ScreenPrefView.getDefaultMonitor();
    List<Monitor> monitors = MeasureTool.viewSetting.getMonitors();
    if (monitors.isEmpty()) {
      return null;
    }
    if (defIndex < 0 || defIndex >= monitors.size()) {
      defIndex = 0;
    }
    return monitors.get(defIndex);
  }
}
