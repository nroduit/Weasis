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

import java.util.List;
import javax.swing.JPopupMenu;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;

/**
 * Items a plugin adds to the series context menu of the DICOM explorer. The menu is rebuilt on
 * every right click, on the EDT, so an implementation answers from its own state and never waits on
 * the network. There is no study-level menu: the series knows its study and its patient through the
 * model ({@code model.getParent(clicked, DicomModel.study)}, {@code DicomModel.patient}).
 *
 * <p>Registered as a service and tracked in {@link ExplorerMenuContributors}.
 */
@FunctionalInterface
public interface ExplorerMenuContributor {

  /**
   * @param menu the popup being built; items go after the built-in ones, below a separator
   * @param model the explorer model owning the series
   * @param clicked the series under the cursor
   * @param selection the selected series of the same MIME type as the clicked one, which includes
   *     it; never empty
   */
  void addMenuItems(
      JPopupMenu menu,
      DicomModel model,
      MediaSeries<? extends MediaElement> clicked,
      List<MediaSeries<? extends MediaElement>> selection);
}
