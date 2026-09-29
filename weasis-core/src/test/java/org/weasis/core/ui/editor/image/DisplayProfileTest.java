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
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.layer.imp.RenderedImageLayer;

class DisplayProfileTest {

  @Test
  void readsWhatTheViewShows() {
    ViewCanvas<?> view = view(Boolean.FALSE);
    OpManager ops = view.getDisplayOpManager();
    when(ops.getParamValue(DisplayProfile.OVERLAY_OP, DisplayProfile.OVERLAY_SHOW, Boolean.class))
        .thenReturn(Optional.of(Boolean.TRUE));
    when(ops.getParamValue(WindowOp.OP_NAME, ActionW.IMAGE_PIX_PADDING.cmd(), Boolean.class))
        .thenReturn(Optional.of(Boolean.FALSE));

    DisplayProfile profile = DisplayProfile.of(view);

    assertAll(
        () -> assertTrue(profile.image()),
        () -> assertTrue(profile.overlay()),
        () -> assertFalse(profile.shutter(), "an unset shutter is off"),
        () -> assertFalse(profile.pixelPadding()),
        () -> assertTrue(profile.annotations()),
        () -> assertEquals(Set.of(LayerItem.SCALE, LayerItem.LUT), profile.annotationItems()),
        () -> assertTrue(profile.drawings(), "unset drawings are shown"),
        () -> assertFalse(profile.crosslines()));
  }

  @Test
  void imageOnlyKeepsTheImageOptionsAndDropsTheRest() {
    DisplayProfile full =
        new DisplayProfile(true, true, false, true, true, EnumSet.of(LayerItem.SCALE), true, true);

    DisplayProfile image = full.imageOnly();

    assertAll(
        () -> assertTrue(image.image()),
        () -> assertTrue(image.overlay()),
        () -> assertFalse(image.shutter()),
        () -> assertTrue(image.pixelPadding()),
        () -> assertFalse(image.annotations()),
        () -> assertFalse(image.drawings()),
        () -> assertFalse(image.crosslines()));
  }

  @Test
  void annotationItemsAreCopied() {
    Set<LayerItem> items = EnumSet.of(LayerItem.SCALE);
    DisplayProfile profile = new DisplayProfile(true, false, false, true, true, items, true, true);
    items.add(LayerItem.LUT);

    assertEquals(Set.of(LayerItem.SCALE), profile.annotationItems());
    assertTrue(
        new DisplayProfile(true, false, false, true, true, Set.of(), true, true)
            .annotationItems()
            .isEmpty());
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
  void liveViewOffersItsOwnItemsWithoutImageOptions() {
    ViewCanvas view = mock(ViewCanvas.class, withSettings().extraInterfaces(LiveCaptureView.class));
    when(((LiveCaptureView) view).getCaptureItems())
        .thenReturn(List.of(LayerItem.ANNOTATIONS, LayerItem.ZOOM));
    LayerAnnotation info = mock(LayerAnnotation.class);
    when(info.getVisible()).thenReturn(Boolean.TRUE);
    when(info.getDisplayPreferences(any())).thenReturn(true);
    when(view.getInfoLayer()).thenReturn(info);

    DisplayProfile profile = DisplayProfile.of(view);

    assertAll(
        () -> assertTrue(DisplayProfile.supports(view)),
        () ->
            assertEquals(
                List.of(LayerItem.ANNOTATIONS, LayerItem.ZOOM), DisplayProfile.itemsFor(view)),
        () ->
            assertEquals(Set.of(LayerItem.ANNOTATIONS, LayerItem.ZOOM), profile.annotationItems()),
        () -> assertTrue(profile.image(), "no image layer reads as shown"),
        () -> assertTrue(profile.pixelPadding(), "no operations read as their defaults"),
        () -> assertFalse(profile.overlay()),
        () -> assertFalse(DisplayProfile.hasDicomImageOptions(view)));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static ViewCanvas<?> view(Boolean crosslines) {
    ViewCanvas view = mock(ViewCanvas.class);
    OpManager ops = mock(OpManager.class);
    when(ops.getParamValue(any(), any(), any())).thenReturn(Optional.empty());
    when(view.getDisplayOpManager()).thenReturn(ops);
    RenderedImageLayer imageLayer = mock(RenderedImageLayer.class);
    when(imageLayer.getVisible()).thenReturn(Boolean.TRUE);
    when(view.getImageLayer()).thenReturn(imageLayer);
    LayerAnnotation info = mock(LayerAnnotation.class);
    when(info.getVisible()).thenReturn(Boolean.TRUE);
    when(info.getDisplayPreferences(any()))
        .thenAnswer(a -> Set.of(LayerItem.SCALE, LayerItem.LUT).contains(a.getArgument(0)));
    when(view.getInfoLayer()).thenReturn(info);
    when(view.getActionValue(LayerType.CROSSLINES.name())).thenReturn(crosslines);
    return view;
  }
}
