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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.weasis.dicom.codec.hp.HangingEngine.Candidate;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.codec.hp.HangingPlan.CellPlan;
import org.weasis.dicom.codec.hp.HangingPlan.Match;

/**
 * The matching of the protocols over a set of studies, the regression test of a protocol library:
 * per protocol, how many studies it applies to and how its cells matched over them; per study, the
 * protocol chosen and the kind of every cell.
 *
 * @param protocols one line per protocol, in the order given
 * @param studies one line per study, in the order given
 */
public record ValidationReport(List<ProtocolLine> protocols, List<StudyLine> studies) {

  /** The counts of a protocol over the studies it applied to. */
  public record ProtocolLine(
      String id,
      String name,
      int applies,
      int chosen,
      Map<Match, Integer> cells,
      int unresolved,
      List<String> failures) {

    /** The share of cells that show a series, 1 when there is none. */
    public double filledShare() {
      int total = cells.values().stream().mapToInt(Integer::intValue).sum();
      int empty = cells.getOrDefault(Match.EMPTY, 0);
      return total == 0 ? 1 : (double) (total - empty) / total;
    }
  }

  /** The best protocol of a study and the kind of every cell of its first plan. */
  public record StudyLine(
      String study,
      String description,
      String protocol,
      double score,
      Map<Match, Integer> cells,
      List<String> unresolved,
      Outcome outcome,
      List<CellPlan> firstStep) {
    public StudyLine {
      firstStep = firstStep == null ? List.of() : List.copyOf(firstStep);
    }
  }

  /** A study to validate: its id, a description and its series. */
  public record Input(
      String id, String description, List<StudySeries> series, Expectation expected) {
    public Input(String id, String description, List<StudySeries> series) {
      this(id, description, series, null);
    }
  }

  /**
   * What a study is expected to hang, written once by hand from a reviewed plan.
   *
   * @param protocol the id of the protocol expected to be chosen, null when any
   * @param cells the series expected in the cells of the first step, by cell number from 1
   */
  public record Expectation(String protocol, Map<Integer, String> cells) {
    public Expectation {
      cells = cells == null ? Map.of() : Map.copyOf(cells);
    }

    /**
     * The expectations of a file: {@code {"studies": {"<uid>": {"protocol", "cells": {"1":
     * uid}}}}}.
     */
    public static Map<String, Expectation> read(JsonObject json) {
      Map<String, Expectation> result = new LinkedHashMap<>();
      JsonObject studies = json.getJsonObject("studies"); // NON-NLS
      if (studies == null) {
        return result;
      }
      studies.forEach(
          (uid, value) -> {
            if (value instanceof JsonObject o) {
              Map<Integer, String> cells = new LinkedHashMap<>();
              JsonObject c = o.getJsonObject("cells"); // NON-NLS
              if (c != null) {
                c.forEach((k, v) -> cells.put(Integer.parseInt(k), ((JsonString) v).getString()));
              }
              result.put(uid, new Expectation(o.getString("protocol", null), cells)); // NON-NLS
            }
          });
      return result;
    }
  }

  /** How a study met its expectation: null fields when it had none. */
  public record Outcome(Boolean protocolMet, int cellsExpected, int cellsMet, List<String> misses) {
    public Outcome {
      misses = misses == null ? List.of() : List.copyOf(misses);
    }

    public boolean met() {
      return (protocolMet == null || protocolMet) && cellsMet == cellsExpected;
    }
  }

