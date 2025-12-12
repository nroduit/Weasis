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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchViewButton.State;

public class SynchManager<E extends ImageElement> {

  protected final ImageViewerEventManager<E> eventManager;

  public SynchManager(ImageViewerEventManager<E> eventManager) {
    this.eventManager = eventManager;
  }

  public void updateAllListeners(ImageViewerPlugin<E> viewerPlugin, SynchView synchView) {
    if (viewerPlugin == null) {
      return;
    }

    ViewCanvas<E> viewPane = viewerPlugin.getSelectedImagePane();
    if (viewPane == null || viewPane.getSeries() == null) {
      return;
    }

    SynchData synch = synchView.getSynchData();
    viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

    final List<ViewCanvas<E>> panes = viewerPlugin.getImagePanels();
    panes.remove(viewPane);

    // Mark orphan views based on FrameOfReferenceUID distribution
    markOrphanViews(viewPane, panes);

    if (synchView.isSynch()) {
      applySynchToAllPanes(panes, synch);
    }

    if (Mode.STACK.equals(synch.getMode())) {
      applyStackMode(viewPane, panes, synch);
    } else if (Mode.TILE.equals(synch.getMode())) {
      applyTileMode(panes, synch);
    }
  }

  /**
   * Mark views as orphan if they have no FrameOfReferenceUID or have a unique one. Orphan views by
   * default do not have synchdata and do not register a listener.
   */
  protected void markOrphanViews(ViewCanvas<E> viewPane, List<ViewCanvas<E>> panes) {
    Map<String, Integer> frameOfReferenceCount = new HashMap<>();

    // Count FrameOfReferenceUID occurrences
    String mainFruid = getFrameOfReferenceUID(viewPane.getSeries());
    if (mainFruid != null) {
      frameOfReferenceCount.put(mainFruid, frameOfReferenceCount.getOrDefault(mainFruid, 0) + 1);
    }

    for (ViewCanvas<E> pane : panes) {
      String fruid = getFrameOfReferenceUID(pane.getSeries());
      if (fruid != null) {
        frameOfReferenceCount.put(fruid, frameOfReferenceCount.getOrDefault(fruid, 0) + 1);
      }
    }

    // Mark orphans
    SynchData mainSynch = (SynchData) viewPane.getActionsInView().get(ActionW.SYNCH_LINK.cmd());
    if (mainSynch != null) {
      boolean isOrphan = mainFruid == null || frameOfReferenceCount.getOrDefault(mainFruid, 0) <= 1;
      mainSynch.setOrphan(isOrphan);
    }

    for (ViewCanvas<E> pane : panes) {
      SynchData synch = (SynchData) pane.getActionsInView().get(ActionW.SYNCH_LINK.cmd());
      String fruid = getFrameOfReferenceUID(pane.getSeries());
      boolean isOrphan = fruid == null || frameOfReferenceCount.getOrDefault(fruid, 0) <= 1;

      if (synch != null) {
        synch.setOrphan(isOrphan);
      }

      // By default, orphan views do not have synchdata and do not register listener
      Boolean forceSyncOrphans = (Boolean) eventManager.getOptions().get("force.sync.orphans");
      if (isOrphan && !Boolean.TRUE.equals(forceSyncOrphans)) {
        pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
        eventManager.removePropertyChangeListener(ActionW.SYNCH.cmd(), pane);
      }
    }
  }

  protected String getFrameOfReferenceUID(MediaSeries<E> series) {
    if (series != null) {
      return (String) series.getTagValue(TagW.get("FrameOfReferenceUID"));
    }
    return null;
  }

  protected void applySynchToAllPanes(List<ViewCanvas<E>> panes, SynchData synch) {
    for (ViewCanvas<E> pane : panes) {
      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), synch);
    }
  }

  protected void applyStackMode(
      ViewCanvas<E> viewPane, List<ViewCanvas<E>> panes, SynchData synch) {
    Boolean forceSyncOrphans = (Boolean) eventManager.getOptions().get("force.sync.orphans");

    for (ViewCanvas<E> pane : panes) {
      SynchData paneSynch = (SynchData) pane.getActionsInView().get(ActionW.SYNCH_LINK.cmd());

      // Skip orphan views unless force sync is enabled
      if (paneSynch != null && paneSynch.isOrphan() && !Boolean.TRUE.equals(forceSyncOrphans)) {
        continue;
      }

      boolean synchByDefault = checkCompatibility(viewPane.getSeries(), pane.getSeries());

      if (synchByDefault) {
        // Views with same FrameOfReferenceUID default to ON
        SynchData copy = synch.copy();
        copy.setState(State.ON);
        pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), copy);
        eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
      } else {
        pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
      }
    }
  }

  protected void applyTileMode(List<ViewCanvas<E>> panes, SynchData synch) {
    for (ViewCanvas<E> pane : panes) {
      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), synch.copy());
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    }
  }

  protected boolean checkCompatibility(MediaSeries<E> series1, MediaSeries<E> series2) {
    return eventManager.isCompatible(series1, series2);
  }
}
