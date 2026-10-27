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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrityTest {

  @TempDir Path dir;

  static Path jar(Path dir, String name, String content) throws IOException {
    Path file = dir.resolve(name);
    try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new JarEntry("a/B.class"));
      out.write(content.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
    return file;
  }

  @Test
  void manifestRoundTrip() throws IOException {
    Path a = jar(dir, "weasis-launcher.jar", "launcher");
    Path b = jar(dir, "lib.jar", "lib");
    String manifest = Integrity.writeManifest(List.of(a, b));
    Map<String, String> digests =
        Integrity.parseManifest(manifest.getBytes(StandardCharsets.UTF_8));
    assertEquals(List.of("weasis-launcher.jar", "lib.jar"), List.copyOf(digests.keySet()));
    assertEquals(Integrity.sha256(a), digests.get("weasis-launcher.jar"));
    assertTrue(Integrity.checkDigest(a, digests).isEmpty());
    assertTrue(Integrity.checkDigest(b, digests).isEmpty());
  }

  @Test
  void manifestAcceptsBinaryMarkerAndPaths() {
    Map<String, String> digests =
        Integrity.parseManifest(
            ("# comment\n" + "a".repeat(64) + " *launcher/x.jar\n" + "B".repeat(64) + "  y.jar\n")
                .getBytes(StandardCharsets.UTF_8));
    assertEquals("a".repeat(64), digests.get("x.jar"));
    assertEquals("b".repeat(64), digests.get("y.jar"));
    assertThrows(
        IllegalArgumentException.class,
        () -> Integrity.parseManifest("abc  x.jar".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void digestProblemsAreNamed() throws IOException {
    Path a = jar(dir, "a.jar", "one");
    Map<String, String> digests = Map.of("a.jar", "0".repeat(64));
    assertEquals(List.of("a.jar does not match its digest"), Integrity.checkDigest(a, digests));
    assertEquals(List.of("a.jar is not in the manifest"), Integrity.checkDigest(a, Map.of()));
  }

  @Test
  void unsignedJarIsRefusedAtTheSignedLevel() throws IOException {
    Path a = jar(dir, "a.jar", "one");
    assertEquals("no trusted certificate", Integrity.checkSignature(a, List.of()));
    assertTrue(Integrity.loadTruststore(dir.resolve("missing")).isEmpty());
  }
}
