/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * What stage 1 trusts: the SHA-256 manifest of the launcher set ({@code sha256sum} format, one
 * {@code <hex> <name>} line per jar) and, at the {@code signed} level, the jar signatures against
 * the certificates of the bootstrap truststore ({@code
 * $APPDIR/launcher/truststore/*.cer|.crt|.pem}, plus a site certificate installed by the packaging
 * hook). A chain that contains a trusted certificate is trusted; validity dates are not checked, a
 * timestamped signature outlives its certificate.
 */
final class Integrity {

  enum Level {
    SIGNED,
    DIGEST
  }

  private static final HexFormat HEX = HexFormat.of();

  private Integrity() {}

  static String sha256(byte[] bytes) {
    return HEX.formatHex(digest().digest(bytes));
  }

  static String sha256(Path file) throws IOException {
    MessageDigest md = digest();
    try (InputStream in = Files.newInputStream(file)) {
      byte[] buffer = new byte[64 * 1024];
      int n;
      while ((n = in.read(buffer)) > 0) {
        md.update(buffer, 0, n);
      }
    }
    return HEX.formatHex(md.digest());
  }

  private static MessageDigest digest() {
    try {
      return MessageDigest.getInstance("SHA-256"); // NON-NLS
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** Parses a {@code sha256sum} manifest: file name → lowercase hex digest. */
  static Map<String, String> parseManifest(byte[] manifest) {
    Map<String, String> digests = new LinkedHashMap<>();
    for (String line : new String(manifest, StandardCharsets.UTF_8).split("\\R")) {
      String l = line.strip();
      if (l.isEmpty() || l.startsWith("#")) {
        continue;
      }
      int sep = l.indexOf(' ');
      if (sep != 64) {
        throw new IllegalArgumentException("Not a sha256sum line: " + line);
      }
      String name = l.substring(sep).strip();
      if (name.startsWith("*")) {
        name = name.substring(1);
      }
      int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
      digests.put(name.substring(slash + 1), l.substring(0, 64).toLowerCase(Locale.ROOT));
    }
    return digests;
  }

  /** One {@code sha256sum} line per file, in the order given. */
  static String writeManifest(List<Path> files) throws IOException {
    StringBuilder sb = new StringBuilder();
    for (Path file : files) {
      sb.append(sha256(file)).append("  ").append(file.getFileName()).append('\n');
    }
    return sb.toString();
  }

  /** The problems of {@code file} against {@code digests}: unknown name or digest mismatch. */
  static List<String> checkDigest(Path file, Map<String, String> digests) throws IOException {
    List<String> problems = new ArrayList<>();
    String name = file.getFileName().toString();
    String expected = digests.get(name);
    if (expected == null) {
      problems.add(name + " is not in the manifest"); // NON-NLS
    } else if (!expected.equals(sha256(file))) {
      problems.add(name + " does not match its digest"); // NON-NLS
    }
    return problems;
  }

  /** The certificates of the truststore directory; empty when there is none. */
  static List<Certificate> loadTruststore(Path dir) {
    List<Certificate> certificates = new ArrayList<>();
    if (dir == null || !Files.isDirectory(dir)) {
      return certificates;
    }
    try (Stream<Path> files = Files.list(dir)) {
      CertificateFactory factory = CertificateFactory.getInstance("X.509"); // NON-NLS
      for (Path file : files.filter(Integrity::isCertificate).sorted().toList()) {
        try (InputStream in = Files.newInputStream(file)) {
          certificates.addAll(factory.generateCertificates(in));
        } catch (Exception e) {
          BootLog.note("ignoring certificate " + file.getFileName() + ": " + e); // NON-NLS
        }
      }
    } catch (Exception e) {
      BootLog.note("cannot read the truststore " + dir + ": " + e); // NON-NLS
    }
    return certificates;
  }

  private static boolean isCertificate(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".cer")
        || name.endsWith(".crt")
        || name.endsWith(".pem")
        || name.endsWith(".der"); // NON-NLS
  }

  /**
   * Verifies that every class or resource of {@code jar} is signed by a chain containing one of
   * {@code trusted}. Returns the first problem, or an empty string.
   */
  static String checkSignature(Path jar, List<Certificate> trusted) {
    if (trusted.isEmpty()) {
      return "no trusted certificate"; // NON-NLS
    }
    try (JarFile jarFile = new JarFile(jar.toFile(), true)) {
      Enumeration<JarEntry> entries = jarFile.entries();
      byte[] buffer = new byte[64 * 1024];
      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        if (entry.isDirectory() || isSignatureFile(entry.getName())) {
          continue;
        }
        try (InputStream in = jarFile.getInputStream(entry)) {
          while (in.read(buffer) > 0) {
            // reading verifies the entry; the signers are known only afterwards
          }
        }
        CodeSigner[] signers = entry.getCodeSigners();
        if (signers == null || signers.length == 0) {
          return entry.getName() + " is not signed"; // NON-NLS
        }
        boolean trustedSigner = false;
        for (CodeSigner signer : signers) {
          for (Certificate c : signer.getSignerCertPath().getCertificates()) {
            if (trusted.contains(c)) {
              trustedSigner = true;
            }
          }
        }
        if (!trustedSigner) {
          return entry.getName() + " is signed by an unknown certificate"; // NON-NLS
        }
      }
      return "";
    } catch (Exception e) {
      return "signature check failed: " + e; // NON-NLS
    }
  }

  private static boolean isSignatureFile(String name) {
    String n = name.toUpperCase(Locale.ROOT);
    return n.equals("META-INF/MANIFEST.MF") // NON-NLS
        || (n.startsWith("META-INF/") // NON-NLS
            && (n.endsWith(".SF")
                || n.endsWith(".RSA")
                || n.endsWith(".DSA")
                || n.endsWith(".EC"))); // NON-NLS
  }
}
