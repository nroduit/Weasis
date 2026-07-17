/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;

public class DefaultSynchManager<E extends ImageElement> extends SynchManager<E> {

  public DefaultSynchManager(ImageViewerEventManager<E> eventManager) {
    super(eventManager);
  }

  @Override
  public void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView) {
    // Non-DICOM series cannot be synchronized across views, but the selected view must still
    // receive its own action events (zoom, pan, …), which are dispatched through the SYNCH
    // property change. Register it so mouse actions apply to the view.
    if (viewerPlugin == null) {
      return;
    }
    ViewCanvas<E> viewPane = viewerPlugin.getSelectedViewCanvas();
    if (viewPane == null || viewPane.getSeries() == null) {
      return;
    }
    viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);
  }

  @Override
  public boolean hasSameOrientation(MediaSeries series1, MediaSeries series2) {
    return false;
  }
}
