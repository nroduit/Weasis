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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.weasis.core.api.gui.model.ViewModel;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.ViewState.Context;
import org.weasis.core.ui.editor.image.ViewState.Position;
import org.weasis.core.ui.editor.image.ViewState.ViewData;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.opencv.op.lut.ColorLut;
import org.weasis.opencv.op.lut.LutShape;

class ViewStateTest {

  private static final ViewPresentation VIEW =
      new ViewPresentation(
          new Position(0.5, -42.0),
          new Context("CT", 0.7),
          ZoomType.CURRENT,
          1.5,
          -10.5,
          20.25,
          90,
          true,
          400.0,
          40.0,
          "lung",
          "SIGMOID",
          ColorLut.GRAY.getByteLut().name(),
          true,
          "slice-location",
          true);
  private static final ViewState SAMPLE =
      new ViewState(new ViewData("1.2.3", 12, "1.2.3.12", null), VIEW);

  @Test
  void jsonSeparatesTheDataFromTheView() {
    JsonObject json = SAMPLE.toJson();
    assertAll(
        () -> assertEquals(SAMPLE, ViewState.fromJson(json)),
        () -> assertEquals("1.2.3", json.getJsonObject("data").getString("series")),
        () -> assertEquals(12, json.getJsonObject("data").getInt("image")),
        () -> assertFalse(json.getJsonObject("view").toString().contains("1.2.3"), "no id"),
        () ->
            assertEquals(
                0.5,
                json.getJsonObject("view")
                    .getJsonObject("position")
                    .getJsonNumber("relative")
                    .doubleValue()),
        () ->
            assertEquals(
                "CT", json.getJsonObject("view").getJsonObject("context").getString("modality")),
        () -> assertEquals("current", json.getJsonObject("view").getString("zoomType")),
        () ->
            assertEquals(
                "lung",
                json.getJsonObject("view").getJsonObject("windowLevel").getString("preset")));
  }

  @Test
  void aTemplateIsAViewWithoutData() {
    JsonObject template = new ViewState(ViewData.NONE, VIEW).toJson();
    ViewState read = ViewState.fromJson(template);
    assertAll(
        () -> assertFalse(template.containsKey("data")),
        () -> assertEquals(VIEW, read.view()),
        () -> assertEquals(ViewData.NONE, read.data()));
  }

