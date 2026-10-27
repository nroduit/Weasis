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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshot;
import org.weasis.core.ui.docking.ws.snapshot.WorkspaceSnapshotIO;

/**
 * <i>Keep this display</i>: a reading state saved as a session, the exact restore of a study. The
 * workspace snapshot (windows, split tree, views with their data and view parts, the shell), the
 * studies it shows with the archive each came from, the protocol that hung it when one did with the
 * bindings of its cells, a label and a pinned flag. It carries identifiers of patient data (study
 * and series UIDs), never a name, a token or a URL with credentials: it is stored in the private
 * user document and treated as patient data.
 *
 * @param id the session id, the instant of the capture
 * @param label a label the user gave, may be null
 * @param pinned whether the session escapes the retention by number
 * @param created when it was kept
 * @param updated when it was last kept or restored
 * @param sources the archives and the studies of each, what a restore must get access to
 * @param protocol the id of the protocol that hung the display, may be null
 * @param step the reading step shown, from 1, 0 when no protocol
 * @param bindings the series shown per display set of the step, by display set number
 * @param workspace the workspace snapshot
 */
public record Session(
    String id,
    String label,
    boolean pinned,
    Instant created,
    Instant updated,
    List<Source> sources,
    String protocol,
    int step,
    Map<Integer, String> bindings,
    WorkspaceSnapshot workspace,
    List<Derived> derived) {

  /** The archive id a source is loaded from when none is known: files on this machine. */
  public static final String LOCAL = "local"; // NON-NLS

  /** The keys a session must never hold: demographics and secrets. */
  static final Set<String> FORBIDDEN =
      Set.of(
          "patientName", // NON-NLS
          "patientId", // NON-NLS
          "patientBirthDate", // NON-NLS
          "token", // NON-NLS
          "password", // NON-NLS
          "authorization", // NON-NLS
          "url"); // NON-NLS

  public Session {
    sources = sources == null ? List.of() : List.copyOf(sources);
    bindings = bindings == null ? Map.of() : Map.copyOf(bindings);
    derived = derived == null ? List.of() : List.copyOf(derived);
  }

  /** A session showing acquired series only. */
  public Session(
      String id,
      String label,
      boolean pinned,
      Instant created,
      Instant updated,
      List<Source> sources,
      String protocol,
      int step,
      Map<Integer, String> bindings,
      WorkspaceSnapshot workspace) {
    this(id, label, pinned, created, updated, sources, protocol, step, bindings, workspace, null);
  }

  /**
   * A series Weasis derived from loaded ones, kept as the spec its provider builds it from again.
   *
   * @param kind the derivation, the provider's kind
   * @param spec what the provider needs to build the series again
   */
  public record Derived(String kind, JsonObject spec) {}

  /** The studies of one archive. */
  public record Source(String archive, List<String> studies) {
    public Source {
      archive = archive == null ? LOCAL : archive;
      studies = studies == null ? List.of() : List.copyOf(studies);
    }
  }

  /** Every study UID of the session. */
  public Set<String> studies() {
    Set<String> uids = new LinkedHashSet<>();
    sources.forEach(s -> uids.addAll(s.studies()));
    return uids;
  }

  public Session withUpdated(Instant at) {
    return new Session(
        id, label, pinned, created, at, sources, protocol, step, bindings, workspace, derived);
  }

  public JsonObject toJson() {
    JsonObjectBuilder json =
        Json.createObjectBuilder()
            .add(ListDocument.ID, id)
            .add("pinned", pinned) // NON-NLS
            .add("created", created.toString()) // NON-NLS
            .add("updated", updated.toString()); // NON-NLS
    if (label != null) {
      json.add("label", label); // NON-NLS
    }
    JsonArrayBuilder sourcesJson = Json.createArrayBuilder();
    for (Source source : sources) {
      JsonArrayBuilder studies = Json.createArrayBuilder();
      source.studies().forEach(studies::add);
      sourcesJson.add(
          Json.createObjectBuilder()
              .add("archive", source.archive()) // NON-NLS
              .add("studies", studies)); // NON-NLS
    }
    json.add("sources", sourcesJson); // NON-NLS
    if (protocol != null) {
      json.add("protocol", protocol).add("step", step); // NON-NLS
      JsonObjectBuilder b = Json.createObjectBuilder();
      bindings.forEach((k, v) -> b.add(String.valueOf(k), v));
      json.add("bindings", b); // NON-NLS
    }
    if (!derived.isEmpty()) {
      JsonArrayBuilder derivedJson = Json.createArrayBuilder();
      for (Derived d : derived) {
        derivedJson.add(
            Json.createObjectBuilder().add("kind", d.kind()).add("spec", d.spec())); // NON-NLS
      }
      json.add("derived", derivedJson); // NON-NLS
    }
    return json.add("workspace", WorkspaceSnapshotIO.toJson(workspace)).build(); // NON-NLS
  }

  public static Session fromJson(JsonObject json) {
    List<Source> sources = new ArrayList<>();
    JsonArray array = json.getJsonArray("sources"); // NON-NLS
    if (array != null) {
      for (JsonObject s : array.getValuesAs(JsonObject.class)) {
        List<String> studies = new ArrayList<>();
        JsonArray uids = s.getJsonArray("studies"); // NON-NLS
        if (uids != null) {
          uids.getValuesAs(JsonString.class).forEach(u -> studies.add(u.getString()));
        }
        sources.add(new Source(s.getString("archive", LOCAL), studies)); // NON-NLS
      }
    }
    Map<Integer, String> bindings = new LinkedHashMap<>();
    JsonObject b = json.getJsonObject("bindings"); // NON-NLS
    if (b != null) {
      b.forEach((k, v) -> bindings.put(Integer.parseInt(k), ((JsonString) v).getString()));
    }
    List<Derived> derived = new ArrayList<>();
    JsonArray derivedJson = json.getJsonArray("derived"); // NON-NLS
    if (derivedJson != null) {
      for (JsonObject d : derivedJson.getValuesAs(JsonObject.class)) {
        derived.add(new Derived(d.getString("kind"), d.getJsonObject("spec"))); // NON-NLS
      }
    }
    return new Session(
        json.getString(ListDocument.ID),
        json.getString("label", null), // NON-NLS
        json.getBoolean("pinned", false), // NON-NLS
        Instant.parse(json.getString("created")), // NON-NLS
        Instant.parse(json.getString("updated")), // NON-NLS
        sources,
        json.getString("protocol", null), // NON-NLS
        json.getInt("step", 0), // NON-NLS
        bindings,
        WorkspaceSnapshotIO.fromJson(json.getJsonObject("workspace")), // NON-NLS
        derived);
  }

  /** The keys of a JSON tree that a session must not hold, empty when it is clean. */
  public static List<String> forbiddenKeys(JsonValue value) {
    List<String> found = new ArrayList<>();
    walk(value, "", found);
    return found;
  }

  private static void walk(JsonValue value, String path, List<String> found) {
    if (value instanceof JsonObject object) {
      object.forEach(
          (k, v) -> {
            if (FORBIDDEN.contains(k) || k.toLowerCase(Locale.ROOT).contains("password")) {
              found.add(path + k);
            }
            walk(v, path + k + ".", found);
          });
    } else if (value instanceof JsonArray array) {
      for (int i = 0; i < array.size(); i++) {
        walk(array.get(i), path + i + ".", found);
      }
    }
  }
}
