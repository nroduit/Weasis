/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventType;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.SeriesEvent;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.provenance.Provenance;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.ObjectDescriptor;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.wado.LoadSeries;

/**
 * Translates the {@link ObservableEvent}s of the DICOM model into {@code dicom:} events on the bus:
 * patients, studies and series added or removed, studies opened in a viewer, non-image objects
 * announced, loading started and stopped, and the progress of a series. The synchronous family
 * keeps driving the application; this only observes it. It also installs the masker that applies
 * the identity profile of a remote caller to the payloads, since this plugin knows the tags.
 */
final class DicomEventBridge implements PropertyChangeListener {
  private static final Logger LOGGER = LoggerFactory.getLogger(DicomEventBridge.class);

  static final String PATIENT_ADDED = "dicom:patient.added"; // NON-NLS
  static final String PATIENT_REMOVED = "dicom:patient.removed"; // NON-NLS
  static final String STUDY_ADDED = "dicom:study.added"; // NON-NLS
  static final String STUDY_REMOVED = "dicom:study.removed"; // NON-NLS
  static final String STUDY_OPENED = "dicom:study.opened"; // NON-NLS
  static final String STUDY_CLOSED = "dicom:study.closed"; // NON-NLS
  private static final String SERIES_CHANGED = "view2d:series.changed"; // NON-NLS
  private static final String VIEW_CLOSED = "workspace:view.closed"; // NON-NLS
  private static final String VIEW = "view"; // NON-NLS
  static final String SERIES_ADDED = "dicom:series.added"; // NON-NLS
  static final String SERIES_REMOVED = "dicom:series.removed"; // NON-NLS
  static final String OBJECT_ADDED = "dicom:object.added"; // NON-NLS
  static final String LOADING_STARTED = "dicom:loading.started"; // NON-NLS
  static final String LOADING_PROGRESS = "dicom:loading.progress"; // NON-NLS
  static final String LOADING_STOPPED = "dicom:loading.stopped"; // NON-NLS
  static final String PANEL_CHANGED = "workspace:panel.changed"; // NON-NLS

  private static final String SINCE = "5.0"; // NON-NLS
  private static final String PATIENT_ID = "patientID"; // NON-NLS
  private static final String STUDY_UID = "studyInstanceUID"; // NON-NLS
  private static final String SERIES_UID = "seriesInstanceUID"; // NON-NLS
  private static final String SERIES = "series"; // NON-NLS

  /** The tag whose masking rule applies to each kind of identity a payload declares. */
  private static final Map<String, Integer> TAG_OF_KIND =
      Map.of(
          Event.PATIENT_NAME, Tag.PatientName,
          Event.PATIENT_ID, Tag.PatientID,
          Event.BIRTH_DATE, Tag.PatientBirthDate,
          Event.ACCESSION_NUMBER, Tag.AccessionNumber,
          Event.DESCRIPTION, Tag.StudyDescription,
          Event.DATE, Tag.StudyDate,
          Event.FREE_TEXT, Tag.ImageComments);

  private final DicomModel model;
  private final EventBus bus;

  /**
   * The model fires ADD for series only: the study and the patient are announced on first sight.
   */
  private final Set<MediaSeriesGroup> announced = ConcurrentHashMap.newKeySet();

  /** The study each pane of each view shows, read from the viewers' own events. */
  private final Map<String, Map<Object, String>> shown = new java.util.HashMap<>();

  DicomEventBridge(DicomModel model) {
    this(model, EventBus.global());
  }

  DicomEventBridge(DicomModel model, EventBus bus) {
    this.model = model;
    this.bus = bus;
    bus.setRemoteMasker(DicomEventBridge::maskForRemote);
    // Every opener (hanging protocol, drop, explorer, verb) ends in a pane showing a series, so the
    // open studies are derived from the viewers' events rather than from one opening path.
    bus.subscribe(
        org.weasis.core.api.event.EventFilter.parse(SERIES_CHANGED + "," + VIEW_CLOSED),
        org.weasis.core.api.event.Subscription.Delivery.EDT,
        "dicom:study.opened/closed", // NON-NLS
        this::viewerChanged);
  }

