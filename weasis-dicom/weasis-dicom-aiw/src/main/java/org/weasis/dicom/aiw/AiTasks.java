/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.aiw.TaskBoard.Source;
import org.weasis.dicom.aiw.TaskBoard.Task;
import org.weasis.dicom.aiw.UpsNodes.Node;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.DicomWebOrigin;
import org.weasis.dicom.explorer.items.ExplorerTarget;
import org.weasis.dicom.explorer.rs.RsQueryParams;
import org.weasis.dicom.web.AuthorizationProvider;
import org.weasis.dicom.web.InvokeImageDisplay;
import org.weasis.dicom.web.UpsConfig;

/**
 * The tasks of a study of the explorer: where they are asked (the server the study came from, when
 * the document of {@link UpsNodes} enables it), and the three things the menus do with them: run
 * one on a target, cancel a queued one, open the results of a finished one. Headless: the menus and
 * the {@code ups:} verbs go through here, and every call to the server runs off the event dispatch
 * thread.
 */
public final class AiTasks {

  private static final Logger LOGGER = LoggerFactory.getLogger(AiTasks.class);

  private AiTasks() {}

  /**
   * A study with the server that takes its tasks.
   *
   * @param target the study, or some of its series
   * @param origin the DICOMweb server the study came from
   * @param node the entry of that server in the document of the user
   */
  public record Desk(ExplorerTarget target, DicomWebOrigin origin, Node node) {

    public String studyUid() {
      return target.studyUID();
    }

    public UpsService service() {
      return AiTasks.service(origin, node);
    }

    Source source() {
      MediaSeriesGroup patient = target.patient();
      return new Source(
          service(),
          patient == null ? null : TagD.getTagValue(patient, Tag.PatientID, String.class),
          TagD.getTagValue(target.study(), Tag.AccessionNumber, String.class),
          node.pollSeconds());
    }

    /**
     * Whether a task was asked on what the target is: the same series, or, for the whole study, a
     * task that names no series or names them all.
     */
    public boolean covers(Task task) {
      Set<String> wanted = Set.copyOf(target.seriesUIDs());
      if (target.wholeStudy()) {
        return task.inputSeries().isEmpty() || task.inputSeries().containsAll(wanted);
      }
      return task.inputSeries().equals(wanted);
    }
  }

  /** The desk of a target, empty when its study has no DICOMweb origin or no enabled server. */
  public static Optional<Desk> desk(ExplorerTarget target) {
    try {
      Optional<DicomWebOrigin> origin = target.origin();
      return origin.flatMap(UpsNodes::forOrigin).map(node -> new Desk(target, origin.get(), node));
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot resolve the task server of a study", e);
      return Optional.empty();
    }
  }

  static UpsService service(DicomWebOrigin origin, Node node) {
    UpsConfig.Builder builder =
        UpsConfig.builder()
            .baseUrl(origin.baseUrl())
            .requesterAet(node.requesterAet())
            .requesterInPath(node.requesterInPath());
    origin.authMethod().ifPresent(method -> builder.authorization(bearer(method)));
    return new UpsService(builder.build(), node.requesterAet());
  }

  /**
   * Adapts a Weasis authentication method to the provider of the client (RFC 6750 §2.1). Caching
   * and refreshing stay with the method; the client only asks for the current token and, on a 401,
   * tells the method the token it sent was refused.
   */
  static AuthorizationProvider bearer(AuthMethod method) {
    if (method == null) {
      return AuthorizationProvider.NONE;
    }
    return AuthorizationProvider.bearer(
        () -> {
          var token = method.getToken();
          return token == null ? null : token.accessToken();
        },
        method::resetToken);
  }

  /** The study as the workitem names its patient and its request (RAD-80). */
  static Attributes studyAttributes(ExplorerTarget target) {
    Attributes study = new Attributes();
    study.setString(Tag.StudyInstanceUID, VR.UI, target.studyUID());
    set(study, Tag.AccessionNumber, VR.SH, target.study());
    MediaSeriesGroup patient = target.patient();
    if (patient != null) {
      set(study, Tag.PatientID, VR.LO, patient);
      set(study, Tag.PatientName, VR.PN, patient);
      set(study, Tag.IssuerOfPatientID, VR.LO, patient);
    }
    return study;
  }

  private static void set(Attributes attributes, int tag, VR vr, MediaSeriesGroup group) {
    Object value = group.getTagValue(TagD.get(tag));
    if (value instanceof String text && !text.isBlank()) {
      attributes.setString(tag, vr, text);
    }
  }

