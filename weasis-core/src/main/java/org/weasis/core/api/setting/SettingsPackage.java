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
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.service.UICore;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;

/**
 * A portable copy of the settings and documents of one layer, to move them to another machine, to
 * keep a backup, or to give a configuration to Viewer Hub as a site or group template.
 *
 * <pre>
 * manifest.json              kind "weasis.package", schema, created, weasis version,
 *                            source (user, profile, host), layer, entries (path, id, schema,
 *                            updated, sha256)
 * settings/&lt;group&gt;.json       user settings          (layer user)
 * documents/&lt;name&gt;.json       user documents         (layer user)
 * launcher/weasis.properties the person's launch values (layer user)
 * machine/settings/…         machine settings       (layer machine, or user with the machine)
 * machine/documents/…        machine documents
 * config/&lt;name&gt;.json          the user documents laid out as the config folder of a resources
 *                            package (layer site)
 * </pre>
 *
 * <p>Secrets are never in a package: they are kept apart ({@code auth-secrets.json}) and the
 * documents do not carry them. Nor are the private documents ({@link DocumentStore#declarePrivate},
 * the reading sessions). A site package is not imported: its {@code config} folder goes into the
 * resources package of the site, a document shipped as a folder ({@link
 * SiteDocuments#declareFolder}) laid out one entry per file.
 *
 * <p>The manifest lists the references of the entries ({@link DocumentStore#REFERENCES}) with where
 * each one resolves when the package is written: in the package, in the site documents, among the
 * ids a registry knows (built-in), missing, or unchecked when no registry tells. An import warns
 * about every reference that resolves neither in the package nor on this machine.
 *
 * <p>An import checks the manifest and the checksums, then plans every file: entries added,
 * replaced, unchanged, or skipped because the site locks them. Applying the plan first writes a
 * backup package of the current layer. A document is merged entry by entry (by {@code id}) or
 * replaced whole; a settings group key by key or replaced whole; the launch values are always
 * merged.
 */
public final class SettingsPackage {
  private static final Logger LOGGER = LoggerFactory.getLogger(SettingsPackage.class);

  public static final String KIND = "weasis.package"; // NON-NLS
  public static final int SCHEMA = 1;
  public static final String MANIFEST = "manifest.json"; // NON-NLS

  static final String SETTINGS = "settings/"; // NON-NLS
  static final String DOCUMENTS = "documents/"; // NON-NLS
  static final String LAUNCHER = "launcher/weasis.properties"; // NON-NLS
  static final String MACHINE_SETTINGS = "machine/settings/"; // NON-NLS
  static final String MACHINE_DOCUMENTS = "machine/documents/"; // NON-NLS
  static final String CONFIG = "config/"; // NON-NLS

  private static final long MAX_SIZE = 64L * 1024 * 1024;
  private static final Set<String> HEADER =
      Set.of(ListDocument.KIND_KEY, ListDocument.ID, ListDocument.SCHEMA, ListDocument.UPDATED);

  /**
   * Launch values that describe the installation rather than the person: the log files, the screen
   * scale and the notices already shown. Left out until every key is declared with its scope.
   */
  private static final List<String> LAUNCHER_EXCLUDED_PREFIXES =
      List.of("org.apache.sling.commons.log."); // NON-NLS

  private static final Set<String> LAUNCHER_EXCLUDED =
      Set.of(
          "flatlaf.uiScale", // NON-NLS
          "weasis.show.disclaimer", // NON-NLS
          "weasis.show.update.next.release", // NON-NLS
          "weasis.version.release"); // NON-NLS

  /** The layer a package holds. */
  public enum Layer {
    USER,
    MACHINE,
    SITE;

    public String id() {
      return name().toLowerCase(Locale.ROOT);
    }

    public static Layer of(String id) {
      return valueOf(id.trim().toUpperCase(Locale.ROOT));
    }
  }

  /** How an import treats what is already there. */
  public enum Mode {
    /** Entries and keys of the package added or replaced, the others kept. */
    MERGE,
    /** Each document and settings group of the package replaces the current one whole. */
    REPLACE
  }

  /**
   * Where the layers are read from and written to.
   *
   * @param launcher the launch values of the person ({@code weasis.properties}), own entries only
   * @param saveLauncher saves {@code launcher} after an import
   * @param siteDocument the site document of a name, to know what the site locks and holds
   * @param knownIds the ids a registry knows for a document id, see {@link DocumentStore#knownIds}
   * @param lockedKeys the setting keys the site locks
   * @param source user, profile, host and weasis version, written in the manifest
   */
  public record Environment(
      SettingsFileStore userSettings,
      SettingsFileStore machineSettings,
      Path userDocuments,
      Path machineDocuments,
      Properties launcher,
      Runnable saveLauncher,
      Function<String, Optional<JsonObject>> siteDocument,
      Function<String, Optional<Set<String>>> knownIds,
      Set<String> lockedKeys,
      Map<String, String> source) {}

