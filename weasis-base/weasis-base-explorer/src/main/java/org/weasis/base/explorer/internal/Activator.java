/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.explorer.internal;

import org.weasis.base.explorer.DefaultExplorerFactory;
import org.weasis.plugin.PluginContext;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {

  public Activator(PluginContext context) {
    super(context);
  }

  @Override
  public void stop() {
    getPluginWrapper().getPluginManager().getExtensions(DefaultExplorerFactory.class).stream()
        .findFirst()
        .ifPresent(DefaultExplorerFactory::stop);
  }
}
