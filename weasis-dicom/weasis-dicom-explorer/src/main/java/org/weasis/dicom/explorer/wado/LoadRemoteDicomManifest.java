/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.explorer.wado;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.download.SeriesDownloadPrefUtils;
import org.weasis.dicom.explorer.wado.DownloadManager.PriorityTaskComparator;

public class LoadRemoteDicomManifest extends ExplorerTask {

    private final String[] xmlFiles;
    private final DicomModel dicomModel;

    public LoadRemoteDicomManifest(String[] xmlFiles, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.xmlFiles = xmlFiles;
        this.dicomModel = (DicomModel) explorerModel;
    }

    public LoadRemoteDicomManifest(File[] xmlFiles, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (xmlFiles == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        String[] xmlRef = new String[xmlFiles.length];
        for (int i = 0; i < xmlFiles.length; i++) {
            if (xmlFiles[i] != null) {
                xmlRef[i] = xmlFiles[i].getAbsolutePath();
            }
        }
        this.xmlFiles = xmlRef;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null,
            this));
        for (int i = 0; i < xmlFiles.length; i++) {
            if (xmlFiles[i] != null) {
                URI uri = null;
                try {
                    if (!xmlFiles[i].startsWith("http")) { //$NON-NLS-1$
                        try {
                            File file = new File(xmlFiles[i]);
                            if (file.canRead()) {
                                uri = file.toURI();
                            }
                        } catch (Exception e) {
                        }
                    }
                    if (uri == null) {
                        uri = new URL(xmlFiles[i]).toURI();
                    }
                    ArrayList<LoadSeries> wadoTasks = DownloadManager.buildDicomSeriesFromXml(uri, dicomModel);

                    if (wadoTasks != null) {
                        boolean downloadImmediately = SeriesDownloadPrefUtils.downloadImmediately();
                        for (final LoadSeries loadSeries : wadoTasks) {
                            DownloadManager.addLoadSeries(loadSeries, dicomModel, downloadImmediately);
                        }
                        // Sort tasks from the download priority order (low number has a higher priority), TASKS
                        // is sorted from low to high priority).
                        Collections.sort(DownloadManager.TASKS, Collections.reverseOrder(new PriorityTaskComparator()));
                    }
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }

        DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
        return true;
    }

    @Override
    protected void done() {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel, null,
            this));
    }

}
