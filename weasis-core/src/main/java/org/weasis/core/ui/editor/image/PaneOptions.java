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

import java.util.List;
import java.util.Optional;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.PaneIndex;
import org.weasis.core.api.command.ViewId;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import picocli.CommandLine.Option;

/**
 * The {@code --view} and {@code --pane} options a verb takes to name a pane, and their
 * resolution to the canvas: the selected view and pane when none is given. A mixin for any
 * namespace, of the core or of a plugin.
 */
public class PaneOptions {
  @Option(
      names = "--view",
      paramLabel = "<view-id>",
      description = "View id from 'workspace:list' (default: the selected view).")
  ViewId viewId;

  @Option(
      names = "--pane",
      paramLabel = "<pane-index>",
      description = "Pane index in the grid of the view, from 1 (default: the selected pane).")
  PaneIndex pane;

  /** A resolved pane with the view that holds it. */
  public record Pane(ImageViewerPlugin<?> container, ViewCanvas<?> view) {}

  /** The pane, or empty when nothing is selected and no viewer was named. */
  public Optional<ViewCanvas<?>> resolve() {
    return resolvePane().map(Pane::view);
  }

  /** The pane with its view, or empty when nothing is selected and no viewer was named. */
  public Optional<Pane> resolvePane() {
    ImageViewerPlugin<?> viewer;
    if (viewId == null) {
      Optional<ViewerPlugin<?>> selected = GuiUtils.getUICore().getSelectedPlugin();
      if (selected.isEmpty() || !(selected.get() instanceof ImageViewerPlugin<?> v)) {
        return Optional.empty();
      }
      viewer = v;
    } else {
      WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
      viewer =
          Optional.ofNullable(manager)
              .flatMap(m -> m.findView(viewId.value()))
              .filter(ImageViewerPlugin.class::isInstance)
              .map(ImageViewerPlugin.class::cast)
              .orElseThrow(
                  () ->
                      CommandFailure.unknownHandle(
                          "Unknown image viewer id: " + viewId, "workspace:list")); // NON-NLS
    }
    if (pane == null) {
      ViewCanvas<?> selected = viewer.getSelectedViewCanvas();
      return selected == null ? Optional.empty() : Optional.of(new Pane(viewer, selected));
    }
    List<? extends ViewCanvas<?>> panels = viewer.getImagePanels();
    if (pane.value() > panels.size()) {
      throw new IllegalArgumentException(
          "Pane index out of range: " + pane + " (" + panels.size() + " panes)");
    }
    return Optional.of(new Pane(viewer, panels.get(pane.toOffset())));
  }
}