  /** Keeps the studies on screen per view and pane, and publishes a study's first and last view. */
  private void viewerChanged(Event event) {
    String viewId = String.valueOf(event.get(VIEW));
    Set<String> before = openStudies();
    if (VIEW_CLOSED.equals(event.name())) {
      shown.remove(viewId);
    } else {
      Map<Object, String> panes = shown.computeIfAbsent(viewId, k -> new java.util.HashMap<>());
      Object pane = event.get("pane") == null ? "?" : event.get("pane"); // NON-NLS
      String study = studyOfSeriesId(event.get(SERIES));
      if (study == null) {
        panes.remove(pane);
      } else {
        panes.put(pane, study);
      }
    }
    Set<String> after = openStudies();
    for (String study : after) {
      if (!before.contains(study)) {
        bus.publish(
            Event.of(STUDY_OPENED)
                .put(STUDY_UID, study)
                .put(VIEW, viewId)
                .put(SERIES, event.get(SERIES)));
      }
    }
    for (String study : before) {
      if (!after.contains(study)) {
        bus.publish(Event.of(STUDY_CLOSED).put(STUDY_UID, study));
      }
    }
  }

  private Set<String> openStudies() {
    Set<String> studies = new LinkedHashSet<>();
    shown.values().forEach(panes -> studies.addAll(panes.values()));
    return studies;
  }

  private String studyOfSeriesId(Object id) {
    if (id == null) {
      return null;
    }
    return model
        .findSeries(String.valueOf(id))
        .filter(MediaSeriesGroup.class::isInstance)
        .map(series -> studyOf((MediaSeriesGroup) series))
        .orElse(null);
  }

  static List<EventType> types() {
    return List.of(
        EventType.of(PATIENT_ADDED, SINCE, "A patient entered the explorer.")
            .field(PATIENT_ID, PatientHandle.class, "patient identifier, the handle of dicom:list")
            .field("patientName", String.class, "patient name")
            .field("patientBirthDate", String.class, "birth date")
            .build(),
        EventType.of(PATIENT_REMOVED, SINCE, "A patient and everything under it left the explorer.")
            .field(PATIENT_ID, PatientHandle.class, "patient identifier")
            .build(),
        EventType.of(STUDY_ADDED, SINCE, "A study entered the explorer.")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .field(PATIENT_ID, PatientHandle.class, "patient identifier")
            .field("studyDate", String.class, "study date")
            .field("studyDescription", String.class, "study description")
            .field("accessionNumber", String.class, "accession number")
            .build(),
        EventType.of(STUDY_REMOVED, SINCE, "A study and its series left the explorer.")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .build(),
        EventType.of(STUDY_OPENED, SINCE, "A first pane shows a series of this study.")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .field(VIEW, String.class, "the viewer that opened it")
            .field(SERIES, SeriesUid.class, "the series id shown")
            .build(),
        EventType.of(STUDY_CLOSED, SINCE, "No pane of any viewer shows this study anymore.")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .build(),
        EventType.of(
                SERIES_ADDED,
                SINCE,
                "A series entered the explorer; its images may still be loading.")
            .field("id", SeriesUid.class, "series id, the handle of the other verbs")
            .field(SERIES_UID, SeriesUid.class, "series instance UID")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .field("modality", String.class, "modality")
            .field("seriesDescription", String.class, "series description")
            .field("imageCount", int.class, "images present when the event was published")
            .build(),
        EventType.of(SERIES_REMOVED, SINCE, "A series left the explorer.")
            .field("id", SeriesUid.class, "series id")
            .field(STUDY_UID, StudyUid.class, "study instance UID")
            .build(),
        EventType.of(OBJECT_ADDED, SINCE, "A non-image object (KO, PR, SR, SEG, RT) is available.")
            .field("type", String.class, "modality of the object")
            .field("sopInstanceUID", String.class, "SOP instance UID")
            .field(SERIES_UID, SeriesUid.class, "series instance UID of the object")
            .field(
                "provenanceKind", String.class, "external, user... when the object says") // NON-NLS
            .field("provenanceSource", String.class, "the algorithm, device or person") // NON-NLS
            .build(),
        EventType.of(LOADING_STARTED, SINCE, "A load, download or export task started.")
            .field("source", String.class, "download, local, export or task")
            .field(SERIES, SeriesUid.class, "the series a download fills, when it is one")
            .build(),
        EventType.of(
                LOADING_PROGRESS,
                SINCE,
                "Images arrived in a series; one event per series per tick.")
            .coalesced()
            .field(SERIES, SeriesUid.class, "series id")
            .field("images", int.class, "images present")
            .build(),
        EventType.of(LOADING_STOPPED, SINCE, "A load, download or export task ended.")
            .field("source", String.class, "download, local, export or task")
            .field(SERIES, SeriesUid.class, "the series a download filled, when it is one")
            .field("canceled", boolean.class, "whether it was canceled")
            .build());
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (!(evt instanceof ObservableEvent event)) {
      return;
    }
    Object value = event.getNewValue();
    switch (event.getActionCommand()) {
      case ADD -> added(value);
      case REMOVE -> removed(value);
      case UPDATE -> {
        if (value instanceof DicomSpecialElement element) {
          objectAdded(element);
        }
      }
      case LOADING_START -> loading(LOADING_STARTED, value, false);
      case LOADING_STOP -> loading(LOADING_STOPPED, value, false);
      case LOADING_CANCEL -> loading(LOADING_STOPPED, value, true);
      case UPDATE_TOOLBARS ->
          bus.publish(Event.of(PANEL_CHANGED).put("panel", "toolbars")); // NON-NLS
      case UPDATE_TOOLS -> bus.publish(Event.of(PANEL_CHANGED).put("panel", "tools")); // NON-NLS
      default -> {
        // selection and replacement stay local to the explorer
      }
    }
  }

