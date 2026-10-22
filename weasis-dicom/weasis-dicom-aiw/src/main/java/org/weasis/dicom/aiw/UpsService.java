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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Code;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.UIDUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.dicom.web.ProcedureStepState;
import org.weasis.dicom.web.UpsConfig;
import org.weasis.dicom.web.UpsQuery;
import org.weasis.dicom.web.UpsRS;
import org.weasis.dicom.web.UpsResponse;
import org.weasis.dicom.web.UpsWorkitem;

/**
 * The UPS-RS transactions a viewer performs as an IHE AIW-I Task Requestor and Watcher (PS3.18
 * §11.1): search the tasks of a study, create one when the study has none for that code, request
 * the cancellation of a queued one, watch its state, and read the results it names.
 *
 * <p>The wire layer is {@link UpsRS}; this class holds only the role logic, so it carries no Swing
 * and can run from a command line, a script or the explorer. Weasis creates and watches: assigning
 * a performer and stepping the state of a workitem belong to the Task Manager, never here.
 */
public class UpsService {

  private static final Logger LOGGER = LoggerFactory.getLogger(UpsService.class);

  private final UpsRS client;
  private final String aeTitle;

  /**
   * @param config the origin server and the credentials of this user agent
   * @param aeTitle the AE Title identifying this user agent, i.e. the subscriber of a notification
   *     channel; {@code null} to use the requester AE Title of {@code config}
   */
  public UpsService(UpsConfig config, String aeTitle) {
    this.client = new UpsRS(Objects.requireNonNull(config, "Configuration cannot be null"));
    this.aeTitle = aeTitle;
  }

  /** The wire client, so the host can reuse its connection pool and its configuration. */
  public UpsRS getClient() {
    return client;
  }

  /**
   * Searches the workitems of a study (PS3.18 §11.9, RAD-81). The study is matched through the
   * Referenced Request Sequence, never the Input Information Sequence: (0040,A370) is a Required
   * matching key where (0040,4021) is Optional (PS3.4 Table CC.2.5-3), so only the former is
   * portable as a Search key. Patient ID and Accession Number are added when known, since a
   * conformant origin must be able to filter on all three.
   */
  public List<Attributes> search(String studyUid, String patientId, String accessionNumber)
      throws IOException, InterruptedException {
    Objects.requireNonNull(studyUid, "Study Instance UID cannot be null");
    UpsQuery query = new UpsQuery();
    if (patientId != null && !patientId.isBlank()) {
      query.match("PatientID", patientId);
    }
    if (accessionNumber != null && !accessionNumber.isBlank()) {
      query.match("ReferencedRequestSequence.AccessionNumber", accessionNumber);
    }
    // Referenced Request Sequence is the filter the maintainer asked for; the Study UID is
    // reachable there and, unlike the top-level one, an origin is required to index it.
    query.match("ReferencedRequestSequence.StudyInstanceUID", studyUid);
    query.includeAllFields();
    return client.searchWorkitems(query);
  }

  /**
   * Creates the workitem of a task when the study has none for that code (PS3.18 §11.4, RAD-80).
   * This is the "Run" verb: a Task Requestor never re-addresses or steps a workitem that exists, so
   * the only two outcomes are the creation of the workitem and a no-op.
   *
   * <p>Non-terminal means SCHEDULED or IN PROGRESS; COMPLETED and CANCELED are terminal and do not
   * suppress a new task (PS3.4 Table CC.1.1-1 defines exactly these four states).
   *
   * @param catalogEntry the Scheduled Workitem Code identifying the task
   * @param study the study the results belong to, source of the patient and request attributes
   * @param instances the instances a performer retrieves to run the task
   * @return the UID of the existing or created workitem
   */
  public String run(Code catalogEntry, Attributes study, Iterable<Attributes> instances)
      throws IOException, InterruptedException {
    Objects.requireNonNull(catalogEntry, "Catalog entry cannot be null");
    Objects.requireNonNull(study, "Study cannot be null");
    String studyUid = study.getString(Tag.StudyInstanceUID);
    if (studyUid == null) {
      throw new IllegalArgumentException("The study has no Study Instance UID");
    }
    Optional<Attributes> pending = nonTerminalWorkitem(studyUid, catalogEntry);
    if (pending.isPresent()) {
      String uid = pending.get().getString(Tag.SOPInstanceUID);
      LOGGER.debug("UPS workitem {} already covers {} on study {}", uid, catalogEntry, studyUid);
      return uid;
    }
    Attributes data =
        UpsWorkitem.of(catalogEntry).patient(study).request(study).inputs(instances).build();
    // The SOP Instance UID is not part of the data set: it travels in the workitem query parameter
    // (PS3.18 §11.4.1.1). It is minted here rather than left to the origin, because a bare
    // POST /workitems is ambiguous with the Search-with-body transaction of §11.9 - an origin that
    // routes on the query string cannot tell them apart, and answering the Create as a Search
    // makes the workitem never appear. Minting the UID is what the parameter is for.
    String workitemUid = UIDUtils.createUID();
    UpsResponse response = client.createWorkitem(data, workitemUid);
    return response.workitemUid().orElse(workitemUid);
  }

  /**
   * Returns the first non-terminal workitem of the study carrying {@code catalogEntry}, if any. One
   * is enough: its existence is what suppresses a Create, and the caller only needs its UID.
   */
  private Optional<Attributes> nonTerminalWorkitem(String studyUid, Code catalogEntry)
      throws IOException, InterruptedException {
    for (Attributes workitem : search(studyUid, null, null)) {
      String state = workitem.getString(Tag.ProcedureStepState);
      if (isNonTerminal(state) && hasCode(workitem, catalogEntry)) {
        return Optional.of(workitem);
      }
    }
    return Optional.empty();
  }

