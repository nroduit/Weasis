/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.awt.Window;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import org.weasis.pref.ConfigData;

public class WeasisMainFrame implements WeasisMainFrameMBean {

  private RootPaneContainer rootPaneContainer;
  private ConfigData configData;

  public void setRootPaneContainer(RootPaneContainer rootPaneContainer) {
    this.rootPaneContainer = rootPaneContainer;
  }

  @Override
  public RootPaneContainer getRootPaneContainer() {
    return rootPaneContainer;
  }

  @Override
  public ConfigData getConfigData() {
    return configData;
  }

  public void setConfigData(ConfigData configData) {
    this.configData = configData;
  }

  public Window getWindow() {
    if (rootPaneContainer == null) {
      return null;
    }

    Window window = SwingUtilities.getWindowAncestor(rootPaneContainer.getRootPane());
    if (window != null && window.isDisplayable()) {
      return window;
    }
    return null;
  }
}
