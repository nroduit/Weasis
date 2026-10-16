/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.explorer.model.TreeModelNode;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot.ViewDescriptor;
import org.weasis.core.ui.editor.image.ViewerPlugin;

class ViewerRestorerTest {

  private static final JsonObject STATE =
      Json.createObjectBuilder()
          .add("name", "CT") // NON-NLS
          .add("series", Json.createArrayBuilder().add("1.2.3")) // NON-NLS
          .build();

  private final List<ViewerPlugin<?>> registered = new ArrayList<>();

  private ViewerRestorer restorer(
      List<WorkspaceView> pool, SeriesViewerFactory factory, DataExplorerModel model) {
    return new ViewerRestorer(
        pool,
        () -> factory == null ? List.of() : List.of(factory),
        () -> model == null ? List.of() : List.of(model),
        registered::add);
  }

  @Test
  void anOpenViewIsReusedWithoutAnyFactory() {
    WorkspaceView live = mock(WorkspaceView.class);
    when(live.viewId()).thenReturn("v1");
    Optional<WorkspaceView> view =
        restorer(List.of(live), null, null).recreate(new ViewDescriptor("v1", "2d", STATE));
    assertSame(live, view.orElseThrow());
  }

  @Test
  void aTypeWithoutFactoryOrASeriesNoLongerLoadedIsSkipped() {
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("2d");
    DataExplorerModel empty = mock(DataExplorerModel.class);
    when(empty.findSeries(any())).thenReturn(Optional.empty());
    assertAll(
        () ->
            assertTrue(
                restorer(List.of(), null, empty)
                    .recreate(new ViewDescriptor("v2", "2d", STATE))
                    .isEmpty()),
        () ->
            assertTrue(
                restorer(List.of(), factory, empty)
                    .recreate(new ViewDescriptor("v2", "2d", STATE))
                    .isEmpty()),
        () ->
            assertTrue(
                restorer(List.of(), factory, empty)
                    .recreate(new ViewDescriptor("v2", "mpr", STATE))
                    .isEmpty()),
        () -> assertTrue(registered.isEmpty()));
    verify(factory, never()).createSeriesViewer(any(), any());
  }

  @Test
  @SuppressWarnings("unchecked")
  void aViewerThatCannotTakeItsStateIsClosedAndLeftOut() {
    MediaSeries<MediaElement> series = mock(MediaSeries.class);
    DataExplorerModel model = mock(DataExplorerModel.class);
    when(model.findSeries("1.2.3")).thenReturn(Optional.of(series));
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("mpr");
    when(factory.canReadSeries(series)).thenReturn(true);
    ViewerPlugin<MediaElement> plugin = mock(ViewerPlugin.class);
    doReturn(plugin).when(factory).createSeriesViewer(any(), eq(model));

    Optional<WorkspaceView> view =
        restorer(List.of(), factory, model).recreate(new ViewDescriptor("v4", "mpr", STATE));

    assertTrue(view.isEmpty());
    verify(plugin).close();
  }

  @Test
  @SuppressWarnings("unchecked")
  void aViewerOfATreeModelGetsItsGroupBack() {
    MediaSeries<MediaElement> series = mock(MediaSeries.class);
    MediaSeriesGroup patient = mock(MediaSeriesGroup.class);
    TreeModelNode node = mock(TreeModelNode.class);
    DataExplorerModel model =
        mock(DataExplorerModel.class, withSettings().extraInterfaces(TreeModel.class));
    when(model.findSeries("1.2.3")).thenReturn(Optional.of(series));
    when(model.getTreeModelNodeForNewPlugin()).thenReturn(node);
    when(((TreeModel) model).getParent(series, node)).thenReturn(patient);
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("2d");
    when(factory.canReadSeries(series)).thenReturn(true);
    ViewerPlugin<MediaElement> plugin = mock(ViewerPlugin.class);
    when(plugin.restoreState(any(), any())).thenReturn(true);
    doReturn(plugin).when(factory).createSeriesViewer(any(), eq(model));

    restorer(List.of(), factory, model).recreate(new ViewDescriptor("v5", "2d", STATE));

    verify(plugin).setGroupID(patient);
  }

  @Test
  @SuppressWarnings("unchecked")
  void aClosedViewerIsCreatedAgainWithItsIdAndGivenItsState() {
    MediaSeries<MediaElement> series = mock(MediaSeries.class);
    DataExplorerModel model = mock(DataExplorerModel.class);
    when(model.findSeries("1.2.3")).thenReturn(Optional.of(series));
    SeriesViewerFactory factory = mock(SeriesViewerFactory.class);
    when(factory.getViewerTypeId()).thenReturn("2d");
    when(factory.canReadSeries(series)).thenReturn(true);
    ViewerPlugin<MediaElement> plugin = mock(ViewerPlugin.class);
    when(plugin.viewId()).thenReturn("v3");
    when(plugin.restoreState(any(), any())).thenReturn(true);
    ArgumentCaptor<ViewerOpenOptions> options = ArgumentCaptor.forClass(ViewerOpenOptions.class);
    doReturn(plugin).when(factory).createSeriesViewer(options.capture(), eq(model));

    Optional<WorkspaceView> view =
        restorer(List.of(), factory, model).recreate(new ViewDescriptor("v3", "2d", STATE));

    ArgumentCaptor<SeriesResolver> resolver = ArgumentCaptor.forClass(SeriesResolver.class);
    verify(plugin).restoreState(eq(STATE), resolver.capture());
    assertAll(
        () -> assertSame(plugin, view.orElseThrow()),
        () -> assertEquals("v3", options.getValue().uid(), "the viewer keeps its id"),
        () -> assertEquals(List.of(plugin), registered),
        () -> assertSame(series, resolver.getValue().resolve("1.2.3").orElseThrow()),
        () -> assertTrue(resolver.getValue().resolve("9.9").isEmpty()));
  }
}
