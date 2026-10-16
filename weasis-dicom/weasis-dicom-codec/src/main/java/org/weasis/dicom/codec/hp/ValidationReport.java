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
      List<String> unresolved) {}

  /** A study to validate: its id, a description and its series. */
  public record Input(String id, String description, List<StudySeries> series) {}

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
          bestUnresolved =
              plan.unresolved().stream()
                  .map(u -> u.label() + ": " + u.reason())
                  .toList(); // NON-NLS
          counter[1]++;
        }
      }
      studies.add(
          new StudyLine(
              input.id(), input.description(), best, bestScore, bestCells, bestUnresolved));
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
      studiesJson.add(s.add("cells", cells(line.cells())).add("unresolved", unresolved)); // NON-NLS
    }
    return Json.createObjectBuilder()
        .add("protocols", protocolsJson) // NON-NLS
        .add("studies", studiesJson) // NON-NLS
        .build();
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
      out.append('\n');
    }
    return out.toString().stripTrailing();
  }

  private static String cut(String text, int width) {
    return text.length() <= width ? text : text.substring(0, width - 1) + "…";
  }
}