  /** The layers of the running application. */
  public static Environment currentEnvironment() {
    UICore core = UICore.getInstance();
    Map<String, String> source = new TreeMap<>();
    source.put("user", AppProperties.WEASIS_USER); // NON-NLS
    source.put("profile", AppProperties.WEASIS_PROFILE); // NON-NLS
    source.put("weasis", AppProperties.WEASIS_VERSION); // NON-NLS
    hostName().ifPresent(h -> source.put("host", h)); // NON-NLS
    return new Environment(
        SettingsFileStore.user(),
        SettingsFileStore.machine(),
        DocumentStore.folder(Scope.USER),
        DocumentStore.folder(Scope.MACHINE),
        core.getSystemPreferences(),
        core::saveSystemPreferences,
        SiteDocuments::read,
        DocumentStore::knownIds,
        Settings.lockedKeys(),
        source);
  }

  /** Where an import keeps the backup of what it replaces: {@code <prefDir>/backups}. */
  public static Path backupFolder() {
    Path documents = DocumentStore.folder(Scope.USER);
    return documents == null ? null : documents.getParent().resolve("backups"); // NON-NLS
  }

  private static Optional<String> hostName() {
    try {
      return Optional.of(InetAddress.getLocalHost().getHostName());
    } catch (IOException | SecurityException e) {
      return Optional.empty();
    }
  }

  /** One file of a package, as listed in the manifest. */
  public record Entry(String path, String id, int schema, String updated, String sha256) {}

  /** Where a reference resolves when the package is written. */
  public enum Resolved {
    PACKAGE,
    SITE,
    BUILT_IN,
    MISSING,
    UNCHECKED;

    public String id() {
      return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static Resolved of(String id) {
      try {
        return valueOf(id.toUpperCase(Locale.ROOT).replace('-', '_'));
      } catch (IllegalArgumentException | NullPointerException e) {
        return UNCHECKED;
      }
    }
  }

  /**
   * A reference of an entry of the package to an entry of a document.
   *
   * @param from the path of the file in the package and the entry id, {@code path#id}
   * @param document the document id referred to, such as {@code windowPresets}
   * @param id the entry id referred to
   */
  public record Reference(String from, String document, String id, Resolved resolved) {}

  public record Manifest(
      String layer,
      String created,
      Map<String, String> source,
      List<Entry> entries,
      List<Reference> references) {

    /** The references the package does not resolve and that the source did not know either. */
    public List<Reference> missing() {
      return references.stream().filter(r -> r.resolved() == Resolved.MISSING).toList();
    }
  }

  /** What an import does to one file. */
  public record Change(
      String path,
      String target,
      int added,
      int replaced,
      int unchanged,
      int skipped,
      String note) {

    public boolean changes() {
      return added > 0 || replaced > 0;
    }
  }

  /**
   * What an import does, or did.
   *
   * @param warnings the references of the package that resolve neither in it nor here
   */
  public record Plan(
      Manifest manifest,
      List<Change> changes,
      List<String> errors,
      List<String> warnings,
      Path backup) {}

  private SettingsPackage() {}

  /** The default name of a package: {@code weasis-settings-<user>-<profile>-<date>.zip}. */
  public static String defaultName(Layer layer, Map<String, String> source) {
    String date =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss") // NON-NLS
            .withZone(ZoneId.systemDefault())
            .format(Instant.now());
    String who =
        layer == Layer.USER
            ? source.getOrDefault("user", "user") + "-" + source.getOrDefault("profile", "default")
            : layer.id();
    return ("weasis-settings-" + who + "-" + date + ".zip").replaceAll("[^A-Za-z0-9._-]", "_");
  }

  // ---- Writing ------------------------------------------------------------------------------

  /**
   * Writes a package of a layer.
   *
   * @param includeMachine with a user package, adds the settings and documents of this workstation
   *     (the same person moving to a new machine)
   */
  public static Manifest write(Path zip, Layer layer, boolean includeMachine, Environment env)
      throws IOException {
    Map<String, byte[]> files = new TreeMap<>();
    if (layer == Layer.USER) {
      addFolder(files, SETTINGS, settingsFolder(env.userSettings()));
      addFolder(files, DOCUMENTS, env.userDocuments());
      byte[] launcher = launcherBytes(env.launcher());
      if (launcher != null) {
        files.put(LAUNCHER, launcher);
      }
    }
    if (layer == Layer.MACHINE || (layer == Layer.USER && includeMachine)) {
      addFolder(files, MACHINE_SETTINGS, settingsFolder(env.machineSettings()));
      addFolder(files, MACHINE_DOCUMENTS, env.machineDocuments());
    }
    if (layer == Layer.SITE) {
      addFolder(files, CONFIG, env.userDocuments());
      splitFolderDocuments(files);
    }

    List<Entry> entries = new ArrayList<>();
    files.forEach((path, bytes) -> entries.add(describe(path, bytes)));
    Manifest manifest =
        new Manifest(
            layer.id(),
            Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
            env.source(),
            entries,
            references(files, env));

    Path parent = zip.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Path temp = Files.createTempFile(parent, zip.getFileName().toString(), ".tmp"); // NON-NLS
    try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
      put(out, MANIFEST, toJson(manifest).toString().getBytes(StandardCharsets.UTF_8));
      for (Map.Entry<String, byte[]> file : files.entrySet()) {
        put(out, file.getKey(), file.getValue());
      }
    } catch (IOException e) {
      Files.deleteIfExists(temp);
      throw e;
    }
    Files.move(temp, zip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    LOGGER.info("Settings package {} written: layer {}, {} files", zip, layer.id(), entries.size());
    return manifest;
  }

