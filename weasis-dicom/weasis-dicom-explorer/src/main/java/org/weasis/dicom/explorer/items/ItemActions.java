/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesImporter;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.KOSpecialElement;
import org.weasis.dicom.codec.PRSpecialElement;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.main.ThumbnailMouseAndKeyAdapter;

/**
 * The actions of the explorer items as headless services: what a button of the study content panel,
 * a row of the list or a {@code dicom:} verb runs. Opening an object means opening what it refers
 * to: the key images of a key object selection, the referenced series with a presentation state
 * applied, the referenced series of a segmentation or an RT object (their tools follow the series).
 */
public final class ItemActions {

  /**
   * Set on a series before it is opened: the presentation state the next 2D view of the series
   * applies instead of its default choice, then cleared by that view.
   */
  public static final TagW REQUESTED_PR =
      new TagW("RequestedPresentationState", TagType.OBJECT); // NON-NLS

  private ItemActions() {}

  /** The referenced series of an object that are loaded, in the order the object names them. */
  public static List<DicomSeries> referencedLoadedSeries(ObjectItem item, DicomModel model) {
    List<DicomSeries> loaded = new ArrayList<>();
    for (String uid : item.descriptor().referencedSeriesUIDs()) {
      if (model.getSeriesNode(uid) instanceof DicomSeries series) {
        loaded.add(series);
      }
    }
    return loaded;
  }

  /**
   * Opens what the object refers to.
   *
   * @return the referenced series the viewer shows, null when none of them is loaded
   */
  public static DicomSeries open(ObjectItem item, DicomModel model) {
    Objects.requireNonNull(item);
    Objects.requireNonNull(model);
    List<DicomSeries> loaded = referencedLoadedSeries(item, model);
    DicomSeries first = loaded.isEmpty() ? null : loaded.getFirst();
    if (item.element() instanceof KOSpecialElement ko) {
      MediaSeriesGroup patient = model.getParent(item.study(), DicomModel.patient);
      if (patient != null) {
        model.openRelatedSeries(ko, patient);
        return first;
      }
    }
    if (first == null) {
      return null;
    }
    if (item.element() instanceof PRSpecialElement pr) {
      first.setTag(REQUESTED_PR, pr);
    }
    ThumbnailMouseAndKeyAdapter.openSeriesInDefaultPlugin(first, model);
    return first;
  }

  /** Starts the download of the series whose transfer is attached but not running. */
  public static int download(Collection<SeriesItem> items) {
    int started = 0;
    for (SeriesItem item : items) {
      if (item.downloadState() == DownloadState.PENDING
          && item.series().getSeriesLoader() instanceof SeriesImporter loader) {
        loader.resume();
        started++;
      }
    }
    return started;
  }

  /** Starts every pending download of a study. */
  public static int downloadAll(StudyContents contents) {
    return download(contents.notDownloaded());
  }

  /** Stops the download of a series; false when nothing was running. */
  public static boolean stop(SeriesItem item) {
    return item.downloadState() == DownloadState.DOWNLOADING
        && item.series().getSeriesLoader() instanceof SeriesImporter loader
        && loader.stop();
  }
}
