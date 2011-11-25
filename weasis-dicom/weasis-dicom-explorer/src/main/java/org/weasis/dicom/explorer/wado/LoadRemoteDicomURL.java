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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.swing.SwingWorker;

import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.explorer.model.TreeModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;

public class LoadRemoteDicomURL extends SwingWorker<Boolean, String> {

    private final URL[] urls;
    private final DicomModel dicomModel;

    public LoadRemoteDicomURL(String[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        URL[] urlRef = new URL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                try {
                    urlRef[i] = new URL(urls[i]);
                } catch (MalformedURLException e) {
                    e.printStackTrace();
                }
            }
        }
        this.urls = urlRef;
        this.dicomModel = (DicomModel) explorerModel;
    }

    public LoadRemoteDicomURL(URL[] urls, DataExplorerModel explorerModel) {
        if (urls == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.urls = urls;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStart, dicomModel, null,
            this));
        String seriesUID = null;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                seriesUID = urls[i].toString();
                break;
            }
        }
        if (seriesUID != null) {
            String unknown = org.weasis.dicom.codec.Messages.getString("DicomMediaIO.unknown");//$NON-NLS-1$
            MediaSeriesGroup patient = dicomModel.getHierarchyNode(TreeModel.rootNode, unknown);
            if (patient == null) {
                patient = new MediaSeriesGroupNode(TagW.PatientPseudoUID, unknown, TagW.PatientName);
                patient.setTag(TagW.PatientID, unknown);
                patient.setTag(TagW.PatientName, unknown);
                dicomModel.addHierarchyNode(TreeModel.rootNode, patient);
            }
            MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, unknown);
            if (study == null) {
                study = new MediaSeriesGroupNode(TagW.StudyInstanceUID, unknown, TagW.StudyDate);
                dicomModel.addHierarchyNode(patient, study);
            }
            Series dicomSeries = dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
            dicomSeries.setTag(TagW.SeriesInstanceUID, seriesUID);
            final WadoParameters wadoParameters = new WadoParameters("", false, "", null, null); //$NON-NLS-1$ //$NON-NLS-2$
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            List<DicomInstance> dicomInstances = new ArrayList<DicomInstance>();
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, dicomInstances);
            dicomModel.addHierarchyNode(study, dicomSeries);
            for (int i = 0; i < urls.length; i++) {
                if (urls[i] != null) {
                    String url = urls[i].toString();
                    DicomInstance dcmInstance = new DicomInstance(url, null);
                    dcmInstance.setDirectDownloadFile(url);
                    dicomInstances.add(dcmInstance);
                }
            }

            if (dicomInstances.size() > 0) {
                String modality = (String) dicomSeries.getTagValue(TagW.Modality);
                boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
                final LoadSeries loadSeries = new LoadSeries(dicomSeries, dicomModel);
                if (!ps) {
                    loadSeries.startDownloadImageReference(wadoParameters);
                }

                Integer sn = (Integer) (ps ? Integer.MAX_VALUE : dicomSeries.getTagValue(TagW.SeriesNumber));

                DownloadPriority priority =
                    new DownloadPriority((String) patient.getTagValue(TagW.PatientName),
                        (String) study.getTagValue(TagW.StudyInstanceUID), (Date) study.getTagValue(TagW.StudyDate), sn);
                loadSeries.setPriority(priority);
                LoadRemoteDicomManifest.loadingQueue.offer(loadSeries);
                LoadRemoteDicomManifest.addLoadSeries(loadSeries, dicomModel);
                LoadRemoteDicomManifest.executor.prestartAllCoreThreads();
            }
        }
        return true;
    }

    @Override
    protected void done() {
        dicomModel.firePropertyChange(new ObservableEvent(ObservableEvent.BasicAction.LoadingStop, dicomModel, null,
            this));
    }

}