  private static Path settingsFolder(SettingsFileStore store) {
    if (store == null) {
      return null;
    }
    store.flushAll();
    return store.folder().orElse(null);
  }

  private static void addFolder(Map<String, byte[]> files, String prefix, Path folder)
      throws IOException {
    if (folder == null || !Files.isDirectory(folder)) {
      return;
    }
    try (Stream<Path> list = Files.list(folder)) {
      for (Path file : list.filter(Files::isRegularFile).toList()) {
        String name = file.getFileName().toString();
        if (name.endsWith(".json") && !DocumentStore.isPrivate(name)) { // NON-NLS
          files.put(prefix + name, Files.readAllBytes(file));
        }
      }
    }
  }

  /**
   * In a site package, a document the site ships as a folder becomes {@code config/<folder>/<id>
   * .json}, one entry per file, as {@link SiteDocuments#read} expects it.
   */
  private static void splitFolderDocuments(Map<String, byte[]> files) {
    for (String path : List.copyOf(files.keySet())) {
      String name = path.substring(CONFIG.length());
      Optional<SiteDocuments.FolderDocument> folder = SiteDocuments.folderOf(name);
      if (folder.isEmpty()) {
        continue;
      }
      JsonObject document;
      try {
        document = readObject(files.get(path));
      } catch (JsonException | ClassCastException e) {
        continue; // left as one file, which the site layer also reads
      }
      files.remove(path);
      Set<String> used = new LinkedHashSet<>();
      if (document.get(folder.get().entries()) instanceof JsonArray array) {
        for (JsonObject entry : JsonUtil.objects(array)) {
          String file = fileName(entry.getString(ListDocument.ID, "entry"), used); // NON-NLS
          files.put(
              CONFIG + folder.get().folder() + "/" + file,
              entry.toString().getBytes(StandardCharsets.UTF_8));
        }
      }
    }
  }

  /** A file name for an entry id, unique within {@code used}. */
  static String fileName(String id, Set<String> used) {
    String base = id.replaceAll("[^A-Za-z0-9._-]", "_"); // NON-NLS
    String name = base + ".json"; // NON-NLS
    for (int i = 2; !used.add(name.toLowerCase(Locale.ROOT)); i++) {
      name = base + "-" + i + ".json"; // NON-NLS
    }
    return name;
  }

  // ---- References

  /** The references of every document of the package, with where each one resolves. */
  private static List<Reference> references(Map<String, byte[]> files, Environment env) {
    Map<String, JsonObject> documents = new LinkedHashMap<>();
    Map<String, Set<String>> packaged = new LinkedHashMap<>();
    files.forEach(
        (path, bytes) -> {
          if (path.startsWith(SETTINGS)
              || path.startsWith(MACHINE_SETTINGS)
              || !path.endsWith(".json")) { // NON-NLS
            return;
          }
          try {
            JsonObject json = readObject(bytes);
            documents.put(path, json);
            Set<String> ids =
                packaged.computeIfAbsent(documentOf(path, json), k -> new LinkedHashSet<>());
            if (folderEntryOf(path).isPresent()) {
              ids.add(json.getString(ListDocument.ID, ""));
            } else {
              ids.addAll(DocumentStore.entryIds(json));
            }
          } catch (JsonException | ClassCastException e) {
            // a bare array: no references
          }
        });
    List<Reference> refs = new ArrayList<>();
    Map<String, Set<String>> siteIds = new LinkedHashMap<>();
    documents.forEach(
        (path, json) -> {
          Map<String, Map<String, Set<String>>> byEntry =
              folderEntryOf(path).isPresent()
                  ? Map.of(json.getString(ListDocument.ID, ""), DocumentStore.referencesOf(json))
                  : DocumentStore.references(json);
          byEntry.forEach(
              (entry, docs) ->
                  docs.forEach(
                      (doc, ids) -> {
                        for (String id : ids) {
                          Resolved resolved =
                              packaged.getOrDefault(doc, Set.of()).contains(id)
                                  ? Resolved.PACKAGE
                                  : resolveHere(doc, id, env, siteIds, Resolved.MISSING);
                          refs.add(new Reference(path + "#" + entry, doc, id, resolved));
                        }
                      }));
        });
    return refs;
  }

