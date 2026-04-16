/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderCineListener;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeries.MEDIA_POSITION;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchData.SyncState;
import org.weasis.core.ui.editor.image.SynchManager;
import org.weasis.core.ui.editor.image.SynchView;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewSynchData;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprView;

public class DicomSynchManager extends SynchManager<DicomImageElement> {

  public DicomSynchManager(EventManager eventManager) {
    super(eventManager);
  }

  @Override
  public void updateAllListeners(
      ImageViewerPlugin<DicomImageElement> viewerPlugin, SynchView synchView) {
    if (viewerPlugin == null) {
      return;
    }
    ViewCanvas<DicomImageElement> viewPane = viewerPlugin.getSelectedViewCanvas();
    if (viewPane == null) {
      return;
    }

    MediaSeries<DicomImageElement> series = viewPane.getSeries();
    if (series == null) {
      return;
    }
    SynchData synch = synchView.getSynchData();
    setupViewPane(viewPane, synch);

    boolean needsRepaint = false;

    // TODO problem with deactivated sync and original syncdata
    if (!synchView.isSynch() && !synch.isOriginal()) {
      handleManuallyDisabledSync(viewerPlugin, viewPane, series, synch);
    } else {
      needsRepaint = handleActiveSync(viewerPlugin, viewPane, series, synch);
    }

    viewPane.updateSynchState();
    if (needsRepaint && viewPane instanceof View2d view) {
      view.repaint();
    }
  }

  private void setupViewPane(ViewCanvas<DicomImageElement> viewPane, SynchData synch) {
    SynchData oldSynch = (SynchData) viewPane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (oldSynch == null || !oldSynch.getMode().equals(synch.getMode())) {
      oldSynch = synch;
    }

    // viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);

    Optional<SliderCineListener> cineAction = eventManager.getAction(ActionW.SCROLL_SERIES);
    cineAction.ifPresent(a -> a.enableAction(true));
    viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
  }