  /** Matches every candidate against every study and counts. */
  public static ValidationReport run(List<Candidate> candidates, List<Input> inputs) {
    Map<String, ProtocolLine> lines = new LinkedHashMap<>();
    Map<String, Map<Match, Integer>> cellsByProtocol = new LinkedHashMap<>();
    Map<String, List<String>> failuresByProtocol = new LinkedHashMap<>();
    Map<String, int[]> counters = new LinkedHashMap<>();
    for (Candidate candidate : candidates) {
      String id = candidate.document().id();
      cellsByProtocol.put(id, new EnumMap<>(Match.class));
      failuresByProtocol.put(id, new ArrayList<>());
      counters.put(id, new int[3]); // applies, chosen, unresolved
    }

    List<StudyLine> studies = new ArrayList<>();
    for (Input input : inputs) {
      List<ProtocolScore> scores = HangingEngine.select(candidates, input.series());
      String best = null;
      double bestScore = 0;
      Map<Match, Integer> bestCells = new EnumMap<>(Match.class);
      List<String> bestUnresolved = List.of();
      HangingPlan bestPlan = null;
      for (int i = 0; i < scores.size(); i++) {
        ProtocolScore score = scores.get(i);
        String id = score.document().id();
        HangingPlan plan = HangingEngine.plan(score, input.series());
        Map<Match, Integer> cells = count(plan);
        int[] counter = counters.get(id);
        counter[0]++;
        counter[2] += plan.unresolved().size();
        cells.forEach((k, v) -> cellsByProtocol.get(id).merge(k, v, Integer::sum));
        if (cells.getOrDefault(Match.EMPTY, 0) > 0 || !plan.unresolved().isEmpty()) {
          failuresByProtocol
              .get(id)
              .add(
                  input.description()
                      + ": " // NON-NLS
                      + cells.getOrDefault(Match.EMPTY, 0)
                      + " empty, " // NON-NLS
                      + plan.unresolved().size()
                      + " unresolved"); // NON-NLS
        }
        if (i == 0) {
          best = id;
          bestScore = score.score();
          bestCells = cells;
          bestPlan = plan;
          bestUnresolved =
              plan.unresolved().stream()
                  .map(u -> u.label() + ": " + u.reason())
                  .toList(); // NON-NLS
          counter[1]++;
        }
      }
      studies.add(
          new StudyLine(
              input.id(),
              input.description(),
              best,
              bestScore,
              bestCells,
              bestUnresolved,
              input.expected() == null ? null : outcome(input.expected(), best, bestPlan),
              bestPlan == null
                  ? List.of()
                  : bestPlan.firstStep().map(HangingPlan.Step::cells).orElse(List.of())));
    }
    for (Candidate candidate : candidates) {
      String id = candidate.document().id();
      int[] counter = counters.get(id);
      lines.put(
          id,
          new ProtocolLine(
              id,
              candidate.document().protocol().getHangingProtocolName(),
              counter[0],
              counter[1],
              cellsByProtocol.get(id),
              counter[2],
              failuresByProtocol.get(id)));
    }
    return new ValidationReport(new ArrayList<>(lines.values()), studies);
  }

  /** Compares the chosen protocol and the cells of its first step with the expectation. */
  static Outcome outcome(Expectation expected, String protocol, HangingPlan plan) {
    List<String> misses = new ArrayList<>();
    Boolean protocolMet = null;
    if (expected.protocol() != null) {
      protocolMet = expected.protocol().equals(protocol);
      if (!protocolMet) {
        misses.add("protocol " + protocol + " instead of " + expected.protocol()); // NON-NLS
      }
    }
    int met = 0;
    List<CellPlan> cells =
        plan == null ? List.of() : plan.firstStep().map(HangingPlan.Step::cells).orElse(List.of());
    for (Map.Entry<Integer, String> e : expected.cells().entrySet()) {
      int index = e.getKey() - 1;
      String actual = index >= 0 && index < cells.size() ? cells.get(index).series() : null;
      if (e.getValue().equals(actual)) {
        met++;
      } else {
        misses.add("cell " + e.getKey() + ": " + actual + " instead of " + e.getValue()); // NON-NLS
      }
    }
    return new Outcome(protocolMet, expected.cells().size(), met, misses);
  }

  /** The studies with an expectation that was met, and those with one. */
  public int[] expectations() {
    int with = 0;
    int met = 0;
    for (StudyLine line : studies) {
      if (line.outcome() != null) {
        with++;
        if (line.outcome().met()) {
          met++;
        }
      }
    }
    return new int[] {met, with};
  }

  /** The number of cells of every kind over the steps of a plan. */
  static Map<Match, Integer> count(HangingPlan plan) {
    Map<Match, Integer> counts = new EnumMap<>(Match.class);
    for (HangingPlan.Step step : plan.steps()) {
      for (CellPlan cell : step.cells()) {
        counts.merge(cell.match(), 1, Integer::sum);
      }
    }
    return counts;
  }

