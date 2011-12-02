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
package org.weasis.dicom.explorer;

import java.util.ArrayList;

import javax.swing.SwingWorker;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.explorer.wado.LoadRemoteDicomManifest;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class LoadDicomDir extends SwingWorker<Boolean, String> {

    private final ArrayList<LoadSeries> seriesList;
    private final DicomModel dicomModel;

    public LoadDicomDir(ArrayList<LoadSeries> seriesList, DataExplorerModel explorerModel) {
        if (seriesList == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.seriesList = seriesList;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        LoadRemoteDicomManifest.executor.setCorePoolSize(1);
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null,
            this));

        for (LoadSeries s : seriesList) {
            LoadRemoteDicomManifest.loadingQueue.offer(s);
            LoadRemoteDicomManifest.addLoadSeries(s, dicomModel);
        }

        LoadRemoteDicomManifest.executor.prestartAllCoreThreads();
        return true;
    }

    @Override
    protected void done() {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel, null,
            this));
    }

}
