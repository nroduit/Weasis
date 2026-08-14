/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.img.util.DicomUtils;
import org.dcm4che3.net.service.QueryRetrieveLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.codec.utils.SeriesInstanceList;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.PluginOpeningStrategy;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.RetrieveType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.mf.SopInstance;
import org.weasis.dicom.op.CFind;
import org.weasis.dicom.param.AdvancedParams;
import org.weasis.dicom.param.DicomNode;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.param.DicomState;
import org.weasis.dicom.param.ListenerParams;
import org.weasis.dicom.tool.DicomListener;

/**
 * Parameters shared by the per-series tasks of a single Query/Retrieve request, whichever protocol
 * transfers the images. It also carries the image level C-FIND those tasks run to list their own
 * instances.
 *
 * <p>C-MOVE brings the objects back through the local {@link DicomListener} instead of the retrieve
 * association, so the listener must stay up for the whole batch: it is started by the first series
 * that needs it and stopped once the last one has finished.
 */
public class RetrieveContext {
  private static final Logger LOGGER = LoggerFactory.getLogger(RetrieveContext.class);

  private final RetrieveType type;
  private final AdvancedParams params;
  private final DefaultDicomNode callingNode;
  private final DefaultDicomNode calledNode;
  private final DicomModel dicomModel;
  private final PluginOpeningStrategy openingStrategy;
  private final DicomListener dicomListener;
  private final Path storageDir;
  private final Set<LoadQrSeries> pendingTasks = ConcurrentHashMap.newKeySet();

  public RetrieveContext(
      RetrieveType type,
      AdvancedParams params,
      DefaultDicomNode callingNode,
      DefaultDicomNode calledNode,
      DicomModel dicomModel,
      PluginOpeningStrategy openingStrategy,
      DicomListener dicomListener,
      Path storageDir) {
    this.type = type;
    this.params = params;
    this.callingNode = callingNode;
    this.calledNode = calledNode;
    this.dicomModel = dicomModel;
    this.openingStrategy = openingStrategy;
    this.dicomListener = dicomListener;
    this.storageDir = storageDir;
  }

  public RetrieveType getType() {
    return type;
  }

  public AdvancedParams getParams() {
    return params;
  }

  public DicomModel getDicomModel() {
    return dicomModel;
  }

  public PluginOpeningStrategy getOpeningStrategy() {
    return openingStrategy;
  }

  public Path getStorageDir() {
    return storageDir;
  }

  public String getDestinationAet() {
    return callingNode.getAeTitle();
  }

  /** C-FIND and C-GET identify the SCU by its AE title only. */
  public DicomNode getCallingNodeForQuery() {
    return callingNode.getDicomNodeWithOnlyAET();
  }

  /** C-MOVE needs the full node so the archive can open the store association back. */
  public DicomNode getCallingNodeForMove() {
    return callingNode.getDicomNode();
  }

  public DicomNode getCalledNode() {
    return calledNode.getDicomNode();
  }

  /** Series retrieved with C-MOVE share a single listener, so they must not run in parallel. */
  public boolean hasConcurrentRetrieve() {
    return RetrieveType.CMOVE != type;
  }

  /**
   * Lists the SOP instances of the series with an image level C-FIND, unless they are already
   * known. Without them the progress bar has no total and a resumed series cannot tell what is
   * still missing, so this runs when the series download starts rather than when the retrieve is
   * prepared: a large selection begins transferring without waiting for every series to be
   * enumerated.
   */
  public void fillInstances(DicomSeries series) {
    SeriesInstanceList instanceList =
        (SeriesInstanceList) series.getTagValue(TagW.WadoInstanceReferenceList);
    if (instanceList == null || !instanceList.isEmpty()) {
      return;
    }
    MediaSeriesGroup study = dicomModel.getParent(series, DicomModel.study);
    String studyUid = TagD.getTagValue(study, Tag.StudyInstanceUID, String.class);
    String seriesUid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
    if (!StringUtil.hasText(studyUid) || !StringUtil.hasText(seriesUid)) {
      return;
    }

    DicomParam[] keys = {
      new DicomParam(Tag.StudyInstanceUID, studyUid),
      new DicomParam(Tag.SeriesInstanceUID, seriesUid),
      CFind.SOPInstanceUID,
      CFind.InstanceNumber
    };
    DicomState state =
        CFind.process(
            params, getCallingNodeForQuery(), getCalledNode(), 0, QueryRetrieveLevel.IMAGE, keys);
    List<Attributes> instances = state.getDicomRSP();
    if (instances == null) {
      LOGGER.warn("No instance found for series {}: {}", seriesUid, state.getMessage());
      return;
    }
    for (Attributes instanceDataset : instances) {
      String sopUid = instanceDataset.getString(Tag.SOPInstanceUID);
      Integer instanceNumber =
          DicomUtils.getIntegerFromDicomElement(instanceDataset, Tag.InstanceNumber, null);
      if (StringUtil.hasText(sopUid) && instanceList.getSopInstance(sopUid) == null) {
        instanceList.addSopInstance(new SopInstance(sopUid, instanceNumber));
      }
    }
  }

  public void register(LoadQrSeries task) {
    pendingTasks.add(task);
  }

  /** Releases a finished task and shuts the listener down once the batch is over. */
  public void unregister(LoadQrSeries task) {
    pendingTasks.remove(task);
    if (pendingTasks.isEmpty()) {
      stopListener();
    }
  }

  /**
   * Starts the store listener receiving the C-MOVE objects if it is not already running.
   *
   * @return null when the listener is ready, otherwise the reason it cannot be used
   */
  public synchronized String ensureListenerStarted() {
    if (dicomListener == null) {
      return Messages.getString("RetrieveTask.msg_start_listener");
    }
    if (dicomListener.isRunning()) {
      return null;
    }
    try {
      dicomListener.start(getCallingNodeForMove(), new ListenerParams(params, true));
      return null;
    } catch (Exception e) {
      LOGGER.error("Start DICOM listener", e);
      return Messages.getString("RetrieveTask.msg_start_listener");
    }
  }

  private synchronized void stopListener() {
    if (dicomListener != null && dicomListener.isRunning()) {
      dicomListener.stop();
    }
  }

  /** Location of the C-GET SOP class configuration, null when the resource is unreadable. */
  public static URL getCGetSopClassUrl() {
    var sopClass = ResourceUtil.getResource(DicomResource.CGET_SOP_UID);
    if (sopClass.canRead()) {
      try {
        return sopClass.toURI().toURL();
      } catch (MalformedURLException e) {
        LOGGER.error("SOP Class url conversion", e);
      }
    }
    return null;
  }
}
