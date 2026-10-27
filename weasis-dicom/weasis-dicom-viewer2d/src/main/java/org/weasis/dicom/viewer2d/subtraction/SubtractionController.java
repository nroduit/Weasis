/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.subtraction;

import java.util.List;
import java.util.Optional;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.explorer.exp.ExplorerTask;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.subtraction.SubtractionService.Derived;

/**
 * Glue between the Swing surfaces (image tool section, explorer menu, view button) and the
 * subtraction: enters and leaves the live preview of a pane, pushes the mask and weight of the
 * section to it, builds the derived series off the EDT with the explorer's loading indicator and
 * opens the result in the selected pane.
 */
public final class SubtractionController {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubtractionController.class);

  private SubtractionController() {}

  /** The masks the selected view's series can take, none for an empty or derived view. */
  public static List<MediaSeries<DicomImageElement>> compatibleMasks(
      ViewCanvas<DicomImageElement> view) {
    if (view == null || view.getSeries() == null) {
      return List.of();
    }
    return SubtractionService.compatibleMasks(view.getSeries());
  }

  /** The preview the selected pane shows, when it shows one. */
  public static Optional<SubtractionView> selectedPreview() {
    return EventManager.getInstance().getSelectedViewPane() instanceof SubtractionView view
        ? Optional.of(view)
        : Optional.empty();
  }

  /** Turns the live preview of the selected pane on or off, with the section's mask and weight. */
  public static void setPreview(boolean on) {
    EventManager manager = EventManager.getInstance();
    ViewCanvas<DicomImageElement> view = manager.getSelectedViewPane();
    if (on) {
      if (view instanceof SubtractionView || view == null || view.getSeries() == null) {
        return;
      }
      selectedMask()
          .ifPresent(
              mask ->
                  enterPreview(manager.getSelectedView2dContainer(), view, mask, selectedFactor()));
    } else if (view instanceof SubtractionView preview) {
      preview.exitPreview();
    }
  }

  /** Replaces a plain pane by the preview of its series minus the mask. */
  public static SubtractionView enterPreview(
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> view,
      MediaSeries<DicomImageElement> mask,
      double factor) {
    if (!(container instanceof View2dContainer)) {
      throw new IllegalStateException("The preview needs a 2D viewer");
    }
    if (!SubtractionCompatibility.isCompatible(view.getSeries(), mask)) {
      throw new IllegalStateException("The mask cannot be subtracted from the series shown");
    }
    return SubtractionView.enter(container, view, mask, factor);
  }

  /** Pushes the mask and weight of the section to the preview of the selected pane, if any. */
  public static void updatePreview() {
    selectedPreview()
        .ifPresent(
            preview ->
                selectedMask().ifPresent(mask -> preview.setParameters(mask, selectedFactor())));
  }

  /** Builds what the selected pane previews, else the series of the pane minus the chosen mask. */
  public static void subtractSelected() {
    Optional<SubtractionView> preview = selectedPreview();
    if (preview.isPresent()) {
      preview.get().buildSeries();
      return;
    }
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null || view.getSeries() == null) {
      return;
    }
    selectedMask().ifPresent(mask -> subtract(view.getSeries(), mask, selectedFactor()));
  }

  @SuppressWarnings("unchecked")
  private static Optional<MediaSeries<DicomImageElement>> selectedMask() {
    return EventManager.getInstance()
        .getAction(SubtractionAction.MASK)
        .map(a -> a.getSelectedItem())
        .filter(MediaSeries.class::isInstance)
        .map(m -> (MediaSeries<DicomImageElement>) m);
  }

  private static double selectedFactor() {
    return EventManager.getInstance()
        .getAction(SubtractionAction.FACTOR)
        .map(a -> a.getSliderValue() / 100.0)
        .orElse(1.0);
  }

  /** Builds the series minus the mask in the background and opens it when done. */
  public static void subtract(
      MediaSeries<DicomImageElement> series, MediaSeries<DicomImageElement> mask, double factor) {
    SubtractionSpec spec =
        new SubtractionSpec(
            SubtractionCommands.uidOf(series),
            SubtractionCommands.uidOf(mask),
            factor,
            0.0,
            false,
            false);
    DataExplorerModel model =
        series.getTagValue(TagW.ExplorerModel) instanceof DataExplorerModel m ? m : null;
    ExplorerTask<Derived, Void> task =
        new ExplorerTask<>(Messages.getString("SubtractionTool.build"), false) {
          @Override
          protected Derived doInBackground() throws Exception {
            fire(ObservableEvent.BasicAction.LOADING_START);
            return SubtractionService.create(spec, series, mask);
          }

          @Override
          protected void done() {
            fire(ObservableEvent.BasicAction.LOADING_STOP);
            try {
              SubtractionService.open(get());
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              Throwable cause = e.getCause() == null ? e : e.getCause();
              LOGGER.error("Subtraction failed", cause);
              JOptionPane.showMessageDialog(
                  GuiUtils.getUICore().getApplicationWindow(),
                  Messages.getString("SubtractionTool.failed") + "\n" + cause.getMessage(),
                  Messages.getString("SubtractionTool.title"),
                  JOptionPane.ERROR_MESSAGE);
            }
          }

          private void fire(ObservableEvent.BasicAction action) {
            if (model != null) {
              GuiExecutor.execute(
                  () -> model.firePropertyChange(new ObservableEvent(action, model, null, this)));
            }
          }
        };
    task.execute();
  }
}