  public JsonObject toJson() {
    JsonArrayBuilder protocolsJson = Json.createArrayBuilder();
    for (ProtocolLine line : protocols) {
      JsonObjectBuilder p = Json.createObjectBuilder().add("protocol", line.id()); // NON-NLS
      if (line.name() != null) {
        p.add("name", line.name()); // NON-NLS
      }
      p.add("applies", line.applies()) // NON-NLS
          .add("chosen", line.chosen()) // NON-NLS
          .add("cells", cells(line.cells())) // NON-NLS
          .add("unresolved", line.unresolved()) // NON-NLS
          .add("filledShare", Math.round(line.filledShare() * 1000) / 1000.0); // NON-NLS
      JsonArrayBuilder failures = Json.createArrayBuilder();
      line.failures().forEach(failures::add);
      protocolsJson.add(p.add("failures", failures)); // NON-NLS
    }
    JsonArrayBuilder studiesJson = Json.createArrayBuilder();
    for (StudyLine line : studies) {
      JsonObjectBuilder s = Json.createObjectBuilder().add("study", line.study()); // NON-NLS
      if (line.description() != null) {
        s.add("description", line.description()); // NON-NLS
      }
      if (line.protocol() != null) {
        s.add("protocol", line.protocol())
            .add("score", Math.round(line.score() * 1000) / 1000.0); // NON-NLS
      }
      JsonArrayBuilder unresolved = Json.createArrayBuilder();
      line.unresolved().forEach(unresolved::add);
      s.add("cells", cells(line.cells())).add("unresolved", unresolved); // NON-NLS
      JsonArrayBuilder first = Json.createArrayBuilder();
      for (CellPlan cell : line.firstStep()) {
        JsonObjectBuilder c =
            Json.createObjectBuilder()
                .add("cell", cell.cell() + 1) // NON-NLS
                .add("match", cell.match().name().toLowerCase(Locale.ROOT)); // NON-NLS
        if (cell.label() != null) {
          c.add("label", cell.label()); // NON-NLS
        }
        if (cell.series() != null) {
          c.add("series", cell.series()); // NON-NLS
        }
        first.add(c);
      }
      s.add("firstStep", first); // NON-NLS
      if (line.outcome() != null) {
        JsonObjectBuilder o =
            Json.createObjectBuilder().add("met", line.outcome().met()); // NON-NLS
        JsonArrayBuilder misses = Json.createArrayBuilder();
        line.outcome().misses().forEach(misses::add);
        s.add("expected", o.add("misses", misses)); // NON-NLS
      }
      studiesJson.add(s);
    }
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add("protocols", protocolsJson) // NON-NLS
            .add("studies", studiesJson); // NON-NLS
    int[] expectations = expectations();
    if (expectations[1] > 0) {
      json.add(
          "expectations",
          Json.createObjectBuilder()
              .add("met", expectations[0])
              .add("total", expectations[1])); // NON-NLS
    }
    return json.build();
  }

  private static JsonObjectBuilder cells(Map<Match, Integer> counts) {
    JsonObjectBuilder json = Json.createObjectBuilder();
    for (Match match : Match.values()) {
      json.add(match.name().toLowerCase(Locale.ROOT), counts.getOrDefault(match, 0));
    }
    return json;
  }

  /** A table per protocol, then one line per study. */
  public String text() {
    StringBuilder out = new StringBuilder();
    out.append(
        "protocol                 applies chosen exact prefer fallbk shared empty unresolved\n"); // NON-NLS
    for (ProtocolLine line : protocols) {
      out.append(
          String.format(
              Locale.ROOT,
              "%-24s %7d %6d %5d %6d %6d %6d %5d %10d%n", // NON-NLS
              cut(line.id(), 24),
              line.applies(),
              line.chosen(),
              line.cells().getOrDefault(Match.EXACT, 0),
              line.cells().getOrDefault(Match.PREFERRED, 0),
              line.cells().getOrDefault(Match.FALLBACK, 0),
              line.cells().getOrDefault(Match.SHARED, 0),
              line.cells().getOrDefault(Match.EMPTY, 0),
              line.unresolved()));
    }
    for (StudyLine line : studies) {
      out.append(line.description() == null ? line.study() : line.description())
          .append(": ") // NON-NLS
          .append(line.protocol() == null ? "no protocol applies" : line.protocol()); // NON-NLS
      if (line.protocol() != null) {
        out.append(' ');
        line.cells()
            .forEach(
                (k, v) ->
                    out.append(k.name().toLowerCase(Locale.ROOT))
                        .append('=')
                        .append(v)
                        .append(' '));
        if (!line.unresolved().isEmpty()) {
          out.append("unresolved=").append(line.unresolved()); // NON-NLS
        }
      }
      if (line.outcome() != null) {
        out.append(line.outcome().met() ? " [as expected]" : " [NOT as expected: "); // NON-NLS
        if (!line.outcome().met()) {
          out.append(String.join("; ", line.outcome().misses())).append(']');
        }
      }
      out.append('\n');
    }
    int[] expectations = expectations();
    if (expectations[1] > 0) {
      out.append(expectations[0])
          .append(" of ") // NON-NLS
          .append(expectations[1])
          .append(" expectations met\n"); // NON-NLS
    }
    return out.toString().stripTrailing();
  }

  private static String cut(String text, int width) {
    return text.length() <= width ? text : text.substring(0, width - 1) + "…";
  }
}
