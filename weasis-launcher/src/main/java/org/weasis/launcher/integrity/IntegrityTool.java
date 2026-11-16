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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.tukaani.xz.XZInputStream;

/**
 * The release side of the integrity list, for the release scripts and a site with its own codebase:
 *
 * <pre>
 * java -cp weasis-launcher.jar org.weasis.launcher.integrity.IntegrityTool keygen &lt;dir&gt; &lt;id&gt;
 * java -cp weasis-launcher.jar org.weasis.launcher.integrity.IntegrityTool digest &lt;base.json&gt; &lt;folder of jars&gt;...
 * java -cp weasis-launcher.jar org.weasis.launcher.integrity.IntegrityTool sign &lt;base.json&gt; &lt;id.key&gt;
 * java -cp weasis-launcher.jar org.weasis.launcher.integrity.IntegrityTool verify &lt;base.json&gt; &lt;id.pub&gt;
 * </pre>
 *
 * {@code keygen} writes {@code <id>.key} (PKCS#8, base64, keep it private) and {@code <id>.pub}
 * (X.509, base64, the file a site drops in {@code integrity-keys/} or the release ships in {@code
 * release-keys.json}); {@code digest} rewrites the {@code integrity} object of {@code base.json}
 * from the jars found (a packed {@code .jar.xz} is digested decompressed, under the jar name, which
 * is what the launcher checks); {@code sign}
 * writes {@code base.json.sig} as {@code <id>:<base64 signature>}.
 */
public final class IntegrityTool {

  private static final String JAR = ".jar"; // NON-NLS
  private static final String XZ = ".xz"; // NON-NLS
  private static final String JAR_XZ = JAR + XZ;