  /**
   * The document id of a packaged file: its {@code id}, or for one entry of a folder document
   * ({@code config/<folder>/<entry>.json}) the document of that folder.
   */
  private static String documentOf(String path, JsonObject json) {
    Optional<String> folderDocument = folderEntryOf(path);
    if (folderDocument.isPresent()) {
      return folderDocument.get();
    }
    String name = path.substring(path.lastIndexOf('/') + 1);
    return json.getString(ListDocument.ID, ListDocument.documentId(Path.of(name)));
  }

  /** The document id when the path is one entry of a folder document of a site package. */
  private static Optional<String> folderEntryOf(String path) {
    if (!path.startsWith(CONFIG)) {
      return Optional.empty();
    }
    String rest = path.substring(CONFIG.length());
    int slash = rest.indexOf('/');
    if (slash < 0) {
      return Optional.empty();
    }
    String folder = rest.substring(0, slash);
    return SiteDocuments.folders().stream()
        .filter(f -> f.folder().equals(folder))
        .map(f -> ListDocument.documentId(Path.of(f.name())))
        .findFirst();
  }

  /**
   * Where a reference resolves outside the package: in the site document, among the known ids, or
   * {@code otherwise} when a registry knows the document's ids and not this one.
   */
  private static Resolved resolveHere(
      String doc,
      String id,
      Environment env,
      Map<String, Set<String>> siteIds,
      Resolved otherwise) {
    Set<String> site =
        siteIds.computeIfAbsent(
            doc,
            d ->
                env.siteDocument()
                    .apply(d + ".json") // NON-NLS
                    .map(DocumentStore::entryIds)
                    .orElse(Set.of()));
    if (site.contains(id)) {
      return Resolved.SITE;
    }
    Optional<Set<String>> known = env.knownIds().apply(doc);
    if (known.isEmpty()) {
      return Resolved.UNCHECKED;
    }
    return known.get().contains(id) ? Resolved.BUILT_IN : otherwise;
  }

  /** The references of the package that resolve neither in it nor on this machine. */
  private static List<String> unresolved(Manifest manifest, Environment env) {
    List<String> warnings = new ArrayList<>();
    Map<String, Set<String>> siteIds = new LinkedHashMap<>();
    Map<String, Set<String>> userIds = new LinkedHashMap<>();
    for (Reference ref : manifest.references()) {
      if (ref.resolved() == Resolved.PACKAGE) {
        continue;
      }
      Set<String> user = userIds.computeIfAbsent(ref.document(), d -> currentUserIds(d, env));
      if (user.contains(ref.id())) {
        continue;
      }
      Resolved here = resolveHere(ref.document(), ref.id(), env, siteIds, Resolved.MISSING);
      if (here == Resolved.MISSING
          || (here == Resolved.UNCHECKED && ref.resolved() == Resolved.MISSING)) {
        warnings.add(
            "%s refers to %s '%s', which is not on this machine"
                .formatted(ref.from(), ref.document(), ref.id()));
      }
    }
    return warnings;
  }

  private static Set<String> currentUserIds(String doc, Environment env) {
    Set<String> ids = new LinkedHashSet<>();
    for (Path folder : new Path[] {env.userDocuments(), env.machineDocuments()}) {
      Path file = folder == null ? null : folder.resolve(doc + ".json"); // NON-NLS
      if (file != null && Files.isRegularFile(file)) {
        try {
          ids.addAll(DocumentStore.entryIds(readObject(Files.readAllBytes(file))));
        } catch (IOException | JsonException | ClassCastException e) {
          // unreadable: its ids are not counted
        }
      }
    }
    return ids;
  }

  private static byte[] launcherBytes(Properties launcher) throws IOException {
    if (launcher == null) {
      return null;
    }
    Properties portable = new Properties();
    launcher.forEach(
        (k, v) -> {
          if (k instanceof String key && v instanceof String value && isPortableLaunchKey(key)) {
            portable.setProperty(key, value);
          }
        });
    if (portable.isEmpty()) {
      return null;
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    portable.store(out, null);
    return out.toByteArray();
  }

  static boolean isPortableLaunchKey(String key) {
    return !LAUNCHER_EXCLUDED.contains(key)
        && LAUNCHER_EXCLUDED_PREFIXES.stream().noneMatch(key::startsWith);
  }

  private static Entry describe(String path, byte[] bytes) {
    String name = path.substring(path.lastIndexOf('/') + 1);
    String id = name.endsWith(".json") ? name.substring(0, name.length() - 5) : name; // NON-NLS
    int schema = 1;
    String updated = null;
    if (name.endsWith(".json")) { // NON-NLS
      try {
        JsonObject json = readObject(bytes);
        id = json.getString(ListDocument.ID, id);
        schema = JsonUtil.getInt(json, ListDocument.SCHEMA, 1);
        updated = json.getString(ListDocument.UPDATED, null);
      } catch (JsonException | ClassCastException e) {
        // a bare array or a document written before the header: described by its name only
      }
    }
    return new Entry(path, id, schema, updated, sha256(bytes));
  }

  private static void put(ZipOutputStream out, String name, byte[] bytes) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    out.write(bytes);
    out.closeEntry();
  }

