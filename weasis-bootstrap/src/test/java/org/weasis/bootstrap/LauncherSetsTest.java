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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The download of a launcher set against a local HTTP stub: the fault cases of the bootstrap. */
class LauncherSetsTest {

  @TempDir Path temp;
  private HttpServer server;
  private final Map<String, byte[]> files = new HashMap<>();
  private final Map<String, Integer> statuses = new HashMap<>();
  private String codebase;
  private Path source;

  @BeforeEach
  void startServer() throws IOException {
    source = Files.createDirectories(temp.resolve("source"));
    IntegrityTest.jar(source, "weasis-launcher.jar", "launcher");
    IntegrityTest.jar(source, "weasis-core-5.0.0.jar", "core");
    publishSource();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          String name = exchange.getRequestURI().getPath();
          name = name.substring(name.lastIndexOf('/') + 1);
          int status = statuses.getOrDefault(name, 200);
          byte[] body = files.get(name);
          if (body == null || status != 200) {
            exchange.sendResponseHeaders(body == null ? 404 : status, -1);
            exchange.close();
            return;
          }
          exchange.sendResponseHeaders(200, body.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
          }
        });
    server.start();
    codebase = "http://127.0.0.1:" + server.getAddress().getPort() + "/weasis";
  }

  private void publishSource() throws IOException {
    try (Stream<Path> list = Files.list(source)) {
      for (Path p : list.toList()) {
        files.put(p.getFileName().toString(), Files.readAllBytes(p));
      }
    }
    files.put(
        "launcher.sha256",
        Integrity.writeManifest(
                List.of(
                    source.resolve("weasis-launcher.jar"), source.resolve("weasis-core-5.0.0.jar")))
            .getBytes(StandardCharsets.UTF_8));
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  private PackageConfig config() {
    String json =
        """
        {"weasisPreferences": [
          {"code": "weasis.version", "value": "5.0.0"},
          {"code": "weasis.launcher.url", "value": "${weasis.codebase.url}/launcher/weasis-launcher.jar"},
          {"code": "weasis.launcher.libs", "value": "${weasis.codebase.url}/launcher/weasis-core-5.0.0.jar"},
          {"code": "weasis.launcher.sha256", "value": "${weasis.codebase.url}/launcher/launcher.sha256"}
        ]}
        """;
    return PackageConfig.parse(json.getBytes(StandardCharsets.UTF_8), codebase);
  }

  private Object resolve(LauncherSets sets) {
    return sets.resolve(config(), new Http(Map.of()), Integrity.Level.DIGEST, List.of(), n -> {});
  }

  @Test
  void downloadsVerifiesAndInstallsAtomically() throws IOException {
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    Object result = resolve(sets);
    LauncherSets.LauncherSet set = assertInstanceOf(LauncherSets.LauncherSet.class, result);
    assertFalse(set.embedded());
    assertEquals("hub", set.kind());
    assertTrue(set.id().startsWith("5.0.0-"));
    assertEquals(2, set.jars().size());
    assertEquals("weasis-launcher.jar", set.jars().getFirst().getFileName().toString());
    assertTrue(Files.exists(set.dir().resolve(LauncherSets.VERIFIED_MARKER)));
    assertNoPartLeft(sets);

    // cached: served again without the network
    server.stop(0);
    Object again =
        new LauncherSets(temp.resolve("cache"))
            .resolve(config(), new Http(Map.of()), Integrity.Level.DIGEST, List.of(), n -> {});
    assertInstanceOf(
        LauncherSets.Refusal.class, again, "the manifest must be fetched at every start");
  }

  @Test
  void corruptedJarIsRefusedAndNothingIsInstalled() throws IOException {
    files.put("weasis-core-5.0.0.jar", "garbage".getBytes(StandardCharsets.UTF_8));
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    LauncherSets.Refusal refusal = assertInstanceOf(LauncherSets.Refusal.class, resolve(sets));
    assertTrue(refusal.reason().contains("does not match its digest"), refusal.reason());
    assertNoSetInstalled(sets);
    assertNoPartLeft(sets);
  }

  @Test
  void serverErrorAndMissingFileAreRefused() throws IOException {
    statuses.put("weasis-core-5.0.0.jar", 500);
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    LauncherSets.Refusal refusal = assertInstanceOf(LauncherSets.Refusal.class, resolve(sets));
    assertTrue(refusal.reason().contains("HTTP 500"), refusal.reason());
    assertNoSetInstalled(sets);

    statuses.clear();
    statuses.put("launcher.sha256", 503);
    refusal = assertInstanceOf(LauncherSets.Refusal.class, resolve(sets));
    assertTrue(refusal.reason().startsWith("manifest unreachable"), refusal.reason());

    statuses.clear();
    files.put("launcher.sha256", "not a manifest".getBytes(StandardCharsets.UTF_8));
    refusal = assertInstanceOf(LauncherSets.Refusal.class, resolve(sets));
    assertTrue(refusal.reason().startsWith("manifest malformed"), refusal.reason());
  }

  @Test
  void unsignedSetIsRefusedAtTheSignedLevel() throws IOException {
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    Object result =
        sets.resolve(config(), new Http(Map.of()), Integrity.Level.SIGNED, List.of(), n -> {});
    LauncherSets.Refusal refusal = assertInstanceOf(LauncherSets.Refusal.class, result);
    assertTrue(refusal.reason().startsWith("signature refused"), refusal.reason());
    assertNoSetInstalled(sets);
  }

  @Test
  void failedSetIsSkippedUntilTheHubNamesAnother() throws IOException {
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    LauncherSets.LauncherSet set = assertInstanceOf(LauncherSets.LauncherSet.class, resolve(sets));
    sets.markFailed(set, "exit 1 before READY");
    LauncherSets.Refusal refusal = assertInstanceOf(LauncherSets.Refusal.class, resolve(sets));
    assertTrue(refusal.reason().contains("failed before READY"), refusal.reason());
    assertEquals(set.sha(), sets.failedSha().orElseThrow());
    assertTrue(sets.current().isEmpty());

    // a new manifest (new set) is downloaded again
    IntegrityTest.jar(source, "weasis-core-5.0.0.jar", "core v2");
    publishSource();
    LauncherSets.LauncherSet fresh =
        assertInstanceOf(LauncherSets.LauncherSet.class, resolve(sets));
    assertFalse(fresh.id().equals(set.id()));
    sets.markCurrent(fresh);
    assertEquals(fresh.id(), sets.current().orElseThrow().id());
  }

  @Test
  void leftoverPartDirectoriesAreRemoved() throws IOException {
    LauncherSets sets = new LauncherSets(temp.resolve("cache"));
    Path leftover = Files.createDirectories(sets.cacheDir().resolve("5.0.0-abc.part-12345"));
    Files.writeString(leftover.resolve("x.jar"), "x");
    assertInstanceOf(LauncherSets.LauncherSet.class, resolve(sets));
    assertFalse(Files.exists(leftover));
  }

  @Test
  void embeddedSetReadsTheRenamedJars() throws IOException {
    Path appDir = Files.createDirectories(temp.resolve("app"));
    Path launcher = Files.createDirectories(appDir.resolve("launcher"));
    Files.writeString(launcher.resolve("weasis-core-5.0.0.jar.embedded"), "c");
    Files.writeString(launcher.resolve("weasis-launcher.jar.embedded"), "l");
    Files.writeString(launcher.resolve("launcher.sha256"), "");
    LauncherSets.LauncherSet set = LauncherSets.embedded(appDir).orElseThrow();
    assertTrue(set.embedded());
    assertEquals("embedded", set.kind());
    assertEquals(
        List.of("weasis-launcher.jar.embedded", "weasis-core-5.0.0.jar.embedded"),
        set.jars().stream().map(p -> p.getFileName().toString()).toList());
    assertTrue(LauncherSets.embedded(temp.resolve("nowhere")).isEmpty());
  }

  private static void assertNoSetInstalled(LauncherSets sets) throws IOException {
    if (!Files.isDirectory(sets.cacheDir())) {
      return;
    }
    try (Stream<Path> dirs = Files.list(sets.cacheDir())) {
      assertTrue(dirs.filter(Files::isDirectory).findAny().isEmpty(), "no set directory expected");
    }
  }

  private static void assertNoPartLeft(LauncherSets sets) throws IOException {
    try (Stream<Path> dirs = Files.list(sets.cacheDir())) {
      assertTrue(dirs.noneMatch(d -> d.getFileName().toString().contains(".part-")));
    }
  }
}
