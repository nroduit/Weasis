/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher.integrity;

import com.formdev.flatlaf.json.Json;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.launcher.FileUtil;

/**
 * Integrity of the plugins and libraries a configuration names: {@code base.json} lists, under
 * {@code integrity.files}, the SHA-256 and size of every file, and {@code base.json.sig} carries a
 * detached Ed25519 signature of {@code base.json} by a release key or a site key. The launcher
 * checks the signature when it reads the configuration, every file when it downloads it, and every
 * cached file again when its size or time changed, or once a day.
 *
 * <p>Levels: {@link Level#SIGNED} when the signature verifies, {@link Level#DIGEST} when only the
 * digests are present, {@link Level#NONE} when the configuration says nothing. A remote
 * configuration ({@code cdb=}) without a valid signature is refused unless the property {@code
 * weasis.integrity} lowers the level to {@code digest} or {@code none}, which is logged at every
 * start; the launcher then falls back to the installed configuration.
 */
public final class Integrity {

  private static final Logger LOGGER = LoggerFactory.getLogger(Integrity.class);

  /** The launcher property that lowers the level a remote configuration must reach. */
  public static final String P_LEVEL = "weasis.integrity"; // NON-NLS

  /** The folder of the site keys, {@code <launcher folder>/integrity-keys} by default. */
  public static final String P_KEYS_DIR = "weasis.integrity.keys.dir"; // NON-NLS

  /** The signature file next to the configuration. */
  public static final String SIG_SUFFIX = ".sig"; // NON-NLS

  static final String ALGORITHM = "Ed25519"; // NON-NLS
  static final String RELEASE_KEYS = "/org/weasis/launcher/integrity/release-keys.json"; // NON-NLS
  private static final String CACHE_FILE = "integrity.properties"; // NON-NLS

  /** What the configuration promises for a file. */
  public record Entry(String sha256, long size) {}

  /** A public key the launcher trusts, by id. */
  public record Key(String id, PublicKey publicKey) {}

