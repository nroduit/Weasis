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
import org.weasis.dicom.viewer2d.subtraction.SubtractionSpec.Dsa;

/**
 * Glue between the Swing surfaces (image tool section, explorer menu, view button) and the
 * subtraction: enters and leaves the live preview of a pane, pushes the mask and weight of the
 * section to it, builds the derived series off the EDT with the explorer's loading indicator and
 * opens the result in the selected pane.
 */
public final class SubtractionController {

  private static final Logger LOGGER = LoggerFactory.getLogger(SubtractionController.class);

  private SubtractionController() {}

  /**
   * The masks the selected view's series can take, none for an empty or derived view: the frames of
   * a run, then the series of the study.
   */
  public static List<Object> maskChoices(ViewCanvas<DicomImageElement> view) {
    if (view == null || view.getSeries() == null) {
      return List.of();
    }
    return SubtractionService.maskChoices(view.getSeries());
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
      Object choice = selectedMask().orElse(null);
      if (choice instanceof FrameMask frames) {
        enterPreview(
            manager.getSelectedView2dContainer(),
            view,
            view.getSeries(),
            selectedFactor(),
            selectedDsa(frames));
      } else if (choice instanceof MediaSeries<?> mask) {
        enterPreview(
            manager.getSelectedView2dContainer(), view, cast(mask), selectedFactor(), null);
      }
    } else if (view instanceof SubtractionView preview) {
      preview.exitPreview();
    }
  }

  /**
   * Replaces a plain pane by the preview of its series minus the mask: another series, or with
   * {@code dsa} the frames of the run itself.
   */
  public static SubtractionView enterPreview(
      ImageViewerPlugin<DicomImageElement> container,
      ViewCanvas<DicomImageElement> view,
      MediaSeries<DicomImageElement> mask,
      double factor,
      Dsa dsa) {
    if (!(container instanceof View2dContainer)) {
      throw new IllegalStateException("The preview needs a 2D viewer");
    }
    SubtractionCompatibility.Result result =
        dsa == null
            ? SubtractionCompatibility.pair(view.getSeries(), mask)
            : SubtractionCompatibility.pairFrames(view.getSeries(), dsa);
    if (result instanceof SubtractionCompatibility.Refusal refusal) {
      throw new IllegalStateException(
          "The mask cannot be subtracted from the series shown: " + refusal.reason());
    }
    return SubtractionView.enter(container, view, mask, factor, dsa);
  }

  /** Opens the subtracted display a run asks for (Recommended Viewing Mode SUB), if it does. */
  public static void previewIfRecommended(
      ImageViewerPlugin<DicomImageElement> container, ViewCanvas<DicomImageElement> view) {
    MediaSeries<DicomImageElement> series = view == null ? null : view.getSeries();
    if (view instanceof SubtractionView || !DsaMask.recommendsSubtraction(series)) {
      return;
    }
    try {
      enterPreview(container, view, series, 1.0, DsaMask.proposeMask(series));
    } catch (IllegalStateException e) {
      LOGGER.warn("The run cannot be shown subtracted as its DICOM asks", e);
    }
  }

  /** Pushes the mask, weight and shift of the section to the preview of the selected pane. */
  public static void updatePreview() {
    SubtractionView preview = selectedPreview().orElse(null);
    Object choice = selectedMask().orElse(null);
    if (preview == null || choice == null) {
      return;
    }
    if (choice instanceof FrameMask frames) {
      preview.setParameters(preview.getSeries(), selectedFactor(), selectedDsa(frames));
    } else if (choice instanceof MediaSeries<?> mask) {
      preview.setParameters(cast(mask), selectedFactor(), null);
    }
  }

  /** Selects the frame the selected pane shows as the mask of its run. */
  public static void useCurrentFrameAsMask() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null || view.getSeries() == null) {
      return;
    }
    DicomImageElement shown =
        view instanceof SubtractionView preview ? preview.getSourceSlice() : view.getImage();
    int frame = DsaMask.frameOf(view.getSeries(), shown);
    if (frame < 1) {
      return;
    }
    EventManager.getInstance()
        .getAction(SubtractionAction.MASK)
        .ifPresent(a -> a.setSelectedItem(FrameMask.ofFrame(frame)));
  }

  /** The chosen frames with the shift of the sliders. */
  private static Dsa selectedDsa(FrameMask frames) {
    EventManager manager = EventManager.getInstance();
    double rows =
        manager.getAction(SubtractionAction.SHIFT_ROWS).map(a -> a.getRealValue()).orElse(0.0);
    double columns =
        manager.getAction(SubtractionAction.SHIFT_COLUMNS).map(a -> a.getRealValue()).orElse(0.0);
    return frames.base().withShift(rows, columns);
  }

  @SuppressWarnings("unchecked")
  private static MediaSeries<DicomImageElement> cast(MediaSeries<?> series) {
    return (MediaSeries<DicomImageElement>) series;
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
    Object choice = selectedMask().orElse(null);
    if (choice instanceof FrameMask frames) {
      subtract(view.getSeries(), view.getSeries(), selectedFactor(), selectedDsa(frames));
    } else if (choice instanceof MediaSeries<?> mask) {
      subtract(view.getSeries(), cast(mask), selectedFactor(), null);
    }
  }

  private static Optional<Object> selectedMask() {
    return EventManager.getInstance()
        .getAction(SubtractionAction.MASK)
        .map(a -> a.getSelectedItem());
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
    subtract(series, mask, factor, null);
  }

  /** As {@link #subtract(MediaSeries, MediaSeries, double)}, the mask of a run from its frames. */
  public static void subtract(
      MediaSeries<DicomImageElement> series,
      MediaSeries<DicomImageElement> mask,
      double factor,
      Dsa dsa) {
    SubtractionSpec spec =
        dsa != null
            ? SubtractionSpec.ofRun(SubtractionCommands.uidOf(series), dsa, factor)
            : new SubtractionSpec(
                SubtractionCommands.uidOf(series),
                SubtractionCommands.uidOf(mask),
                PhaseGrouper.phaseOf(series).map(PhaseGrouper.Phase::index).orElse(null),
                PhaseGrouper.phaseOf(mask).map(PhaseGrouper.Phase::index).orElse(null),
                factor,
                0.0,
                false,
                false);
    MediaSeries<DicomImageElement> parent = SubtractionService.parentOf(series);
    MediaSeries<DicomImageElement> maskParent = SubtractionService.parentOf(mask);
    DataExplorerModel model =
        parent.getTagValue(TagW.ExplorerModel) instanceof DataExplorerModel m ? m : null;
    ExplorerTask<Derived, Void> task =
        new ExplorerTask<>(Messages.getString("SubtractionTool.build"), false) {
          @Override
          protected Derived doInBackground() throws Exception {
            fire(ObservableEvent.BasicAction.LOADING_START);
            return SubtractionService.create(spec, parent, maskParent);
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
