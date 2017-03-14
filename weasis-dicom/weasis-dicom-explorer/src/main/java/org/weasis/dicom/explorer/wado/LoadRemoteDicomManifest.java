/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefView;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;

public class LoadRemoteDicomManifest extends ExplorerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadRemoteDicomManifest.class);

    private final DicomModel dicomModel;
    private final List<String> xmlFiles;
    private final AtomicInteger retryNb = new AtomicInteger(0);
    private final List<LoadSeries> loadSeriesList = new ArrayList<>();
    private final PropertyChangeListener propertyChangeListener = evt -> {
        if (evt instanceof ObservableEvent) {
            ObservableEvent event = (ObservableEvent) evt;
            if (ObservableEvent.BasicAction.LOADING_STOP.equals(event.getActionCommand())) {
                if (event.getNewValue() instanceof LoadSeries) {
                    checkDownloadIssues((LoadSeries) event.getNewValue());
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

        if (DownloadManager.TASKS.isEmpty()) {
            if (!loadSeriesList.isEmpty() && tryDownloadingAgain()) {
                LOGGER.info("Try downloading ({}) the missing elements", retryNb.get());
                List<LoadSeries> oldList = new ArrayList<>(loadSeriesList);
                loadSeriesList.clear();
                for (LoadSeries s : oldList) {
                    LoadSeries taskResume = s.getCopy(s);
                    loadSeriesList.add(taskResume);      
                }

                startDownloadingSeries(loadSeriesList, true);
            }
            else {
                dicomModel.removePropertyChangeListener(propertyChangeListener);
            }
        }
    }

    private boolean tryDownloadingAgain() {
        if(retryNb.getAndIncrement() == 0) {
            return true;
        }
        boolean[] ret = { false };
        GuiExecutor.instance().invokeAndWait(() -> {
            PluginTool explorer = null;
            DataExplorerView dicomExplorer = UIManager.getExplorerplugin(DicomExplorer.NAME);
            if (dicomExplorer instanceof PluginTool) {
                explorer = (PluginTool) dicomExplorer;
            }
            int confirm = JOptionPane.showConfirmDialog(WinUtil.getParentWindow(explorer),
                "Network error, cannot download.\nTry to download again the missing elements?", null,
                JOptionPane.YES_NO_OPTION);
            ret[0] = JOptionPane.YES_OPTION == confirm;
        });
        return ret[0];
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        try {
            Iterator<String> iter = xmlFiles.iterator();
            while(iter.hasNext()){
                downloadManifest(iter);
            }
        } catch (DownloadException e) {
            LOGGER.error("Download failed", e); //$NON-NLS-1$
            if (tryDownloadingAgain()) {
                LOGGER.info("Try donloaging again: {}", xmlFiles);
                new LoadRemoteDicomManifest(xmlFiles, dicomModel).execute();
            }
        }

        // Add listener to know when download of series ends
        dicomModel.addPropertyChangeListener(propertyChangeListener);

        DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
        return true;
    }

    private void downloadManifest(Iterator<String> iter) throws DownloadException {
        URI uri = null;
        try {
            String path = iter.next();
            if (!path .startsWith("http")) { //$NON-NLS-1$
                try {
                    File file = new File(path);
                    if (file.canRead()) {
                        uri = file.toURI();
                    }
                } catch (Exception e) {
                    // Do nothing
                }
            }
            if (uri == null) {
                uri = new URL(path).toURI();
            }

            List<LoadSeries> wadoTasks = DownloadManager.buildDicomSeriesFromXml(uri, dicomModel);
            iter.remove();

            if (wadoTasks != null) {
                loadSeriesList.addAll(wadoTasks);
                boolean downloadImmediately =
                                BundleTools.SYSTEM_PREFERENCES.getBooleanProperty(SeriesDownloadPrefView.DOWNLOAD_IMMEDIATELY, true);
                startDownloadingSeries(wadoTasks, downloadImmediately);
            }
        } catch (URISyntaxException | MalformedURLException e) {
            LOGGER.error("Loading manifest", e);
        }
    }

    private void startDownloadingSeries(List<LoadSeries> series, boolean downloadImmediately) {
        for (final LoadSeries loadSeries : series) {
            DownloadManager.addLoadSeries(loadSeries, dicomModel, downloadImmediately);
        }

        // Sort tasks from the download priority order (low number has a higher priority), TASKS
        // is sorted from low to high priority).
        Collections.sort(DownloadManager.TASKS, Collections.reverseOrder(new PriorityTaskComparator()));
    }
}
