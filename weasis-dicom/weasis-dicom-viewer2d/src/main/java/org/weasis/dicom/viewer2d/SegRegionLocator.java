/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Dimension;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.swing.JProgressBar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewProgress;
import org.weasis.core.ui.model.graphic.imp.seg.SegContour;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * Moves the displayed views to the position of a segmentation region. The search is always executed
 * off the EDT, with a progress bar painted in the views, because resolving the region requires
 * either scanning the segmentation volume (MPR) or loading the contours of every slice (2D).
 */
public final class SegRegionLocator {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegRegionLocator.class);

  private SegRegionLocator() {}

  /**
   * Displays the given region in the view: MPR views move their crosshair to the centre of the
   * densest slice of the region, other views scroll to the image holding its largest contour.
   *
   * @param view the view to navigate
   * @param region the region to display
   * @param contourFinder resolves the contour of a region in a given image (used by 2D views only)
   */
  public static void show(
      ViewCanvas<DicomImageElement> view,
      SegRegion<?> region,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder) {
    if (view == null || region == null) {
      return;
    }
    if (view instanceof MprView mprView) {
      showInMpr(mprView, region);
    } else {
      showInSeries(view, region, contourFinder);
    }
  }

  private static void showInMpr(MprView view, SegRegion<?> region) {
    MprController controller = view.getMprController();
    SegmentationVolume volume = null;
    int segmentNumber = -1;
    for (SegmentationVolume segVolume : controller.getSegVolumes()) {
      int number = segVolume.getSegmentNumber(region);
      if (number > 0) {
        volume = segVolume;
        segmentNumber = number;
        break;
      }
    }
    if (volume == null) {
      return;
    }

    List<MprView> views = controller.getMprViews();
    JProgressBar bar = createProgressBar();
    setProgressBar(views, bar);
    SegmentationVolume segVolume = volume;
    int segment = segmentNumber;
    CompletableFuture.supplyAsync(
            () -> segVolume.findSegmentCenter(segment, progressUpdater(views, bar)),
            ForkJoinPool.commonPool())
        .whenComplete(
            (voxel, error) ->
                GuiExecutor.execute(
                    () -> {
                      setProgressBar(views, null);
                      logError(error);
                      if (voxel != null) {
                        controller.setCrossHairAtVoxel(voxel);
                      }
                    }));
  }

  private static void showInSeries(
      ViewCanvas<DicomImageElement> view,
      SegRegion<?> region,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder) {
    if (contourFinder == null || !(view.getSeries() instanceof DicomSeries series)) {
      return;
    }
    List<ViewCanvas<DicomImageElement>> views = List.of(view);
    JProgressBar bar = createProgressBar();
    setProgressBar(views, bar);
    BiConsumer<Integer, Integer> progress = progressUpdater(views, bar);
    CompletableFuture.supplyAsync(
            () -> findBestImage(series, region, contourFinder, progress), ForkJoinPool.commonPool())
        .whenComplete(
            (image, error) ->
                GuiExecutor.execute(
                    () -> {
                      setProgressBar(views, null);
                      logError(error);
                      if (image != null) {
                        scrollToImage(view, series, image);
                      }
                    }));
  }

  /** Returns the image holding the largest contour of the region, or {@code null}. */
  private static DicomImageElement findBestImage(
      DicomSeries series,
      SegRegion<?> region,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder,
      BiConsumer<Integer, Integer> progress) {
    List<DicomImageElement> images = series.copyOfMedias(null, null);
    long maxPixels = Long.MIN_VALUE;
    DicomImageElement bestImage = null;
    int total = images.size();
    int done = 0;
    for (DicomImageElement image : images) {
      SegContour contour = contourFinder.apply(image, region);
      if (contour != null && contour.getNumberOfPixels() > maxPixels) {
        maxPixels = contour.getNumberOfPixels();
        bestImage = image;
      }
      progress.accept(++done, total);
    }
    return bestImage;
  }

  private static void scrollToImage(
      ViewCanvas<DicomImageElement> view, DicomSeries series, DicomImageElement image) {
    EventManager.getInstance()
        .getAction(ActionW.SCROLL_SERIES)
        .ifPresent(
            action -> {
              Filter<DicomImageElement> filter =
                  (Filter<DicomImageElement>) view.getActionValue(ActionW.FILTERED_SERIES.cmd());
              int index = series.getImageIndex(image, filter, view.getCurrentSortComparator());
              if (index >= 0) {
                action.setSliderValue(index + 1);
              }
            });
  }

  private static JProgressBar createProgressBar() {
    JProgressBar bar = new JProgressBar(0, 1);
    Dimension dim = new Dimension(GuiUtils.getScaleLength(200), GuiUtils.getScaleLength(30));
    bar.setSize(dim);
    bar.setPreferredSize(dim);
    bar.setMaximumSize(dim);
    bar.setStringPainted(true);
    bar.setString(Messages.getString("seg.locating"));
    return bar;
  }

  private static BiConsumer<Integer, Integer> progressUpdater(
      List<? extends ViewCanvas<DicomImageElement>> views, JProgressBar bar) {
    return (done, total) ->
        GuiExecutor.execute(
            () -> {
              bar.setMaximum(total);
              bar.setValue(done);
              repaint(views);
            });
  }

  private static void setProgressBar(
      List<? extends ViewCanvas<DicomImageElement>> views, JProgressBar bar) {
    GuiExecutor.execute(
        () -> {
          for (ViewCanvas<DicomImageElement> view : views) {
            if (view instanceof ViewProgress progressView) {
              progressView.setProgressBar(bar);
            }
          }
          repaint(views);
        });
  }

  private static void repaint(List<? extends ViewCanvas<DicomImageElement>> views) {
    for (ViewCanvas<DicomImageElement> view : views) {
      if (view != null) {
        view.getJComponent().repaint();
      }
    }
  }

  private static void logError(Throwable error) {
    if (error != null) {
      LOGGER.error("Cannot locate the segmentation region", error);
    }
  }
}
