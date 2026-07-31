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
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import javax.swing.JProgressBar;
import org.dcm4che3.data.Tag;
import org.joml.Vector3d;
import org.joml.Vector3i;
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
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.viewer2d.mpr.MprController;
import org.weasis.dicom.viewer2d.mpr.MprView;

/**
 * Moves the displayed views to the position of a segmentation region. The search is always executed
 * off the EDT, with a progress bar painted in the views, because resolving the region requires
 * either scanning the segmentation volume or loading the contours of every slice.
 */
public final class SegRegionLocator {

  private static final Logger LOGGER = LoggerFactory.getLogger(SegRegionLocator.class);

  /**
   * Identifies the latest search request. A slow search whose result arrives after the user has
   * clicked another region is discarded, so it neither scrolls the view nor clears the progress bar
   * of the request that superseded it.
   */
  private static final AtomicLong REQUEST_SEQ = new AtomicLong();

  private SegRegionLocator() {}

  /**
   * Displays the given region in the view: MPR views move their crosshair to the centre of the
   * densest slice of the region, other views scroll to the image holding its largest contour.
   *
   * <p>Must be called on the EDT: {@code sources} and {@code contourFinder} are expected to be
   * bound to a snapshot of the caller's state, since the search itself runs in the background.
   *
   * @param view the view to navigate
   * @param region the region to display
   * @param sources the segmentations the region may belong to, used to take the (much faster)
   *     segmentation-volume path when one of them exposes a 3D volume
   * @param contourFinder resolves the contour of a region in a given image, used as a fallback when
   *     no segmentation volume can locate the region
   */
  public static void show(
      ViewCanvas<DicomImageElement> view,
      SegRegion<?> region,
      List<? extends SpecialElementRegion> sources,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder) {
    if (view == null || region == null) {
      return;
    }
    if (view instanceof MprView mprView) {
      showInMpr(mprView, region);
    } else {
      showInSeries(view, region, sources, contourFinder);
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
    long seq = REQUEST_SEQ.incrementAndGet();
    JProgressBar bar = createProgressBar();
    setProgressBar(views, bar);
    SegmentationVolume segVolume = volume;
    int segment = segmentNumber;
    CompletableFuture.supplyAsync(
            () -> segVolume.findSegmentCenter(segment, progressUpdater(views, bar, seq)),
            ForkJoinPool.commonPool())
        .whenComplete(
            (voxel, error) ->
                GuiExecutor.execute(
                    () -> {
                      logError(error);
                      if (isStale(seq)) {
                        return;
                      }
                      setProgressBar(views, null);
                      if (voxel != null) {
                        controller.setCrossHairAtVoxel(voxel);
                      }
                    }));
  }

  private static void showInSeries(
      ViewCanvas<DicomImageElement> view,
      SegRegion<?> region,
      List<? extends SpecialElementRegion> sources,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder) {
    boolean noSource = sources == null || sources.isEmpty();
    if (!(view.getSeries() instanceof DicomSeries series) || (noSource && contourFinder == null)) {
      return;
    }
    List<ViewCanvas<DicomImageElement>> views = List.of(view);
    long seq = REQUEST_SEQ.incrementAndGet();
    JProgressBar bar = createProgressBar();
    setProgressBar(views, bar);
    BiConsumer<Integer, Integer> progress = progressUpdater(views, bar, seq);
    CompletableFuture.supplyAsync(
            () -> locateInSeries(series, region, sources, contourFinder, progress),
            ForkJoinPool.commonPool())
        .whenComplete(
            (image, error) ->
                GuiExecutor.execute(
                    () -> {
                      logError(error);
                      if (isStale(seq)) {
                        return;
                      }
                      setProgressBar(views, null);
                      if (image != null) {
                        scrollToImage(view, series, image);
                      }
                    }));
  }

  /**
   * Resolves the image of the series that best shows the region: through the 3D segmentation volume
   * when one is available (a single linear scan, no image decoding), otherwise by loading the
   * contours of every image of the series.
   */
  private static DicomImageElement locateInSeries(
      DicomSeries series,
      SegRegion<?> region,
      List<? extends SpecialElementRegion> sources,
      BiFunction<DicomImageElement, SegRegion<?>, SegContour> contourFinder,
      BiConsumer<Integer, Integer> progress) {
    DicomImageElement image = findByVolume(series, region, sources, progress);
    if (image != null || contourFinder == null) {
      return image;
    }
    return findBestImage(series, region, contourFinder, progress);
  }

  /**
   * Locates the region in the first segmentation volume that contains it, then maps the resulting
   * voxel back to the closest image of the series. Returns {@code null} when no volume holds the
   * region.
   */
  private static DicomImageElement findByVolume(
      DicomSeries series,
      SegRegion<?> region,
      List<? extends SpecialElementRegion> sources,
      BiConsumer<Integer, Integer> progress) {
    if (sources == null) {
      return null;
    }
    for (SpecialElementRegion source : preferOwners(sources, region)) {
      SegmentationVolume volume = source.getOrBuildSegmentationVolume();
      if (volume == null) {
        continue;
      }
      int segment = volume.getSegmentNumber(region);
      if (segment <= 0) {
        continue;
      }
      Vector3i voxel = volume.findSegmentCenter(segment, progress);
      if (voxel == null) {
        continue;
      }
      DicomImageElement image =
          findClosestImage(series, volume.voxelToLps(voxel.x, voxel.y, voxel.z));
      if (image != null) {
        return image;
      }
    }
    return null;
  }

  /**
   * Narrows {@code sources} to the segmentations that actually declare {@code region}, falling back
   * to the whole list when none does. {@link org.weasis.opencv.seg.RegionAttributes#equals} only
   * compares segment number and label, so two SEG files of the same patient may otherwise claim
   * each other's regions.
   */
  private static List<? extends SpecialElementRegion> preferOwners(
      List<? extends SpecialElementRegion> sources, SegRegion<?> region) {
    List<? extends SpecialElementRegion> owners =
        sources.stream().filter(s -> declares(s, region)).toList();
    return owners.isEmpty() ? sources : owners;
  }

  private static boolean declares(SpecialElementRegion source, SegRegion<?> region) {
    return source.getSegAttributes().values().stream().anyMatch(a -> a == region);
  }

  /** Returns the image of the series whose plane is the closest to the given LPS position. */
  private static DicomImageElement findClosestImage(DicomSeries series, Vector3d position) {
    List<DicomImageElement> images = series.copyOfMedias(null, null);
    Vector3d normal =
        images.stream()
            .map(DicomMediaUtils::computeImageNormal)
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    if (normal == null) {
      return null;
    }
    double target = normal.dot(position);
    DicomImageElement closest = null;
    double smallestDistance = Double.MAX_VALUE;
    for (DicomImageElement image : images) {
      double[] ipp = TagD.getTagValue(image, Tag.ImagePositionPatient, double[].class);
      if (ipp == null || ipp.length != 3) {
        continue;
      }
      double distance = Math.abs(normal.dot(new Vector3d(ipp)) - target);
      if (distance < smallestDistance) {
        smallestDistance = distance;
        closest = image;
      }
    }
    return closest;
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

  private static boolean isStale(long seq) {
    return seq != REQUEST_SEQ.get();
  }

  /**
   * Builds a progress callback that repaints the views at most once per percent, so a scan pushing
   * one event per slice does not flood the EDT.
   */
  private static BiConsumer<Integer, Integer> progressUpdater(
      List<? extends ViewCanvas<DicomImageElement>> views, JProgressBar bar, long seq) {
    AtomicInteger lastPercent = new AtomicInteger(-1);
    return (done, total) -> {
      if (isStale(seq)) {
        return;
      }
      int percent = total <= 0 ? 0 : (int) (100L * done / total);
      if (lastPercent.getAndSet(percent) == percent && done < total) {
        return;
      }
      GuiExecutor.execute(
          () -> {
            if (isStale(seq)) {
              return;
            }
            bar.setMaximum(total);
            bar.setValue(done);
            repaint(views);
          });
    };
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
