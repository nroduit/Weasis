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
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.SliderCineListener;
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
import org.weasis.dicom.codec.geometry.ImageOrientation;
import org.weasis.dicom.viewer2d.mip.MipView;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * DICOM-specific synchronization strategy.
 *
 * <p>The entry point is {@link #updateAllListeners}, called every time the global {@code SYNCH}
 * action changes or a per-view button toggles. The flow is:
 *
 * <pre>
 *   updateAllListeners
 *     ├── setupViewPane                — register the active view as a SYNCH listener
 *     ├── handleManuallyDisabledSync   — selected view sync was turned OFF by the user
 *     └── handleActiveSync
 *           ├── handleTileMode         — fan out KO filter / scroll bounds across tiles
 *           └── handleStackMode
 *                 └── per pane → configurePaneForStack(StackSyncContext, pane)
 * </pre>
 */
public class DicomSynchManager extends SynchManager<DicomImageElement> {

  /**
   * Aggregates the inputs shared by every per-pane configuration step in stack mode. Replaces a
   * 7-parameter method call so that the call sites read more naturally.
   */
  private record StackSyncContext(
      MediaSeries<DicomImageElement> sourceSeries,
      SynchData containerSynch,
      Double sourceSlicePosition,
      Map<String, List<ViewCanvas<DicomImageElement>>> framesOfReference,
      boolean autoSyncActivated) {}

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

    if (!synchView.isSynch() && !synch.isOriginal()) {
      handleManuallyDisabledSync(viewerPlugin, viewPane, series);
    } else {
      needsRepaint = handleActiveSync(viewerPlugin, viewPane, series, synch);
    }

    viewPane.updateSynchState();
    if (needsRepaint && viewPane instanceof View2d view) {
      view.repaint();
    }
  }

  private void setupViewPane(ViewCanvas<DicomImageElement> viewPane, SynchData synch) {
    // Ensure the current mode is honoured: if there is already a synch link for the same mode,
    // keep it; otherwise the new synch (= the global SynchView data) takes over when the per-pane
    // data is (re)created later by getOrCreateSynchData().
    SynchData existing = (SynchData) viewPane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (existing != null && !existing.getMode().equals(synch.getMode())) {
      viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
    }
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), viewPane);
    eventManager.getAction(ActionW.SCROLL_SERIES).ifPresent(a -> a.enableAction(true));
    viewPane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
  }

  private void handleManuallyDisabledSync(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series) {

    String fruid = getFrameOfReferenceUID(series);
    Optional<SliderCineListener> cineAction = eventManager.getAction(ActionW.SCROLL_SERIES);
    for (ViewCanvas<DicomImageElement> pane : getSiblingViews(viewerPlugin, viewPane)) {
      pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

      MediaSeries<DicomImageElement> s = pane.getSeries();
      boolean specialView = pane instanceof MipView;

      if (shouldEnableCrosslines(s, fruid, specialView, series)) {
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
      MediaSeries<DicomImageElement> series) {

    if (paneSeries == null || frameOfReferenceUID == null || specialView) {
      return false;
    }
    return frameOfReferenceUID.equals(getFrameOfReferenceUID(paneSeries))
        && !ImageOrientation.hasSameOrientation(series, paneSeries);
  }

  private boolean handleActiveSync(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {
    if (Mode.TILE.equals(synch.getMode())) {
      handleTileMode(viewerPlugin, viewPane, series, synch);
      return false;
    } else if (Mode.STACK.equals(synch.getMode())) {
      return handleStackMode(viewerPlugin, viewPane, series, synch);
    }
    return false;
  }

  private void handleTileMode(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {

    List<ViewCanvas<DicomImageElement>> panes = getSiblingViews(viewerPlugin, viewPane);
    panes.add(viewPane);
    if (panes.size() == 1) {
      return;
    }
    setSyncModeButton(true, true);
    configureScrollLimits(viewPane, series, panes);
    applyKOFilterToPanes(viewPane, panes, synch);
  }

  private void configureScrollLimits(
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      List<ViewCanvas<DicomImageElement>> panes) {

    @SuppressWarnings("unchecked")
    Filter<DicomImageElement> filter =
        (Filter<DicomImageElement>) viewPane.getActionValue(ActionW.FILTERED_SERIES.cmd());
    int maxShift = series.size(filter) - panes.size();
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
      oldSynch.setAutoSyncState(SyncState.ON);
      KOManager.updateKOFilter(pane, selectedKO, enableFilter, frameIndex);

      pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), oldSynch);
      pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), false);
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
      pane.updateSynchState();
    }
  }

  private boolean handleStackMode(
      ImageViewerPlugin<DicomImageElement> viewerPlugin,
      ViewCanvas<DicomImageElement> viewPane,
      MediaSeries<DicomImageElement> series,
      SynchData synch) {

    DicomImageElement img = series.getMedia(MEDIA_POSITION.MIDDLE, null, null);
    Double slicePosition = img == null ? null : (Double) img.getTagValue(TagW.SlicePosition);
    List<ViewCanvas<DicomImageElement>> views = getAllVisibleSiblingViews(viewerPlugin, viewPane);
    views.add(viewPane);

    if (views.size() > 1) {
      handleMultipleViewsInStack(views, series, synch, slicePosition);
    } else {
      handleSingleViewInStack();
      viewPane.setActionsInView(ActionW.SYNCH_LINK.cmd(), null);
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

    // Multiple views are present: enable the sync-mode toggle button (without forcing selection).
    eventManager.getAction(ActionW.SYNCH_MODE).ifPresent(e -> e.enableAction(true));
    if (synchActivated) {
      synch.setAutoSyncState(SyncState.ON);
    }

    boolean manualSyncActivated = synch.isManualSynchActivated();
    StackSyncContext ctx =
        new StackSyncContext(series, synch, slicePosition, frUidsMap, synchActivated);

    for (ViewCanvas<DicomImageElement> pane : views) {
      List<ViewCanvas<DicomImageElement>> manuallySyncableViews =
          collectManuallySyncableViews(pane, views, manualSyncActivated);
      configurePaneForStack(ctx, pane, manuallySyncableViews);
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
      boolean manualSyncActivated) {
    if (manualSyncActivated) {
      if (pane.getSeries() == null) {
        return Collections.emptyList();
      }
      // If at least one already manually synced view has a different orientation than the current
      // view, we cannot propose manual sync for the current view.
      ImageOrientation.Plan currentViewOrientation =
          ImageOrientation.getPlan(pane.getSeries().getMedia(MEDIA_POSITION.MIDDLE, null, null));
      for (ViewCanvas<DicomImageElement> v : views) {
        ViewSynchData sd = (ViewSynchData) v.getActionValue(ActionW.SYNCH_LINK.cmd());
        if (sd != null && sd.isManualSynchActivated()) {
          ImageOrientation.Plan orientation =
              ImageOrientation.getPlan(v.getSeries().getMedia(MEDIA_POSITION.MIDDLE, null, null));
          if (orientation != currentViewOrientation) {
            return Collections.emptyList();
          }
          break;
        }
      }
    }
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
    return frameOfReferenceGroups.values().stream().anyMatch(list -> list.size() > 1);
  }

  private void configurePaneForStack(
      StackSyncContext ctx,
      ViewCanvas<DicomImageElement> pane,
      List<ViewCanvas<DicomImageElement>> manuallySyncableViews) {
    pane.getGraphicManager().deleteByLayerType(LayerType.CROSSLINES);

    MediaSeries<DicomImageElement> paneSeries = pane.getSeries();
    String paneFrameUID = getFrameOfReferenceUID(paneSeries);

    ViewSynchData sd = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());

    if (shouldConfigurePaneSync(paneSeries, pane, ctx, paneFrameUID)) {
      configurePaneSynchData(
          pane, ctx.sourceSeries(), paneSeries, ctx.containerSynch(), paneFrameUID);
      sd = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
    } else if (sd != null) {
      sd.setAutoSyncState(SyncState.OFF);
      List<ViewCanvas<DicomImageElement>> autoSyncViews = ctx.framesOfReference().get(paneFrameUID);
      // Two cases: user manually disabled auto-sync on a view, OR auto-sync is globally on but
      // this view cannot be auto-synced with the others
      sd.setCanBeAutoSynced(autoSyncViews != null && autoSyncViews.size() > 1);
    }

    // If manual sync is already active, register this pane as a SYNCH listener
    if (ctx.containerSynch().getManualSyncState() == SyncState.ON
        && sd != null
        && !sd.getManualSyncDataSet().isEmpty()) {
      eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    }

    if (sd == null) {
      // Several views are auto-synced in the container, but this one is not
      sd = getOrCreateSynchData(pane, ctx.containerSynch());
      sd.setAutoSyncState(SyncState.OFF);
      sd.setManualSyncState(SyncState.OFF);
      sd.setCanBeAutoSynced(false);
    }
    if (!manuallySyncableViews.isEmpty()) {
      // Manual sync is possible: enable the manual button. When manual sync is actually active
      // on this view, ensure Scroll stays enabled (manual sync is built on scroll propagation)
      // but DO NOT wipe the other user-chosen options — they remain configurable from the
      // per-view popup. configureScrollOnlyActions respects isOriginal() and only forces a
      // full scroll-only reset on default/computed SynchData.
      sd.setCanBeManuallySynced(true);
      if (sd.isManualSynchActivated()) {
        configureScrollOnlyActions(sd);
      }
      setSyncModeButton(true, true);
    } else {
      sd.setCanBeManuallySynced(false);
    }
    pane.setActionsInView(ActionW.SYNCH_LINK.cmd(), sd);
    pane.updateSynchState();
  }

  private boolean shouldConfigurePaneSync(
      MediaSeries<DicomImageElement> paneSeries,
      ViewCanvas<DicomImageElement> pane,
      StackSyncContext ctx,
      String paneFrameUID) {
    if (paneSeries == null) {
      return false;
    }
    SynchData synch = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (synch != null && !synch.isSynchActivated() && !synch.isOriginal()) {
      return false;
    }
    List<ViewCanvas<DicomImageElement>> matching = ctx.framesOfReference().get(paneFrameUID);
    return ctx.autoSyncActivated()
        && ctx.sourceSlicePosition() != null
        && matching != null
        && matching.size() > 1;
  }

  private void configurePaneSynchData(
      ViewCanvas<DicomImageElement> pane,
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> paneSeries,
      SynchData synch,
      String paneFrameUID) {

    boolean sameOrientation = ImageOrientation.hasSameOrientation(series, paneSeries);
    boolean sameFrameRef = paneFrameUID.equals(getFrameOfReferenceUID(series));

    ViewSynchData paneSynch;
    if (sameOrientation && sameFrameRef) {
      paneSynch = configureSameOrientationSync(pane, series, paneSeries, synch);
    } else if (sameFrameRef) {
      paneSynch = configureDifferentOrientationSync(pane, synch);
    } else {
      return;
    }
    if (paneSynch.isOriginal()) {
      paneSynch.setAutoSyncState(SyncState.ON);
    }
    paneSynch.setCanBeAutoSynced(true);
    paneSynch.setFrameOfReferenceUID(paneFrameUID);

    setSyncModeButton(true, true);
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
    if (!(hasNoPRState && hasSameSize)) {
      // Pixel size differs or a Presentation State is applied: only scroll synchronizes
      configureScrollOnlyActions(oldSynch);
    }
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    return oldSynch;
  }

  private ViewSynchData configureDifferentOrientationSync(
      ViewCanvas<DicomImageElement> pane, SynchData synch) {

    pane.setActionsInView(ActionW.SYNCH_CROSSLINE.cmd(), true);

    ViewSynchData oldSynch = getOrCreateSynchData(pane, synch);
    if (!(pane instanceof MprView)) {
      // Only MPR views support full sync across orientations; other views fall back to scroll
      configureScrollOnlyActions(oldSynch);
    }
    eventManager.addPropertyChangeListener(ActionW.SYNCH.cmd(), pane);
    return oldSynch;
  }

  private void configureScrollOnlyActions(SynchData synchData) {
    // Respect user customizations made via the per-view sync options popup. When the user has
    // explicitly tweaked the per-view propagation list (isOriginal == false), do not silently
    // wipe their choices on the next updateAllListeners() pass (which is triggered every time
    // the selected view changes). Default/computed SynchData (isOriginal == true) is still
    // restricted to scroll-only as before.
    if (!synchData.isOriginal()) {
      // Always keep scroll enabled — without it the views would not be linked at all.
      synchData.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
      return;
    }
    synchData.getActions().replaceAll((_, _) -> false);
    synchData.getActions().put(ActionW.SCROLL_SERIES.cmd(), true);
  }

  private void handleSingleViewInStack() {
    ComboItemListener<SynchView> synchAction = eventManager.getAction(ActionW.SYNCH).orElse(null);
    if (synchAction != null && synchAction.getSelectedItem() instanceof SynchView sel) {
      sel.getSynchData().setAutoSyncState(SyncState.OFF);
      sel.getSynchData().setManualSyncState(SyncState.OFF);
      setSyncModeButton(false, false);
    }
  }

  /** Set the global SYNCH_MODE toggle's enabled / selected state in one call. */
  private void setSyncModeButton(boolean enabled, boolean selected) {
    eventManager
        .getAction(ActionW.SYNCH_MODE)
        .ifPresent(
            e -> {
              e.setSelectedWithoutTriggerAction(selected);
              e.enableAction(enabled);
            });
  }

  private ViewSynchData getOrCreateSynchData(ViewCanvas<DicomImageElement> pane, SynchData synch) {
    ViewSynchData oldSynch = (ViewSynchData) pane.getActionValue(ActionW.SYNCH_LINK.cmd());
    if (oldSynch == null || oldSynch.isOriginal() || !oldSynch.getMode().equals(synch.getMode())) {
      return new ViewSynchData(synch.getMode(), synch.getActions(), synch.isSynchActivated());
    }
    return oldSynch;
  }

  /**
   * @return the other panes in the same container as {@code viewPane} (mutable list, can be added
   *     to by the caller). Empty when the input view has no series.
   */
  protected List<ViewCanvas<DicomImageElement>> getSiblingViews(
      ImageViewerPlugin<DicomImageElement> viewerPlugin, ViewCanvas<DicomImageElement> viewPane) {
    if (viewPane == null || viewPane.getSeries() == null) {
      return Collections.emptyList();
    }
    List<ViewCanvas<DicomImageElement>> views = viewerPlugin.getImagePanels();
    views.remove(viewPane);
    return views;
  }

  /**
   * Same as {@link #getSiblingViews} but additionally includes panes from every other visible
   * {@link View2dContainer} that shares the same group ID and is also in {@link Mode#STACK}.
   */
  protected List<ViewCanvas<DicomImageElement>> getAllVisibleSiblingViews(
      ImageViewerPlugin<DicomImageElement> viewerPlugin, ViewCanvas<DicomImageElement> viewPane) {
    List<ViewCanvas<DicomImageElement>> views = getSiblingViews(viewerPlugin, viewPane);
    if (!views.isEmpty() || viewPane != null) {
      if (viewerPlugin instanceof View2dContainer) {
        addVisibleViews(views, viewerPlugin);
      }
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

    return image2 != null && ImageOrientation.hasSameOrientation(image1, image2);
  }
}