  /** SCHEDULED and IN PROGRESS are the two states a Task Requestor still has to reckon with. */
  static boolean isNonTerminal(String state) {
    return ProcedureStepState.SCHEDULED.code().equals(state)
        || ProcedureStepState.IN_PROGRESS.code().equals(state);
  }

  private static boolean hasCode(Attributes workitem, Code expected) {
    Sequence codes = workitem.getSequence(Tag.ScheduledWorkitemCodeSequence);
    if (codes == null) {
      return false;
    }
    for (Attributes code : codes) {
      // The origin round-trips the code dataset, which is rarely byte-identical to the source one
      // (item order, empty Type 2 members). A code is identified by its value and its designator
      // (PS3.3 Table 8.8-1), so those two decide the match, not the serialised item.
      if (expected.getCodeValue().equals(code.getString(Tag.CodeValue))
          && expected
              .getCodingSchemeDesignator()
              .equals(code.getString(Tag.CodingSchemeDesignator))) {
        return true;
      }
    }
    return false;
  }

  /**
   * Requests the cancellation of a queued task (PS3.18 §11.8, PS3.4 Table CC.2.2-1). Only a
   * SCHEDULED workitem can be cancelled this way: an IN PROGRESS one is owned by its performer, so
   * the request has to go to the origin's own policy.
   *
   * @param reason the reason for cancellation, or {@code null} for a bare request
   */
  public void cancel(String workitemUid, String reason) throws IOException, InterruptedException {
    Objects.requireNonNull(workitemUid, "Workitem UID cannot be null");
    client.requestCancellation(workitemUid, UpsRS.cancellationReason(reason, null, null, null));
  }

  /**
   * Subscribes to the workitems of the study and delivers every event report to {@code listener}
   * (PS3.18 §11.10, RAD-86) over the notification channel (PS3.18 §11.13, RAD-109).
   *
   * <p>The channel is a WebSocket, so the caller keeps the returned handle and closes it when the
   * tab goes away; the report itself carries the Event Type ID (0000,1002) and the Affected SOP
   * Instance UID (0000,1000), which is all a Watcher needs to refresh a row.
   *
   * @return the subscription's completion handle: the channel is open once it completes
   */
  public CompletableFuture<?> watch(Consumer<Attributes> listener) {
    Objects.requireNonNull(listener, "Listener cannot be null");
    return client.openNotificationChannel(aeTitle, listener);
  }

  /** Subscribes to the workitems of a study without opening a channel, for the polling fallback. */
  public UpsResponse subscribe(Attributes filter, boolean deletionLock)
      throws IOException, InterruptedException {
    return client.subscribeFiltered(aeTitle, Objects.requireNonNull(filter), deletionLock);
  }

  public void unsubscribe(String workitemUid) throws IOException, InterruptedException {
    client.unsubscribe(workitemUid, aeTitle);
  }

  /**
   * Reads the results named by the Output Information Sequence (0040,4033) of a workitem (PS3.18
   * §11.5, RAD-83). A Task Requestor never writes that sequence - the performer fills it when it
   * COMPLETES the task - so this is read-only.
   *
   * @return the referenced instances, one entry per series, keyed by Series Instance UID
   */
  public Map<String, List<Attributes>> loadOutputs(String workitemUid)
      throws IOException, InterruptedException {
    Attributes workitem = client.retrieveWorkitem(Objects.requireNonNull(workitemUid));
    Map<String, List<Attributes>> outputs = new LinkedHashMap<>();
    Sequence items = workitem.getSequence(Tag.OutputInformationSequence);
    if (items == null) {
      return outputs;
    }
    for (Attributes item : items) {
      String seriesUid = item.getString(Tag.SeriesInstanceUID);
      if (seriesUid == null) {
        continue;
      }
      Sequence referenced = item.getSequence(Tag.ReferencedSOPSequence);
      List<Attributes> instances = outputs.computeIfAbsent(seriesUid, k -> new ArrayList<>());
      if (referenced != null) {
        for (Attributes sop : referenced) {
          instances.add(sop);
        }
      }
    }
    return outputs;
  }

  /** The SOP Instance UIDs of the results of a workitem, for the caller to load them. */
  public List<String> outputInstanceUids(String workitemUid)
      throws IOException, InterruptedException {
    Map<String, List<Attributes>> outputs = loadOutputs(workitemUid);
    List<String> uids = new ArrayList<>();
    for (List<Attributes> instances : outputs.values()) {
      for (Attributes sop : instances) {
        String uid = sop.getString(Tag.ReferencedSOPInstanceUID);
        if (uid != null) {
          uids.add(uid);
        }
      }
    }
    return uids;
  }

  /** The states a Task Requestor distinguishes, for the caller to pick the menu entry. */
  public static ProcedureStepState stateOf(Attributes workitem) {
    String state = workitem.getString(Tag.ProcedureStepState);
    try {
      return ProcedureStepState.fromCode(state);
    } catch (IllegalArgumentException e) {
      // An unknown state is not a reason to fail the listing: the row stays unactionable.
      LOGGER.warn("Unknown Procedure Step State {} on a workitem", state);
      return null;
    }
  }

  /** Whether the workitem names results in its Output Information Sequence (0040,4033). */
  public static boolean hasOutputs(Attributes workitem) {
    Sequence items = workitem.getSequence(Tag.OutputInformationSequence);
    return items != null && !items.isEmpty();
  }
}
