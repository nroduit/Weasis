/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.fusion;

import java.util.ArrayList;
import java.util.List;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.mpr.MprView;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.opencv.op.lut.ByteLut;

/**
 * Stateless helpers shared by the fusion EventManager actions: they apply a parameter to every pane
 * of the selected container (so MPR planes stay in sync), build the resampled PET volume off the
 * EDT, and list the study series compatible with the displayed base series.
 */
public final class FusionController {

  private FusionController() {}

  /**
   * Every pane of the selected container, so fusion applies to all MPR planes (each plane is a
   * separate view with its own FusionOp). Falls back to the selected pane alone.
   */
  private static List<ViewCanvas<DicomImageElement>> targetViews() {
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container != null) {
      List<ViewCanvas<DicomImageElement>> views = container.getView2ds();
      if (views != null && !views.isEmpty()) {
        return views;
      }
    }
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    return view != null ? List.of(view) : List.of();
  }

  /**
   * Captures the active fusion configuration of {@code view}, or {@code null} when fusion is off or
   * has no overlay series. Used to seed a newly opened MPR with the 2D view's fusion.
   */
  public static FusionState snapshot(ViewCanvas<DicomImageElement> view) {
    if (view == null) {
      return null;
    }
    OpManager disOp = view.getDisplayOpManager();
    boolean enabled =
        disOp
            .getParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_ENABLED, Boolean.class)
            .orElse(Boolean.FALSE);
    if (!enabled
        || !(disOp.getParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_SERIES).orElse(null)
            instanceof MediaSeries<?> series)) {
      return null;
    }
    ByteLut lut =
        disOp.getParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_LUT).orElse(null)
                instanceof ByteLut l
            ? l
            : null;
    double base =
        disOp.getParamValue(FusionOp.OP_NAME, FusionOp.P_OPACITY_BASE, Double.class).orElse(1.0);
    double overlay =
        disOp
            .getParamValue(FusionOp.OP_NAME, FusionOp.P_OPACITY_OVERLAY, Double.class)
            .orElse(0.75);
    Volume<?, ?> volume =
        disOp.getParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_VOLUME).orElse(null)
                instanceof Volume<?, ?> v
            ? v
            : null;
    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> overlaySeries = (MediaSeries<DicomImageElement>) series;
    return new FusionState(overlaySeries, lut, base, overlay, volume);
  }

  /**
   * Applies an inherited {@link FusionState} to the given panes (typically the MPR planes). Reuses
   * the snapshot's volume when present, otherwise rebuilds it for the target geometry.
   */
  public static void applyState(List<ViewCanvas<DicomImageElement>> views, FusionState state) {
    if (state == null || views == null || views.isEmpty()) {
      return;
    }
    for (ViewCanvas<DicomImageElement> view : views) {
      OpManager disOp = view.getDisplayOpManager();
      disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_SERIES, state.series());
      disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_LUT, state.lut());
      disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_OPACITY_BASE, state.baseOpacity());
      disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_OPACITY_OVERLAY, state.overlayOpacity());
      if (state.volume() != null) {
        disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_VOLUME, state.volume());
      }
      disOp.setParamValue(FusionOp.OP_NAME, FusionOp.P_FUSION_ENABLED, Boolean.TRUE);
      clearCache(disOp);
      view.getImageLayer().updateDisplayOperations();
    }
    if (state.volume() == null) {
      buildVolume(state.series());
    }
  }

  /** Applies a FusionOp parameter to every pane of the current container. */
  public static void applyParam(String propertyName, Object value) {
    // The cached overlays bake in the color LUT (and series), so they must be invalidated when
    // either changes. Opacity is applied at composite time and does not need a cache clear.
    boolean clearCache =
        FusionOp.P_FUSION_LUT.equals(propertyName) || FusionOp.P_FUSION_SERIES.equals(propertyName);
    for (ViewCanvas<DicomImageElement> view : targetViews()) {
      OpManager disOp = view.getDisplayOpManager();
      disOp.setParamValue(FusionOp.OP_NAME, propertyName, value);
      if (clearCache) {
        clearCache(disOp);
      }
      view.getImageLayer().updateDisplayOperations();
    }
  }

  private static void clearCache(OpManager disOp) {
    disOp
        .getNode(FusionOp.OP_NAME)
        .ifPresent(
            node -> {
              if (node instanceof FusionOp fusionOp) {
                fusionOp.clearCache();
              }
            });
  }

  /**
   * Builds the rectified PET volume off the EDT so fusion can be resliced on any plane, then
   * applies it to every pane. Until the build completes, fusion falls back to the single-slice
   * path.
   */
  public static void buildVolume(Object selectedSeries) {
    if (!(selectedSeries instanceof MediaSeries)) {
      return;
    }
    @SuppressWarnings("unchecked")
    MediaSeries<DicomImageElement> overlaySeries = (MediaSeries<DicomImageElement>) selectedSeries;
    Thread worker =
        new Thread(
            () -> {
              Volume<?, ?> volume = FusionVolumeBuilder.build(overlaySeries);
              if (volume != null) {
                GuiExecutor.execute(() -> applyParam(FusionOp.P_FUSION_VOLUME, volume));
              }
            },
            "fusion-volume-builder"); // NON-NLS
    worker.setDaemon(true);
    worker.start();
  }

  /**
   * Lists the same-study series that can be fused onto the displayed base series (see {@link
   * FusionCompatibility}).
   */
  @SuppressWarnings("unchecked")
  public static List<MediaSeries<DicomImageElement>> compatibleSeries(
      ViewCanvas<DicomImageElement> view) {
    List<MediaSeries<DicomImageElement>> result = new ArrayList<>();
    if (view == null || view.getSeries() == null) {
      return result;
    }
    if (!(view.getSeries().getTagValue(TagW.ExplorerModel) instanceof DicomModel model)) {
      return result;
    }
    // In MPR the displayed series is a derived reslice with a generated FrameOfReferenceUID;
    // resolve
    // the original acquisition series so study lookup and compatibility use the real values.
    MediaSeries<DicomImageElement> refSeries = resolveSourceSeries(view);
    MediaSeriesGroup study = model.getParent(refSeries, DicomModel.study);
    if (study == null) {
      return result;
    }
    for (MediaSeriesGroup seriesGroup : model.getChildren(study)) {
      if (seriesGroup instanceof MediaSeries<?> ms
          && FusionCompatibility.isCompatible(refSeries, (MediaSeries<DicomImageElement>) ms)) {
        result.add((MediaSeries<DicomImageElement>) ms);
      }
    }
    return result;
  }

  /**
   * DICOM Modality of the displayed base series (resolving the MPR source series), or {@code null}.
   */
  public static String baseModality(ViewCanvas<DicomImageElement> view) {
    return view == null || view.getSeries() == null ? null : modalityOf(resolveSourceSeries(view));
  }

  /** DICOM Modality of a series (e.g. CT, MR, PT, NM), or {@code null}. */
  public static String modalityOf(Object series) {
    return series instanceof MediaSeries<?> ms
        ? TagD.getTagValue(ms, Tag.Modality, String.class)
        : null;
  }

  /** Original acquisition series backing the view (the MPR source series, or the view's own). */
  private static MediaSeries<DicomImageElement> resolveSourceSeries(
      ViewCanvas<DicomImageElement> view) {
    if (view instanceof MprView mprView && mprView.getMprController() != null) {
      Volume<?, ?> volume = mprView.getMprController().getVolume();
      if (volume != null && volume.getStack() != null && volume.getStack().getSeries() != null) {
        return volume.getStack().getSeries();
      }
    }
    return view.getSeries();
  }
}
