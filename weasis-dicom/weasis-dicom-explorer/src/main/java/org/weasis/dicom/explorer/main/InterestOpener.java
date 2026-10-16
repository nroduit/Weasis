/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.editor.ViewerOpenOptions;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.items.Interest;

/**
 * Opens a series on its images of interest only: the default viewer shows the series with the stack
 * filtered to the images a key object, an annotated presentation state, a segmentation or the
 * user's own graphics point at. The same filter mechanism the viewer uses for a key object
 * selection, generalized to every source.
 */
public final class InterestOpener {

  private InterestOpener() {}

  /** The stack filter keeping the images of interest; null when there are none. */
  public static Filter<DicomImageElement> filterOf(Interest interest) {
    if (interest == null || interest.isEmpty()) {
      return null;
    }
    Set<String> uids = interest.images();
    return new Filter<>() {
      @Override
      public boolean passes(DicomImageElement item) {
        return item != null
            && uids.contains(TagD.getTagValue(item, Tag.SOPInstanceUID, String.class));
      }
    };
  }

  /** Opens the series in its default viewer, then keeps its images of interest only. */
  public static void open(DicomSeries series, DicomModel model) {
    Objects.requireNonNull(series);
    ViewerPluginBuilder.openInDefaultViewer(List.of(series), model, ViewerOpenOptions.defaults());
    applyWhenReady(series);
  }

  /**
   * Applies the images-of-interest filter to every open view showing the series, once the viewers
   * have built their content. Does nothing when the series has no image of interest.
   */
  public static void applyWhenReady(DicomSeries series) {
    Filter<DicomImageElement> filter = filterOf(Interest.of(series));
    if (filter == null) {
      return;
    }
    for (ViewerPlugin<?> plugin : GuiUtils.getUICore().getViewerPlugins()) {
      if (plugin instanceof ImageViewerPlugin<?> viewer && shows(viewer, series)) {
        viewer
            .whenReady()
            .thenRun(() -> GuiExecutor.execute(() -> applyToViewer(viewer, series, filter)));
      }
    }
  }

  private static boolean shows(ImageViewerPlugin<?> viewer, DicomSeries series) {
    for (ViewCanvas<?> canvas : viewer.getImagePanels()) {
      if (canvas.getSeries() == series) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private static void applyToViewer(
      ImageViewerPlugin<?> viewer, DicomSeries series, Filter<DicomImageElement> filter) {
    for (ViewCanvas<?> canvas : viewer.getImagePanels()) {
      if (canvas.getSeries() == series) {
        applyToView((ViewCanvas<DicomImageElement>) canvas, series, filter);
      }
    }
  }

  /** Sets the stack filter of a view and shows the first image that passes it. */
  public static boolean applyToView(
      ViewCanvas<DicomImageElement> view, DicomSeries series, Filter<DicomImageElement> filter) {
    if (view == null || series == null || filter == null || series.size(filter) == 0) {
      return false;
    }
    view.setActionsInView(ActionW.FILTERED_SERIES.cmd(), filter);
    DicomImageElement first = series.getMedia(0, filter, view.getCurrentSortComparator());
    if (first != null && !first.isImageAvailable()) {
      first.getImage();
    }
    if (view instanceof DefaultView2d<DicomImageElement> view2d) {
      view2d.setImage(first);
      view2d
          .getEventManager()
          .getAction(ActionW.SCROLL_SERIES)
          .ifPresent(s -> s.setSliderMinMaxValue(1, series.size(filter), 1));
      return true;
    }
    return false;
  }

  /** The number of images of interest a view's series has, 0 when none or not a DICOM series. */
  public static int count(ViewCanvas<? extends MediaElement> view) {
    return view != null && view.getSeries() instanceof DicomSeries series
        ? Interest.of(series).count()
        : 0;
  }
}
