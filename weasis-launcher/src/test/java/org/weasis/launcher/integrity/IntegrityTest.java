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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

class IntegrityTest {

  private static final String BASE =
      "{\n  \"weasisPreferences\": [\n    {\"code\": \"weasis.plugins.1\", \"value\": \"x\"}\n  ]\n}\n";

  @AfterEach
  void reset() {
    Integrity.reset();
    System.clearProperty(Integrity.P_LEVEL);
    System.clearProperty(Integrity.P_KEYS_DIR);
  }

  private static Path setup(Path dir) throws IOException {
    Path jars = dir.resolve("bundle");
    Files.createDirectories(jars);
    Files.writeString(jars.resolve("weasis-core-5.0.0.jar"), "core bytes"); // NON-NLS
    Files.writeString(jars.resolve("weasis-dicom-codec-5.0.0.jar"), "codec bytes"); // NON-NLS
    Path base = dir.resolve("base.json");
    Files.writeString(base, BASE);
    return base;
  }

  @Test
  void theListIsWrittenSignedVerifiedAndATamperedFileIsRefused(@TempDir Path dir) throws Exception {
    Path base = setup(dir);
    int entries = IntegrityTool.digest(base, List.of(dir.resolve("bundle")));
    String text = Files.readString(base);
    Map<String, Integrity.Entry> parsed = Integrity.parse(Files.readAllBytes(base));
    Path keys = dir.resolve("keys");
    IntegrityTool.keygen(keys, "site-2026"); // NON-NLS
    Path sig = IntegrityTool.sign(base, keys.resolve("site-2026.key"));
    System.setProperty(Integrity.P_KEYS_DIR, keys.toString());

    Integrity.load(base.toUri(), true);
    Integrity.Report signed = Integrity.report();
    Path core = dir.resolve("bundle").resolve("weasis-core-5.0.0.jar");
    boolean good = Integrity.check(core, "weasis-core-5.0.0.jar");
    Files.writeString(core, "tampered"); // NON-NLS
    boolean bad = Integrity.check(core, "weasis-core-5.0.0.jar");
    assertAll(
        () -> assertEquals(2, entries),
        () -> assertTrue(text.contains("\"integrity\""), text),
        () -> assertTrue(text.contains("\"weasisPreferences\""), text),
        () -> assertEquals(2, parsed.size()),
        () -> assertEquals(10, parsed.get("weasis-core-5.0.0.jar").size()),
        () -> assertTrue(Files.readString(sig).startsWith("site-2026:")),
        () -> assertEquals(Integrity.Level.SIGNED, signed.level()),
        () -> assertEquals("site-2026", signed.keyId()),
        () -> assertTrue(good),
        () -> assertFalse(bad),
        () -> assertEquals(1, Integrity.report().failures().size()),
        () -> assertTrue(Integrity.check(core, "unknown.jar"), "a file the list does not name"),
        () -> assertTrue(IntegrityTool.verify(base, keys.resolve("site-2026.pub")).isPresent()));

    // the list is rewritten in place, not duplicated
    IntegrityTool.digest(base, List.of(dir.resolve("bundle")));
    String again = Files.readString(base);
    assertEquals(1, again.split("\"integrity\"").length - 1, again);
  }