  static JsonObject toJson(Manifest manifest) {
    JsonObjectBuilder source = Json.createObjectBuilder();
    manifest.source().forEach(source::add);
    JsonArrayBuilder entries = Json.createArrayBuilder();
    for (Entry e : manifest.entries()) {
      JsonObjectBuilder o =
          Json.createObjectBuilder()
              .add("path", e.path()) // NON-NLS
              .add("id", e.id()) // NON-NLS
              .add("schema", e.schema()); // NON-NLS
      JsonUtil.addIfPresent(o, "updated", e.updated()); // NON-NLS
      entries.add(o.add("sha256", e.sha256())); // NON-NLS
    }
    JsonArrayBuilder references = Json.createArrayBuilder();
    for (Reference ref : manifest.references()) {
      references.add(
          Json.createObjectBuilder()
              .add("from", ref.from()) // NON-NLS
              .add("document", ref.document()) // NON-NLS
              .add("id", ref.id()) // NON-NLS
              .add("resolved", ref.resolved().id())); // NON-NLS
    }
    return Json.createObjectBuilder()
        .add("kind", KIND) // NON-NLS
        .add("schema", SCHEMA) // NON-NLS
        .add("created", manifest.created()) // NON-NLS
        .add("layer", manifest.layer()) // NON-NLS
        .add("source", source) // NON-NLS
        .add("entries", entries) // NON-NLS
        .add("references", references) // NON-NLS
        .build();
  }

  // ---- Reading and importing ------------------------------------------------------------------

  /**
   * Plans the import of a package and, unless {@code dryRun}, applies it after writing a backup
   * package of the layers it touches into {@code backupFolder}.
   *
   * @throws IOException when the package cannot be read or is not a Weasis settings package
   */
  public static Plan importPackage(
      Path zip, Mode mode, boolean dryRun, Environment env, Path backupFolder) throws IOException {
    Map<String, byte[]> files = readZip(zip);
    byte[] manifestBytes = files.remove(MANIFEST);
    if (manifestBytes == null) {
      throw new IOException("No " + MANIFEST + " in " + zip);
    }
    Manifest manifest = readManifest(manifestBytes);
    if (Layer.SITE.id().equals(manifest.layer())) {
      throw new IOException(
          "A site package is not imported: its config folder goes into the resources package");
    }

    List<String> errors = new ArrayList<>();
    Map<String, byte[]> verified = new LinkedHashMap<>();
    for (Entry entry : manifest.entries()) {
      byte[] bytes = files.remove(entry.path());
      if (bytes == null) {
        errors.add(entry.path() + ": listed in the manifest, missing from the package");
      } else if (!sha256(bytes).equals(entry.sha256())) {
        errors.add(entry.path() + ": checksum does not match, file skipped");
      } else {
        verified.put(entry.path(), bytes);
      }
    }
    files.keySet().forEach(p -> errors.add(p + ": not listed in the manifest, file skipped"));

    Path backup = null;
    boolean touchesMachine =
        verified.keySet().stream().anyMatch(p -> p.startsWith("machine/")); // NON-NLS
    if (!dryRun && backupFolder != null) {
      backup =
          backupFolder.resolve(
              defaultName(Layer.USER, env.source())
                  .replace("weasis-settings", "backup")); // NON-NLS
      write(backup, Layer.USER, touchesMachine, env);
    }

    List<Change> changes = new ArrayList<>();
    for (Map.Entry<String, byte[]> file : verified.entrySet()) {
      String name = file.getKey().substring(file.getKey().lastIndexOf('/') + 1);
      if (DocumentStore.isPrivate(name)) {
        errors.add(file.getKey() + ": a private document is not imported, file skipped");
        continue;
      }
      try {
        Change change = importFile(file.getKey(), file.getValue(), mode, !dryRun, env);
        if (change != null) {
          changes.add(change);
        } else {
          errors.add(file.getKey() + ": not a path of a settings package, file skipped");
        }
      } catch (IOException | RuntimeException e) {
        LOGGER.warn("Cannot import {}", file.getKey(), e);
        errors.add(file.getKey() + ": " + e.getMessage());
      }
    }
    if (!dryRun) {
      if (env.userSettings() != null) {
        env.userSettings().flushAll();
      }
      if (env.machineSettings() != null) {
        env.machineSettings().flushAll();
      }
      if (env.saveLauncher() != null) {
        env.saveLauncher().run();
      }
      LOGGER.info("Settings package {} imported ({}), backup {}", zip, mode, backup);
    }
    return new Plan(manifest, changes, errors, unresolved(manifest, env), backup);
  }

