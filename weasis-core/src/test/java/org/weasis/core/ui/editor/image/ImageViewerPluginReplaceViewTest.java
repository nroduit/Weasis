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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.stubbing.Answer;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.UICore;

@DisplayName("ImageViewerPlugin.replaceView")
class ImageViewerPluginReplaceViewTest {

  @SuppressWarnings("unchecked")
  private static ViewCanvas<ImageElement> canvas() {
    ViewCanvas<ImageElement> canvas = mock(ViewCanvas.class, RETURNS_DEEP_STUBS);
    when(canvas.getJComponent()).thenReturn(new JPanel());
    when(canvas.getSeries()).thenReturn(null);
    return canvas;
  }

  /** A real plugin on the default 1x1 layout, whose single cell hosts a viewport pane. */
  @SuppressWarnings("unchecked")
  private static ImageViewerPlugin<ImageElement> plugin(ViewCanvas<ImageElement> initial) {
    Answer<Object> answer =
        invocation ->
            switch (invocation.getMethod().getName()) {
              case "isViewType" -> invocation.getArgument(0) == ImageViewerPlugin.VIEWPORT_CLASS;
              case "createDefaultView" -> initial;
              default -> CALLS_REAL_METHODS.answer(invocation);
            };
    // The viewer constructor registers itself on UICore, which cannot be built in a test
    try (MockedStatic<GuiUtils> gui = mockStatic(GuiUtils.class, CALLS_REAL_METHODS)) {
      gui.when(GuiUtils::getUICore).thenReturn(mock(UICore.class, RETURNS_DEEP_STUBS));
      ImageViewerPlugin<ImageElement> plugin =
          mock(
              ImageViewerPlugin.class,
              withSettings()
                  .useConstructor(
                      mock(ImageViewerEventManager.class, RETURNS_DEEP_STUBS),
                      ImageViewerPlugin.VIEWS_1x1,
                      "uid", // NON-NLS
                      "name", // NON-NLS
                      null,
                      null)
                  .defaultAnswer(answer));
      // The answer is not active yet while the constructor runs: its layout stays empty
      plugin.setLayoutModel(ImageViewerPlugin.VIEWS_1x1);
      return plugin;
    }
  }

  @Test
  @DisplayName("replaces the canvas hosted by a viewport pane")
  void replacesCanvasOfPane() {
    ViewCanvas<ImageElement> oldView = canvas();
    ViewCanvas<ImageElement> newView = canvas();
    ImageViewerPlugin<ImageElement> plugin = plugin(oldView);
    ViewportPane<ImageElement> pane = plugin.getViewportPanes().getFirst();
    plugin.selectedPane = pane;

    plugin.replaceView(oldView, newView);

    assertAll(
        () -> assertEquals(List.of(pane), plugin.getViewportPanes()),
        () -> assertEquals(List.of(newView), pane.getAllViewCanvases()),
        () -> assertSame(newView, pane.getSelectedCanvas()),
        () -> assertSame(newView, plugin.getSelectedViewCanvas()),
        () -> assertEquals(1, pane.getComponentCount()),
        () -> assertSame(newView.getJComponent(), pane.getComponent(0)),
        () -> assertSame(pane, plugin.getOwningPane(newView)));
    verify(oldView).disposeView();
    verify(newView).setSelected(true);
  }

  @Test
  @DisplayName("leaves the layout untouched when the old view is not in it")
  void ignoresUnknownView() {
    ViewCanvas<ImageElement> hosted = canvas();
    ViewCanvas<ImageElement> unknown = canvas();
    ViewCanvas<ImageElement> newView = canvas();
    ImageViewerPlugin<ImageElement> plugin = plugin(hosted);
    ViewportPane<ImageElement> pane = plugin.getViewportPanes().getFirst();

    plugin.replaceView(unknown, newView);

    assertEquals(List.of(hosted), pane.getAllViewCanvases());
    assertFalse(plugin.getAllCanvasesIncludingPanes().contains(newView));
    verify(unknown, never()).disposeView();
  }
}
