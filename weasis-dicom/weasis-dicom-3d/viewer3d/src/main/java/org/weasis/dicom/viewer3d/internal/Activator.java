/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.viewer3d.View3DContainer;
import org.weasis.dicom.viewer3d.vr.OpenglUtils;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    BundleTools.registerExistingComponents(View3DContainer.UI);
  }

  @Override
  public void stop() {
    GuiUtils.getUICore().closeSeriesViewerType(View3DContainer.class);
    // Must happen here rather than from a JVM shutdown hook: JOGL still needs to load classes to
    // tear the context down, and the plugin class loaders are closed once the plugins are unloaded.
    OpenglUtils.destroy();
  }

  //  @Override
  //  public synchronized void serviceChanged(final ServiceEvent event) {
  //    // Tools and Toolbars (with non-immediate instance) must be instantiated in the EDT
  //    GuiExecutor.execute(() -> BundleTools.dataExplorerChanged(event, View3DContainer.UI));
  //  }
}
