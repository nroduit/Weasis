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
package org.weasis.dicom.explorer;

import java.util.List;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.dicom.explorer.wado.DownloadManager;
import org.weasis.dicom.explorer.wado.LoadSeries;

public class LoadDicomDir extends ExplorerTask {

    private final List<LoadSeries> seriesList;
    private final DicomModel dicomModel;

    public LoadDicomDir(List<LoadSeries> loadSeries, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (loadSeries == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.seriesList = loadSeries;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        for (LoadSeries s : seriesList) {
            DownloadManager.addLoadSeries(s, dicomModel, true);
        }

        DownloadManager.UNIQUE_EXECUTOR.prestartAllCoreThreads();
        return true;
    }

}