  private IntegrityTool() {}

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      usage();
      return;
    }
    switch (args[0]) {
      case "keygen" -> keygen(Path.of(args[1]), args.length > 2 ? args[2] : "release"); // NON-NLS
      case "digest" -> { // NON-NLS
        List<Path> folders = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
          folders.add(Path.of(args[i]));
        }
        int n = digest(Path.of(args[1]), folders);
        System.out.println(n + " entries written to " + args[1]);
      }
      case "sign" -> { // NON-NLS
        Path sig = sign(Path.of(args[1]), Path.of(args[2]));
        System.out.println("signature written to " + sig);
      }
      case "verify" -> { // NON-NLS
        Optional<Integrity.Key> key = verify(Path.of(args[1]), Path.of(args[2]));
        System.out.println(key.map(k -> "verified with " + k.id()).orElse("NOT VERIFIED"));
        if (key.isEmpty()) {
          System.exit(1);
        }
      }
      default -> usage();
    }
  }

  private static void usage() {
    System.out.println(
        "Usage: keygen <dir> [id] | digest <base.json> <jar folder>... | sign <base.json> <id.key>"
            + " | verify <base.json> <id.pub>");
  }

  /** Writes {@code <id>.key} and {@code <id>.pub} into the folder; the id of the key pair. */
  public static String keygen(Path dir, String id) throws IOException, GeneralSecurityException {
    KeyPair pair = KeyPairGenerator.getInstance(Integrity.ALGORITHM).generateKeyPair();
    Files.createDirectories(dir);
    Files.writeString(
        dir.resolve(id + ".key"), // NON-NLS
        Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()) + "\n");
    Files.writeString(
        dir.resolve(id + ".pub"), // NON-NLS
        Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()) + "\n");
    return id;
  }

  /**
   * Rewrites the {@code integrity} object of the configuration from the jars of the folders (one
   * level, {@code .jar} and {@code .jar.xz} files); the entries are keyed by jar name, the number
   * of entries written.
   *
   * <p>A packed {@code <name>.jar.xz} wins over a {@code <name>.jar} beside it and is digested
   * decompressed: that is the file the launcher checks, and the packer writes the jar again
   * (without compression) before it compresses it, so the jar it started from has another digest.
   *
   * <p>The list is keyed by name only: when two folders hold a jar of the same name, the first
   * folder wins. The distribution passes the bundle first, whose jars are those the launcher
   * downloads and checks; the launcher set carries its own manifest ({@code launcher.sha256}).
   */
  public static int digest(Path baseJson, List<Path> folders) throws IOException {
    Map<String, Integrity.Entry> entries = new TreeMap<>();
    for (Path folder : folders) {
      Map<String, Integrity.Entry> found = new TreeMap<>();
      try (Stream<Path> files = Files.list(folder)) {
        for (Path file : files.sorted().toList()) {
          String name = file.getFileName().toString();
          if (name.endsWith(JAR_XZ)) {
            found.put(name.substring(0, name.length() - XZ.length()), digestXz(file));
          } else if (name.endsWith(JAR) && !Files.exists(file.resolveSibling(name + XZ))) {
            found.put(name, new Integrity.Entry(Integrity.sha256(file), Files.size(file)));
          }
        }
      }
      found.forEach(
          (name, entry) -> {
            Integrity.Entry first = entries.putIfAbsent(name, entry);
            if (first != null && !first.sha256().equalsIgnoreCase(entry.sha256())) {
              System.out.println(
                  name + " of " + folder + " differs from the one listed first: not listed");
            }
          });
    }
    String text = Files.readString(baseJson, StandardCharsets.UTF_8);
    Files.writeString(baseJson, withIntegrity(text, entries), StandardCharsets.UTF_8);
    return entries.size();
  }

  /** The digest and the size of the content of an xz file, as the launcher decompresses it. */
  static Integrity.Entry digestXz(Path file) throws IOException {
    try (InputStream in = new XZInputStream(Files.newInputStream(file))) {
      MessageDigest digest = MessageDigest.getInstance("SHA-256"); // NON-NLS
      byte[] buffer = new byte[65536];
      long size = 0;
      int read;
      while ((read = in.read(buffer)) > 0) {
        digest.update(buffer, 0, read);
        size += read;
      }
      return new Integrity.Entry(HexFormat.of().formatHex(digest.digest()), size);
    } catch (GeneralSecurityException e) {
      throw new IOException(e);
    }
  }

  /** The configuration text with its {@code integrity} object replaced or inserted first. */
  static String withIntegrity(String text, Map<String, Integrity.Entry> entries) {
    StringBuilder block =
        new StringBuilder(
            "  \"integrity\": {\n    \"algorithm\": \"sha256\",\n    \"files\": {"); // NON-NLS
    boolean first = true;
    for (Map.Entry<String, Integrity.Entry> e : entries.entrySet()) {
      block.append(first ? "\n" : ",\n");
      first = false;
      block
          .append("      \"")
          .append(e.getKey())
          .append("\": {\"sha256\": \"") // NON-NLS
          .append(e.getValue().sha256())
          .append("\", \"size\": ") // NON-NLS
          .append(e.getValue().size())
          .append('}');
    }
    block.append(first ? "}\n  }" : "\n    }\n  }");
    String stripped = removeIntegrity(text);
    int brace = stripped.indexOf('{');
    boolean empty = stripped.substring(brace + 1).strip().startsWith("}");
    return stripped.substring(0, brace + 1)
        + "\n"
        + block
        + (empty ? "\n" : ",\n")
        + stripped.substring(brace + 1).stripLeading();
  }

  /** The configuration text without its {@code integrity} object, when it has one. */
  static String removeIntegrity(String text) {
    int key = text.indexOf("\"integrity\""); // NON-NLS
    if (key < 0) {
      return text;
    }
    int open = text.indexOf('{', key);
    int depth = 0;
    int i = open;
    for (; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}') {
        depth--;
        if (depth == 0) {
          break;
        }
      }
    }
    int end = i + 1;
    while (end < text.length() && Character.isWhitespace(text.charAt(end))) {
      end++;
    }
    if (end < text.length() && text.charAt(end) == ',') {
      end++;
    }
    int start = key;
    while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
      start--;
    }
    String before = text.substring(0, start);
    String after = text.substring(end).stripLeading();
    if (after.startsWith("}") && before.stripTrailing().endsWith(",")) {
      // the object was the last member: drop the comma the previous member carried
      before = before.stripTrailing();
      before = before.substring(0, before.length() - 1);
    }
    return before + "\n  " + after;
  }

  /** Signs the configuration with the private key file; the signature file written. */
  public static Path sign(Path baseJson, Path keyFile)
      throws IOException, GeneralSecurityException {
    String name = keyFile.getFileName().toString();
    String id = name.endsWith(".key") ? name.substring(0, name.length() - 4) : name; // NON-NLS
    PrivateKey key =
        KeyFactory.getInstance(Integrity.ALGORITHM)
            .generatePrivate(
                new PKCS8EncodedKeySpec(
                    Base64.getDecoder().decode(Files.readString(keyFile).strip())));
    Signature signer = Signature.getInstance(Integrity.ALGORITHM);
    signer.initSign(key);
    signer.update(Files.readAllBytes(baseJson));
    Path sig = baseJson.resolveSibling(baseJson.getFileName() + Integrity.SIG_SUFFIX);
    Files.writeString(sig, id + ":" + Base64.getEncoder().encodeToString(signer.sign()) + "\n");
    return sig;
  }

  /** Verifies {@code base.json.sig} with one public key file; the key when it verifies. */
  public static Optional<Integrity.Key> verify(Path baseJson, Path pubFile) throws IOException {
    String name = pubFile.getFileName().toString();
    String id = name.endsWith(".pub") ? name.substring(0, name.length() - 4) : name; // NON-NLS
    Optional<Integrity.Key> key = Integrity.key(id, Files.readString(pubFile));
    if (key.isEmpty()) {
      return Optional.empty();
    }
    Path sig = baseJson.resolveSibling(baseJson.getFileName() + Integrity.SIG_SUFFIX);
    if (!Files.isRegularFile(sig)) {
      return Optional.empty();
    }
    return Integrity.verify(
        Files.readAllBytes(baseJson), Files.readAllBytes(sig), List.of(key.get()));
  }
}
