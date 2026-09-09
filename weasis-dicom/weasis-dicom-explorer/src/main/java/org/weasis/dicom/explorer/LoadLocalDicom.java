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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.Filter;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.SeriesThumbnail;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.NetworkFileSystems;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.model.GraphicModel;
import org.weasis.core.ui.serialize.XmlSerializer;
import org.weasis.core.util.FileUtil;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomMediaIO.Reading;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SortSeriesStack;
import org.weasis.dicom.codec.utils.DicomMediaUtils;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.imp.DicomZipCodec;
import org.weasis.dicom.explorer.imp.DicomZipMediaIO;

/**
 * Imports DICOM files and folders from the local file system. Directory entries are visited with
 * the attributes returned by the listing, every candidate file is opened once to check its magic
 * number and headers are parsed by a small pool of threads so that a network share is not driven
 * one round trip at a time. Files read from a network share or an optical disc can be copied into
 * the local cache first, so later pixel reads never go back to the slow medium.
 */
public class LoadLocalDicom extends LoadDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadLocalDicom.class);

  /** Preference key: copy DICOM files read from a network share or optical media into the cache. */
  public static final String NETWORK_CACHE = "weasis.import.dicom.network.cache"; // NON-NLS

  private static final int DICOM_PREAMBLE_LENGTH = 132;
  private static final byte[] DICM = "DICM".getBytes(StandardCharsets.US_ASCII);
  private static final byte[] ZIP_MAGIC = {'P', 'K', 3, 4};
  private static final String DICOMDIR = "DICOMDIR"; // NON-NLS
  private static final String SIDECAR_EXTENSION = ".xml"; // NON-NLS

  private final File[] files;
  private final boolean recursive;
  private final boolean cacheNetworkFiles;

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
    this.cacheNetworkFiles =
        GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(NETWORK_CACHE, true);
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    startLoadingEvent();
    if (files.length > 0) {
      openingStrategy.prepareImport();
      int threads = Math.clamp(Runtime.getRuntime().availableProcessors(), 2, 8);
      ExecutorService executor = ThreadUtil.newFixedThreadPool(threads, "DicomFileReader");
      try {
        new Importer(executor, threads * 4).importAll(files);
      } finally {
        executor.shutdownNow();
      }
    }
    return true;
  }

  /** Outcome of reading one file on a reader thread; {@code reader} is null for non-DICOM files. */
  private record ReadResult(Path file, DicomMediaIO reader, Reading status, boolean zip) {
    static final ReadResult NONE = new ReadResult(null, null, null, false);
  }

  private record PendingFile(Path file, Future<ReadResult> result, DirectoryState directory) {}

  /** Per-directory state: series to refresh and presentation sidecars matched by name. */
  private static final class DirectoryState {
    private final boolean copyToCache;
    private final boolean listed;
    private final Set<DicomSeries> series = new LinkedHashSet<>();
    private final Set<String> sidecars = new HashSet<>();
    private final Map<String, DicomMediaIO> awaitingSidecar = new HashMap<>();

    /** {@code listed} is true when the directory listing reveals which sidecar files exist. */
    DirectoryState(boolean copyToCache, boolean listed) {
      this.copyToCache = copyToCache;
      this.listed = listed;
    }
  }

  private final class Importer extends SimpleFileVisitor<Path> {
    private final ExecutorService executor;
    private final int window;
    private final Deque<PendingFile> pending = new ArrayDeque<>();
    private final Deque<DirectoryState> directories = new ArrayDeque<>();
    private final Set<DirectoryState> dirty = new LinkedHashSet<>();

    Importer(ExecutorService executor, int window) {
      this.executor = executor;
      this.window = window;
    }

    void importAll(File[] roots) throws IOException {
      List<Path> folders = new ArrayList<>();
      for (File root : roots) {
        if (isCancelled()) {
          return;
        }
        if (root == null) {
          continue;
        }
        Path path = root.toPath();
        BasicFileAttributes attrs = readAttributes(path);
        if (attrs == null) {
          continue;
        }
        if (attrs.isDirectory()) {
          folders.add(path);
        } else {
          submit(path, attrs, new DirectoryState(shouldCopy(path.getParent()), false));
        }
      }
      drainAll();

      for (Path folder : folders) {
        if (isCancelled()) {
          return;
        }
        Files.walkFileTree(
            folder,
            EnumSet.of(FileVisitOption.FOLLOW_LINKS),
            recursive ? Integer.MAX_VALUE : 1,
            this);
      }
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
      if (isCancelled()) {
        return FileVisitResult.TERMINATE;
      }
      // Finish the parent folder before descending, so its thumbnails show up first
      drainAll();
      directories.push(new DirectoryState(shouldCopy(dir), true));
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
      if (isCancelled()) {
        return FileVisitResult.TERMINATE;
      }
      if (attrs.isDirectory() || !attrs.isRegularFile()) {
        return FileVisitResult.CONTINUE;
      }
      DirectoryState directory = directories.peek();
      String name = file.getFileName().toString();
      if (name.equalsIgnoreCase(DICOMDIR)) {
        return FileVisitResult.CONTINUE;
      }
      if (name.toLowerCase(Locale.ROOT).endsWith(SIDECAR_EXTENSION)) {
        directory.sidecars.add(name);
        DicomMediaIO reader =
            directory.awaitingSidecar.remove(
                name.substring(0, name.length() - SIDECAR_EXTENSION.length()));
        if (reader != null) {
          applySidecar(reader, file);
        }
        return FileVisitResult.CONTINUE;
      }
      submit(file, attrs, directory);
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) {
      LOGGER.warn("Cannot read {}: {}", file, exc.getMessage());
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
      if (exc != null) {
        LOGGER.warn("Cannot list {}: {}", dir, exc.getMessage());
      }
      drainAll();
      DirectoryState directory = directories.pop();
      directory.awaitingSidecar.clear();
      return isCancelled() ? FileVisitResult.TERMINATE : FileVisitResult.CONTINUE;
    }

    private void submit(Path file, BasicFileAttributes attrs, DirectoryState directory) {
      if (attrs.size() == 0) {
        return;
      }
      Future<ReadResult> future = executor.submit(() -> readFile(file, directory.copyToCache));
      pending.add(new PendingFile(file, future, directory));
      while (pending.size() > window && !isCancelled()) {
        integrate(pending.poll());
      }
    }

    /** Integrates every pending file, then refreshes the thumbnails of the touched series. */
    private void drainAll() {
      while (!pending.isEmpty()) {
        PendingFile next = pending.poll();
        if (isCancelled()) {
          next.result().cancel(true);
        } else {
          integrate(next);
        }
      }
      for (DirectoryState directory : dirty) {
        refreshThumbnails(directory.series);
        directory.series.clear();
      }
      dirty.clear();
    }

    private void integrate(PendingFile pendingFile) {
      ReadResult result;
      try {
        result = pendingFile.result().get();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (ExecutionException e) {
        LOGGER.error("Cannot read {}", pendingFile.file(), e.getCause());
        errors.incrementAndGet();
        return;
      }
      if (result.zip()) {
        new DicomZipMediaIO(result.file().toUri(), null).delegate(dicomModel);
        return;
      }
      DicomMediaIO reader = result.reader();
      if (reader == null) {
        return;
      }
      switch (result.status()) {
        case READABLE -> {
          DirectoryState directory = pendingFile.directory();
          directory.series.add(buildDicomStructure(reader));
          dirty.add(directory);
          attachSidecar(reader, result.file(), directory);
        }
        case ERROR -> errors.incrementAndGet();
        case UNSUPPORTED -> unsupported.incrementAndGet();
        default -> {
          // Excluded objects (DICOMDIR) are ignored
        }
      }
    }

    private void attachSidecar(DicomMediaIO reader, Path file, DirectoryState directory) {
      String name = file.getFileName().toString();
      Path sidecar = file.resolveSibling(name + SIDECAR_EXTENSION);
      if (directory.listed) {
        // The folder listing tells whether the sidecar exists: no extra stat
        if (directory.sidecars.contains(sidecar.getFileName().toString())) {
          applySidecar(reader, sidecar);
        } else {
          directory.awaitingSidecar.put(name, reader);
        }
      } else {
        applySidecar(reader, sidecar);
      }
    }

    private void refreshThumbnails(Set<DicomSeries> series) {
      if (series.isEmpty()) {
        return;
      }
      if (openingStrategy.isFullImportSession()) {
        updateSeriesThumbnail(series, dicomModel);
      } else {
        for (DicomSeries s : series) {
          dicomModel.buildThumbnail(s);
        }
      }
    }
  }

  private boolean shouldCopy(Path directory) {
    return cacheNetworkFiles
        && directory != null
        && NetworkFileSystems.isSlowRandomAccess(directory);
  }

  private static BasicFileAttributes readAttributes(Path path) {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class);
    } catch (IOException e) {
      LOGGER.warn("Cannot read {}: {}", path, e.getMessage());
      return null;
    }
  }

  private static void applySidecar(DicomMediaIO reader, Path sidecar) {
    GraphicModel graphicModel = XmlSerializer.readPresentationModel(sidecar.toFile());
    if (graphicModel != null) {
      reader.setTag(TagW.PresentationModel, graphicModel);
    }
  }

  /** Runs on a reader thread: one open to sniff the magic number, then the header parse. */
  private static ReadResult readFile(Path file, boolean copyToCache) {
    boolean dicom = FileUtil.isFileExtensionMatching(file, DicomCodec.FILE_EXTENSIONS);
    boolean zip = !dicom && FileUtil.isFileExtensionMatching(file, DicomZipCodec.FILE_EXTENSIONS);
    if (!dicom && !zip) {
      byte[] head;
      try {
        head = readHead(file);
      } catch (IOException e) {
        LOGGER.warn("Cannot read {}: {}", file, e.getMessage());
        return ReadResult.NONE;
      }
      dicom = matches(head, DICM, 128);
      zip = !dicom && matches(head, ZIP_MAGIC, 0);
    }
    if (dicom) {
      return openReader(file, copyToCache);
    }
    return zip ? new ReadResult(file, null, null, true) : ReadResult.NONE;
  }

  private static byte[] readHead(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      return in.readNBytes(DICOM_PREAMBLE_LENGTH);
    }
  }

  private static boolean matches(byte[] head, byte[] magic, int offset) {
    if (head.length < offset + magic.length) {
      return false;
    }
    for (int i = 0; i < magic.length; i++) {
      if (head[offset + i] != magic[i]) {
        return false;
      }
    }
    return true;
  }

  private static ReadResult openReader(Path file, boolean copyToCache) {
    Path cached = copyToCache ? copyToCache(file) : null;
    Path source = cached == null ? file : cached;
    DicomMediaIO reader = new DicomMediaIO(source);
    Reading status = reader.getReadingStatus();
    if (status == Reading.READABLE) {
      if (source.startsWith(AppProperties.APP_TEMP_DIR)) {
        reader.getFileCache().setOriginalTempFile(source);
      }
    } else if (cached != null) {
      FileUtil.delete(cached);
    }
    return new ReadResult(file, reader, status, false);
  }

  /** Copies a file from a network share into the local cache with one bulk transfer. */
  private static Path copyToCache(Path file) {
    Path target = null;
    try {
      target = Files.createTempFile(DicomMediaIO.DICOM_EXPORT_DIR, "image_", ".dcm"); // NON-NLS
      Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
      return target;
    } catch (IOException e) {
      LOGGER.warn("Cannot copy {} into the local cache, reading it in place", file, e);
      FileUtil.delete(target);
      return null;
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

  /**
   * Checks if a series is a multi-phase series that can be separated.
   *
   * @param series the series to check
   * @return true if the series has multiple phases (step > 1) and is a DicomSeries
   */
  public static boolean isMultiPhaseSeries(MediaSeries<?> series) {
    return series.getTagValue(TagW.stepNDimensions) instanceof Integer step && step > 1;
  }

  public static MediaSeries<DicomImageElement> confirmSplittingMultiPhaseSeries(
      MediaSeries<DicomImageElement> series) {
    if (isMultiPhaseSeries(series) && series instanceof DicomSeries dicomSeries) {
      int result =
          JOptionPane.showConfirmDialog(
              GuiUtils.getUICore().getApplicationWindow(),
              Messages.getString("msg.multi.phase"),
              Messages.getString("multi.phase.title"),
              JOptionPane.OK_CANCEL_OPTION,
              JOptionPane.QUESTION_MESSAGE);

      if (result == JOptionPane.OK_OPTION) {
        DicomModel dicomModel =
            (DicomModel) dicomSeries.getTagValue(org.weasis.core.api.media.data.TagW.ExplorerModel);
        if (dicomModel != null) {
          LoadLocalDicom.seriesPostProcessing(dicomSeries, dicomModel, true);
          return dicomSeries;
        }
      }
      return null;
    }
    return series;
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
        double firstPosSum = DicomMediaUtils.getSlicePositionValue(imageList.getFirst());

        int samePositionCount = 1;
        for (int i = 1; i < imageList.size(); i++) {
          double posSum = DicomMediaUtils.getSlicePositionValue(imageList.get(i));
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
