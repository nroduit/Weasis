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

import java.net.MalformedURLException;
import java.net.URL;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.ExplorerTask;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.mf.WadoParameters;

public class LoadRemoteDicomURL extends ExplorerTask<Boolean, String> {
  private static final Logger LOGGER = LoggerFactory.getLogger(LoadRemoteDicomURL.class);

  private final URL[] urls;
  private final DicomModel dicomModel;

  public LoadRemoteDicomURL(String[] urls, DataExplorerModel explorerModel) {
    super(Messages.getString("DicomExplorer.loading"), true);
    if (urls == null || !(explorerModel instanceof DicomModel)) {
      throw new IllegalArgumentException("invalid parameters");
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
    super(Messages.getString("DicomExplorer.loading"), true);
    if (urls == null || !(explorerModel instanceof DicomModel)) {
      throw new IllegalArgumentException("invalid parameters");
    }
    this.urls = urls;
    this.dicomModel = (DicomModel) explorerModel;
  }

  @Override
  protected Boolean doInBackground() throws Exception {
    String seriesUID = null;
    for (URL item : urls) {
      if (item != null) {
        seriesUID = item.toString();
        break;
      }
    }
    if (seriesUID != null) {
      String unknown = TagW.NO_VALUE;
      MediaSeriesGroup patient =
          new MediaSeriesGroupNode(
              TagD.getUID(Level.PATIENT), UIDUtils.createUID(), DicomModel.patient.tagView());
      patient.setTag(TagD.get(Tag.PatientID), unknown);
      patient.setTag(TagD.get(Tag.PatientName), unknown);
      dicomModel.addHierarchyNode(MediaSeriesGroupNode.rootNode, patient);

      MediaSeriesGroup study =
          new MediaSeriesGroupNode(
              TagD.getUID(Level.STUDY), UIDUtils.createUID(), DicomModel.study.tagView());
      dicomModel.addHierarchyNode(patient, study);

      Series dicomSeries = new DicomSeries(seriesUID);
      dicomSeries.setTag(TagW.ExplorerModel, dicomModel);
      dicomSeries.setTag(TagD.get(Tag.SeriesInstanceUID), seriesUID);
      final WadoParameters wadoParameters = new WadoParameters("", false);
      dicomSeries.setTag(TagW.WadoParameters, wadoParameters);
      SeriesInstanceList seriesInstanceList = new SeriesInstanceList();
      dicomSeries.setTag(TagW.WadoInstanceReferenceList, seriesInstanceList);
      dicomModel.addHierarchyNode(study, dicomSeries);
      for (URL value : urls) {
        if (value != null) {
          String url = value.toString();
          SopInstance sop = seriesInstanceList.getSopInstance(url, null);
          if (sop == null) {
            sop = new SopInstance(url, null);
            sop.setDirectDownloadFile(url);
            seriesInstanceList.addSopInstance(sop);
          }
        }
      }

      if (!seriesInstanceList.isEmpty()) {
        String modality = TagD.getTagValue(dicomSeries, Tag.Modality, String.class);
        boolean ps = "PR".equals(modality) || "KO".equals(modality); // NON-NLS
        final LoadSeries loadSeries =
            new LoadSeries(
                dicomSeries,
                dicomModel,
                BundleTools.SYSTEM_PREFERENCES.getIntProperty(
                    LoadSeries.CONCURRENT_DOWNLOADS_IN_SERIES, 4),
                true);
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
