/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.dicom.codec.seg.SegmentationVolumeBuildExecutor;
import org.weasis.dicom.explorer.exp.ExplorerTask;

/**
 * UI-aware {@link SegmentationVolumeBuildExecutor} that surfaces the canonical segmentation volume
 * build as a cancellable {@link ExplorerTask} in the DICOM explorer's bottom loading panel.
 *
 * <p>The work runs on an {@link ExplorerTask} (a {@link javax.swing.SwingWorker}); cancelling the
 * returned {@link CompletableFuture} cancels the underlying SwingWorker and removes the panel via
 * the matching {@code LOADING_STOP} event.
 */
public final class UISegmentationVolumeBuildExecutor implements SegmentationVolumeBuildExecutor {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(UISegmentationVolumeBuildExecutor.class);

  @Override
  public CompletableFuture<SegmentationVolume> schedule(
      SegSpecialElement seg, Callable<SegmentationVolume> work) {

    DataExplorerModel model = resolveModel(seg);
    String message = "Building SEG";

    CompletableFuture<SegmentationVolume> result = new CompletableFuture<>();
    ExplorerTask<SegmentationVolume, Void> task =
        new ExplorerTask<>(message, false) {
          @Override
          protected SegmentationVolume doInBackground() throws Exception {
            return work.call();
          }

          @Override
          protected void done() {
            if (model != null) {
              model.firePropertyChange(
                  new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, model, null, this));
            }
            if (isCancelled()) {
              result.completeExceptionally(new CancellationException());
              return;
            }
            try {
              result.complete(get());
            } catch (CancellationException ce) {
              result.completeExceptionally(ce);
            } catch (ExecutionException ee) {
              result.completeExceptionally(ee.getCause() == null ? ee : ee.getCause());
            } catch (InterruptedException ie) {
              Thread.currentThread().interrupt();
              result.completeExceptionally(ie);
            }
          }
        };

    // Bridge external future cancellation to the SwingWorker so the loading panel is removed and
    // the build is interrupted in one step.
    result.whenComplete(
        (_, err) -> {
          if (err instanceof CancellationException && !task.isDone()) {
            task.cancel();
          }
        });

    if (model != null) {
      model.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.LOADING_START, model, null, task));
    }
    try {
      task.execute();
    } catch (RuntimeException e) {
      LOGGER.error("Failed to schedule segmentation volume build", e);
      if (model != null) {
        model.firePropertyChange(
            new ObservableEvent(ObservableEvent.BasicAction.LOADING_STOP, model, null, task));
      }
      result.completeExceptionally(e);
    }
    return result;
  }

  private static DataExplorerModel resolveModel(SegSpecialElement seg) {
    DicomSeries series =
        seg == null || seg.getMediaReader() == null ? null : seg.getMediaReader().getMediaSeries();
    Object tag = series == null ? null : series.getTagValue(TagW.ExplorerModel);
    if (tag instanceof DataExplorerModel m) {
      return m;
    }
    // Fallback: the active DICOM explorer's model.
    var view =
        GuiUtils.getUICore().getExplorerPlugin(org.weasis.dicom.explorer.main.DicomExplorer.NAME);
    if (view != null && view.getDataExplorerModel() instanceof DataExplorerModel m) {
      return m;
    }
    return null;
  }

  private static String buildTaskMessage(SegSpecialElement seg) {
    return "Building SEG";
  }
}
