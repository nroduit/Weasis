/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.print;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.UICore;

/** The destinations a page can be sent to: the system printers, then those of the plugins. */
public final class PrintDestinations {
  private static final Logger LOGGER = LoggerFactory.getLogger(PrintDestinations.class);

  private PrintDestinations() {}

  public static List<PrintDestination> all() {
    List<PrintDestination> destinations = new ArrayList<>(SystemPrinterDestination.lookup());
    UICore core = GuiUtils.getUICore();
    if (core != null && core.getPluginManager() != null) {
      for (PrintDestinationProvider provider :
          core.getPluginManager().getExtensions(PrintDestinationProvider.class)) {
        try {
          destinations.addAll(provider.destinations());
        } catch (RuntimeException e) {
          LOGGER.warn("Print destinations of {} skipped", provider.getClass().getName(), e);
        }
      }
    }
    destinations.add(new PdfFileDestination());
    return destinations;
  }

  public static Optional<PrintDestination> find(List<PrintDestination> destinations, String id) {
    return destinations.stream().filter(d -> d.id().equals(id)).findFirst();
  }
}