  private static Change importFile(
      String path, byte[] bytes, Mode mode, boolean apply, Environment env) throws IOException {
    if (path.equals(LAUNCHER)) {
      return importLauncher(bytes, apply, env);
    }
    String name = path.substring(path.lastIndexOf('/') + 1);
    if (path.startsWith(MACHINE_SETTINGS)) {
      return importSettings(path, bytes, env.machineSettings(), mode, apply, env.lockedKeys());
    }
    if (path.startsWith(MACHINE_DOCUMENTS)) {
      return importDocument(path, name, bytes, env.machineDocuments(), mode, apply, env);
    }
    if (path.startsWith(SETTINGS)) {
      return importSettings(path, bytes, env.userSettings(), mode, apply, env.lockedKeys());
    }
    if (path.startsWith(DOCUMENTS)) {
      return importDocument(path, name, bytes, env.userDocuments(), mode, apply, env);
    }
    return null;
  }

  // ---- Settings groups

  private static Change importSettings(
      String path,
      byte[] bytes,
      SettingsFileStore store,
      Mode mode,
      boolean apply,
      Set<String> lockedKeys)
      throws IOException {
    JsonObject json = readObject(bytes);
    ListDocument.checkSchema(json, SettingsFileStore.SCHEMA, path);
    String name = path.substring(path.lastIndexOf('/') + 1);
    String group = json.getString(ListDocument.ID, name.substring(0, name.length() - 5));
    if (store == null) {
      return new Change(path, group, 0, 0, 0, 0, "no settings store");
    }
    int[] counts = new int[4];
    boolean known =
        store
            .folder()
            .map(f -> Files.isRegularFile(f.resolve(SettingsFileStore.fileName(group))))
            .orElse(false);
    if (!apply && !known) {
      // A dry run never opens a group that has no file: that would create it
      countAdded(json, lockedKeys, counts);
    } else {
      Preferences node = store.group(group);
      try {
        // Counted against what is there, then applied: the same figures in a dry run
        copyInto(json, node, false, lockedKeys, counts);
        if (apply) {
          if (mode == Mode.REPLACE) {
            clear(node, lockedKeys);
          }
          copyInto(json, node, true, lockedKeys, new int[4]);
        }
      } catch (BackingStoreException e) {
        throw new IOException(e);
      }
    }
    return new Change(
        path, "settings/" + group, counts[0], counts[1], counts[2], counts[3], null); // NON-NLS
  }

  /** counts: added, replaced, unchanged, skipped. */
  private static void copyInto(
      JsonObject json, Preferences node, boolean apply, Set<String> lockedKeys, int[] counts)
      throws BackingStoreException {
    JsonObject values = json.getJsonObject("values"); // NON-NLS
    if (values != null) {
      for (Map.Entry<String, JsonValue> e : values.entrySet()) {
        if (!(e.getValue() instanceof JsonString s)) {
          continue;
        }
        String key = e.getKey();
        if (lockedKeys.contains(key)) {
          counts[3]++;
          continue;
        }
        String current = node.get(key, null);
        if (current == null) {
          counts[0]++;
        } else if (current.equals(s.getString())) {
          counts[2]++;
          continue;
        } else {
          counts[1]++;
        }
        if (apply) {
          node.put(key, s.getString());
        }
      }
    }
    JsonObject nodes = json.getJsonObject("nodes"); // NON-NLS
    if (nodes != null) {
      for (Map.Entry<String, JsonValue> e : nodes.entrySet()) {
        if (e.getValue().getValueType() == JsonValue.ValueType.OBJECT) {
          if (apply || node.nodeExists(e.getKey())) {
            copyInto(e.getValue().asJsonObject(), node.node(e.getKey()), apply, lockedKeys, counts);
          } else {
            countAdded(e.getValue().asJsonObject(), lockedKeys, counts);
          }
        }
      }
    }
  }

  private static void countAdded(JsonObject json, Set<String> lockedKeys, int[] counts) {
    JsonObject values = json.getJsonObject("values"); // NON-NLS
    if (values != null) {
      values.keySet().forEach(k -> counts[lockedKeys.contains(k) ? 3 : 0]++);
    }
    JsonObject nodes = json.getJsonObject("nodes"); // NON-NLS
    if (nodes != null) {
      nodes.values().stream()
          .filter(v -> v.getValueType() == JsonValue.ValueType.OBJECT)
          .forEach(v -> countAdded(v.asJsonObject(), lockedKeys, counts));
    }
  }

  private static void clear(Preferences node, Set<String> lockedKeys) throws BackingStoreException {
    for (String key : node.keys()) {
      if (!lockedKeys.contains(key)) {
        node.remove(key);
      }
    }
    for (String child : node.childrenNames()) {
      clear(node.node(child), lockedKeys);
    }
  }

  // ---- Documents