  @Test
  void aPackedJarIsDigestedAsTheLauncherDecompressesIt(@TempDir Path dir) throws Exception {
    Path base = setup(dir);
    Path bundle = dir.resolve("bundle");
    // build-xz writes the jar again before packing it: the packed content differs from the jar
    byte[] packed =
        "core bytes written again without compression".getBytes(StandardCharsets.UTF_8); // NON-NLS
    try (OutputStream out =
        new XZOutputStream(
            Files.newOutputStream(bundle.resolve("weasis-core-5.0.0.jar.xz")), new LZMA2Options())) {
      out.write(packed);
    }

    // the launcher set holds the same jar, unpacked: the bundle, listed first, keeps its entry
    Path launcher = dir.resolve("launcher");
    Files.createDirectories(launcher);
    Files.writeString(launcher.resolve("weasis-core-5.0.0.jar"), "core bytes"); // NON-NLS

    int entries = IntegrityTool.digest(base, List.of(bundle, launcher));
    Map<String, Integrity.Entry> parsed = Integrity.parse(Files.readAllBytes(base));
    Integrity.Entry core = parsed.get("weasis-core-5.0.0.jar");
    assertAll(
        () -> assertEquals(2, entries, "the .xz and its .jar are one entry"),
        () -> assertFalse(parsed.containsKey("weasis-core-5.0.0.jar.xz")),
        () -> assertEquals(Integrity.sha256(packed), core.sha256()),
        () -> assertEquals(packed.length, core.size()),
        () ->
            assertEquals(
                Integrity.sha256(bundle.resolve("weasis-dicom-codec-5.0.0.jar")),
                parsed.get("weasis-dicom-codec-5.0.0.jar").sha256(),
                "a jar without .xz is digested as it is"));
  }

  @Test
  void aTamperedConfigurationOrAMissingSignatureIsRefusedRemotelyUnlessLowered(@TempDir Path dir)
      throws Exception {
    Path base = setup(dir);
    IntegrityTool.digest(base, List.of(dir.resolve("bundle")));
    Path keys = dir.resolve("keys");
    IntegrityTool.keygen(keys, "release"); // NON-NLS
    IntegrityTool.sign(base, keys.resolve("release.key"));
    System.setProperty(Integrity.P_KEYS_DIR, keys.toString());
    Files.writeString(base, Files.readString(base).replace("\"x\"", "\"y\""));

    assertThrows(Integrity.Refused.class, () -> Integrity.load(base.toUri(), true));
    Integrity.load(base.toUri(), false);
    Integrity.Level local = Integrity.report().level();
    System.setProperty(Integrity.P_LEVEL, "digest"); // NON-NLS
    Integrity.load(base.toUri(), true);
    Integrity.Report lowered = Integrity.report();
    Files.deleteIfExists(dir.resolve("base.json.sig"));
    Files.writeString(base, BASE);
    assertThrows(Integrity.Refused.class, () -> Integrity.load(base.toUri(), true));
    System.setProperty(Integrity.P_LEVEL, "none"); // NON-NLS
    Integrity.load(base.toUri(), true);
    assertAll(
        () -> assertEquals(Integrity.Level.DIGEST, local, "a local file is never refused"),
        () -> assertEquals(Integrity.Level.DIGEST, lowered.level()),
        () -> assertTrue(lowered.lowered()),
        () -> assertEquals(Integrity.Level.NONE, Integrity.report().level()));
  }

  @Test
  void aCachedFileIsHashedAgainWhenItChangedAndOnceADay(@TempDir Path dir) throws Exception {
    Path cache = dir.resolve("cache");
    Path plugins = cache.resolve("plugins");
    Files.createDirectories(plugins);
    Path jar = plugins.resolve("weasis-core-5.0.0.jar");
    Files.writeString(jar, "core bytes"); // NON-NLS
    Integrity.expect(
        Map.of("weasis-core-5.0.0.jar", new Integrity.Entry(Integrity.sha256(jar), 10)),
        Integrity.Level.DIGEST);
    boolean first = Integrity.verifyCached(cache, jar, "weasis-core-5.0.0.jar");
    int afterFirst = Integrity.report().verified();
    boolean second = Integrity.verifyCached(cache, jar, "weasis-core-5.0.0.jar");
    int afterSecond = Integrity.report().verified();
    Files.writeString(jar, "core byteX"); // NON-NLS same size, other content
    boolean changed = Integrity.verifyCached(cache, jar, "weasis-core-5.0.0.jar");
    assertAll(
        () -> assertTrue(first),
        () -> assertEquals(1, afterFirst),
        () -> assertTrue(second),
        () -> assertEquals(1, afterSecond, "the record of today spares the second hash"),
        () -> assertFalse(changed),
        () -> assertTrue(Files.isRegularFile(cache.resolve("integrity.properties"))));
  }
}
