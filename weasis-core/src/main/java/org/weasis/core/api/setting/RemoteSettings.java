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

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;

/**
 * Mirrors the user layer — the settings groups and the user documents — to the preference service
 * ({@code weasis.pref.url}), so that it follows the person to any workstation. The machine store
 * and the secrets never leave the workstation.
 *
 * <p>One module per file, sent as {@code text/plain} with {@code module=<kind>/<id>@<schema>}
 * ({@code settings/weasis-core@1}, {@code documents/windowPresets@1}); the schema in the name keeps
 * clients of another schema apart. The service has no listing, so Weasis keeps its own: the module
 * {@value #INDEX_MODULE} lists the modules it has written. A body is compact JSON with every
 * non-ASCII character escaped, so that the charset of the service's response does not matter.
 *
 * <p>At start, {@link #pull()} reads the index and the modules it lists (in parallel, within a
 * deadline; the local files are used when the service is unreachable) and merges them into the
 * local files before any plugin reads them: a settings group key by key, the newer file (by {@code
 * updated}) winning a key both changed, the loser logged; a document entry by entry, the newer
 * document on top. Keys and entries only on one side are kept, so a deletion made on one
 * workstation can come back from another. A change is pushed a short time after it is saved, and at
 * exit: every file whose content differs from what the service last had, then the index.
 */
