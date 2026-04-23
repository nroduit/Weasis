/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image;

import java.util.Objects;
import java.util.Optional;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.SynchData.SyncState;

public abstract class SynchManager<E extends ImageElement> {

  protected final ImageViewerEventManager<E> eventManager;

  public SynchManager(ImageViewerEventManager<E> eventManager) {
    this.eventManager = eventManager;
  }

  public abstract void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView);

  protected String getFrameOfReferenceUID(MediaSeries<E> series) {
    if (series != null) {
      return (String) series.getTagValue(TagW.get("FrameOfReferenceUID"));
    }
    return null;
  }

  protected boolean hasSameFrUid(MediaSeries<E> series1, MediaSeries<E> series2) {
    if (series1 == null || series2 == null) {
      return false;
    }
    return Objects.equals(getFrameOfReferenceUID(series1), getFrameOfReferenceUID(series2));
  }

  protected void applyManualSync() {

    ComboItemListener<SynchView> synchAction = eventManager.getAction(ActionW.SYNCH).orElse(null);
    if (synchAction != null && synchAction.getSelectedItem() instanceof SynchView sel) {
      sel.getSynchData().setManualSyncState(SyncState.ON);
      Optional<ToggleButtonListener> synchMode = eventManager.getAction(ActionW.SYNCH_MODE);
      synchMode.ifPresent(
          e -> {
            e.setSelectedWithoutTriggerAction(true);
            e.enableAction(true);
          });
    }
  }

  public abstract boolean hasSameOrientation(MediaSeries<E> series1, MediaSeries<E> series2);
}
