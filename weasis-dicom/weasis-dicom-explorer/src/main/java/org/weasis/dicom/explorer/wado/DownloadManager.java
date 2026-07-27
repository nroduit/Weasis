/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.wado;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker.StateValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Thumbnail;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.util.GzipManager;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomSorter;
import org.weasis.dicom.explorer.HangingProtocols.OpeningViewer;
import org.weasis.dicom.explorer.Messages;

public class DownloadManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(DownloadManager.class);

  public enum Status {
    DOWNLOADING,
    COMPLETE,
    ERROR
  }

  public static final String CONCURRENT_SERIES = "download.concurrent.series";
  private static final List<LoadSeries> TASKS = new ArrayList<>();

  // Executor without concurrency (only one task is executed at the same time)
  private static final BlockingQueue<Runnable> UNIQUE_QUEUE =
      new PriorityBlockingQueue<>(10, new PriorityTaskComparator());
  public static final ThreadPoolExecutor UNIQUE_EXECUTOR =
      new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, UNIQUE_QUEUE);

  // Executor with simultaneous tasks
  private static final BlockingQueue<Runnable> PRIORITY_QUEUE =
      new PriorityBlockingQueue<>(10, new PriorityTaskComparator());
  public static final ThreadPoolExecutor CONCURRENT_EXECUTOR =
      new ThreadPoolExecutor(
          GuiUtils.getUICore().getSystemPreferences().getIntProperty(CONCURRENT_SERIES, 3),
          GuiUtils.getUICore().getSystemPreferences().getIntProperty(CONCURRENT_SERIES, 3),
          0L,
          TimeUnit.MILLISECONDS,
          PRIORITY_QUEUE,
          ThreadUtil.namedThreadFactory("SeriesDownloader"));

  public static class PriorityTaskComparator implements Comparator<Runnable> {

    @Override
    public int compare(final Runnable r1, final Runnable r2) {
      LoadSeries o1 = (LoadSeries) r1;
      LoadSeries o2 = (LoadSeries) r2;
      DownloadPriority val1 = o1.getPriority();
      DownloadPriority val2 = o2.getPriority();

      int rep = val1.getPriority().compareTo(val2.getPriority());
      if (rep != 0) {
        return rep;
      }
      if (val1.getPatient() != val2.getPatient()) {
        rep = DicomSorter.PATIENT_COMPARATOR.compare(val1.getPatient(), val2.getPatient());
      }
      if (rep != 0) {
        return rep;
      }

      if (val1.getStudy() != val2.getStudy()) {
        rep = DicomSorter.STUDY_COMPARATOR.compare(val1.getStudy(), val2.getStudy());
      }
      if (rep != 0) {
        return rep;
      }
      return DicomSorter.SERIES_COMPARATOR.compare(val1.getSeries(), val2.getSeries());
    }
  }

  private DownloadManager() {}

  public static List<LoadSeries> getTasks() {
    return TASKS;
  }

  public static OpeningViewer getOpeningViewer() {
    return OpeningViewer.ALL_PATIENTS;
  }

  public static boolean removeSeriesInQueue(final LoadSeries series) {
    return series.getPriority().hasConcurrentDownload()
        ? DownloadManager.PRIORITY_QUEUE.remove(series)
        : DownloadManager.UNIQUE_QUEUE.remove(series);
  }

  public static void offerSeriesInQueue(final LoadSeries series) {
    if (series.getPriority().hasConcurrentDownload()) {
      if (!DownloadManager.PRIORITY_QUEUE.offer(series)) {
        LOGGER.warn("Cannot add series {} to download queue", series.getDicomSeries());
      }
    } else {
      if (!DownloadManager.UNIQUE_QUEUE.offer(series)) {
        LOGGER.warn("Cannot add series {} to download queue", series.getDicomSeries());
      }
    }
  }

  public static synchronized void addLoadSeries(
      final LoadSeries series, DicomModel dicomModel, boolean startLoading) {
    if (series != null) {
      boolean isHiddenSeries = DicomModel.isHiddenModality(series.getDicomSeries());
      if (startLoading || isHiddenSeries) {
        offerSeriesInQueue(series);
      } else {
        GuiExecutor.execute(
            () -> {
              series.getProgressBar().setValue(0);
              series.stop();
            });
      }
      if (dicomModel != null) {
        dicomModel.firePropertyChange(
            new ObservableEvent(
                ObservableEvent.BasicAction.LOADING_START, dicomModel, null, series));
      }
      if (!isHiddenSeries && !DownloadManager.TASKS.contains(series)) {
        DownloadManager.TASKS.add(series);
      }
    }
  }

  public static synchronized void removeLoadSeries(LoadSeries series, DicomModel dicomModel) {
    if (series != null) {
      DownloadManager.TASKS.remove(series);
      if (dicomModel != null) {
        if (series.isCancelled()) {
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.LOADING_CANCEL, dicomModel, null, series));
        } else {
          dicomModel.firePropertyChange(
              new ObservableEvent(
                  ObservableEvent.BasicAction.LOADING_STOP, dicomModel, null, series));
        }
      }
      if (DownloadManager.TASKS.isEmpty()) {
        // When all loadseries are ended, reset to default the number of simultaneous download
        // (series)
        DownloadManager.CONCURRENT_EXECUTOR.setCorePoolSize(
            GuiUtils.getUICore()
                .getSystemPreferences()
                .getIntProperty(DownloadManager.CONCURRENT_SERIES, 3));
      }
    }
  }

  public static boolean hasRunningTasks() {
    synchronized (DownloadManager.getTasks()) {
      return DownloadManager.getTasks().stream()
          .anyMatch(loadSeries -> StateValue.STARTED.equals(loadSeries.getState()));
    }
  }

  public static void stopDownloading(DicomSeries series, DicomModel dicomModel) {
    if (series != null) {
      synchronized (DownloadManager.getTasks()) {
        for (final LoadSeries loading : DownloadManager.getTasks()) {
          if (loading.getDicomSeries() == series) {
            removeLoadSeries(loading, dicomModel);
            removeSeriesInQueue(loading);
            if (StateValue.STARTED.equals(loading.getState())) {
              loading.cancel();
            }
            // Ensure to stop downloading
            series.setSeriesLoader(null);
            break;
          }
        }
      }
    }
  }

  public static void resume() {
    handleAllSeries(LoadSeries::resume);
  }

  public static void stop() {
    handleAllSeries(LoadSeries::stop);
  }

  private static void handleAllSeries(LoadSeriesHandler handler) {
    for (LoadSeries loadSeries : new ArrayList<>(DownloadManager.getTasks())) {
      handler.handle(loadSeries);
      Thumbnail thumbnail = (Thumbnail) loadSeries.getDicomSeries().getTagValue(TagW.Thumbnail);
      if (thumbnail != null) {
        thumbnail.repaint();
      }
    }
  }

  @FunctionalInterface
  private interface LoadSeriesHandler {
    void handle(LoadSeries loadSeries);
  }

  public static Collection<LoadSeries> buildDicomSeries(URI uri, final DicomModel model)
      throws DownloadException {
    Map<String, LoadSeries> seriesMap = new HashMap<>();
    try {
      Path manifestFile = downloadManifest(uri);
      ReaderParams params = new ReaderParams(model, seriesMap);
      if (isJsonManifest(manifestFile)) {
        JsonManifestParser.parse(manifestFile, params);
      } else {
        XmlManifestParser.parse(manifestFile, params);
      }
    } catch (StreamIOException e) {
      throw new DownloadException(getErrorMessage(uri), e); // rethrow network issue
    } catch (Exception e) {
      String message = getErrorMessage(uri);
      LOGGER.error("{}", message, e);

      GuiExecutor.execute(
          () -> {
            ColorLayerUI layer =
                ColorLayerUI.createTransparentLayerUI(GuiUtils.getUICore().getBaseArea());
            JOptionPane.showOptionDialog(
                WinUtil.getValidComponent(ColorLayerUI.getContentPane(layer)),
                StringUtil.getTruncatedString(message, 130, Suffix.THREE_PTS),
                null,
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                null,
                null);
            if (layer != null) {
              layer.hideUI();
            }
          });
    }
    return seriesMap.values();
  }

  /** Downloads the manifest, decompressing it if needed, and returns a local file to parse. */
  private static Path downloadManifest(URI uri) throws IOException {
    String path = uri.getPath();
    URLParameters urlParameters =
        new URLParameters(
            manifestAcceptHeaders(),
            StringUtil.getInt(System.getProperty("UrlConnectionTimeout"), 7000),
            StringUtil.getInt(System.getProperty("UrlReadTimeout"), 15000) * 2);
    ClosableURLConnection urlConnection = NetworkUtil.getUrlConnection(uri.toURL(), urlParameters);

    LOGGER.info("Downloading manifest: {}", path);
    try (InputStream urlInputStream = urlConnection.getInputStream()) {
      // Detect gzip by its magic number rather than the file extension, so a compressed manifest
      // is decompressed whatever its name (.gz, .json, .xml or no extension). The parser format is
      // then chosen from the content itself (see isJsonManifest).
      PushbackInputStream pb = new PushbackInputStream(new BufferedInputStream(urlInputStream), 4);
      boolean gzip = startsWithGzipMagic(pb);
      InputStream stream = gzip ? new GZIPInputStream(pb) : pb;

      // A local, uncompressed manifest can be parsed in place without copying it to a temp file.
      if (!gzip
          && uri.toString().startsWith("file:") // NON-NLS
          && (path.endsWith(".xml") || path.endsWith(".json"))) {
        return Path.of(path);
      }

      Path tempFile =
          Files.createTempFile(AppProperties.APP_TEMP_DIR, "wado_", ".manifest"); // NON-NLS
      FileUtil.writeStreamWithIOException(stream, tempFile);
      return tempFile;
    }
  }

  /** Peeks the first two bytes for the gzip magic number, leaving the stream repositioned. */
  private static boolean startsWithGzipMagic(PushbackInputStream in) throws IOException {
    byte[] header = new byte[4];
    int n = in.read(header, 0, 4);
    if (n > 0) {
      in.unread(header, 0, n);
    }
    return n == 4 && GzipManager.isGzip(header);
  }

  /** Detects a JSON manifest by sniffing the first meaningful character. */
  private static boolean isJsonManifest(Path file) throws IOException {
    try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
      int c = in.read();
      // Skip an optional UTF-8 BOM
      if (c == 0xEF) {
        in.read();
        in.read();
        c = in.read();
      }
      while (c != -1 && Character.isWhitespace(c)) {
        c = in.read();
      }
      return c == '{';
    }
  }

  /**
   * Negotiates the manifest format. XML is requested by default: the manifest size is unknown
   * before it is fetched and an instance-level manifest can be large, so the StAX parser streams it
   * instead of buffering the whole document like the JSON parser. A single value (not a {@code
   * q}-weighted list) is sent on purpose: the manifest server (viewer-hub) selects the format by a
   * plain substring match on the header, so any {@code application/json} token would make it return
   * JSON. The response format is still detected from the bytes (see {@link #isJsonManifest}), so a
   * single-format server keeps working. Set {@code -Dweasis.manifest.accept=json} to force JSON.
   */
  private static Map<String, String> manifestAcceptHeaders() {
    String accept =
        "json".equalsIgnoreCase(System.getProperty("weasis.manifest.accept")) // NON-NLS
            ? "application/json" // NON-NLS
            : "application/xml"; // NON-NLS
    return Map.of("Accept", accept, "Accept-Encoding", "gzip"); // NON-NLS
  }

  private static String getErrorMessage(URI uri) {
    return Messages.getString("DownloadManager.error_load_xml")
        + StringUtil.COLON_AND_SPACE
        + uri.toString();
  }
}
