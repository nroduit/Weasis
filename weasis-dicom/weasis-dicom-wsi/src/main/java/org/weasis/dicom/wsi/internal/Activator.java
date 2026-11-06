/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi.internal;

import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.dicom.wsi.WsiContainer;
import org.weasis.dicom.wsi.WsiSettings;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {

  public Activator(org.weasis.plugin.PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    SettingCatalog.declare(WsiSettings.class);
  }

  @Override
  public void stop() {
    GuiUtils.getUICore().closeSeriesViewerType(WsiContainer.class);
  }
}
