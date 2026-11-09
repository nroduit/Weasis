/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.provenance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A value with its provenance, and the sources that contested it without outranking it: "T2 by
 * header; the classifier says FLAIR, 0.62". The why panel shows both, the logic uses the value.
 *
 * @param value the value in force, may be null when nothing is known
 * @param provenance where it came from, null when the value is null
 * @param contests the sources that proposed another value and lost, with their value described
 */
public record Sourced<T>(T value, Provenance provenance, List<Contest> contests) {

  /** A losing proposal: what it said and where it came from. */
  public record Contest(String value, Provenance provenance) {}

  public Sourced {
    contests = contests == null ? List.of() : List.copyOf(contests);
  }

  public Sourced(T value, Provenance provenance) {
    this(value, provenance, List.of());
  }

  public static <T> Sourced<T> none() {
    return new Sourced<>(null, null, List.of());
  }

  public boolean isKnown() {
    return value != null;
  }

  /**
   * The precedence rule of the specification: a provider may only fill a value no higher source
   * produced, or contest it. {@code USER > EXTERNAL > HEADER > TABLE > CLASSIFIER} above the
   * threshold {@code > HEURISTIC}; a classifier below the threshold never fills, it only contests.
   *
   * @param threshold the confidence a classifier needs to fill or replace a value
   * @return the value in force with the other one as a contest when it disagrees
   */
  public Sourced<T> merge(Sourced<T> other, double threshold) {
    if (other == null || !other.isKnown()) {
      return this;
    }
    if (!isKnown()) {
      return belowThreshold(other.provenance, threshold)
          ? withContest(other)
          : new Sourced<>(other.value, other.provenance, merged(contests, other.contests));
    }
    boolean otherWins =
        !belowThreshold(other.provenance, threshold) && other.provenance.outranks(provenance);
    if (Objects.equals(value, other.value)) {
      return otherWins
          ? new Sourced<>(other.value, other.provenance, merged(contests, other.contests))
          : new Sourced<>(value, provenance, merged(contests, other.contests));
    }
    return otherWins ? other.withContest(this) : withContest(other);
  }

  private static boolean belowThreshold(Provenance provenance, double threshold) {
    return provenance != null
        && provenance.kind() == Provenance.Kind.CLASSIFIER
        && provenance.confidence() != null
        && provenance.confidence() < threshold;
  }

  private Sourced<T> withContest(Sourced<T> loser) {
    List<Contest> all = new ArrayList<>(contests);
    all.add(new Contest(String.valueOf(loser.value), loser.provenance));
    all.addAll(loser.contests);
    return new Sourced<>(value, provenance, all);
  }

  private static List<Contest> merged(List<Contest> a, List<Contest> b) {
    List<Contest> all = new ArrayList<>(a);
    all.addAll(b);
    return all;
  }
}
