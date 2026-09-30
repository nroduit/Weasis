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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import jakarta.json.JsonObject;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.ToggleButtonListener;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.UICore;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;

/** The display state is the contract of every display verb: its keys, its units and its indexes. */
class ViewDisplayStateTest {

  private static final String SERIES_UID = "1.2.840.113619.2.55.3.1";

  @SuppressWarnings("unchecked")
  private static ViewCanvas<ImageElement> view(int frameIndex) {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    ViewModel model = mock(ViewModel.class);
    when(model.getViewScale()).thenReturn(1.25);
    when(model.getModelOffsetX()).thenReturn(12.0);
    when(model.getModelOffsetY()).thenReturn(-8.0);
    when(view.getViewModel()).thenReturn(model);

    OpManager manager = mock(OpManager.class);
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Double.class))
        .thenReturn(Optional.of(1500.0));
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Double.class))
        .thenReturn(Optional.of(-600.0));
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.PRESET.cmd()))
        .thenReturn(Optional.of("Lung"));
    when(view.getDisplayOpManager()).thenReturn(manager);

    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    when(series.getTagValue(TagW.get("SeriesInstanceUID"))).thenReturn(SERIES_UID);
    when(series.size(null)).thenReturn(120);
    when(view.getSeries()).thenReturn(series);
    when(view.getImage()).thenReturn(mock(ImageElement.class));
    when(view.getFrameIndex()).thenReturn(frameIndex);
    when(view.getActionValue(ActionW.ROTATION.cmd())).thenReturn(90);
    when(view.getActionValue(ActionW.FLIP.cmd())).thenReturn(Boolean.TRUE);
    return view;
  }

  @SuppressWarnings("unchecked")
  private static ImageViewerPlugin<ImageElement> container(List<ViewCanvas<ImageElement>> panes) {
    ImageViewerPlugin<ImageElement> container = mock(ImageViewerPlugin.class);
    when(container.viewId()).thenReturn("view-1"); // NON-NLS
    ImageViewerEventManager<ImageElement> events = mock(ImageViewerEventManager.class);
    ToggleButtonListener synchMode = mock(ToggleButtonListener.class);
    when(synchMode.isSelected()).thenReturn(true);
    when(events.getAction(ActionW.SYNCH_MODE)).thenReturn(Optional.of(synchMode));
    when(container.getEventManager()).thenReturn(events);
    when(container.getImagePanels()).thenReturn(panes);
    when(container.getSelectedViewCanvas()).thenReturn(panes.getLast());
    when(container.getLayoutModel())
        .thenReturn(new MigLayoutModel("1x2", "1x2", 1, 2, "canvas")); // NON-NLS
    when(container.getSynchView()).thenReturn(SynchView.DEFAULT_STACK);
    return container;
  }

  /** The viewer type is declared by the factory, which only the running application registers. */
  private static MockedStatic<GuiUtils> viewerTypeIs(String id) {
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn(id);
    UICore core = mock(UICore.class);
    when(core.getViewerFactory(any(SeriesViewer.class))).thenReturn(factory);
    MockedStatic<GuiUtils> gui = mockStatic(GuiUtils.class);
    gui.when(GuiUtils::getUICore).thenReturn(core);
    return gui;
  }

  @Test
  void paneStateNamesTheTargetAndCountsFromOne() {
    ViewCanvas<ImageElement> first = view(25);
    ImageViewerPlugin<ImageElement> container = container(List.of(first, view(0)));

    JsonObject state;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      state = ViewDisplayState.of(container, first);
    }
    JsonObject windowLevel = state.getJsonObject("windowLevel");
    assertAll(
        () -> assertEquals("view-1", state.getJsonObject("target").getString("view")),
        () -> assertEquals("2d", state.getJsonObject("target").getString("viewerType")),
        () -> assertEquals(1, state.getJsonObject("target").getInt("pane")),
        () -> assertEquals(1.25, state.getJsonNumber("zoom").doubleValue()),
        () -> assertEquals(1500.0, windowLevel.getJsonNumber("window").doubleValue()),
        () -> assertEquals(-600.0, windowLevel.getJsonNumber("level").doubleValue()),
        () -> assertEquals("Lung", windowLevel.getString("preset")),
        () -> assertEquals(90, state.getInt("rotation")),
        () -> assertTrue(state.getBoolean("flip")),
        () -> assertEquals(12.0, state.getJsonObject("pan").getJsonNumber("x").doubleValue()),
        // the 26th image of the series, which is index 25 in the code
        () -> assertEquals(26, state.getJsonObject("target").getInt("image")),
        () -> assertEquals(120, state.getJsonObject("target").getInt("imageCount")),
        () -> assertEquals(SERIES_UID, state.getJsonObject("target").getString("series")),
        () -> assertFalse(state.getJsonObject("target").containsKey("frame"), state.toString()),
        () -> assertEquals("1x2", state.getString("grid")),
        () -> assertEquals("stack", state.getString("sync")));
  }

  @Test
  void aMultiframeInstanceReportsItsFrame() {
    ViewCanvas<ImageElement> view = view(3);
    ImageElement image = mock(ImageElement.class);
    when(image.getTagValue(TagW.get("NumberOfFrames"))).thenReturn(60);
    when(image.getKey()).thenReturn(2);
    when(view.getImage()).thenReturn(image);
    JsonObject state;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      state = ViewDisplayState.of(container(List.of(view)), view);
    }
    JsonObject target = state.getJsonObject("target");
    assertAll(
        () -> assertEquals(4, target.getInt("image")),
        () -> assertEquals(3, target.getInt("frame")));
  }

  @Test
  void viewerStateListsEveryPaneAndTheSelectedOne() {
    ViewCanvas<ImageElement> first = view(0);
    ViewCanvas<ImageElement> second = view(4);
    JsonObject state;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      state = ViewDisplayState.ofViewer(container(List.of(first, second)));
    }

    var panes = state.getJsonArray("panes");
    assertAll(
        () -> assertEquals(2, panes.size()),
        () -> assertEquals(1, panes.getJsonObject(0).getInt("pane")),
        () -> assertFalse(panes.getJsonObject(0).getBoolean("selected")),
        () -> assertTrue(panes.getJsonObject(1).getBoolean("selected")),
        () -> assertEquals(5, panes.getJsonObject(1).getInt("image")),
        () -> assertEquals(SERIES_UID, panes.getJsonObject(1).getString("series")));
  }

  @Test
  void aPaneThatFollowsNoOtherReportsNoSynchronization() {
    ViewCanvas<ImageElement> first = view(0);
    SynchData unlinked = new SynchData(SynchData.Mode.STACK, new HashMap<>(), false);
    when(first.getActionValue(ActionW.SYNCH_LINK.cmd())).thenReturn(unlinked);
    JsonObject state;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      state = ViewDisplayState.of(container(List.of(first)), first);
    }
    assertEquals(ViewDisplayState.SYNCH_NONE, state.getString("sync"));
  }

  @Test
  void anEmptyPaneStillHasAState() {
    ViewCanvas<ImageElement> empty = view(0);
    when(empty.getImage()).thenReturn(null);
    when(empty.getSeries()).thenReturn(null);
    JsonObject state;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      state = ViewDisplayState.of(container(List.of(empty)), empty);
    }
    JsonObject target = state.getJsonObject("target");
    assertAll(
        () -> assertEquals(1, target.getInt("pane")),
        () -> assertFalse(target.containsKey("image"), state.toString()),
        () -> assertFalse(target.containsKey("series"), state.toString()),
        () -> assertTrue(ViewDisplayState.text(state).contains("zoom="), state.toString()));
  }

  @Test
  void textOutputCarriesTheSameValuesOnOneLine() {
    ViewCanvas<ImageElement> first = view(25);
    ImageViewerPlugin<ImageElement> container = container(List.of(first));
    String line;
    String viewer;
    try (MockedStatic<GuiUtils> gui = viewerTypeIs("2d")) {
      line = ViewDisplayState.text(ViewDisplayState.of(container, first));
      viewer = ViewDisplayState.textViewer(ViewDisplayState.ofViewer(container));
    }
    assertAll(
        () -> assertTrue(line.contains("pane=1"), line),
        () -> assertTrue(line.contains("window=1500.0"), line),
        () -> assertTrue(line.contains("preset=Lung"), line),
        () -> assertTrue(line.contains("image=26/120"), line),
        () -> assertTrue(line.contains("sync=stack"), line),
        () -> assertTrue(viewer.contains("panes=1"), viewer),
        () -> assertTrue(viewer.contains("selected=1"), viewer));
  }
}
