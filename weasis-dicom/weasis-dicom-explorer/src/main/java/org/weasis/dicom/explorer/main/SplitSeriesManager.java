/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import javax.swing.SwingUtilities;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.*;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;

/**
 * Manages split series functionality for DICOM series. Handles tracking, updating, and UI
 * synchronization of split series.
 */
public class SplitSeriesManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(SplitSeriesManager.class);

  private static final int REPLACEMENT_SERIES_NUMBER = -1;
  private static final int INITIAL_SPLIT_NUMBER = 1;

  private final DicomExplorer explorer;
  private final DicomModel model;

  // Thread-safe collections for split series tracking
  private final ConcurrentHashMap<String, List<DicomSeries>> splitSeriesCache =
      new ConcurrentHashMap<>();
  private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();

  public SplitSeriesManager(DicomExplorer explorer) {
    this.explorer = Objects.requireNonNull(explorer, "DicomExplorer cannot be null");
    this.model = explorer.getDataExplorerModel();
  }

  /**
   * Updates split series for the given DICOM series. This is the main entry point for split series
   * processing.
   *
   * @param dcmSeries the DICOM series to update
   */
  public void updateSplitSeries(DicomSeries dcmSeries) {
    String seriesUID = getSeriesInstanceUID(dcmSeries);
    if (seriesUID == null) {
      LOGGER.warn("Cannot update split series: Series is null");
      return;
    }

    try {
      processSplitSeriesUpdate(dcmSeries, seriesUID);
    } catch (Exception e) {
      LOGGER.error("Error updating split series for: {}", seriesUID, e);
    }
  }

  private void processSplitSeriesUpdate(DicomSeries dcmSeries, String seriesUID) {
    cacheLock.writeLock().lock();
    try {
      List<DicomSeries> splitSeries = discoverSplitSeries(dcmSeries, seriesUID);

      if (splitSeries.isEmpty()) {
        splitSeriesCache.remove(seriesUID);
        return;
      }
      splitSeriesCache.put(seriesUID, new ArrayList<>(splitSeries));

      if (splitSeries.size() > 1) {
        updateSplitSeriesNumbers(splitSeries);
        rebuildThumbnails(splitSeries);
        SwingUtilities.invokeLater(() -> refreshAffectedStudyPanes(splitSeries));
      }
    } finally {
      cacheLock.writeLock().unlock();
    }
  }

  private List<DicomSeries> discoverSplitSeries(DicomSeries dcmSeries, String seriesUID) {
    MediaSeriesGroup study = model.getParent(dcmSeries, DicomModel.study);
    // In case the Series has been replaced (split number = -1) and removed
    if (study == null) {
      return Collections.emptyList();
    }

    List<DicomSeries> splitSeries = new ArrayList<>();
    for (MediaSeriesGroup series : model.getChildren(study)) {
      if (series instanceof DicomSeries s) {
        String currentSeriesUID = getSeriesInstanceUID(s);
        if (seriesUID.equals(currentSeriesUID)) {
          splitSeries.add(s);
        }
      }
    }
    return splitSeries;
  }

  /** Updates split series numbers for proper ordering and display. */
  private void updateSplitSeriesNumbers(List<DicomSeries> splitSeries) {
    if (splitSeries.size() <= 1) {
      return;
    }

    // Sort by existing split number, then by series number, then by creation order
    splitSeries.sort(
        (s1, s2) -> {
          Integer split1 = TagD.getTagValue(s1, TagD.SplitSeriesNumber, Integer.class);
          Integer split2 = TagD.getTagValue(s2, TagD.SplitSeriesNumber, Integer.class);
          if (split1 != null && split2 != null) {
            return Integer.compare(split1, split2);
          }

          // If only one has a split number, it comes first
          if (split1 != null) return -1;
          if (split2 != null) return 1;

          // If neither has split numbers, use series comparator
          return DicomSorter.SERIES_COMPARATOR.compare(s1, s2);
        });

    // Assign sequential split numbers starting from 1
    for (int i = 0; i < splitSeries.size(); i++) {
      DicomSeries series = splitSeries.get(i);
      int splitNumber = i + INITIAL_SPLIT_NUMBER;
      series.setTag(TagD.SplitSeriesNumber, splitNumber);
      LOGGER.trace("Updated split series number: {} for series: {}", splitNumber, series);
    }
  }

  /**
   * Rebuilds thumbnails for all split series. This forces thumbnail regeneration to reflect the
   * split series numbering.
   */
  private void rebuildThumbnails(List<DicomSeries> splitSeries) {
    for (DicomSeries series : splitSeries) {
      // Clear existing thumbnail to force rebuild
      series.setTag(TagW.Thumbnail, null);
      model.buildThumbnail(series);
    }
  }

  /** Refreshes the UI for all study panes that contain the split series. */
  private void refreshAffectedStudyPanes(List<DicomSeries> splitSeries) {
    Set<StudyPane> studyPanes = new HashSet<>();
    DicomPaneManager paneManager = explorer.getPaneManager();

    for (DicomSeries series : splitSeries) {
      SeriesPane seriesPane = paneManager.getSeriesPane(series);
      if (seriesPane != null) {
        seriesPane.updateThumbnail();
      }

      MediaSeriesGroup study = model.getParent(series, DicomModel.study);
      if (study != null) {
        StudyPane studyPane = paneManager.getStudyPane(study);
        if (studyPane != null) {
          studyPanes.add(studyPane);
        }
      }
    }

    for (StudyPane studyPane : studyPanes) {
      studyPane.refreshLayoutAsync();
    }
  }

  // ========== Public API ==========

  /**
   * Gets all split series for a given SeriesInstanceUID. This method checks the cache first
   *
   * @param seriesInstanceUID the SeriesInstanceUID to look up
   * @return a list of DicomSeries that are part of the split series, or an empty list if none found
   */
  public List<DicomSeries> getSplitSeries(String seriesInstanceUID) {
    if (seriesInstanceUID == null) {
      return new ArrayList<>();
    }

    cacheLock.readLock().lock();
    try {
      List<DicomSeries> cached = splitSeriesCache.get(seriesInstanceUID);
      if (cached != null) {
        return new ArrayList<>(cached);
      }

      // If not cached, discover and cache
      MediaSeriesGroup series = model.getSeriesNode(seriesInstanceUID);
      if (series instanceof DicomSeries dcmSeries) {
        List<DicomSeries> discovered = discoverSplitSeries(dcmSeries, seriesInstanceUID);
        if (!discovered.isEmpty()) {
          splitSeriesCache.put(seriesInstanceUID, new ArrayList<>(discovered));
        }
        return discovered;
      }

    } finally {
      cacheLock.readLock().unlock();
    }
    return Collections.emptyList();
  }

  /** Gets all split series for a given DICOM series. */
  public List<DicomSeries> getSplitSeries(DicomSeries series) {
    if (series == null) {
      return new ArrayList<>();
    }
    String seriesUID = getSeriesInstanceUID(series);
    return getSplitSeries(seriesUID);
  }

  /** Marks a series as a replacement series. */
  public void markSeriesAsReplacement(DicomSeries series) {
    if (series != null) {
      series.setTag(TagD.SplitSeriesNumber, REPLACEMENT_SERIES_NUMBER);
      LOGGER.trace("Marked series as replacement: {}", series);
    }
  }

  /** Checks if a series is marked as a replacement series. */
  public boolean isReplacementSeries(DicomSeries series) {
    if (series == null) {
      return false;
    }

    Integer splitNumber = TagD.getTagValue(series, TagD.SplitSeriesNumber, Integer.class);
    return splitNumber != null && splitNumber == REPLACEMENT_SERIES_NUMBER;
  }

  /** Invalidates the split series cache for a given series instance UID. */
  public void invalidateSplitSeriesCache(String seriesInstanceUID) {
    if (seriesInstanceUID != null) {
      cacheLock.writeLock().lock();
      try {
        splitSeriesCache.remove(seriesInstanceUID);
        LOGGER.debug("Invalidated split series cache for: {}", seriesInstanceUID);
      } finally {
        cacheLock.writeLock().unlock();
      }
    }
  }

  public void invalidateSplitSeriesCache(DicomSeries series) {
    String seriesUID = getSeriesInstanceUID(series);
    invalidateSplitSeriesCache(seriesUID);
  }

  // ========== Utility Methods ==========

  private String getSeriesInstanceUID(DicomSeries series) {
    if (series == null) {
      return null;
    }
    return TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
  }

  // ========== Cleanup ==========

  /** Disposes of resources and cleans up the manager. */
  public void dispose() {
    cacheLock.writeLock().lock();
    try {
      splitSeriesCache.clear();
      LOGGER.debug("SplitSeriesManager disposed");
    } finally {
      cacheLock.writeLock().unlock();
    }
  }
}
