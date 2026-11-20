/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JPopupMenu;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * The registry of the {@link ExplorerMenuContributor}s, filled by the activator of the explorer
 * from the registered services and directly by a plugin that has no service layer. Free of any
 * framework import, so the port to another plugin system only changes who calls {@link #register}.
 */
public final class ExplorerMenuContributors {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExplorerMenuContributors.class);

  private static final List<ExplorerMenuContributor> CONTRIBUTORS = new CopyOnWriteArrayList<>();

  private ExplorerMenuContributors() {}

  public static void register(ExplorerMenuContributor contributor) {
    if (contributor != null && !CONTRIBUTORS.contains(contributor)) {
      CONTRIBUTORS.add(contributor);
      LOGGER.info("Register explorer menu contributor: {}", contributor.getClass().getName());
    }
  }

  public static void unregister(ExplorerMenuContributor contributor) {
    if (contributor != null && CONTRIBUTORS.remove(contributor)) {
      LOGGER.info("Unregister explorer menu contributor: {}", contributor.getClass().getName());
    }
  }

  /** The contributors in registration order, read-only. */
  public static List<ExplorerMenuContributor> all() {
    return Collections.unmodifiableList(CONTRIBUTORS);
  }

  /**
   * Asks every contributor for its items. A separator is inserted before the first contributed item
   * when the menu already had some; nothing is added when no contributor adds anything. A
   * contributor that throws is logged and skipped, so a plugin never breaks the menu.
   */
  public static void addMenuItems(
      JPopupMenu menu,
      DicomModel model,
      MediaSeries<? extends MediaElement> clicked,
      List<MediaSeries<? extends MediaElement>> selection) {
    int before = menu.getComponentCount();
    for (ExplorerMenuContributor contributor : CONTRIBUTORS) {
      try {
        contributor.addMenuItems(menu, model, clicked, selection);
      } catch (RuntimeException e) {
        LOGGER.warn("Explorer menu contributor {} failed", contributor.getClass().getName(), e);
      }
    }
    if (before > 0 && menu.getComponentCount() > before) {
      menu.insert(new JPopupMenu.Separator(), before);
    }
  }
}