public final class RemoteSettings {
  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteSettings.class);

  public static final String INDEX_MODULE = "index@1"; // NON-NLS
  public static final String INDEX_KIND = "weasis.index"; // NON-NLS
  public static final String STATE_FILE = "sync-state.json"; // NON-NLS
  static final String SETTINGS = "settings/"; // NON-NLS
  static final String DOCUMENTS = "documents/"; // NON-NLS

  private static final long PUSH_DELAY_MS = 3000;
  private static final long PULL_DEADLINE_MS = 8000;

  /** Reads and writes one module of the preference service. */
  public interface Transport {
    /** The body of a module, or null when the service holds none. */
    String get(String module) throws IOException;

    void put(String module, String body) throws IOException;
  }

  /** What a pull or a push did. */
  public record Report(List<String> pulled, List<String> pushed, List<String> errors) {}

  private static final RemoteSettings INSTANCE = new RemoteSettings(PUSH_DELAY_MS);

  private final long pushDelayMs;
  private final ScheduledExecutorService scheduler;
  private final AtomicBoolean scheduled = new AtomicBoolean();
  private final Object lock = new Object();
  private final Set<String> modules = new ConcurrentSkipListSet<>();
  private final Map<String, String> pushed = new TreeMap<>();
  private volatile Transport transport;
  private SettingsFileStore store;
  private Path documents;
  private Path stateFile;

  RemoteSettings(long pushDelayMs) {
    this.pushDelayMs = pushDelayMs;
    this.scheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread t = new Thread(r, "settings-sync"); // NON-NLS
              t.setDaemon(true);
              return t;
            });
  }

  public static RemoteSettings getInstance() {
    return INSTANCE;
  }

  /**
   * Turns the mirror on.
   *
   * @param store the user settings store
   * @param documents the folder of the user documents
   * @param stateFile where the checksums of what the service holds are kept between sessions
   */
  public void configure(
      Transport transport, SettingsFileStore store, Path documents, Path stateFile) {
    synchronized (lock) {
      this.store = store;
      this.documents = documents;
      this.stateFile = stateFile;
      readState();
      this.transport = transport;
    }
  }

  public boolean isEnabled() {
    return transport != null;
  }

  /** Schedules a push of what changed; does nothing while the mirror is off. */
  public void changed() {
    if (transport != null && scheduled.compareAndSet(false, true)) {
      scheduler.schedule(
          () -> {
            scheduled.set(false);
            push();
          },
          pushDelayMs,
          TimeUnit.MILLISECONDS);
    }
  }

  // ---- Pull ---------------------------------------------------------------------------------

  /** Reads the modules of the service and merges them into the local files. */
  public Report pull() {
    Transport t = transport;
    if (t == null) {
      return new Report(List.of(), List.of(), List.of("the mirror is off"));
    }
    List<String> pulled = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    synchronized (lock) {
      String index;
      try {
        index = t.get(INDEX_MODULE);
      } catch (IOException e) {
        LOGGER.warn("Preference service unreachable, the local settings are used", e);
        return new Report(List.of(), List.of(), List.of("unreachable: " + e.getMessage()));
      }
      readIndex(index).forEach(modules::add);
      if (index != null && !index.isBlank()) {
        try {
          pushed.put(INDEX_MODULE, sha(canonical(parse(index))));
        } catch (IOException e) {
          pushed.remove(INDEX_MODULE); // rewritten at the next push
        }
      }
      Map<String, String> bodies = fetch(t, modules, errors);
      bodies.forEach(
          (module, body) -> {
            try {
              if (merge(module, body)) {
                pulled.add(module);
              }
              pushed.put(module, sha(canonical(parse(body))));
            } catch (IOException | RuntimeException e) {
              LOGGER.warn("Cannot merge the remote module {}", module, e);
              errors.add(module + ": " + e.getMessage());
            }
          });
      if (store != null) {
        store.flushAll();
      }
      writeState();
    }
    LOGGER.info("Settings pulled from the preference service: {} merged", pulled.size());
    changed();
    return new Report(pulled, List.of(), errors);
  }

  private Map<String, String> fetch(Transport t, Set<String> wanted, List<String> errors) {
    Map<String, Future<String>> futures = new LinkedHashMap<>();
    try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
      for (String module : wanted) {
        if (supported(module)) {
          futures.put(module, pool.submit(() -> t.get(module)));
        }
      }
      Map<String, String> bodies = new LinkedHashMap<>();
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(PULL_DEADLINE_MS);
      for (Map.Entry<String, Future<String>> f : futures.entrySet()) {
        try {
          long left = Math.max(0, deadline - System.nanoTime());
          String body = f.getValue().get(left, TimeUnit.NANOSECONDS);
          if (body != null && !body.isBlank()) {
            bodies.put(f.getKey(), body);
          }
        } catch (TimeoutException e) {
          f.getValue().cancel(true);
          errors.add(f.getKey() + ": no answer in time");
        } catch (ExecutionException e) {
          errors.add(f.getKey() + ": " + e.getCause().getMessage());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      pool.shutdownNow();
      return bodies;
    }
  }

  /** A module this version reads: a settings group or a document at schema 1. */
  static boolean supported(String module) {
    int at = module.lastIndexOf('@');
    if (at < 0 || !(module.startsWith(SETTINGS) || module.startsWith(DOCUMENTS))) {
      return false;
    }
    try {
      int schema = Integer.parseInt(module.substring(at + 1));
      return module.startsWith(SETTINGS)
          ? schema <= SettingsFileStore.SCHEMA
          : schema <= ListDocument.SCHEMA_VERSION;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /**
   * @return true when the local file changed
   */
  boolean merge(String module, String body) throws IOException {
    JsonObject remote = parse(body);
    String id = module.substring(module.indexOf('/') + 1, module.lastIndexOf('@'));
    if (module.startsWith(SETTINGS)) {
      return mergeSettings(id, remote);
    }
    return mergeDocument(id, remote);
  }

  private boolean mergeSettings(String group, JsonObject remote) throws IOException {
    if (store == null) {
      return false;
    }
    Path file = store.folder().map(f -> f.resolve(SettingsFileStore.fileName(group))).orElse(null);
    Instant local =
        file != null && Files.isRegularFile(file) ? updated(parse(file)) : Instant.EPOCH;
    boolean remoteWins = updated(remote).isAfter(local);
    try {
      return mergeNode(remote, store.group(group), remoteWins, group);
    } catch (BackingStoreException e) {
      throw new IOException(e);
    }
  }

  private static boolean mergeNode(
      JsonObject remote, Preferences node, boolean remoteWins, String where)
      throws BackingStoreException {
    boolean changed = false;
    JsonObject values = remote.getJsonObject("values"); // NON-NLS
    if (values != null) {
      for (Map.Entry<String, JsonValue> e : values.entrySet()) {
        if (!(e.getValue() instanceof JsonString s)) {
          continue;
        }
        String current = node.get(e.getKey(), null);
        if (current == null) {
          node.put(e.getKey(), s.getString());
          changed = true;
        } else if (!current.equals(s.getString())) {
          if (remoteWins) {
            node.put(e.getKey(), s.getString());
            changed = true;
          }
          LOGGER.info(
              "Setting {}/{} changed on both sides: the {} value is kept",
              where,
              e.getKey(),
              remoteWins ? "remote" : "local");
        }
      }
    }
    JsonObject nodes = remote.getJsonObject("nodes"); // NON-NLS
    if (nodes != null) {
      for (Map.Entry<String, JsonValue> e : nodes.entrySet()) {
        if (e.getValue().getValueType() == JsonValue.ValueType.OBJECT) {
          changed |=
              mergeNode(
                  e.getValue().asJsonObject(),
                  node.node(e.getKey()),
                  remoteWins,
                  where + "/" + e.getKey());
        }
      }
    }
    return changed;
  }

  private boolean mergeDocument(String id, JsonObject remote) throws IOException {
    if (documents == null) {
      return false;
    }
    Path file = documents.resolve(id + ".json"); // NON-NLS
    if (!Files.isRegularFile(file)) {
      ListDocument.writeAtomic(file, remote);
      return true;
    }
    JsonObject local;
    try {
      local = parse(file);
    } catch (IOException e) {
      // a bare array written before the header, or a broken file: the service's copy replaces it
      ListDocument.writeAtomic(file, remote);
      return true;
    }
    boolean remoteNewer = updated(remote).isAfter(updated(local));
    Map<String, JsonValue> body =
        SettingsPackage.mergeBody(
            remoteNewer ? local : remote, remoteNewer ? remote : local, Set.of(), new int[4]);
    if (body.equals(withoutHeader(local))) {
      return false;
    }
    JsonObjectBuilder merged =
        ListDocument.header(id, JsonUtil.getInt(local, ListDocument.SCHEMA, 1));
    body.forEach(merged::add);
    ListDocument.writeAtomic(file, merged.build());
    return true;
  }

  private static Map<String, JsonValue> withoutHeader(JsonObject document) {
    Map<String, JsonValue> body = new LinkedHashMap<>(document);
    List.of(ListDocument.KIND_KEY, ListDocument.ID, ListDocument.SCHEMA, ListDocument.UPDATED)
        .forEach(body::remove);
    return body;
  }

  // ---- Push ---------------------------------------------------------------------------------

  /** Sends every local file whose content differs from what the service holds, then the index. */
  public Report push() {
    Transport t = transport;
    if (t == null) {
      return new Report(List.of(), List.of(), List.of("the mirror is off"));
    }
    List<String> sent = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    synchronized (lock) {
      if (store != null) {
        store.flushAll();
      }
      Map<String, String> local = new TreeMap<>();
      collect(store == null ? null : store.folder().orElse(null), SETTINGS, local, errors);
      collect(documents, DOCUMENTS, local, errors);
      boolean newModule = false;
      for (Map.Entry<String, String> e : local.entrySet()) {
        String sha = sha(e.getValue());
        if (sha.equals(pushed.get(e.getKey()))) {
          continue;
        }
        try {
          t.put(e.getKey(), e.getValue());
          pushed.put(e.getKey(), sha);
          sent.add(e.getKey());
          newModule |= modules.add(e.getKey());
        } catch (IOException ex) {
          LOGGER.warn("Cannot send {} to the preference service", e.getKey(), ex);
          errors.add(e.getKey() + ": " + ex.getMessage());
        }
      }
      String index = indexBody();
      String indexSha = sha(index);
      if (newModule || !indexSha.equals(pushed.get(INDEX_MODULE))) {
        try {
          t.put(INDEX_MODULE, index);
          pushed.put(INDEX_MODULE, indexSha);
          sent.add(INDEX_MODULE);
        } catch (IOException ex) {
          errors.add(INDEX_MODULE + ": " + ex.getMessage());
        }
      }
      writeState();
    }
    if (!sent.isEmpty()) {
      LOGGER.info("Settings sent to the preference service: {}", sent);
    }
    return new Report(List.of(), sent, errors);
  }

  private static void collect(
      Path folder, String kind, Map<String, String> into, List<String> errors) {
    if (folder == null || !Files.isDirectory(folder)) {
      return;
    }
    try (Stream<Path> list = Files.list(folder)) {
      for (Path file : list.filter(Files::isRegularFile).toList()) {
        String name = file.getFileName().toString();
        if (!name.endsWith(".json")) { // NON-NLS
          continue;
        }
        try {
          JsonObject json = parse(file);
          int schema = JsonUtil.getInt(json, ListDocument.SCHEMA, 1);
          String module = kind + name.substring(0, name.length() - 5) + "@" + schema;
          into.put(module, canonical(json));
        } catch (IOException e) {
          errors.add(file + ": not sent, " + e.getMessage());
        }
      }
    } catch (IOException e) {
      errors.add(folder + ": " + e.getMessage());
    }
  }

  private String indexBody() {
    JsonArrayBuilder list = Json.createArrayBuilder();
    new TreeSet<>(modules).forEach(list::add);
    return canonical(
        Json.createObjectBuilder()
            .add(ListDocument.KIND_KEY, INDEX_KIND)
            .add(ListDocument.SCHEMA, 1)
            .add("modules", list) // NON-NLS
            .build());
  }

  static List<String> readIndex(String body) {
    if (body == null || body.isBlank()) {
      return List.of();
    }
    try {
      JsonObject index = parse(body);
      return JsonUtil.getStringList(index, "modules"); // NON-NLS
    } catch (IOException e) {
      LOGGER.warn("The remote index cannot be read, it will be written again", e);
      return List.of();
    }
  }

  // ---- State and JSON -----------------------------------------------------------------------

  private void readState() {
    pushed.clear();
    modules.clear();
    if (stateFile == null || !Files.isRegularFile(stateFile)) {
      return;
    }
    try {
      JsonObject state = parse(stateFile);
      JsonObject sent = state.getJsonObject("pushed"); // NON-NLS
      if (sent != null) {
        sent.forEach(
            (k, v) -> {
              if (v instanceof JsonString s) {
                pushed.put(k, s.getString());
                if (!k.equals(INDEX_MODULE)) {
                  modules.add(k);
                }
              }
            });
      }
    } catch (IOException e) {
      LOGGER.warn("Cannot read {}, everything will be sent again", stateFile, e);
    }
  }

  private void writeState() {
    if (stateFile == null) {
      return;
    }
    JsonObjectBuilder sent = Json.createObjectBuilder();
    pushed.forEach(sent::add);
    try {
      ListDocument.writeAtomic(
          stateFile,
          Json.createObjectBuilder()
              .add(ListDocument.SCHEMA, 1)
              .add("pushed", sent) // NON-NLS
              .build());
    } catch (IOException e) {
      LOGGER.warn("Cannot write {}", stateFile, e);
    }
  }

  private static Instant updated(JsonObject json) {
    Instant updated = JsonUtil.getInstant(json, ListDocument.UPDATED);
    return updated == null ? Instant.EPOCH : updated;
  }

  /** Compact JSON with every non-ASCII character escaped. */
  static String canonical(JsonObject json) {
    String text = json.toString();
    StringBuilder b = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c < 0x80) {
        b.append(c);
      } else {
        b.append(String.format("\\u%04x", (int) c)); // NON-NLS
      }
    }
    return b.toString();
  }

  private static JsonObject parse(Path file) throws IOException {
    return parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  private static JsonObject parse(String text) throws IOException {
    try (var reader = Json.createReader(new StringReader(text))) {
      return reader.readObject();
    } catch (JsonException | ClassCastException e) {
      throw new IOException("Not a JSON object", e);
    }
  }

  private static String sha(String text) {
    return SettingsPackage.sha256(text.getBytes(StandardCharsets.UTF_8));
  }

  /** The preference service of Viewer Hub: {@code <url>?user=&profile=&module=}. */
  public record HttpTransport(String url, String user, String profile) implements Transport {
    private static final int CONNECT_TIMEOUT_MS = 3000;
    private static final int READ_TIMEOUT_MS = 6000;

    private String address(String module) {
      return "%s?user=%s&profile=%s&module=%s" // NON-NLS
          .formatted(url, encode(user), encode(profile), encode(module));
    }

    private static String encode(String value) {
      return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8);
    }

    @Override
    public String get(String module) throws IOException {
      URLParameters parameters =
          new URLParameters(
              Map.of("Accept", "text/plain"), // NON-NLS
              CONNECT_TIMEOUT_MS,
              READ_TIMEOUT_MS);
      ClosableURLConnection http = NetworkUtil.getUrlConnection(address(module), parameters);
      if (http.urlConnection() instanceof HttpURLConnection c
          && c.getResponseCode() == HttpURLConnection.HTTP_NO_CONTENT) {
        return null;
      }
      String body = NetworkUtil.read(http.urlConnection());
      return body.isBlank() ? null : body;
    }

    @Override
    public void put(String module, String body) throws IOException {
      URLParameters parameters =
          new URLParameters(Map.of("Content-Type", "text/plain; charset=UTF-8"), true); // NON-NLS
      ClosableURLConnection http = NetworkUtil.getUrlConnection(address(module), parameters);
      try (OutputStream out = http.getOutputStream()) {
        out.write(body.getBytes(StandardCharsets.UTF_8));
      }
      if (http.urlConnection() instanceof HttpURLConnection c) {
        NetworkUtil.readResponse(c, parameters.headers());
      }
    }
  }
}
