/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.beans.PropertyChangeListener;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.NetworkUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.util.StreamIOException;
import org.weasis.core.util.StringUtil;
import org.weasis.core.util.StringUtil.Suffix;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefView;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;

public class LoadRemoteDicomManifest extends ExplorerTask<Boolean, String> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadRemoteDicomManifest.class);

    private final DicomModel dicomModel;
    private final List<String> xmlFiles;
    private final AtomicInteger retryNb = new AtomicInteger(0);
    private final List<LoadSeries> loadSeriesList = new ArrayList<>();
    private final PropertyChangeListener propertyChangeListener = evt -> {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            if (event.getNewValue() instanceof LoadSeries) {
                LoadSeries s = (LoadSeries) event.getNewValue();
                BasicAction cmd = event.getActionCommand();
                if (ObservableEvent.BasicAction.LOADING_STOP.equals(cmd)
                    || ObservableEvent.BasicAction.LOADING_CANCEL.equals(cmd)) {
                    checkDownloadIssues(s);
                } else if (ObservableEvent.BasicAction.LOADING_START.equals(cmd)) {
                    if (!loadSeriesList.contains(s)) {
                        loadSeriesList.add(s);
                    }
                }
            }
        }
    };

    public LoadRemoteDicomManifest(List<String> xmlFiles, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.xmlFiles = xmlFiles.stream().filter(Objects::nonNull).collect(Collectors.toList());
        this.dicomModel = (DicomModel) explorerModel;
    }

    private void checkDownloadIssues(LoadSeries loadSeries) {
        if (!loadSeries.hasDownloadFailed()) {
            loadSeriesList.remove(loadSeries);
        }

        if (DownloadManager.TASKS.isEmpty() || DownloadManager.TASKS.stream().allMatch(LoadSeries::isStopped)) {
            if (!loadSeriesList.isEmpty() && tryDownloadingAgain(null)) {
                LOGGER.info("Try downloading ({}) the missing elements", retryNb.get()); //$NON-NLS-1$
                List<LoadSeries> oldList = new ArrayList<>(loadSeriesList);
                loadSeriesList.clear();
                dicomModel.removePropertyChangeListener(propertyChangeListener);
                for (LoadSeries s : oldList) {
                    LoadSeries task = s.cancelAndReplace(s);
                    loadSeriesList.add(task);
                }
                startDownloadingSeries(loadSeriesList, true);

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
        boolean[] ret = { false };
        GuiExecutor.instance().invokeAndWait(() -> {
            int confirm = JOptionPane.showConfirmDialog(UIManager.getApplicationWindow(), getErrorMessage(e),
                Messages.getString("LoadRemoteDicomManifest.net_err_msg"), JOptionPane.YES_NO_OPTION); //$NON-NLS-1$
            ret[0] = JOptionPane.YES_OPTION == confirm;
        });
        return ret[0];
    }

    private static String getErrorMessage(DownloadException e) {
        StringBuilder buf = new StringBuilder();
        if (e == null) { // images
            buf.append(Messages.getString("LoadRemoteDicomManifest.cannot_download")); //$NON-NLS-1$
        } else { // xml manifest
            buf.append(StringUtil.getTruncatedString(e.getMessage(), 130, Suffix.THREE_PTS));
            if (e.getCause() instanceof StreamIOException) {
                String serverMessage = e.getCause().getMessage();
                if (StringUtil.hasText(serverMessage)) {
                    buf.append("\n"); //$NON-NLS-1$
                    buf.append(Messages.getString("LoadRemoteDicomManifest.server_resp")); //$NON-NLS-1$
                    buf.append(StringUtil.COLON_AND_SPACE);
                    buf.append(StringUtil.getTruncatedString(serverMessage, 100, Suffix.THREE_PTS));
                }
            }
        }
        buf.append("\n\n"); //$NON-NLS-1$
        buf.append(Messages.getString("LoadRemoteDicomManifest.download_again")); //$NON-NLS-1$
        return buf.toString();
    }

    @Override
    protected void done() {
        DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        try {
            Iterator<String> iter = xmlFiles.iterator();
            while (iter.hasNext()) {
                downloadManifest(iter);
            }
        } catch (DownloadException e) {
            LOGGER.error("Download failed", e); //$NON-NLS-1$
            if (tryDownloadingAgain(e)) {
                LOGGER.info("Try donloaging again: {}", xmlFiles); //$NON-NLS-1$
                LoadRemoteDicomManifest mf = new LoadRemoteDicomManifest(xmlFiles, dicomModel);
                mf.retryNb.set(retryNb.get());
                mf.execute();
            }
        }

        // Add listener to know when download of series ends
        dicomModel.addPropertyChangeListener(propertyChangeListener);

        return true;
    }

    private void downloadManifest(Iterator<String> iter) throws DownloadException {
        try {
            URI uri = NetworkUtil.getURI(iter.next());
            Collection<LoadSeries> wadoTasks = DownloadManager.buildDicomSeriesFromXml(uri, dicomModel);
            iter.remove();

            if (wadoTasks != null) {
                loadSeriesList.addAll(wadoTasks);
                boolean downloadImmediately = BundleTools.SYSTEM_PREFERENCES
                    .getBooleanProperty(SeriesDownloadPrefView.DOWNLOAD_IMMEDIATELY, true);
                startDownloadingSeries(wadoTasks, downloadImmediately);
            }
        } catch (URISyntaxException | MalformedURLException e) {
            LOGGER.error("Loading manifest", e); //$NON-NLS-1$
        }
    }

    private void startDownloadingSeries(Collection<LoadSeries> wadoTasks, boolean downloadImmediately) {
        for (final LoadSeries loadSeries : wadoTasks) {
            DownloadManager.addLoadSeries(loadSeries, dicomModel, downloadImmediately);
        }

        // Sort tasks from the download priority order (low number has a higher priority), TASKS
        // is sorted from low to high priority).
        Collections.sort(DownloadManager.TASKS, Collections.reverseOrder(new PriorityTaskComparator()));
    }
}
