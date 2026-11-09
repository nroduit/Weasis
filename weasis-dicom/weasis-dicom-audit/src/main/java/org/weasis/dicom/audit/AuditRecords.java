/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.audit;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.audit.ActiveParticipant;
import org.dcm4che3.audit.AuditMessage;
import org.dcm4che3.audit.AuditMessages;
import org.dcm4che3.audit.AuditMessages.EventActionCode;
import org.dcm4che3.audit.AuditMessages.EventID;
import org.dcm4che3.audit.AuditMessages.EventOutcomeIndicator;
import org.dcm4che3.audit.AuditMessages.EventTypeCode;
import org.dcm4che3.audit.AuditMessages.ParticipantObjectIDTypeCode;
import org.dcm4che3.audit.AuditMessages.ParticipantObjectTypeCode;
import org.dcm4che3.audit.AuditMessages.ParticipantObjectTypeCodeRole;
import org.dcm4che3.audit.AuditMessages.RoleIDCode;
import org.dcm4che3.audit.AuditSourceIdentification;
import org.dcm4che3.audit.EventIdentification;
import org.dcm4che3.audit.ParticipantObjectIdentification;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.gui.util.AppProperties;

/**
 * The PS3.15 A.5 audit message of an application event: one per table row of the specification. The
 * patient identifiers the standard requires are carried as the event holds them; the message goes
 * to the audit record repository of the site, never to a caller, so the identity masking of the
 * command bridge does not apply here.
 */
public final class AuditRecords {

  /** The event the application publishes on an integrity or policy failure. */
  public static final String SECURITY_ALERT = "weasis:security.alert"; // NON-NLS

  private static final String WEASIS = "Weasis"; // NON-NLS
  private static final String LOCAL_CODES = "99WEASIS"; // NON-NLS

  private final String user;
  private final String host;

  public AuditRecords() {
    this(System.getProperty("user.name", "unknown"), localHost()); // NON-NLS
  }

  AuditRecords(String user, String host) {
    this.user = user;
    this.host = host;
  }

  /** The message of an event, empty for an event the trail does not record. */
  public Optional<AuditMessage> of(Event event) {
    if (event == null) {
      return Optional.empty();
    }
    return switch (event.name()) {
      case "weasis:ready" ->
          Optional.of(applicationActivity(event, EventTypeCode.ApplicationStart)); // NON-NLS
      case "weasis:shutdown" ->
          Optional.of(applicationActivity(event, EventTypeCode.ApplicationStop)); // NON-NLS
      case "dicom:study.opened" -> Optional.of(instancesAccessed(event)); // NON-NLS
      case "dicom:loading.started" ->
          transfer(event, EventID.BeginTransferringDICOMInstances); // NON-NLS
      case "dicom:loading.stopped" -> transfer(event, EventID.DICOMInstancesTransferred); // NON-NLS
      case "command:finished" -> command(event); // NON-NLS
      case SECURITY_ALERT -> Optional.of(securityAlert(event));
      default -> Optional.empty();
    };
  }

  private AuditMessage applicationActivity(Event event, EventTypeCode type) {
    EventIdentification id =
        eventIdentification(EventID.ApplicationActivity, EventActionCode.Execute, event, true);
    id.getEventTypeCode().add(type);
    return AuditMessages.createMessage(id, List.of(application(), person()));
  }

  private AuditMessage instancesAccessed(Event event) {
    EventIdentification id =
        eventIdentification(EventID.DICOMInstancesAccessed, EventActionCode.Read, event, true);
    List<ParticipantObjectIdentification> objects = new ArrayList<>();
    study(event.get("studyInstanceUID")).ifPresent(objects::add); // NON-NLS
    patient(event.get("patientId"), event.get("patientName")).ifPresent(objects::add); // NON-NLS
    return AuditMessages.createMessage(
        id,
        List.of(application(), person()),
        objects.toArray(ParticipantObjectIdentification[]::new));
  }

  private Optional<AuditMessage> transfer(Event event, EventID eventId) {
    if (!"download".equals(event.get("source"))) { // NON-NLS
      return Optional.empty();
    }
    boolean failed = Boolean.TRUE.equals(event.get("canceled")); // NON-NLS
    EventIdentification id = eventIdentification(eventId, EventActionCode.Create, event, !failed);
    List<ParticipantObjectIdentification> objects = new ArrayList<>();
    study(event.get("studyInstanceUID")).ifPresent(objects::add); // NON-NLS
    Object series = event.get("series"); // NON-NLS
    if (series != null) {
      objects.add(
          participant(
              String.valueOf(series),
              ParticipantObjectIDTypeCode.StudyInstanceUID,
              ParticipantObjectTypeCode.SystemObject,
              ParticipantObjectTypeCodeRole.Report));
    }
    return Optional.of(
        AuditMessages.createMessage(
            id,
            List.of(application(), person()),
            objects.toArray(ParticipantObjectIdentification[]::new)));
  }

