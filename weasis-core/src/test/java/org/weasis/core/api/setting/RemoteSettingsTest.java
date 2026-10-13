/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.util.DocumentStore;

@DisplayNameGeneration(ReplaceUnderscores.class)
class RemoteSettingsTest {
  private static final long NO_DELAY = 60_000;
  private static final String CORE = "weasis-core"; // NON-NLS
  private static final String PRESETS = "windowPresets.json"; // NON-NLS

  /** The preference service: modules by name. */
  private static final class Service implements RemoteSettings.Transport {
    final Map<String, String> modules = new TreeMap<>();
    int puts;
    boolean down;

    @Override
    public String get(String module) throws IOException {
      if (down) {
        throw new IOException("connection refused"); // NON-NLS
      }
      return modules.get(module);
    }

    @Override
    public void put(String module, String body) throws IOException {
      if (down) {
        throw new IOException("connection refused"); // NON-NLS
      }
      modules.put(module, body);
      puts++;
    }
  }

  /** One workstation of the same user. */
  private static final class Station {
    final SettingsFileStore store = new SettingsFileStore(NO_DELAY);
    final Path documents;
    final RemoteSettings remote = new RemoteSettings(NO_DELAY);

    Station(Path root, Service service) throws IOException {
      store.configure(root.resolve("settings"), List.of()); // NON-NLS
      documents = Files.createDirectories(root.resolve("documents")); // NON-NLS
      remote.configure(service, store, documents, root.resolve(RemoteSettings.STATE_FILE));
    }
  }

  private static String presets(String updated, String... idAndName) {
    StringBuilder b =
        new StringBuilder(
            "{\"kind\":\"weasis.document\",\"id\":\"windowPresets\",\"schema\":1,\"updated\":\""
                + updated
                + "\",\"presets\":[");
    for (int i = 0; i < idAndName.length; i += 2) {
      b.append(i > 0 ? "," : "")
          .append("{\"id\":\"")
          .append(idAndName[i])
          .append("\",\"name\":\"")
          .append(idAndName[i + 1])
          .append("\"}");
    }
    return b.append("]}").toString();
  }

  private static JsonObject json(String text) {
    return Json.createReader(new StringReader(text)).readObject();
  }

  @Test
  void a_push_sends_each_changed_file_once_with_the_index(@TempDir Path dir) throws IOException {
    Service service = new Service();
    Station a = new Station(dir.resolve("a"), service);
    a.store.group(CORE).put("weasis.theme", "dark"); // NON-NLS
    Files.writeString(
        a.documents.resolve(PRESETS), presets("2026-10-01T00:00:00Z", "user.é", "Os"));

    RemoteSettings.Report first = a.remote.push();
    int puts = service.puts;
    RemoteSettings.Report second = a.remote.push();

    assertAll(
        () ->
            assertEquals(
                List.of("documents/windowPresets@1", "settings/weasis-core@1", "index@1"),
                first.pushed()),
        () -> assertEquals(List.of(), second.pushed()),
        () -> assertEquals(puts, service.puts),
        () ->
            assertTrue(
                service.modules.get("documents/windowPresets@1").chars().allMatch(c -> c < 0x80)),
        () ->
            assertEquals(
                "user.é",
                json(service.modules.get("documents/windowPresets@1"))
                    .getJsonArray("presets")
                    .getJsonObject(0)
                    .getString("id")),
        () ->
            assertEquals(
                List.of("documents/windowPresets@1", "settings/weasis-core@1"),
                RemoteSettings.readIndex(service.modules.get("index@1"))));
  }

  @Test
  void a_new_workstation_receives_the_user_layer(@TempDir Path dir) throws IOException {
    Service service = new Service();
    Station a = new Station(dir.resolve("a"), service);
    a.store.group(CORE).node("zoom").put("factor", "2"); // NON-NLS
    Files.writeString(a.documents.resolve(PRESETS), presets("2026-10-01T00:00:00Z", "user.a", "A"));
    a.remote.push();

    Station b = new Station(dir.resolve("b"), service);
    RemoteSettings.Report pulled = b.remote.pull();

    assertAll(
        () -> assertEquals(2, pulled.pulled().size()),
        () -> assertEquals("2", b.store.group(CORE).node("zoom").get("factor", null)),
        () -> assertTrue(Files.isRegularFile(b.documents.resolve(PRESETS))),
        () -> assertEquals(List.of(), b.remote.push().pushed()));
  }

  @Test
  void a_key_changed_on_both_sides_takes_the_value_of_the_newer_file(@TempDir Path dir)
      throws IOException {
    Service service = new Service();
    service.modules.put(
        "settings/weasis-core@1",
        "{\"kind\":\"weasis.settings\",\"id\":\"weasis-core\",\"schema\":1,"
            + "\"updated\":\"2030-01-01T00:00:00Z\","
            + "\"values\":{\"theme\":\"remote\",\"only.remote\":\"r\"}}");
    service.modules.put("index@1", "{\"modules\":[\"settings/weasis-core@1\"]}");
    Station b = new Station(dir.resolve("b"), service);
    b.store.group(CORE).put("theme", "local"); // NON-NLS
    b.store.group(CORE).put("only.local", "l"); // NON-NLS
    b.store.flushAll(); // stamped now, older than 2030

    b.remote.pull();
    assertAll(
        () -> assertEquals("remote", b.store.group(CORE).get("theme", null)),
        () -> assertEquals("r", b.store.group(CORE).get("only.remote", null)),
        () -> assertEquals("l", b.store.group(CORE).get("only.local", null)));
  }