  public enum Level {
    SIGNED,
    DIGEST,
    NONE;

    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }
  }

  /** What {@code weasis:info --integrity} reports. */
  public record Report(
      Level level,
      String keyId,
      String source,
      int entries,
      int verified,
      List<String> failures,
      boolean lowered) {}

  /** The configuration is refused: a remote codebase without the level the policy requires. */
  public static final class Refused extends IOException {
    public Refused(String message) {
      super(message);
    }
  }

  private static final Map<String, Entry> EXPECTED = new LinkedHashMap<>();
  private static final List<String> FAILURES = new ArrayList<>();
  private static Level level = Level.NONE;
  private static String keyId;
  private static String source;
  private static boolean lowered;
  private static int verifiedCount;

  private Integrity() {}

  /**
   * Reads the integrity list of a configuration and verifies its signature.
   *
   * @param baseJson the configuration read, a file or a remote URL
   * @param remote whether it came from a remote codebase, where a signature is required
   * @throws Refused when a remote configuration does not reach the level the property allows
   */
  public static synchronized void load(URI baseJson, boolean remote) throws IOException {
    reset();
    source = baseJson.toString();
    byte[] bytes = read(baseJson);
    if (bytes == null) {
      LOGGER.warn("Cannot read {} for its integrity list", baseJson);
      refuseIfRemote(remote, "unreadable configuration");
      return;
    }
    EXPECTED.putAll(parse(bytes));
    Optional<byte[]> signature = readSignature(baseJson);
    if (signature.isPresent()) {
      Optional<Key> signer = verify(bytes, signature.get(), keys());
      if (signer.isPresent()) {
        level = Level.SIGNED;
        keyId = signer.get().id();
        LOGGER.info("Configuration {} signed by {}", baseJson, keyId);
      } else {
        FAILURES.add("base.json.sig does not verify with any known key");
        LOGGER.error("The signature of {} does not verify with any known key", baseJson);
        refuseIfRemote(remote, "invalid signature");
        level = EXPECTED.isEmpty() ? Level.NONE : Level.DIGEST;
      }
    } else {
      level = EXPECTED.isEmpty() ? Level.NONE : Level.DIGEST;
      if (remote) {
        refuseIfRemote(remote, "no signature");
      }
    }
  }

  private static void refuseIfRemote(boolean remote, String why) throws Refused {
    if (!remote) {
      return;
    }
    String allowed = System.getProperty(P_LEVEL, Level.SIGNED.key()).toLowerCase(Locale.ROOT);
    Level reached = EXPECTED.isEmpty() ? Level.NONE : Level.DIGEST;
    boolean accepted =
        switch (allowed) {
          case "none" -> true; // NON-NLS
          case "digest" -> reached == Level.DIGEST; // NON-NLS
          default -> false;
        };
    if (!accepted) {
      throw new Refused(
          "Remote configuration refused ("
              + why
              + "): the level "
              + reached.key()
              + " is below what "
              + P_LEVEL
              + "="
              + allowed
              + " allows");
    }
    lowered = true;
    LOGGER.warn(
        "Remote configuration accepted at the level {} because {}={} ({})",
        reached.key(),
        P_LEVEL,
        allowed,
        why);
  }

  static synchronized void reset() {
    EXPECTED.clear();
    FAILURES.clear();
    level = Level.NONE;
    keyId = null;
    source = null;
    lowered = false;
    verifiedCount = 0;
  }

  /** For a test: the expected entries, as a configuration would list them. */
  static synchronized void expect(Map<String, Entry> entries, Level reached) {
    reset();
    EXPECTED.putAll(entries);
    level = reached;
  }

  public static synchronized Optional<Entry> expected(String fileName) {
    return Optional.ofNullable(EXPECTED.get(fileName));
  }

  public static synchronized Report report() {
    return new Report(
        level, keyId, source, EXPECTED.size(), verifiedCount, List.copyOf(FAILURES), lowered);
  }

  // ---------------------------------------------------------------------------- the files

  /**
   * Checks a file just downloaded against the list; a file the list does not name passes. A
   * mismatch is recorded and the file must not be kept.
   */
  public static synchronized boolean check(Path file, String fileName) {
    Entry entry = EXPECTED.get(fileName);
    if (entry == null) {
      return true;
    }
    try {
      long size = Files.size(file);
      if (entry.size() > 0 && size != entry.size()) {
        return fail(fileName, "size " + size + " instead of " + entry.size());
      }
      String digest = sha256(file);
      if (!digest.equalsIgnoreCase(entry.sha256())) {
        return fail(fileName, "sha256 " + digest + " instead of " + entry.sha256());
      }
      verifiedCount++;
      return true;
    } catch (IOException e) {
      return fail(fileName, "cannot read: " + e.getMessage());
    }
  }

  private static boolean fail(String fileName, String why) {
    FAILURES.add(fileName + ": " + why);
    LOGGER.error("Integrity failure of {}: {}", fileName, why);
    return false;
  }

  /**
   * Checks a cached file: the record of the cache says when it was verified and what it looked
   * like; a file whose size or time changed, or verified before today, is hashed again. True when
   * the file can be used.
   */
  public static synchronized boolean verifyCached(Path cacheDir, Path file, String fileName) {
    Entry entry = EXPECTED.get(fileName);
    if (entry == null) {
      return true;
    }
    Properties records = records(cacheDir);
    String today = LocalDate.now(ZoneOffset.UTC).toString();
    try {
      long size = Files.size(file);
      long mtime = Files.getLastModifiedTime(file).toMillis();
      String record = records.getProperty(fileName);
      if (record != null) {
        String[] parts = record.split("\\|");
        if (parts.length == 4
            && parts[0].equalsIgnoreCase(entry.sha256())
            && Long.parseLong(parts[1]) == size
            && Long.parseLong(parts[2]) == mtime
            && parts[3].equals(today)) {
          return true;
        }
      }
      if (!check(file, fileName)) {
        records.remove(fileName);
        store(cacheDir, records);
        return false;
      }
      records.setProperty(fileName, entry.sha256() + "|" + size + "|" + mtime + "|" + today);
      store(cacheDir, records);
      return true;
    } catch (IOException | NumberFormatException e) {
      return fail(fileName, "cannot read the cache record: " + e.getMessage());
    }
  }

  private static Properties records(Path cacheDir) {
    Properties properties = new Properties();
    Path file = cacheDir.resolve(CACHE_FILE);
    if (Files.isRegularFile(file)) {
      FileUtil.loadProperties(file, properties);
    }
    return properties;
  }

  private static void store(Path cacheDir, Properties records) {
    FileUtil.storeProperties(cacheDir.resolve(CACHE_FILE), records, "Verified digests");
  }

  public static String sha256(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256"); // NON-NLS
      byte[] buffer = new byte[65536];
      int read;
      while ((read = in.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (GeneralSecurityException e) {
      throw new IOException(e);
    }
  }

  public static String sha256(byte[] bytes) {
    try {
      return HexFormat.of()
          .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); // NON-NLS
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  // ---------------------------------------------------------------------------- the list

  /** The entries of the {@code integrity.files} object of a configuration, by file name. */
  @SuppressWarnings("unchecked")
  static Map<String, Entry> parse(byte[] baseJson) {
    Map<String, Entry> entries = new LinkedHashMap<>();
    try {
      Object json =
          Json.parse(
              new InputStreamReader(
                  new java.io.ByteArrayInputStream(baseJson), StandardCharsets.UTF_8));
      if (json instanceof Map<?, ?> root
          && root.get("integrity") instanceof Map<?, ?> integrity // NON-NLS
          && integrity.get("files") instanceof Map<?, ?> files) { // NON-NLS
        for (Map.Entry<?, ?> e : files.entrySet()) {
          if (e.getValue() instanceof Map<?, ?> entry) {
            Object sha = entry.get("sha256"); // NON-NLS
            Object size = entry.get("size"); // NON-NLS
            if (sha instanceof String digest) {
              entries.put(String.valueOf(e.getKey()), new Entry(digest, sizeOf(size)));
            }
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Cannot read the integrity list", e);
    }
    return entries;
  }

  /** The parser answers numbers as it likes (a Number or its text): either is a size. */
  private static long sizeOf(Object size) {
    if (size instanceof Number n) {
      return n.longValue();
    }
    try {
      return size == null ? 0 : Long.parseLong(String.valueOf(size).trim());
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static byte[] read(URI uri) {
    try (InputStream in = FileUtil.getAdaptedConnection(uri.toURL(), false).getInputStream()) {
      return in.readAllBytes();
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static Optional<byte[]> readSignature(URI baseJson) {
    byte[] bytes = read(URI.create(baseJson + SIG_SUFFIX));
    return bytes == null || bytes.length == 0 ? Optional.empty() : Optional.of(bytes);
  }

  // ---------------------------------------------------------------------------- the keys

  /**
   * Verifies a detached signature ({@code keyId:base64}, or base64 alone) of the bytes with the
   * keys; the signing key when one verifies.
   */
  public static Optional<Key> verify(byte[] bytes, byte[] signatureFile, List<Key> keys) {
    String text = new String(signatureFile, StandardCharsets.UTF_8).strip();
    String id = null;
    int colon = text.indexOf(':');
    if (colon > 0) {
      id = text.substring(0, colon);
      text = text.substring(colon + 1).strip();
    }
    byte[] signature;
    try {
      signature = Base64.getDecoder().decode(text);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
    for (Key key : keys) {
      if (id != null && !id.equals(key.id())) {
        continue;
      }
      try {
        Signature verifier = Signature.getInstance(ALGORITHM);
        verifier.initVerify(key.publicKey());
        verifier.update(bytes);
        if (verifier.verify(signature)) {
          return Optional.of(key);
        }
      } catch (GeneralSecurityException e) {
        LOGGER.debug("Key {} cannot verify", key.id(), e);
      }
    }
    return Optional.empty();
  }

  /** The release keys of the launcher and the site keys of the installation. */
  public static List<Key> keys() {
    List<Key> keys = new ArrayList<>(releaseKeys());
    keys.addAll(siteKeys(keysDir()));
    return keys;
  }

  /** The keys shipped in the launcher: {@code release-keys.json}, current and next. */
  @SuppressWarnings("unchecked")
  static List<Key> releaseKeys() {
    List<Key> keys = new ArrayList<>();
    try (InputStream in = Integrity.class.getResourceAsStream(RELEASE_KEYS)) {
      if (in == null) {
        return keys;
      }
      Object json = Json.parse(new InputStreamReader(in, StandardCharsets.UTF_8));
      if (json instanceof Map<?, ?> root && root.get("keys") instanceof List<?> list) { // NON-NLS
        for (Object item : list) {
          if (item instanceof Map<?, ?> key) {
            key(String.valueOf(key.get("id")), String.valueOf(key.get("publicKey"))) // NON-NLS
                .ifPresent(keys::add);
          }
        }
      }
    } catch (Exception e) {
      LOGGER.warn("Cannot read the release keys", e);
    }
    return keys;
  }

  /** The {@code *.pub} files of a folder of the installation, one base64 X.509 key each. */
  static List<Key> siteKeys(Path dir) {
    List<Key> keys = new ArrayList<>();
    if (dir == null || !Files.isDirectory(dir)) {
      return keys;
    }
    try (Stream<Path> files = Files.list(dir)) {
      files
          .filter(f -> f.getFileName().toString().endsWith(".pub")) // NON-NLS
          .sorted()
          .forEach(
              f -> {
                try {
                  String name = f.getFileName().toString();
                  key(name.substring(0, name.length() - 4), Files.readString(f).strip())
                      .ifPresent(keys::add);
                } catch (IOException e) {
                  LOGGER.warn("Cannot read the site key {}", f, e);
                }
              });
    } catch (IOException e) {
      LOGGER.warn("Cannot list the site keys of {}", dir, e);
    }
    return keys;
  }

  static Path keysDir() {
    String property = System.getProperty(P_KEYS_DIR);
    if (property != null && !property.isBlank()) {
      return Path.of(property);
    }
    try {
      Path launcher =
          Path.of(Integrity.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      Path folder = Files.isDirectory(launcher) ? launcher : launcher.getParent();
      return folder == null ? null : folder.resolve("integrity-keys"); // NON-NLS
    } catch (Exception e) {
      return null;
    }
  }

  public static Optional<Key> key(String id, String base64) {
    try {
      PublicKey key =
          KeyFactory.getInstance(ALGORITHM)
              .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(base64.strip())));
      return Optional.of(new Key(id, key));
    } catch (GeneralSecurityException | IllegalArgumentException e) {
      LOGGER.warn("Not an {} public key: {}", ALGORITHM, id);
      return Optional.empty();
    }
  }
}
