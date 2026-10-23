/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;

/**
 * Another study of the patient that time-based image sets may bind to: its uid, its date and its
 * series. The {@code rank} counts the priors from the most recent one (1) to the oldest.
 *
 * @param uid the Study Instance UID
 * @param date the study date and time, null when the study has none
 * @param rank 1 for the most recent prior, 2 for the one before
 * @param series the series of the study
 */
public record PriorStudy(String uid, LocalDateTime date, int rank, List<StudySeries> series) {

  public PriorStudy {
    series = series == null ? List.of() : List.copyOf(series);
  }

  /** The Study Instance UID of a study given by its series, null when none is known. */
  public static String uidOf(List<StudySeries> study) {
    for (StudySeries s : study) {
      String uid = s.dataset().getString(Tag.StudyInstanceUID);
      if (uid != null && !uid.isBlank()) {
        return uid;
      }
    }
    return null;
  }

  /** The Study Date and Time of a study given by its series, null without a Study Date. */
  public static LocalDateTime dateOf(List<StudySeries> study) {
    for (StudySeries s : study) {
      LocalDateTime date = dateOf(s.dataset());
      if (date != null) {
        return date;
      }
    }
    return null;
  }

  static LocalDateTime dateOf(Attributes dataset) {
    if (!dataset.containsValue(Tag.StudyDate)) {
      return null;
    }
    Date date = dataset.getDate(Tag.StudyDateAndTime);
    return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }

  /**
   * The priors of a current study among other studies of the same patient: the dated studies older
   * than the current one, most recent first, ranked from 1. A study without date, the current study
   * itself and the studies after it are not priors. Without a current date, there is no prior.
   *
   * @param current the series of the current study
   * @param others the series of the other studies of the patient, one list per study
   */
  public static List<PriorStudy> of(
      List<StudySeries> current, Collection<List<StudySeries>> others) {
    LocalDateTime now = dateOf(current);
    if (now == null || others == null) {
      return List.of();
    }
    String currentUid = uidOf(current);
    record Dated(String uid, LocalDateTime date, List<StudySeries> series) {}
    List<Dated> older = new ArrayList<>();
    for (List<StudySeries> study : others) {
      if (study == null || study.isEmpty()) {
        continue;
      }
      String uid = uidOf(study);
      LocalDateTime date = dateOf(study);
      if (date != null && date.isBefore(now) && !Objects.equals(uid, currentUid)) {
        older.add(new Dated(uid, date, study));
      }
    }
    older.sort(Comparator.comparing(Dated::date).reversed());
    List<PriorStudy> priors = new ArrayList<>();
    for (Dated d : older) {
      priors.add(new PriorStudy(d.uid(), d.date(), priors.size() + 1, d.series()));
    }
    return priors;
  }
}
