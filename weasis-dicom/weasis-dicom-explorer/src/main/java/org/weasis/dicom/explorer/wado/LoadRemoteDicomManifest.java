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

import java.beans.PropertyChangeListener;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.PluginOpeningStrategy;
import org.weasis.dicom.explorer.pref.download.DicomExplorerPrefView;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;

public class LoadRemoteDicomManifest extends ExplorerTask<Boolean, String> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadRemoteDicomManifest.class);

  private final DicomModel dicomModel;
  private final List<String> xmlFiles;
  private final AtomicInteger retryNb = new AtomicInteger(0);
  private final List<LoadSeries> loadSeriesList = new ArrayList<>();
  private final PropertyChangeListener propertyChangeListener =
      evt -> {
        if (evt instanceof ObservableEvent event
            && event.getNewValue() instanceof LoadSeries series) {
          BasicAction cmd = event.getActionCommand();
          if (ObservableEvent.BasicAction.LOADING_STOP.equals(cmd)
              || ObservableEvent.BasicAction.LOADING_CANCEL.equals(cmd)) {
            checkDownloadIssues(series);
          } else if (ObservableEvent.BasicAction.LOADING_START.equals(cmd)
              && !loadSeriesList.contains(series)) {
            loadSeriesList.add(series);
          }
        }
      };

  public LoadRemoteDicomManifest(List<String> xmlFiles, DataExplorerModel explorerModel) {
    super(Messages.getString("DicomExplorer.loading"), true);
    if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
      throw new IllegalArgumentException("invalid parameters");
    }
    this.xmlFiles = xmlFiles.stream().filter(Objects::nonNull).collect(Collectors.toList());
    this.dicomModel = (DicomModel) explorerModel;
  }

  private void checkDownloadIssues(LoadSeries loadSeries) {
    if (!loadSeries.hasDownloadFailed()) {
      loadSeriesList.remove(loadSeries);
      return;
    }

    if (DownloadManager.getTasks().isEmpty()
        || DownloadManager.getTasks().stream().allMatch(LoadSeries::isStopped)) {
      if (!loadSeriesList.isEmpty() && tryDownloadingAgain(null)) {
        LOGGER.info("Try downloading ({}) the missing elements", retryNb.get());
        List<LoadSeries> oldList = new ArrayList<>(loadSeriesList);
        loadSeriesList.clear();
        dicomModel.removePropertyChangeListener(propertyChangeListener);
        for (LoadSeries s : oldList) {
          LoadSeries task;
          if (s.isStopped()) {
            task = s;
          } else {
            task = s.cancelAndReplace(s, true);
          }
          loadSeriesList.add(task);
        }
        startDownloadingSeries(loadSeriesList, true, true);

        dicomModel.addPropertyChangeListener(propertyChangeListener);
      } else {
        dicomModel.removePropertyChangeListener(propertyChangeListener);
      }
    }
  }

  private boolean tryDownloadingAgain(DownloadException e) {
    if (retryNb.getAndIncrement() == 0) {
      return true;
    }
    boolean[] ret = {false};
    GuiExecutor.invokeAndWait(
        () -> {
          int confirm =
              JOptionPane.showConfirmDialog(
                  GuiUtils.getUICore().getApplicationWindow(),
                  getErrorMessage(e),
                  Messages.getString("LoadRemoteDicomManifest.net_err_msg"),
                  JOptionPane.YES_NO_OPTION);
          ret[0] = JOptionPane.YES_OPTION == confirm;
        });
    return ret[0];
  }

  private static String getErrorMessage(DownloadException e) {
    StringBuilder buf = new StringBuilder();
    if (e == null) { // images
      buf.append(Messages.getString("LoadRemoteDicomManifest.cannot_download"));
    } else { // xml manifest
      buf.append(StringUtil.getTruncatedString(e.getMessage(), 130, Suffix.THREE_PTS));
      if (e.getCause() instanceof StreamIOException) {
        String serverMessage = e.getCause().getMessage();
        if (StringUtil.hasText(serverMessage)) {
          buf.append("\n");
          buf.append(Messages.getString("LoadRemoteDicomManifest.server_resp"));
          buf.append(StringUtil.COLON_AND_SPACE);
          buf.append(StringUtil.getTruncatedString(serverMessage, 100, Suffix.THREE_PTS));
        }
      }
    }
    buf.append("\n\n");
    buf.append(Messages.getString("LoadRemoteDicomManifest.download_again"));
    return buf.toString();
  }

  @Override
  protected void done() {
    DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    try {
      for (String xmlFile : xmlFiles) {
        downloadManifest(xmlFile);
      }
    } catch (DownloadException e) {
      LOGGER.error("Download failed", e);
      if (tryDownloadingAgain(e)) {
        LOGGER.info("Try downloading again: {}", xmlFiles);
        LoadRemoteDicomManifest mf = new LoadRemoteDicomManifest(xmlFiles, dicomModel);
        mf.retryNb.set(retryNb.get());
        mf.execute();
      }
    }

    // Add a listener to know when download of series ends
    dicomModel.addPropertyChangeListener(propertyChangeListener);

    return true;
  }

  private void downloadManifest(String path) throws DownloadException {
    try {
      URI uri = NetworkUtil.getURI(path);
      Collection<LoadSeries> wadoTasks = DownloadManager.buildDicomSeriesFromXml(uri, dicomModel);

      loadSeriesList.addAll(wadoTasks);
      boolean downloadImmediately =
          GuiUtils.getUICore()
              .getSystemPreferences()
              .getBooleanProperty(DicomExplorerPrefView.DOWNLOAD_IMMEDIATELY, true);
      startDownloadingSeries(wadoTasks, downloadImmediately, false);
      if (!downloadImmediately) {
        LoadSeries.notifyDownloadCompletion(dicomModel);
      }
    } catch (URISyntaxException | MalformedURLException e) {
      LOGGER.error("Loading manifest", e);
    }
  }

  private void startDownloadingSeries(
      Collection<LoadSeries> wadoTasks, boolean downloadImmediately, boolean retry) {
    if (!wadoTasks.isEmpty()) {
      PluginOpeningStrategy openingStrategy =
          new PluginOpeningStrategy(DownloadManager.getOpeningViewer());
      if (!retry) {
        openingStrategy.prepareImport();
      }
      for (final LoadSeries loadSeries : wadoTasks) {
        loadSeries.setPOpeningStrategy(openingStrategy);
        DownloadManager.addLoadSeries(loadSeries, dicomModel, downloadImmediately);
      }

      // Sort tasks from the download priority order (low number has a higher priority), TASKS
      // are sorted from low-to-high priority.
      DownloadManager.getTasks().sort(Collections.reverseOrder(new PriorityTaskComparator()));
    }
  }
}