  private void added(Object value) {
    if (value instanceof SeriesEvent seriesEvent) {
      if (seriesEvent.getActionCommand() == SeriesEvent.Action.ADD_IMAGE
          && seriesEvent.getSource() instanceof MediaSeries<?> series) {
        String id = seriesId(series);
        bus.publish(
            Event.of(LOADING_PROGRESS)
                .coalesce(LOADING_PROGRESS + ":" + id)
                .put(SERIES, id)
                .put("images", series.size(null))); // NON-NLS
      }
      return;
    }
    if (value instanceof MediaSeriesGroup group) {
      switch (level(group)) {
        case PATIENT -> announce(group);
        case STUDY -> announce(group);
        case SERIES -> {
          announceParents(group);
          bus.publish(series(Event.of(SERIES_ADDED), group));
        }
        default -> {}
      }
    }
  }

  /** Publishes the study and the patient of a series the first time one of their series shows. */
  private void announceParents(MediaSeriesGroup series) {
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    if (study != null) {
      announceParent(model.getParent(study, DicomModel.patient));
      announceParent(study);
    }
  }

  private void announceParent(MediaSeriesGroup group) {
    if (group != null) {
      announce(group);
    }
  }

  private void announce(MediaSeriesGroup group) {
    if (!announced.add(group)) {
      return;
    }
    switch (level(group)) {
      case PATIENT -> bus.publish(patient(Event.of(PATIENT_ADDED), group));
      case STUDY -> bus.publish(study(Event.of(STUDY_ADDED), group));
      default -> {}
    }
  }

  private void removed(Object value) {
    if (value instanceof MediaSeriesGroup group) {
      announced.remove(group);
      switch (level(group)) {
        case PATIENT ->
            bus.publish(
                Event.of(PATIENT_REMOVED)
                    .identity(PATIENT_ID, text(group, Tag.PatientID), Event.PATIENT_ID));
        case STUDY ->
            bus.publish(Event.of(STUDY_REMOVED).put(STUDY_UID, uid(group, Tag.StudyInstanceUID)));
        case SERIES ->
            bus.publish(
                Event.of(SERIES_REMOVED)
                    .put("id", seriesId(group)) // NON-NLS
                    .put(STUDY_UID, studyOf(group)));
        default -> {}
      }
    }
  }

  private void objectAdded(DicomSpecialElement element) {
    Event.Builder event =
        Event.of(OBJECT_ADDED)
            .put("type", TagD.getTagValue(element, Tag.Modality, String.class)) // NON-NLS
            .put(
                "sopInstanceUID",
                TagD.getTagValue(element, Tag.SOPInstanceUID, String.class)) // NON-NLS
            .put(SERIES_UID, TagD.getTagValue(element, Tag.SeriesInstanceUID, String.class));
    try {
      Provenance provenance = ObjectDescriptor.describe(element).provenance();
      if (provenance != null) {
        event
            .put("provenanceKind", provenance.kind().key()) // NON-NLS
            .put("provenanceSource", provenance.source()); // NON-NLS
      }
    } catch (RuntimeException e) {
      LOGGER.debug("No provenance for {}", element, e);
    }
    bus.publish(event);
  }

  private void loading(String name, Object task, boolean canceled) {
    Event.Builder event = Event.of(name);
    if (task instanceof LoadSeries load) {
      event.put("source", "download"); // NON-NLS
      if (load.getDicomSeries() != null) {
        event.put(SERIES, seriesId(load.getDicomSeries()));
      }
    } else if (task instanceof LoadDicom) {
      event.put("source", "local"); // NON-NLS
    } else if (task != null && task.getClass().getName().contains(".exp.")) { // NON-NLS
      event.put("source", "export"); // NON-NLS
    } else {
      event.put("source", "task"); // NON-NLS
    }
    if (LOADING_STOPPED.equals(name)) {
      event.put("canceled", canceled); // NON-NLS
    }
    bus.publish(event);
  }

