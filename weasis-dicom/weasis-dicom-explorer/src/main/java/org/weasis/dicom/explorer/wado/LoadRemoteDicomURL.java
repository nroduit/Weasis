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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.DicomInstance;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.wado.WadoParameters;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;

public class LoadRemoteDicomURL extends ExplorerTask {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadRemoteDicomURL.class);

    private final URL[] urls;
    private final DicomModel dicomModel;

    public LoadRemoteDicomURL(String[] urls, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (urls == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        URL[] urlRef = new URL[urls.length];
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                try {
                    urlRef[i] = new URL(urls[i]);
                } catch (MalformedURLException e) {
                    LOGGER.error("Not a valid URL", e);
                }
            }
        }
        this.urls = urlRef;
        this.dicomModel = (DicomModel) explorerModel;
    }

    public LoadRemoteDicomURL(URL[] urls, DataExplorerModel explorerModel) {
        super(Messages.getString("DicomExplorer.loading"), true); //$NON-NLS-1$
        if (urls == null || !(explorerModel instanceof DicomModel)) {
            throw new IllegalArgumentException("invalid parameters"); //$NON-NLS-1$
        }
        this.urls = urls;
        this.dicomModel = (DicomModel) explorerModel;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        String seriesUID = null;
        for (int i = 0; i < urls.length; i++) {
            if (urls[i] != null) {
                seriesUID = urls[i].toString();
                break;
            }
        }
        if (seriesUID != null) {
            String unknown = TagW.NO_VALUE;
            MediaSeriesGroup patient = dicomModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, unknown);
            if (patient == null) {
                patient =
                    new MediaSeriesGroupNode(TagD.getUID(Level.PATIENT), unknown, DicomModel.patient.getTagView());
                patient.setTag(TagD.get(Tag.PatientID), unknown);
                patient.setTag(TagD.get(Tag.PatientName), unknown);
                dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);
            }
            MediaSeriesGroup study = dicomModel.getHierarchyNode(patient, unknown);
            if (study == null) {
                study = new MediaSeriesGroupNode(TagD.getUID(Level.STUDY), unknown, DicomModel.study.getTagView());
                dicomModel.addHierarchyNode(patient, study);
            }
            Series dicomSeries = new DicomSeries(seriesUID);
            dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
            dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
            final WadoParameters wadoParameters = new WadoParameters("", false, "", null, null); //$NON-NLS-1$ //$NON-NLS-2$
            dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
            List<DicomInstance> dicomInstances = new ArrayList<>();
            dicomSeries.setTag(TagW.WadoInstanceReferenceList, dicomInstances);
            dicomModel.addHierarchyNode(study, dicomSeries);
            for (int i = 0; i < urls.length; i++) {
                if (urls[i] != null) {
                    String url = urls[i].toString();
                    DicomInstance dcmInstance = new DicomInstance(url);
                    dcmInstance.setDirectDownloadFile(url);
                    dicomInstances.add(dcmInstance);
                }
            }

            if (!dicomInstances.isEmpty()) {
                String modality = TagD.getTagValue(dicomSeries, Tag.Modality, String.class);
                boolean ps = modality != null && ("PR".equals(modality) || "KO".equals(modality)); //$NON-NLS-1$ //$NON-NLS-2$
                final LoadSeries loadSeries = new LoadSeries(dicomSeries, dicomModel,
                    BundleTools.SYSTEM_PREFERENCES.getIntProperty(LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4), true);
                if (!ps) {
                    loadSeries.startDownloadImageReference(wadoParameters);
                }
                loadSeries.setPriority(new DownloadPriority(patient, study, dicomSeries, true));
                DownloadManager.addLoadSeries(loadSeries, dicomModel, true);
                DownloadManager.CONCURRENT_EXECUTOR.prestartAllCoreThreads();
            }
        }
        return true;
    }
}