  private void handleManuallyDisabledSync(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {

    String fruid = TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class);
    Optional<SliderCineListener> cineAction = eventManager.getAction(ActionW.SCROLL_SERIES);
    for (ViewCanvas<DicomImageElement> pane : getViews(viewerPlugin, viewPane, false)) {
      pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

      MediaSeries<DicomImageElement> s = pane.getSeries();
      boolean specialView = pane instanceof MipView;

      if (shouldEnableCrosslines(s, fruid, specialView, series, pane)) {
        pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);
        eventManager.addPropertyChangeListener(ActionW.SCROLL_SERIES.cmd(), pane);
        // Force drawing crosslines without changing the slice position
        cineAction.ifPresent(a -> a.stateChanged(a.getSliderModel()));
      }
      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
      pane.updateSynchState();
    }
    viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
  }

  private boolean shouldEnableCrosslines(
      MediaSeries<DicomImageElement> paneSeries,
      String frameOfReferenceUID,
      boolean specialView,
      MediaSeries<DicomImageElement> series,
      ViewCanvas<DicomImageElement> pane) {

    if (paneSeries == null || frameOfReferenceUID == null || specialView) {
      return false;
    }

    String paneFrameUID = TagD.getTagValue(paneSeries, Tag.FrameOfReferenceUID, String.class);
    return frameOfReferenceUID.equals(paneFrameUID)
        && !ImageOrientation.hasSameOrientation(series, paneSeries);
  }

  private boolean handleActiveSync(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {
    if (Mode.TILE.equals(synch.getMode())) {
      return handleTileMode(viewerPlugin, viewPane, series, synch);
    } else if (Mode.STACK.equals(synch.getMode())) {
      return handleStackMode(viewerPlugin, viewPane, series, synch);
    }
    return false;
  }

  private boolean handleTileMode(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {

    final List<ViewCanvas<DicomImageElement>> panes = getViews(viewerPlugin, viewPane, false);
    if (panes.isEmpty()) {
      return false;
    }

    configureSynchModeAction(panes);
    configureScrollLimits(viewPane, series, panes);
    applyKOFilterToPanes(viewPane, panes, synch);

    return ensureViewPaneSynchLink(viewPane, synch);
  }

  private void configureSynchModeAction(List<ViewCanvas<DicomImageElement>> panes) {
    if (!panes.isEmpty()) {
      eventManager
          .getAction(ActionW.SYNCH_MODE)
          .ifPresent(
              e -> {
                e.setSelectedWithoutTriggerAction(true);
                e.enableAction(true);
              });
    }
  }

  private void configureScrollLimits(
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      List<ViewCanvas<DicomImageElement>> panes) {

    final int maxShift =
        series.size(
                (Filter<DicomImageElement>) viewPane.getActionValue(ActionW.FILTERED_SERIES.cmd()))
            - panes.size();

    eventManager
        .getAction(ActionW.SCROLL_SERIES)
        .ifPresent(
            a ->
                a.setSliderMinMaxValue(
                    1, Math.max(maxShift, 1), viewPane.getFrameIndex() + 1, false));
  }

  private void applyKOFilterToPanes(
      ViewCanvas<DicomImageElement> viewPane,
      List<ViewCanvas<DicomImageElement>> panes,
      SynchData synch) {

    Object selectedKO = viewPane.getActionValue(ActionW.KO_SELECTION.cmd());
    Boolean enableFilter = (Boolean) viewPane.getActionValue(ActionW.KO_FILTER.cmd());
    int frameIndex =
        LangUtil.nullToFalse(enableFilter)
            ? 0
            : viewPane.getFrameIndex() - viewPane.getTileOffset();
    for (ViewCanvas<DicomImageElement> pane : panes) {
      ViewSynchData oldSynch = getOrCreateSynchData(pane, synch);
      oldSynch.getActions().put(ActionW.KO_SELECTION.cmd(), true);
      oldSynch.getActions().put(ActionW.KO_FILTER.cmd(), true);
      KOManager.updateKOFilter(pane, selectedKO, enableFilter, frameIndex);

      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
      pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
      pane.updateSynchState();
    }
  }

  private boolean ensureViewPaneSynchLink(ViewCanvas<DicomImageElement> viewPane, SynchData synch) {
    if (viewPane.getAction(ActionW.SYNCH_LINK) == null) {
      viewPane.setActionsInView(
          ActionW.SYNCH_LINK.cmd(),
          new ViewSynchData(synch.getMode(), synch.getActions(), synch.isSynchActivated()));
      return true;
    }
    return false;
  }

  private boolean handleStackMode(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {

    DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    Double val = img == null ? null : (Double) img.getTagValue(TagW.SlicePosition);
    List<ViewCanvas<DicomImageElement>> views = getViews(viewerPlugin, viewPane, true);
    views.add(viewPane);
    if (views.size() > 1) {
      handleMultipleViewsInStack(views, series, synch, val);
    } else {
      handleSingleViewInStack();
    }

    // Force drawing crosslines without changing the slice position
    boolean isMprOrOblique = eventManager.getSelectedView2dContainer() instanceof MprContainer;
    if (!isMprOrOblique) {
      eventManager
          .getAction(ActionW.SCROLL_SERIES)
          .ifPresent(a -> a.stateChanged(a.getSliderModel()));
    }
    return false;
  }

  private void handleMultipleViewsInStack(
      List<ViewCanvas<DicomImageElement>> views,
      MediaSeries<DicomImageElement> series,
      SynchData synch,
      Double slicePosition) {

    Map<String, List<ViewCanvas<DicomImageElement>>> frUidsMap = collectFrameOfReferenceUIDs(views);
    boolean synchActivated = isAutoSynchActivated(frUidsMap);

    // Set Sync button enabled since we have multiple views ?
    Optional<ToggleButtonListener> synchMode = eventManager.getAction(ActionW.SYNCH_MODE);
    synchMode.ifPresent(
        e -> {
          e.enableAction(true);
        });
    if (synchActivated) {
      synch.setAutoSyncState(SyncState.ON);
    }

    boolean manualSyncActivated = synch.isManualSynchActivated();
    ImageOrientation.Plan orientation =
        ImageOrientation.getPlan(series.getMedia(MEDIA_POSITION.MIDDLE, null, null));

    for (ViewCanvas<DicomImageElement> pane : views) {
      // TODO Adapt list of manually syncable views if the synchronization is already on or not
      List<ViewCanvas<DicomImageElement>> manuallySyncableViews =
          collectManuallySyncableViews(pane, views, manualSyncActivated ? orientation : null);
      configurePaneForStack(
          pane, series, synch, synchActivated, slicePosition, frUidsMap, manuallySyncableViews);
    }
  }

  private Map<String, List<ViewCanvas<DicomImageElement>>> collectFrameOfReferenceUIDs(
      List<ViewCanvas<DicomImageElement>> views) {
    return views.stream()
        .filter(view -> view != null && view.getSeries() != null)
        .collect(
            Collectors.groupingBy(
                view -> {
                  String uid = getFrameOfReferenceUID(view.getSeries());
                  return uid != null ? uid : "";
                },
                Collectors.toList()))
        .entrySet()
        .stream()
        .filter(entry -> !entry.getKey().isEmpty())
        .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
  }

  private List<ViewCanvas<DicomImageElement>> collectManuallySyncableViews(
      ViewCanvas<DicomImageElement> pane,
      List<ViewCanvas<DicomImageElement>> views,
      ImageOrientation.Plan orientation) {
    return views.stream()
        .filter(
            view ->
                view != null
                    && view.getSeries() != null
                    && ImageOrientation.hasSameOrientation(view.getSeries(), pane.getSeries())
                    && !hasSameFrUid(pane.getSeries(), view.getSeries()))
        .toList();
  }

  private boolean isAutoSynchActivated(
      Map<String, List<ViewCanvas<DicomImageElement>>> frameOfReferenceGroups) {
    return frameOfReferenceGroups.entrySet().stream()
        .anyMatch(entry -> entry.getValue().size() > 1);
  }

  private void configurePaneForStack(
      ViewCanvas<DicomImageElement> pane,
      MediaSeries<DicomImageElement> series,
      SynchData synch,
      boolean synchActivated,
      Double slicePosition,
      Map<String, List<ViewCanvas<DicomImageElement>>> frUidsMap,
      List<ViewCanvas<DicomImageElement>> manuallySyncableViews) {
    pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

    MediaSeries<DicomImageElement> paneSeries = pane.getSeries();
    String paneFrameUID = TagD.getTagValue(paneSeries, Tag.FrameOfReferenceUID, String.class);

    ViewSynchData sd = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());

    if (shouldConfigurePaneSync(
        paneSeries, pane, synchActivated, slicePosition, paneFrameUID, frUidsMap)) {
      configurePaneSynchData(pane, series, paneSeries, synch, paneFrameUID);
    } else if (sd != null && sd.isAutoSynchActivated()) {
      sd.setAutoSyncState(SyncState.OFF);
    }

    // If manual sync already activated, add listener to panes synced
    if (synch.getManualSyncState() == SyncState.ON
        && sd != null
        && !sd.getManualSyncDataSet().isEmpty()) {
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    }

    if (sd == null) {
      // If we get here, we have several views that are automatically synced in the container, but
      // if the current one was also automatically synchronized, its SynchData would have been set
      // before
      // We are in the case where multiple other views are automatically synced but not that one
      sd = getOrCreateSynchData(pane, synch);
      sd.setAutoSyncState(SyncState.OFF);
      sd.setManualSyncState(SyncState.OFF);
    }
    if (!manuallySyncableViews.isEmpty()) {
      // Configure for manual synchronization
      // Add manual sync button + the list of manually syncable views to choose
      sd.setCanBeManuallySynced(true);
      // Scroll-only synchronization
      configureScrollOnlyActions(sd);
      // TODO probably tries to sync but sync may not be active
      // eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
      eventManager
          .getAction(ActionW.SYNCH_MODE)
          .ifPresent(
              e -> {
                e.setSelectedWithoutTriggerAction(true);
                e.enableAction(true);
              });
      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), sd);
      pane.updateSynchState();
    } else {
      sd.setCanBeManuallySynced(false);
    }
  }

  private boolean shouldConfigurePaneSync(
      MediaSeries<DicomImageElement> paneSeries,
      ViewCanvas<DicomImageElement> pane,
      boolean synchActivated,
      Double slicePosition,
      String paneFrameUID,
      Map<String, List<ViewCanvas<DicomImageElement>>> frUidsMap) {
    if (paneSeries == null || pane instanceof MipView) {
      return false;
    }

    SynchData synch = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (synch != null && (!synch.isSynchActivated() && !synch.isOriginal())) {
      return false;
    }

    return synchActivated
        && slicePosition != null
        && frUidsMap.get(paneFrameUID) != null
        && frUidsMap.get(paneFrameUID).size() > 1;
  }

  private void configurePaneSynchData(
      ViewCanvas<DicomImageElement> pane,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> paneSeries,
      SynchData synch,
      String paneFrameUID) {

    boolean sameOrientation = ImageOrientation.hasSameOrientation(series, paneSeries);
    boolean sameFrameRef =
        paneFrameUID.equals(TagD.getTagValue(series, Tag.FrameOfReferenceUID, String.class));

    ViewSynchData paneSynch;

    if (sameOrientation && sameFrameRef) {
      paneSynch = configureSameOrientationSync(pane, series, paneSeries, synch);
    } else if (sameFrameRef) {
      paneSynch = configureDifferentOrientationSync(pane, synch);
    } else {
      return;
    }
    paneSynch.setAutoSyncState(SyncState.ON);
    paneSynch.setFrameOfReferenceUID(paneFrameUID);

    eventManager
        .getAction(ActionW.SYNCH_MODE)
        .ifPresent(
            e -> {
              e.setSelectedWithoutTriggerAction(true);
              e.enableAction(true);
            });

    pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), paneSynch);
    pane.updateSynchState();
  }

  private ViewSynchData configureSameOrientationSync(
      ViewCanvas<DicomImageElement> pane,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> paneSeries,
      SynchData synch) {

    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);

    boolean hasNoPRState = pane.getActionValue(ActionW.PR_STATE.cmd()) == null;
    boolean hasSameSize = hasSameSize(series, paneSeries);

    ViewSynchData oldSynch = getOrCreateSynchData(pane, synch);
    if (hasNoPRState && hasSameSize) {
      // Full synchronization
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    } else {
      // Scroll-only synchronization
      configureScrollOnlyActions(oldSynch);
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    }
    return oldSynch;
  }

  private ViewSynchData configureDifferentOrientationSync(
      ViewCanvas<DicomImageElement> pane, SynchData synch) {

    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);

    ViewSynchData oldSynch = getOrCreateSynchData(pane, synch);
    if (pane instanceof MprView) {
      // MPR views support full synchronization
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    } else {
      // Scroll-only synchronization
      configureScrollOnlyActions(oldSynch);
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    }
    return oldSynch;
  }

  private void configureScrollOnlyActions(SynchData synchData) {
    for (Entry<String, Boolean> a : synchData.getActions().entrySet()) {
      a.setValue(false);
    }
    synchData.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
  }

  private void handleSingleViewInStack() {
    ComboItemListener<SynchView> synchAction = eventManager.getAction(ActionW.SYNCH).orElse(null);
    if (synchAction != null && synchAction.getSelectedItem() instanceof SynchView sel) {
      sel.getSynchData().setAutoSyncState(SyncState.OFF);
      sel.getSynchData().setManualSyncState(SyncState.OFF);
      Optional<ToggleButtonListener> synchMode = eventManager.getAction(ActionW.SYNCH_MODE);
      synchMode.ifPresent(
          e -> {
            e.setSelectedWithoutTriggerAction(false);
            e.enableAction(false);
          });
    }
  }

  private ViewSynchData getOrCreateSynchData(ViewCanvas<DicomImageElement> pane, SynchData synch) {
    ViewSynchData oldSynch = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (oldSynch == null || oldSynch.isOriginal() || !oldSynch.getMode().equals(synch.getMode())) {
      return new ViewSynchData(synch.getMode(), synch.getActions(), synch.isSynchActivated());
    }
    return oldSynch;
  }

  protected List<ViewCanvas<DicomImageElement>> getViews(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      boolean allOtherVisible) {
    if (viewPane == null || viewPane.getSeries() == null) {
      return Collections.emptyList();
    }

    List<ViewCanvas<DicomImageElement>> views = viewerPlugin.getImagePanels();
    views.remove(viewPane);
    if (allOtherVisible && viewerPlugin instanceof View2dContainer) {
      addVisibleViews(views, viewerPlugin);
    }

    return views;
  }

  private void addVisibleViews(
      List<ViewCanvas<DicomImageElement>> views,
      ImageViewerPlugin<DicomImageElement> viewerPlugin) {
    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (viewerPlugins) {
      viewerPlugins.stream()
          .filter(plugin -> shouldIncludePlugin(plugin, viewerPlugin))
          .map(plugin -> (View2dContainer) plugin)
          .forEach(container -> views.addAll(container.getImagePanels()));
    }
  }

  private boolean shouldIncludePlugin(
      ViewerPlugin<?> plugin, ImageViewerPlugin<DicomImageElement> viewerPlugin) {
    if (!(plugin instanceof View2dContainer view2dContainer)) {
      return false;
    }

    // Include only visible containers with the same group ID and in STACK mode
    return view2dContainer.getDockable().isShowing()
        && !viewerPlugin.equals(plugin)
        && viewerPlugin.getGroupID().equals(view2dContainer.getGroupID())
        && Mode.STACK.equals(view2dContainer.getSynchView().getSynchData().getMode());
  }

  public static boolean hasSameSize(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
    if (series1 == null || series2 == null) {
      return false;
    }
    DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);

    return image1 != null && image2 != null && image1.hasSameSize(image2);
  }

  @Override
  public boolean hasSameOrientation(
      MediaSeries<DicomImageElement> series1, MediaSeries<DicomImageElement> series2) {
    if (series1 == null || series2 == null) {
      return false;
    }
    DicomImageElement image1 = series1.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    DicomImageElement image2 = series2.getMedia(MEDIA_POSITION.MIDDLE, null, null);

    return image1 != null && image2 != null && ImageOrientation.hasSameOrientation(image1, image2);
  }
}