  private Event.Builder patient(Event.Builder event, MediaSeriesGroup group) {
    return event
        .identity(PATIENT_ID, text(group, Tag.PatientID), Event.PATIENT_ID)
        .identity("patientName", text(group, Tag.PatientName), Event.PATIENT_NAME) // NON-NLS
        .identity(
            "patientBirthDate", // NON-NLS
            TagD.getTagValue(group, Tag.PatientBirthDate, LocalDate.class),
            Event.BIRTH_DATE);
  }

  private Event.Builder study(Event.Builder event, MediaSeriesGroup group) {
    MediaSeriesGroup patient = model.getParent(group, DicomModel.patient);
    return event
        .put(STUDY_UID, uid(group, Tag.StudyInstanceUID))
        .identity(
            PATIENT_ID, patient == null ? null : text(patient, Tag.PatientID), Event.PATIENT_ID)
        .identity(
            "studyDate",
            TagD.getTagValue(group, Tag.StudyDate, LocalDate.class),
            Event.DATE) // NON-NLS
        .identity(
            "studyDescription", text(group, Tag.StudyDescription), Event.DESCRIPTION) // NON-NLS
        .identity(
            "accessionNumber", text(group, Tag.AccessionNumber), Event.ACCESSION_NUMBER); // NON-NLS
  }

  private Event.Builder series(Event.Builder event, MediaSeriesGroup group) {
    event
        .put("id", seriesId(group)) // NON-NLS
        .put(SERIES_UID, uid(group, Tag.SeriesInstanceUID))
        .put(STUDY_UID, studyOf(group))
        .put("modality", text(group, Tag.Modality)) // NON-NLS
        .identity(
            "seriesDescription", text(group, Tag.SeriesDescription), Event.DESCRIPTION); // NON-NLS
    if (group instanceof MediaSeries<?> series) {
      event.put("imageCount", series.size(null)); // NON-NLS
    }
    return event;
  }

  private enum Level {
    PATIENT,
    STUDY,
    SERIES,
    OTHER
  }

  private static Level level(MediaSeriesGroup group) {
    TagW tag = group.getTagID();
    if (tag == null) {
      return Level.OTHER;
    }
    if (tag.equals(DicomModel.patient.tagElement())) {
      return Level.PATIENT;
    }
    if (tag.equals(DicomModel.study.tagElement())) {
      return Level.STUDY;
    }
    return tag.equals(DicomModel.series.tagElement()) ? Level.SERIES : Level.OTHER;
  }

  /** The study of a series: the study node of the tree, else what the series itself carries. */
  private String studyOf(MediaSeriesGroup series) {
    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
    String uid = study == null ? null : uid(study, Tag.StudyInstanceUID);
    return uid != null ? uid : uid(series, Tag.StudyInstanceUID);
  }

  /** The handle {@code dicom:list} gives a series, which tells split sub-series apart. */
  static String seriesId(MediaSeriesGroup series) {
    Object id = series.getTagValue(TagW.SubseriesInstanceUID);
    return id != null ? String.valueOf(id) : uid(series, Tag.SeriesInstanceUID);
  }

  private static String uid(MediaSeriesGroup group, int tag) {
    return TagD.getTagValue(group, tag, String.class);
  }

  private static String text(MediaSeriesGroup group, int tag) {
    return TagD.getTagValue(group, tag, String.class);
  }

  /**
   * The payload a remote subscriber may see: each identity field passes the rule of its tag in the
   * profile the command results use for a remote caller — pseudonymized, date-shifted, cleared or
   * removed — and a kind this bridge does not know is removed.
   */
  static Event maskForRemote(Event event) {
    IdentityMask mask = IdentityMask.forProfile(MaskingProfile.aiRequest());
    Map<String, Object> payload = new LinkedHashMap<>(event.payload());
    Map<String, String> identity = new LinkedHashMap<>();
    event
        .identity()
        .forEach(
            (key, kind) -> {
              Integer tagId = TAG_OF_KIND.get(kind);
              TagW tag = tagId == null ? null : TagD.get(tagId);
              Object value = payload.get(key);
              if (tag == null || value == null) {
                payload.remove(key);
                return;
              }
              switch (mask.actionFor(tag)) {
                case REMOVE -> payload.remove(key);
                case CLEAR -> payload.put(key, "");
                default -> {
                  Object masked = mask.apply(tag, value);
                  if (masked == null) {
                    payload.remove(key);
                  } else {
                    payload.put(key, masked);
                    identity.put(key, kind);
                  }
                }
              }
            });
    return event.withPayload(payload, identity);
  }
}