  @Test
  void documents_are_united_by_entry_and_the_newer_one_wins_an_entry(@TempDir Path dir)
      throws IOException {
    Service service = new Service();
    service.modules.put(
        "documents/windowPresets@1",
        presets("2030-01-01T00:00:00Z", "user.shared", "Remote", "user.remote", "R"));
    service.modules.put("index@1", "{\"modules\":[\"documents/windowPresets@1\"]}");
    Station b = new Station(dir.resolve("b"), service);
    Files.writeString(
        b.documents.resolve(PRESETS),
        presets("2026-01-01T00:00:00Z", "user.shared", "Local", "user.local", "L"));

    b.remote.pull();
    JsonObject merged = json(Files.readString(b.documents.resolve(PRESETS)));
    Map<String, String> names = new TreeMap<>();
    merged
        .getJsonArray("presets")
        .getValuesAs(JsonObject.class)
        .forEach(o -> names.put(o.getString("id"), o.getString("name")));

    assertAll(
        () ->
            assertEquals(
                Map.of("user.local", "L", "user.remote", "R", "user.shared", "Remote"), names),
        () -> assertEquals("weasis.document", merged.getString("kind")));
  }

  @Test
  void a_module_of_a_newer_schema_or_another_kind_is_left_alone(@TempDir Path dir)
      throws IOException {
    Service service = new Service();
    service.modules.put("documents/windowPresets@2", presets("2030-01-01T00:00:00Z", "x", "X"));
    service.modules.put(
        "index@1", "{\"modules\":[\"documents/windowPresets@2\",\"weasis-core\"]}"); // NON-NLS
    Station b = new Station(dir.resolve("b"), service);

    RemoteSettings.Report pulled = b.remote.pull();
    assertAll(
        () -> assertEquals(List.of(), pulled.pulled()),
        () -> assertFalse(Files.exists(b.documents.resolve(PRESETS))),
        () -> assertFalse(RemoteSettings.supported("weasis-core")),
        () -> assertTrue(RemoteSettings.supported("settings/weasis-core@1")));
  }

  @Test
  void an_unreachable_service_leaves_the_local_settings_alone(@TempDir Path dir)
      throws IOException {
    Service service = new Service();
    service.down = true;
    Station b = new Station(dir.resolve("b"), service);
    b.store.group(CORE).put("theme", "local"); // NON-NLS

    RemoteSettings.Report pulled = b.remote.pull();
    RemoteSettings.Report pushed = b.remote.push();
    assertAll(
        () -> assertFalse(pulled.errors().isEmpty()),
        () -> assertFalse(pushed.errors().isEmpty()),
        () -> assertEquals("local", b.store.group(CORE).get("theme", null)),
        () -> assertNull(service.modules.get("index@1")));
  }

  @Test
  void the_state_survives_a_restart_so_nothing_is_sent_again(@TempDir Path dir) throws IOException {
    Service service = new Service();
    Station a = new Station(dir.resolve("a"), service);
    a.store.group(CORE).put("theme", "dark"); // NON-NLS
    a.remote.push();

    RemoteSettings again = new RemoteSettings(NO_DELAY);
    again.configure(
        service, a.store, a.documents, dir.resolve("a").resolve(RemoteSettings.STATE_FILE));
    assertEquals(List.of(), again.push().pushed());
  }

  @Test
  void a_private_document_is_neither_sent_nor_taken_from_the_service(@TempDir Path dir)
      throws IOException {
    DocumentStore.declarePrivate("hp.sessions.json"); // NON-NLS
    Service service = new Service();
    service.modules.put("documents/hp.sessions@1", "{\"sessions\":[{\"id\":\"r\"}]}");
    service.modules.put("index@1", "{\"modules\":[\"documents/hp.sessions@1\"]}");
    Station a = new Station(dir.resolve("a"), service);
    Files.writeString(a.documents.resolve("hp.sessions.json"), "{\"sessions\":[{\"id\":\"s\"}]}");

    RemoteSettings.Report pulled = a.remote.pull();
    RemoteSettings.Report pushed = a.remote.push();
    assertAll(
        () -> assertEquals(List.of(), pulled.pulled()),
        () -> assertTrue(pushed.pushed().stream().noneMatch(m -> m.contains("hp.sessions"))),
        () ->
            assertTrue(Files.readString(a.documents.resolve("hp.sessions.json")).contains("\"s\"")),
        () ->
            assertEquals(
                "{\"sessions\":[{\"id\":\"r\"}]}", service.modules.get("documents/hp.sessions@1")));
  }
}