  private static Change importDocument(
      String path,
      String name,
      byte[] bytes,
      Path folder,
      Mode mode,
      boolean apply,
      Environment env)
      throws IOException {
    if (folder == null) {
      return new Change(path, name, 0, 0, 0, 0, "no document folder");
    }
    JsonObject incoming = readObject(bytes);
    ListDocument.checkSchema(incoming, ListDocument.SCHEMA_VERSION, path);
    Locks locks = siteLocks(env.siteDocument().apply(name));
    Path target = folder.resolve(name);
    if (locks.document()) {
      return new Change(path, target.toString(), 0, 0, 0, 1, "the site locks this document");
    }
    JsonObject current = Files.isRegularFile(target) ? readCurrent(target) : null;

    int[] counts = new int[4];
    JsonObjectBuilder result =
        ListDocument.header(
            ListDocument.documentId(target), JsonUtil.getInt(incoming, ListDocument.SCHEMA, 1));
    mergeBody(mode == Mode.MERGE ? current : null, incoming, locks.ids(), counts)
        .forEach(result::add);
    if (apply && (counts[0] > 0 || counts[1] > 0 || current == null || mode == Mode.REPLACE)) {
      ListDocument.writeAtomic(target, result.build());
    }
    return new Change(path, target.toString(), counts[0], counts[1], counts[2], counts[3], null);
  }

  /**
   * The body of {@code base} with {@code overlay} on top: lists of entries merged by {@code id}
   * (the overlay's entry wins, an id in {@code lockedIds} keeps the base one), lists of plain
   * values united, any other value taken from the overlay. The header keys are left out.
   *
   * @param base the document below, or null
   * @param counts incremented: added, replaced, unchanged, skipped
   */
  static Map<String, JsonValue> mergeBody(
      JsonObject base, JsonObject overlay, Set<String> lockedIds, int[] counts) {
    Set<String> keys = new LinkedHashSet<>();
    if (base != null) {
      keys.addAll(base.keySet());
    }
    keys.addAll(overlay.keySet());
    keys.removeAll(HEADER);
    Map<String, JsonValue> body = new LinkedHashMap<>();
    for (String key : keys) {
      JsonValue merged =
          mergeValue(base == null ? null : base.get(key), overlay.get(key), lockedIds, counts);
      if (merged != null) {
        body.put(key, merged);
      }
    }
    return body;
  }

  private static JsonValue mergeValue(
      JsonValue mine, JsonValue theirs, Set<String> lockedIds, int[] counts) {
    if (theirs == null) {
      return mine;
    }
    if (theirs instanceof JsonArray incoming && hasIds(incoming)) {
      Map<String, JsonObject> merged = new LinkedHashMap<>();
      if (mine instanceof JsonArray existing) {
        for (JsonValue v : existing) {
          if (v instanceof JsonObject o) {
            merged.put(o.getString(ListDocument.ID, "#" + merged.size()), o);
          }
        }
      }
      for (JsonValue v : incoming) {
        if (!(v instanceof JsonObject o)) {
          continue;
        }
        String id = o.getString(ListDocument.ID, null);
        if (id != null && lockedIds.contains(id)) {
          counts[3]++;
          continue;
        }
        JsonObject before = id == null ? null : merged.get(id);
        if (before == null) {
          counts[0]++;
        } else if (before.equals(o)) {
          counts[2]++;
        } else {
          counts[1]++;
        }
        merged.put(id == null ? "#" + merged.size() : id, o);
      }
      JsonArrayBuilder array = Json.createArrayBuilder();
      merged.values().forEach(array::add);
      return array.build();
    }
    if (theirs instanceof JsonArray incoming && mine instanceof JsonArray existing) {
      // a list of plain values (favorites): the union, in order
      Set<JsonValue> union = new LinkedHashSet<>(existing);
      int before = union.size();
      union.addAll(incoming);
      counts[union.size() > before ? 0 : 2]++;
      JsonArrayBuilder array = Json.createArrayBuilder();
      union.forEach(array::add);
      return array.build();
    }
    if (mine == null) {
      counts[0]++;
    } else if (mine.equals(theirs)) {
      counts[2]++;
    } else {
      counts[1]++;
    }
    return theirs;
  }

  private static boolean hasIds(JsonArray array) {
    return !array.isEmpty()
        && array.stream()
            .allMatch(v -> v instanceof JsonObject o && o.containsKey(ListDocument.ID));
  }

  private static JsonObject readCurrent(Path target) throws IOException {
    try {
      return readObject(Files.readAllBytes(target));
    } catch (JsonException | ClassCastException e) {
      // a bare array written before the header, or a broken file: replaced by the package's
      return null;
    }
  }

  /** What a site document locks: the whole document, or entries by id. */
  record Locks(boolean document, Set<String> ids) {}

  static Locks siteLocks(Optional<JsonObject> siteDocument) {
    if (siteDocument.isEmpty()) {
      return new Locks(false, Set.of());
    }
    try {
      JsonObject site = siteDocument.get();
      Set<String> ids = new LinkedHashSet<>();
      for (Map.Entry<String, JsonValue> e : site.entrySet()) {
        if (e.getValue() instanceof JsonArray array) {
          for (JsonValue v : array) {
            if (v instanceof JsonObject o
                && o.getBoolean("locked", false) // NON-NLS
                && o.containsKey(ListDocument.ID)) {
              ids.add(o.getString(ListDocument.ID));
            }
          }
        }
      }
      // a color map document lists its locked ids beside the maps
      ids.addAll(JsonUtil.getStringList(site, "locked")); // NON-NLS
      boolean whole = site.get("locked") == JsonValue.TRUE; // NON-NLS
      return new Locks(whole, ids);
    } catch (JsonException | ClassCastException e) {
      LOGGER.debug(
          "Cannot read the locks of {}", siteDocument.get().getString(ListDocument.ID, null), e);
      return new Locks(false, Set.of());
    }
  }