  private Optional<AuditMessage> command(Event event) {
    if (Event.APP_ORIGIN.equals(event.origin())) {
      return Optional.empty();
    }
    Object status = event.get("status"); // NON-NLS
    boolean ok = status instanceof Number n && n.intValue() == 0;
    org.dcm4che3.audit.EventID remoteCommand = new org.dcm4che3.audit.EventID();
    remoteCommand.setCsdCode("WEASIS-CMD"); // NON-NLS
    remoteCommand.setCodeSystemName(LOCAL_CODES);
    remoteCommand.setOriginalText("Remote command"); // NON-NLS
    EventIdentification id = eventIdentification(remoteCommand, EventActionCode.Execute, event, ok);
    id.setEventOutcomeDescription(String.valueOf(event.get("command"))); // NON-NLS
    ActiveParticipant caller = new ActiveParticipant();
    caller.setUserID(String.valueOf(event.origin()));
    caller.setUserIsRequestor(true);
    caller.setAlternativeUserID("weasis-cli"); // NON-NLS
    return Optional.of(AuditMessages.createMessage(id, List.of(application(), caller)));
  }

  private AuditMessage securityAlert(Event event) {
    EventIdentification id =
        eventIdentification(EventID.SecurityAlert, EventActionCode.Execute, event, false);
    id.getEventTypeCode().add(EventTypeCode.NodeAuthentication);
    id.setEventOutcomeDescription(String.valueOf(event.get("reason"))); // NON-NLS
    return AuditMessages.createMessage(id, List.of(application(), person()));
  }

  // ---------------------------------------------------------------------------- the parts

  private static EventIdentification eventIdentification(
      org.dcm4che3.audit.EventID eventId, String action, Event event, boolean success) {
    EventIdentification id = new EventIdentification();
    id.setEventID(eventId);
    id.setEventActionCode(action);
    Calendar when = Calendar.getInstance();
    when.setTime(event.time() == null ? new Date() : Date.from(event.time()));
    id.setEventDateTime(when);
    id.setEventOutcomeIndicator(
        success ? EventOutcomeIndicator.Success : EventOutcomeIndicator.MinorFailure);
    return id;
  }

  private ActiveParticipant application() {
    ActiveParticipant app = new ActiveParticipant();
    app.setUserID(WEASIS + "@" + host);
    app.setAlternativeUserID(WEASIS + " " + AppProperties.WEASIS_VERSION);
    app.setUserIsRequestor(false);
    app.setNetworkAccessPointID(host);
    app.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.MachineName);
    app.getRoleIDCode().add(RoleIDCode.Application);
    return app;
  }

  private ActiveParticipant person() {
    ActiveParticipant person = new ActiveParticipant();
    person.setUserID(user);
    person.setUserIsRequestor(true);
    person.setNetworkAccessPointID(host);
    person.setNetworkAccessPointTypeCode(AuditMessages.NetworkAccessPointTypeCode.MachineName);
    return person;
  }

  private static Optional<ParticipantObjectIdentification> study(Object uid) {
    return uid == null
        ? Optional.empty()
        : Optional.of(
            participant(
                String.valueOf(uid),
                ParticipantObjectIDTypeCode.StudyInstanceUID,
                ParticipantObjectTypeCode.SystemObject,
                ParticipantObjectTypeCodeRole.Report));
  }

  private static Optional<ParticipantObjectIdentification> patient(Object id, Object name) {
    if (id == null && name == null) {
      return Optional.empty();
    }
    ParticipantObjectIdentification patient =
        participant(
            String.valueOf(id == null ? name : id),
            ParticipantObjectIDTypeCode.PatientNumber,
            ParticipantObjectTypeCode.Person,
            ParticipantObjectTypeCodeRole.Patient);
    if (name != null) {
      patient.setParticipantObjectName(String.valueOf(name));
    }
    return Optional.of(patient);
  }

  private static ParticipantObjectIdentification participant(
      String id, ParticipantObjectIDTypeCode idType, String typeCode, String role) {
    ParticipantObjectIdentification object = new ParticipantObjectIdentification();
    object.setParticipantObjectID(id);
    object.setParticipantObjectIDTypeCode(idType);
    object.setParticipantObjectTypeCode(typeCode);
    object.setParticipantObjectTypeCodeRole(role);
    return object;
  }

  /** The source identification every message carries: this workstation. */
  public AuditSourceIdentification source() {
    return AuditMessages.createAuditSourceIdentification(
        null, host, AuditMessages.AuditSourceTypeCode.EndUserDisplayDevice);
  }

  /** The message as the XML an audit record repository accepts. */
  public static String toXml(AuditMessage message) throws IOException {
    return AuditMessages.toXML(message);
  }

  private static String localHost() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return "localhost"; // NON-NLS
    }
  }
}
