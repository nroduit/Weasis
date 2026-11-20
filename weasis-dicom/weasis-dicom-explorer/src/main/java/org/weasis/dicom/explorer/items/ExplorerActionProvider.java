/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.List;
import java.util.Optional;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.dicom.codec.DicomSeries;

/**
 * What a plugin offers on the studies and the series of the DICOM explorer, for instance sending
 * them to an analysis service and following the result. One provider gives a named group of
 * actions; the explorer shows it wherever the user can point at a study or at series: the context
 * menu of a series, the header of a study and the study content panel. Each place offers the
 * actions on the selected series and on the whole study, so the plugin never asks what to send.
 *
 * <p>Both methods are called while menus and thumbnails are built: they read a state the plugin
 * keeps, and never wait for a network answer. When that state changes, the plugin fires an {@code
 * UPDATE} on the model for the series or the study, and the explorer asks again. The state lives
 * outside the provider object: the plugin manager may create one each time it is asked, and a
 * provider is recognized by its id.
 */
public interface ExplorerActionProvider extends ExtensionPoint {

  /** A stable id of the group, such as {@code ai-tasks}. */
  String getId();

  /** The title of the group, in the language of the interface. */
  String getTitle();

  /** The icon of the group, from the resources of the application; null for none. */
  default ResourceIconPath getIcon() {
    return null;
  }

  /**
   * The actions that apply to the target, in the order they are shown; empty when the group has
   * nothing for it (a study from a source the plugin does not serve), and the group is not shown.
   */
  List<ExplorerAction> getActions(ExplorerTarget target);

  /** The mark of the series, such as a task pending or a result ready; empty for none. */
  default Optional<SeriesBadge> getBadge(DicomSeries series) {
    return Optional.empty();
  }
}
