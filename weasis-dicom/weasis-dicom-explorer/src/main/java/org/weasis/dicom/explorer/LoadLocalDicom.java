/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.io.File;
import java.util.*;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.*;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.*;
import org.weasis.dicom.codec.DicomMediaIO.Reading;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;

public class LoadLocalDicom extends LoadDicom {

  private final File[] files;
  private final boolean recursive;

  public LoadLocalDicom(
      File[] files, boolean recursive, DataExplorerModel explorerModel, OpeningViewer openingMode) {
    this(files, recursive, explorerModel, new PluginOpeningStrategy(openingMode));
  }

  public LoadLocalDicom(
      File[] files,
      boolean recursive,
      DataExplorerModel explorerModel,
      PluginOpeningStrategy openingStrategy) {
    super(explorerModel, false, openingStrategy);
    this.files = Objects.requireNonNull(files);
    this.recursive = recursive;
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    startLoadingEvent();
    if (files.length > 0) {
      openingStrategy.prepareImport();
      addSelectionAndNotify(files, true);
    }
    return true;
  }

  protected void addSelectionAndNotify(File[] file, boolean firstLevel) {
    if (file == null || file.length < 1) {
      return;
    }

    Set<DicomSeries> uniqueSeriesSet = new LinkedHashSet<>();
    ArrayList<File> folders = new ArrayList<>();
    for (File value : file) {
      if (isCancelled()) {
        return;
      }
      if (value == null) {
        continue;
      }

      if (value.isDirectory()) {
        if (firstLevel || recursive) {
          folders.add(value);
        }
      } else if (value.canRead()
              && FileUtil.isFileExtensionMatching(value, DicomCodec.FILE_EXTENSIONS)
          || MimeInspector.isMatchingMimeTypeFromMagicNumber(value, DicomMediaIO.DICOM_MIMETYPE)) {
        DicomMediaIO loader = new DicomMediaIO(value);
        Reading reading = loader.getReadingStatus();
        if (reading == Reading.READABLE) {
          if (value.getPath().startsWith(AppProperties.APP_TEMP_DIR.getPath())) {
            loader.getFileCache().setOriginalTempFile(value);
          }
          uniqueSeriesSet.add(buildDicomStructure(loader));

          File gpxFile = new File(value.getPath() + ".xml");
          GraphicModel graphicModel = XmlSerializer.readPresentationModel(gpxFile);
          if (graphicModel != null) {
            loader.setTag(TagW.PresentationModel, graphicModel);
          }
        } else if (reading == Reading.ERROR) {
          errors.incrementAndGet();
        }
      }
    }

    if (openingStrategy.isFullImportSession()) {
      updateSeriesThumbnail(uniqueSeriesSet, dicomModel);
    } else {
      for (DicomSeries series : uniqueSeriesSet) {
        dicomModel.buildThumbnail(series);
      }
    }

    for (File folder : folders) {
      addSelectionAndNotify(folder.listFiles(), false);
    }
  }

  public static void updateSeriesThumbnail(Set<DicomSeries> seriesList, DicomModel dicomModel) {
    if (dicomModel == null || seriesList == null) {
      return;
    }
    for (DicomSeries series : seriesList) {
      if (series != null) {
        if (!DicomModel.isHiddenModality(series)) {
          boolean split = seriesPostProcessing(series, dicomModel);
          if (!split) {
            dicomModel.buildThumbnail(series);
          }

          if (series.isSuitableFor3d()) {
            dicomModel.firePropertyChange(
                new ObservableEvent(
                    ObservableEvent.BasicAction.UPDATE,
                    series,
                    null,
                    new SeriesEvent(SeriesEvent.Action.UPDATE, series, null)));
          }
        }
      }
    }
  }

  public static boolean seriesPostProcessing(DicomSeries dicomSeries, DicomModel dicomModel) {
    return seriesPostProcessing(dicomSeries, dicomModel, false);
  }

  public static boolean seriesPostProcessing(
      DicomSeries dicomSeries, DicomModel dicomModel, boolean force) {
    Integer step = (Integer) dicomSeries.getTagValue(TagW.stepNDimensions);
    if (step == null || step < 1 || force) {
      int imageCount = dicomSeries.size(null);
      if (imageCount == 0) {
        return false;
      }
      List<DicomImageElement> imageList =
          dicomSeries.copyOfMedias(null, SortSeriesStack.slicePosition);
      int samplingRate = calculateSamplingRateFor4d(imageList);
      dicomSeries.setTag(TagW.stepNDimensions, samplingRate);
      if (samplingRate < 2 || (samplingRate > 7 && !force)) {
        return false;
      }
      for (int i = 0; i < samplingRate; i++) {
        DicomImageElement image = imageList.get(i);
        if (image.getMediaReader() instanceof DicomMediaIO dicomReader) {
          MediaSeries<DicomImageElement> newSeries;
          if (i == 0) {
            dicomSeries.removeAllMedias();
            newSeries = dicomSeries;
          } else {
            newSeries = dicomModel.splitSeries(dicomReader, dicomSeries);
          }
          newSeries.setTag(TagW.stepNDimensions, 1);
          Filter<DicomImageElement> samplingFilter = getDicomImageElementFilter(i, samplingRate);
          newSeries.addAll(Filter.makeList(samplingFilter.filter(imageList)));
          if (i == 0) {
            SeriesThumbnail thumbnail = (SeriesThumbnail) dicomSeries.getTagValue(TagW.Thumbnail);
            if (thumbnail != null) {
              thumbnail.reBuildThumbnail(null, MediaSeries.MEDIA_POSITION.MIDDLE);
            }
          }
          dicomModel.firePropertyChange(
              new ObservableEvent(ObservableEvent.BasicAction.UPDATE, dicomModel, null, newSeries));
        }
      }
      return true;
    }
    return false;
  }

  private static Filter<DicomImageElement> getDicomImageElementFilter(int index, int size) {
    return new Filter<>() {
      private final int samplingRate = size;
      private int currentIndex = index;

      @Override
      public boolean passes(DicomImageElement item) {
        boolean pass = (currentIndex % samplingRate) == 0;
        currentIndex++;
        return pass;
      }
    };
  }

  static int calculateSamplingRateFor4d(List<DicomImageElement> imageList) {
    try {
      if (imageList.size() >= 2) {
        double[] firstPos = (double[]) imageList.getFirst().getTagValue(TagW.SlicePosition);
        double firstPosSum = firstPos[0] + firstPos[1] + firstPos[2];

        int samePositionCount = 1;
        for (int i = 1; i < imageList.size(); i++) {
          double[] pos = (double[]) imageList.get(i).getTagValue(TagW.SlicePosition);
          double posSum = pos[0] + pos[1] + pos[2];
          if (Math.abs(posSum - firstPosSum) < 0.05) {
            samePositionCount++;
          } else {
            break;
          }
        }

        // If we found multiple images at the same position, that's likely our phase count
        if (samePositionCount > 1 && samePositionCount < imageList.size() / 2) {
          return samePositionCount;
        }
      }
    } catch (Exception e) {
      return 1;
    }

    return 1;
  }
}