  // ---- Launch values

  private static Change importLauncher(byte[] bytes, boolean apply, Environment env)
      throws IOException {
    Properties incoming = new Properties();
    try (InputStream in = new ByteArrayInputStream(bytes)) {
      incoming.load(in);
    }
    Properties launcher = env.launcher();
    if (launcher == null) {
      return new Change(LAUNCHER, "weasis.properties", 0, 0, 0, 0, "no launch values"); // NON-NLS
    }
    int added = 0;
    int replaced = 0;
    int unchanged = 0;
    int skipped = 0;
    for (String key : new TreeMap<>(incoming).keySet().stream().map(String::valueOf).toList()) {
      String value = incoming.getProperty(key);
      if (!isPortableLaunchKey(key) || env.lockedKeys().contains(key)) {
        skipped++;
        continue;
      }
      Object current = launcher.get(key);
      if (current == null) {
        added++;
      } else if (current.equals(value)) {
        unchanged++;
        continue;
      } else {
        replaced++;
      }
      if (apply) {
        launcher.setProperty(key, value);
      }
    }
    return new Change(
        LAUNCHER, "weasis.properties", added, replaced, unchanged, skipped, null); // NON-NLS
  }

  // ---- Package files

  static Map<String, byte[]> readZip(Path zip) throws IOException {
    Map<String, byte[]> files = new LinkedHashMap<>();
    long total = 0;
    try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
      ZipEntry entry;
      while ((entry = in.getNextEntry()) != null) {
        if (entry.isDirectory()) {
          continue;
        }
        String name = entry.getName();
        if (name.contains("..") || name.startsWith("/") || name.contains("\\")) {
          throw new IOException("Invalid path in the package: " + name);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        total += copyLimited(in, out, MAX_SIZE - total);
        files.put(name, out.toByteArray());
      }
    }
    return files;
  }

  private static long copyLimited(InputStream in, OutputStream out, long limit) throws IOException {
    byte[] buffer = new byte[8192];
    long copied = 0;
    int n;
    while ((n = in.read(buffer)) > 0) {
      copied += n;
      if (copied > limit) {
        throw new IOException("The package is larger than " + MAX_SIZE + " bytes");
      }
      out.write(buffer, 0, n);
    }
    return copied;
  }

  static Manifest readManifest(byte[] bytes) throws IOException {
    JsonObject json;
    try {
      json = readObject(bytes);
    } catch (JsonException | ClassCastException e) {
      throw new IOException("The manifest is not a JSON object", e);
    }
    if (!KIND.equals(json.getString("kind", null))) { // NON-NLS
      throw new IOException("Not a Weasis settings package");
    }
    try {
      ListDocument.checkSchema(json, SCHEMA, "settings package"); // NON-NLS
    } catch (IllegalArgumentException e) {
      throw new IOException(e.getMessage(), e);
    }
    Map<String, String> source = new TreeMap<>();
    JsonObject src = json.getJsonObject("source"); // NON-NLS
    if (src != null) {
      src.forEach(
          (k, v) -> source.put(k, v instanceof JsonString s ? s.getString() : v.toString()));
    }
    List<Entry> entries = new ArrayList<>();
    JsonArray array = json.getJsonArray("entries"); // NON-NLS
    if (array != null) {
      for (JsonObject o : JsonUtil.objects(array)) {
        entries.add(
            new Entry(
                o.getString("path"), // NON-NLS
                o.getString("id", null), // NON-NLS
                JsonUtil.getInt(o, "schema", 1), // NON-NLS
                o.getString("updated", null), // NON-NLS
                o.getString("sha256", ""))); // NON-NLS
      }
    }
    List<Reference> references = new ArrayList<>();
    JsonArray refs = json.getJsonArray("references"); // NON-NLS
    if (refs != null) {
      for (JsonObject o : JsonUtil.objects(refs)) {
        references.add(
            new Reference(
                o.getString("from", ""), // NON-NLS
                o.getString("document", ""), // NON-NLS
                o.getString("id", ""), // NON-NLS
                Resolved.of(o.getString("resolved", null)))); // NON-NLS
      }
    }
    return new Manifest(
        json.getString("layer", Layer.USER.id()), // NON-NLS
        json.getString("created", null), // NON-NLS
        source,
        entries,
        references);
  }

  private static JsonObject readObject(byte[] bytes) {
    try (var reader =
        Json.createReader(new StringReader(new String(bytes, StandardCharsets.UTF_8)))) {
      return reader.readObject();
    }
  }

  static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