  /**
   * The instances of the series of the target that are in the model, as inputs of a task. Each one
   * carries the Retrieve URL (0008,1190) of its series, which the workitem turns into the WADO-RS
   * Retrieval Sequence a performer needs to fetch the inputs (PS3.4 Table CC.2.5-2b).
   *
   * @param origin the DICOMweb server the study came from, source of the URL of a series that has
   *     none
   */
  static List<Attributes> inputInstances(ExplorerTarget target, DicomWebOrigin origin) {
    List<Attributes> instances = new ArrayList<>();
    String studyUid = target.studyUID();
    for (DicomSeries series : target.series()) {
      String seriesUid = TagD.getTagValue(series, Tag.SeriesInstanceUID, String.class);
      String retrieveUrl = seriesRetrieveUrl(series, origin, studyUid, seriesUid);
      for (MediaElement media : series.getMedias(null, null)) {
        String sop = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
        String sopClass = TagD.getTagValue(media, Tag.SOPClassUID, String.class);
        if (seriesUid == null || sop == null || sopClass == null) {
          continue;
        }
        Attributes instance = new Attributes();
        instance.setString(Tag.StudyInstanceUID, VR.UI, studyUid);
        instance.setString(Tag.SeriesInstanceUID, VR.UI, seriesUid);
        instance.setString(Tag.SOPClassUID, VR.UI, sopClass);
        instance.setString(Tag.SOPInstanceUID, VR.UI, sop);
        instance.setString(Tag.RetrieveURL, VR.UR, retrieveUrl);
        instances.add(instance);
      }
    }
    return instances;
  }

  /**
   * The WADO-RS URL of a series: the one the query gave the series, else the series resource of the
   * origin (PS3.18 §10.4.1.1.2), since the study is retrievable from the server it came from.
   */
  private static String seriesRetrieveUrl(
      DicomSeries series, DicomWebOrigin origin, String studyUid, String seriesUid) {
    String url = TagD.getTagValue(series, Tag.RetrieveURL, String.class);
    if (StringUtil.hasText(url)) {
      return url;
    }
    return origin.baseUrl() + "/studies/" + studyUid + "/series/" + seriesUid; // NON-NLS
  }

  /**
   * Asks the server for a task on the target, unless the study has a pending one of that code (the
   * rule of the Task Requestor in {@link UpsService#run}).
   *
   * @return the UID of the workitem, created or already there
   */
  public static String run(Desk desk, Code code) throws IOException, InterruptedException {
    requireWrite(desk);
    return desk.service()
        .run(code, studyAttributes(desk.target()), inputInstances(desk.target(), desk.origin()));
  }

  /** Asks the server to cancel a queued task. */
  public static void cancel(Desk desk, String workitemUid)
      throws IOException, InterruptedException {
    requireWrite(desk);
    desk.service().cancel(workitemUid, null);
  }

  private static void requireWrite(Desk desk) {
    if (!desk.node().writeAllowed()) {
      throw new IllegalStateException(
          "The document " + UpsNodes.DOCUMENT + " does not allow writing on this server");
    }
  }

  /**
   * Loads the result series of a task into the model through the DICOMweb service of the study, as
   * a {@code $dicom:rs} query does; the objects (SEG, SR, RTSTRUCT) then show as items of the
   * study.
   *
   * @return the series asked for
   */
  public static List<String> open(Desk desk, Task task) {
    List<String> series = List.copyOf(task.outputSeries());
    if (series.isEmpty()) {
      return series;
    }
    Properties properties = new Properties();
    properties.setProperty(RsQueryParams.P_DICOMWEB_URL, desk.origin().baseUrl());
    if (desk.origin().authMethodUid() != null) {
      properties.setProperty(RsQueryParams.P_AUTH_UID, desk.origin().authMethodUid());
    }
    Map<String, List<String>> request = new HashMap<>();
    request.put(InvokeImageDisplay.SERIES_UID, new ArrayList<>(series));
    DicomModel model = desk.target().model();
    DicomModel.LOADING_EXECUTOR.execute(new RsQueryParams(model, properties, request, null, null));
    return series;
  }

  /** Reads the tasks of the study again; the board tells its listener when they are here. */
  public static void refresh(Desk desk) {
    TaskBoard.get().refresh(desk.studyUid(), desk.source());
  }

  /** Tells the explorer that the tasks of a study changed, so its menus and marks are redone. */
  static void announce(DicomModel model, String studyUid) {
    GuiExecutor.execute(
        () -> {
          MediaSeriesGroup study = model.getStudyNode(studyUid);
          if (study != null) {
            model.firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.UPDATE, model, null, study));
          }
        });
  }
}
