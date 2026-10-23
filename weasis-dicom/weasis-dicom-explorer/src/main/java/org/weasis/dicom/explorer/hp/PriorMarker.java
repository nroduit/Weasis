/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.hp;

import java.time.LocalDate;
import java.time.Period;
import java.util.Objects;
import java.util.Optional;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;

/**
 * What marks a cell of a hanging as showing a prior: the rank of the prior when the plan gives it,
 * the study date and how long before the current study it was acquired. A cell is a prior when it
 * shows a study of the patient older than the one the plan was matched on, so that a cell the user
 * filled with a prior series is marked too.
 *
 * @param rank 1 for the most recent prior, 0 when the plan does not rank this study
 * @param date the study date of the prior
 * @param earlier the time from the prior to the current study
 */
public record PriorMarker(int rank, LocalDate date, Period earlier) {

  /**
   * The marker of a cell showing {@code shown}, empty when it shows the current study, a later one,
   * or a study without a date.
   *
   * @param current the study the hanging was matched on, may be null
   * @param shown the study of the series in the cell, may be null
   * @param cell the plan of the cell, may be null
   */
  public static Optional<PriorMarker> of(
      MediaSeriesGroup current, MediaSeriesGroup shown, CellPlan cell) {
    if (current == null || shown == null || current == shown) {
      return Optional.empty();
    }
    LocalDate now = dateOf(current);
    LocalDate date = dateOf(shown);
    if (now == null || date == null || !date.isBefore(now)) {
      return Optional.empty();
    }
    String uid = TagD.getTagValue(shown, Tag.StudyInstanceUID, String.class);
    int rank =
        cell != null && cell.isPrior() && Objects.equals(uid, cell.study()) ? cell.prior() : 0;
    return Optional.of(new PriorMarker(rank, date, Period.between(date, now)));
  }

  /** The marker of a cell of the active hanging, read from its plan and its current study. */
  public static Optional<PriorMarker> ofCell(
      HangingPlanApplier.Applied applied, MediaSeriesGroup shown, CellPlan cell) {
    return applied == null ? Optional.empty() : of(applied.context().study(), shown, cell);
  }

  private static LocalDate dateOf(MediaSeriesGroup study) {
    return TagD.getTagValue(study, Tag.StudyDate, LocalDate.class);
  }

  /**
   * The interval in its largest unit: whole years, else whole months, else days.
   *
   * @return the count and the unit: {@code y}, {@code m} or {@code d}
   */
  public Interval interval() {
    if (earlier.toTotalMonths() >= 12) {
      return new Interval(earlier.getYears(), 'y');
    }
    if (earlier.toTotalMonths() >= 1) {
      return new Interval((int) earlier.toTotalMonths(), 'm');
    }
    return new Interval(earlier.getDays(), 'd');
  }

  /** A count of years, months or days. */
  public record Interval(int count, char unit) {}
}