  @Test
  void theJustificationAndTheScaleGroupTravelInTheJson() {
    ViewPresentation justified =
        new ViewPresentation(
            Position.START,
            Context.UNKNOWN,
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            0,
            true,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            false,
            Justification.of("RIGHT", null), // NON-NLS
            "views"); // NON-NLS
    JsonObject json = justified.toJson();
    ViewPresentation read = ViewPresentation.fromJson(json);
    assertAll(
        () -> assertEquals(justified, read),
        () -> assertEquals(Justification.Horizontal.RIGHT, read.justify().horizontal()),
        () -> assertEquals("views", read.scaleGroup()), // NON-NLS
        () -> assertFalse(VIEW.toJson().containsKey("justify"), "centered: nothing written"),
        () ->
            assertNull(
                new ViewPresentation(
                        Position.START,
                        Context.UNKNOWN,
                        null,
                        0,
                        0,
                        0,
                        0,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        null,
                        false,
                        Justification.CENTER,
                        null)
                    .justify(),
                "a centered justification is no justification"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aProjectionTravelsInTheJsonAndIsAppliedThroughTheAdapter() {
    ViewPresentation projected =
        new ViewPresentation(
            Position.START,
            Context.UNKNOWN,
            ZoomType.BEST_FIT,
            0,
            0,
            0,
            0,
            false,
            null,
            null,
            null,
            null,
            null,
            false,
            null,
            false,
            null,
            null,
            new ViewState.Projection("max", 3, 7.0)); // NON-NLS
    JsonObject json = projected.toJson();
    ViewPresentation read = ViewPresentation.fromJson(json);

    ViewCanvas<ImageElement> pane = mock(ViewCanvas.class);
    ViewCanvas<ImageElement> mip = mock(ViewCanvas.class);
    ViewStateAdapter<ImageElement> adapter = mock(ViewStateAdapter.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    when(pane.getViewStateAdapter()).thenReturn(adapter);
    when(adapter.project(pane, projected.projection())).thenReturn(mip);
    when(mip.getSeries()).thenReturn(series);
    when(mip.getViewStateAdapter()).thenReturn(adapter);
    when(mip.getViewModel()).thenReturn(mock(ViewModel.class));
    when(mip.getDisplayOpManager()).thenReturn(mock(OpManager.class));
    new ViewState(ViewData.NONE, projected).apply(pane);

    assertAll(
        () -> assertEquals(projected, read),
        () -> assertEquals("MAX", read.projection().type(), "the type is an upper-case name"),
        () -> assertEquals("max", json.getJsonObject("projection").getString("type")),
        () -> assertEquals(3, read.projection().extent()),
        () -> assertEquals(7.0, read.projection().thickness()),
        () ->
            assertFalse(VIEW.toJson().containsKey("projection"), "no projection: nothing written"),
        () -> verify(mip, atLeastOnce()).setActionsInView(eq(ViewCanvas.ZOOM_TYPE_CMD), any()),
        () -> verify(pane, never()).setActionsInView(eq(ViewCanvas.ZOOM_TYPE_CMD), any()));
  }

  @Test
  void missingKeysTakeTheirDefaults() {
    ViewState state = ViewState.fromJson(Json.createObjectBuilder().build());
    assertAll(
        () -> assertEquals(ViewState.EMPTY, state),
        () -> assertEquals(ZoomType.BEST_FIT, state.view().zoomType()),
        () -> assertNull(state.view().window()),
        () -> assertEquals(Position.START, state.view().position()));
  }

  @Test
  void anEmptyPaneCapturesAsEmpty() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    assertEquals(ViewState.EMPTY, ViewState.capture(view));
  }

  @Test
  @SuppressWarnings("unchecked")
  void captureReadsTheDataTheFramingTheWindowAndThePosition() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    ImageElement image = mock(ImageElement.class);
    OpManager manager = mock(OpManager.class);
    ViewModel model = mock(ViewModel.class);
    TagW tag = TagW.get("SeriesInstanceUID");
    when(view.getSeries()).thenReturn(series);
    when(view.getImage()).thenReturn(image);
    when(view.getFrameIndex()).thenReturn(4);
    when(view.getViewStateAdapter()).thenReturn(ViewStateAdapter.none());
    when(view.getDisplayOpManager()).thenReturn(manager);
    when(view.getViewModel()).thenReturn(model);
    when(view.getActionValue(ViewCanvas.ZOOM_TYPE_CMD)).thenReturn(ZoomType.REAL);
    when(view.getActionValue(ActionW.ZOOM.cmd())).thenReturn(0.75);
    when(view.getActionValue(ActionW.ROTATION.cmd())).thenReturn(270);
    when(view.getActionValue(ActionW.FLIP.cmd())).thenReturn(Boolean.TRUE);
    when(series.getTagID()).thenReturn(tag);
    when(series.getTagValue(tag)).thenReturn("1.2.840.1");
    when(series.size(any())).thenReturn(9);
    when(image.getPixelSize()).thenReturn(0.5);
    when(model.getViewScale()).thenReturn(0.75);
    when(model.getModelOffsetX()).thenReturn(3.0);
    when(model.getModelOffsetY()).thenReturn(4.0);
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.WINDOW.cmd(), Double.class))
        .thenReturn(Optional.of(1500.0));
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.LEVEL.cmd(), Double.class))
        .thenReturn(Optional.of(-600.0));
    when(manager.getParamValue(WindowOp.OP_NAME, ActionW.LUT_SHAPE.cmd(), LutShape.class))
        .thenReturn(Optional.of(LutShape.LOG));
    when(manager.getParamValue(any(), any())).thenReturn(Optional.empty());
    when(manager.getParamValue(eq(WindowOp.OP_NAME), eq(ActionW.INVERT_LUT.cmd()), any()))
        .thenReturn(Optional.of(Boolean.TRUE));

    ViewState state = ViewState.capture(view);
    ViewPresentation v = state.view();
    assertAll(
        () -> assertEquals("1.2.840.1", state.data().series()),
        () -> assertEquals(5, state.data().image()),
        () -> assertEquals(0.5, v.position().relative(), "index 4 of 9"),
        () -> assertEquals(0.5, v.context().pixelSize()),
        () -> assertEquals(ZoomType.REAL, v.zoomType()),
        () -> assertEquals(0.75, v.zoom()),
        () -> assertEquals(3.0, v.panX()),
        () -> assertEquals(270, v.rotation()),
        () -> assertTrue(v.flip()),
        () -> assertEquals(1500.0, v.window()),
        () -> assertEquals(-600.0, v.level()),
        () -> assertNull(v.preset(), "no adapter, no preset id"),
        () -> assertEquals("LOG", v.lutShape()),
        () -> assertTrue(v.invert()),
        () -> assertNull(v.colorMap()));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aSessionRestoresExactlyThroughSynchEventsTheViewIssuesItself() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    ViewModel model = mock(ViewModel.class);
    when(view.getSeries()).thenReturn(series);
    when(view.getViewModel()).thenReturn(model);
    when(view.getViewStateAdapter()).thenReturn(ViewStateAdapter.none());

    SAMPLE.apply(view);

    List<Map<String, Object>> sent = sent(view, 4);
    Map<String, Object> order = sent.get(0);
    Map<String, Object> framing = sent.get(1);
    Map<String, Object> rendering = sent.get(2);
    Map<String, Object> values = sent.get(3);
    assertAll(
        () -> assertEquals(Boolean.TRUE, order.get(ActionW.INVERSE_STACK.cmd())),
        () -> assertFalse(order.containsKey(ActionW.SORT_STACK.cmd()), "no adapter, no order"),
        () -> assertEquals(1.5, framing.get(ActionW.ZOOM.cmd()), "explicit scale kept"),
        () -> assertEquals(90, framing.get(ActionW.ROTATION.cmd())),
        () -> assertEquals(Boolean.TRUE, framing.get(ActionW.FLIP.cmd())),
        () -> assertEquals(ColorLut.GRAY.getByteLut(), rendering.get(ActionW.LUT.cmd())),
        () -> assertEquals(Boolean.TRUE, rendering.get(ActionW.INVERT_LUT.cmd())),
        () -> assertFalse(rendering.containsKey(ActionW.PRESET.cmd()), "preset unknown here"),
        () -> assertEquals(400.0, values.get(ActionW.WINDOW.cmd())),
        () -> assertEquals(40.0, values.get(ActionW.LEVEL.cmd())),
        () -> assertEquals(LutShape.SIGMOID, values.get(ActionW.LUT_SHAPE.cmd())));
    verify(view).setActionsInView(ViewCanvas.ZOOM_TYPE_CMD, ZoomType.CURRENT);
    verify(model).setModelOffset(-10.5, 20.25);
    verify(series).getMedia(eq(11), any(), any());
  }

  /** Another series with another pixel size and modality: the adapter keeps what transfers. */
  @Test
  @SuppressWarnings("unchecked")
  void aTemplateOnOtherDataKeepsOnlyWhatTransfers() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    ImageElement image = mock(ImageElement.class);
    ImageElement middle = mock(ImageElement.class);
    ViewModel model = mock(ViewModel.class);
    when(view.getSeries()).thenReturn(series);
    when(view.getImage()).thenReturn(image);
    when(view.getViewModel()).thenReturn(model);
    when(view.getViewStateAdapter()).thenReturn(ViewStateAdapter.none());
    when(image.getPixelSize()).thenReturn(0.2); // MR-like, not the 0.7 of the capture
    when(series.size(any())).thenReturn(21);
    when(series.getMedia(eq(10), any(), any())).thenReturn(middle);

    VIEW.applyTo(view, null);

    List<Map<String, Object>> sent = sent(view, 4);
    Map<String, Object> framing = sent.get(1);
    Map<String, Object> values = sent.get(3);
    PropertyChangeEvent cine = events(view).get(1);
    assertAll(
        () ->
            assertEquals(
                middle,
                ((SynchCineEvent) cine.getNewValue()).getMedia(),
                "image 11 of 21: relative position 0.5"),
        () -> assertEquals(0.0, framing.get(ActionW.ZOOM.cmd()), "scale dropped: other pixel size"),
        () -> assertEquals(90, framing.get(ActionW.ROTATION.cmd())),
        () ->
            assertFalse(values.containsKey(ActionW.WINDOW.cmd()), "values dropped: other modality"),
        () -> assertEquals(LutShape.SIGMOID, values.get(ActionW.LUT_SHAPE.cmd())));
    verify(model, never()).setModelOffset(anyInt(), anyInt());
    verify(series, never()).getMedia(eq(11), any(), any());
  }

  /** A presentation state sets the framing and rendering: a template only navigates the pane. */
  @Test
  @SuppressWarnings("unchecked")
  void aTemplateLeavesThePresentationOwnedByThePane() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    ViewModel model = mock(ViewModel.class);
    ViewStateAdapter<ImageElement> adapter = mock(ViewStateAdapter.class);
    when(view.getSeries()).thenReturn(series);
    when(view.getViewModel()).thenReturn(model);
    when(view.getViewStateAdapter()).thenReturn(adapter);
    when(adapter.ownsPresentation(view)).thenReturn(true);

    VIEW.applyTo(view, null);

    List<Map<String, Object>> sent = sent(view, 1);
    assertAll(
        () -> assertEquals(Boolean.TRUE, sent.getFirst().get(ActionW.INVERSE_STACK.cmd())),
        () -> assertFalse(sent.getFirst().containsKey(ActionW.ROTATION.cmd())));
    verify(view, never()).setActionsInView(eq(ViewCanvas.ZOOM_TYPE_CMD), any());
  }

  /** The blended series is data found through the resolver; its parameters are the adapter's. */
  @Test
  @SuppressWarnings("unchecked")
  void aBlendTravelsWithItsOverlayAndComesBackThroughTheAdapter() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    MediaSeries<ImageElement> series = mock(MediaSeries.class);
    MediaSeries<ImageElement> overlay = mock(MediaSeries.class);
    ViewModel model = mock(ViewModel.class);
    ViewStateAdapter<ImageElement> adapter = mock(ViewStateAdapter.class);
    JsonObject params = Json.createObjectBuilder().add("overlayOpacity", 0.4).build(); // NON-NLS
    when(view.getSeries()).thenReturn(series);
    when(view.getViewModel()).thenReturn(model);
    when(view.getViewStateAdapter()).thenReturn(adapter);
    when(view.getDisplayOpManager()).thenReturn(mock(OpManager.class));
    when(adapter.blend(view))
        .thenReturn(Optional.of(new ViewState.Blend("1.2.9", params))); // NON-NLS

    ViewState captured = ViewState.capture(view);
    ViewState read = ViewState.fromJson(captured.toJson());
    read.apply(view); // no resolver: the overlay stays unknown
    read.apply(view, id -> "1.2.9".equals(id) ? Optional.of(overlay) : Optional.empty()); // NON-NLS

    assertAll(
        () -> assertEquals("1.2.9", read.blend().overlay()), // NON-NLS
        () ->
            assertEquals(
                0.4,
                read.blend().params().getJsonNumber("overlayOpacity").doubleValue()), // NON-NLS
        () ->
            assertEquals(
                "1.2.9", captured.toJson().getJsonObject("blend").getString("overlay")), // NON-NLS
        () -> assertNull(ViewState.fromJson(Json.createObjectBuilder().build()).blend()));
    verify(adapter).blend(view, overlay, params);
    verify(adapter, never()).blend(eq(view), eq(series), any());
  }

  @Test
  void applyOnAnEmptyPaneDoesNothing() {
    ViewCanvas<ImageElement> view = mock(ViewCanvas.class);
    SAMPLE.apply(view);
    verify(view, never()).propertyChange(any());
  }

  private static List<PropertyChangeEvent> events(ViewCanvas<ImageElement> view) {
    ArgumentCaptor<PropertyChangeEvent> events = ArgumentCaptor.forClass(PropertyChangeEvent.class);
    verify(view, org.mockito.Mockito.atLeast(1)).propertyChange(events.capture());
    return events.getAllValues();
  }

  /** The maps of the SynchEvents sent, cine events excluded, all issued by the view itself. */
  private static List<Map<String, Object>> sent(ViewCanvas<ImageElement> view, int expected) {
    List<Map<String, Object>> maps =
        events(view).stream()
            .peek(e -> assertEquals(ActionW.SYNCH.cmd(), e.getPropertyName()))
            .map(e -> (SynchEvent) e.getNewValue())
            .peek(s -> assertEquals(view, s.getView()))
            .filter(s -> !(s instanceof SynchCineEvent))
            .map(SynchEvent::getEvents)
            .toList();
    assertEquals(expected, maps.size());
    return maps;
  }

  @Test
  @SuppressWarnings("unchecked")
  void aRestoredPositionIsSoughtAgainWhileImagesArrive() {
    java.util.Map<String, Object> actions = new java.util.HashMap<>();
    ViewCanvas<ImageElement> canvas = org.mockito.Mockito.mock(ViewCanvas.class);
    org.mockito.Mockito.doAnswer(
            inv -> {
              actions.put(inv.getArgument(0), inv.getArgument(1));
              return null;
            })
        .when(canvas)
        .setActionsInView(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.when(canvas.getActionValue(org.mockito.ArgumentMatchers.anyString()))
        .thenAnswer(inv -> actions.get(inv.getArgument(0)));
    org.mockito.Mockito.when(canvas.getViewStateAdapter())
        .thenReturn(
            org.mockito.Mockito.mock(
                ViewStateAdapter.class,
                org.mockito.Mockito.withSettings()
                    .defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS)));
    org.weasis.core.api.media.data.MediaSeries<ImageElement> series =
        org.mockito.Mockito.mock(org.weasis.core.api.media.data.MediaSeries.class);
    org.mockito.Mockito.when(canvas.getSeries()).thenReturn(series);
    ImageElement first = org.mockito.Mockito.mock(ImageElement.class);
    ImageElement third = org.mockito.Mockito.mock(ImageElement.class);
    org.mockito.Mockito.when(canvas.getImage()).thenReturn(first);
    List<ImageElement> fired = new java.util.ArrayList<>();
    org.mockito.Mockito.doAnswer(
            inv -> {
              java.beans.PropertyChangeEvent evt = inv.getArgument(0);
              if (evt.getNewValue() instanceof SynchCineEvent cine) {
                fired.add((ImageElement) cine.getMedia());
              }
              return null;
            })
        .when(canvas)
        .propertyChange(org.mockito.ArgumentMatchers.any());

    // two images loaded of a series restored on its third one: nothing to show yet
    ViewData data = new ViewData("1.2.3", 3, "1.2.3.3", null);
    ViewPresentation.DEFAULT.applyTo(canvas, data, true);
    assertAll(
        () -> assertTrue(fired.isEmpty()),
        () -> assertTrue(actions.get(ViewState.PENDING_CMD) != null, "the restore is pending"));

    // the third image arrives: the pane moves to it, the restore stays pending until the end
    org.mockito.Mockito.when(series.getMedia(2, null, null)).thenReturn(third);
    ViewState.repositionPending(canvas, false);
    assertEquals(List.of(third), fired);
    assertTrue(actions.get(ViewState.PENDING_CMD) != null);

    // the loading ends on the same image: nothing fired, nothing pending
    org.mockito.Mockito.when(canvas.getImage()).thenReturn(third);
    ViewState.repositionPending(canvas, true);
    assertAll(
        () -> assertEquals(1, fired.size()),
        () -> assertTrue(actions.get(ViewState.PENDING_CMD) == null));

    // a user who moved elsewhere is not pulled back
    ViewPresentation.DEFAULT.applyTo(canvas, data, true);
    org.mockito.Mockito.when(canvas.getImage()).thenReturn(first);
    ViewState.repositionPending(canvas, false);
    assertAll(
        () -> assertEquals(1, fired.size(), "nothing fired"),
        () -> assertTrue(actions.get(ViewState.PENDING_CMD) == null, "dropped"));
  }
}
