/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.command.CommandPolicy.FileRules;
import org.weasis.core.api.command.CommandPolicy.NetworkRules;
import org.weasis.core.api.command.CommandPolicy.Refusal;
import org.weasis.core.api.command.CommandPolicy.Request;

/** What a remote caller may write, fetch and open, decided by the instance alone. */
class CommandPolicyTest {

  private static CommandPolicy writingUnder(Path root, Path... shares) {
    Set<Path> onShare = Set.of(shares);
    return CommandPolicy.defaults()
        .withFiles(
            new FileRules(
                List.of(root), false, true, path -> onShare.stream().anyMatch(path::startsWith)));
  }

  private static Optional<Refusal> checkWrite(CommandPolicy policy, Path file) {
    return policy.check(
        CommandOrigin.REMOTE,
        new Request(Set.of(Capability.FILE_WRITE), List.of(), List.of(file), List.of()));
  }

  @Test
  void aFileIsWrittenUnderARootAndNowhereElse(@TempDir Path root) {
    CommandPolicy policy = writingUnder(root);
    assertAll(
        () -> assertTrue(checkWrite(policy, root.resolve("capture.png")).isEmpty()),
        () -> assertTrue(checkWrite(policy, root.resolve("sub/deep/capture.png")).isEmpty()),
        () ->
            assertEquals(
                "files.write.roots",
                checkWrite(policy, root.resolve("../escaped.png")).orElseThrow().key()),
        () ->
            assertEquals(
                "files.write.roots",
                checkWrite(policy, Path.of("/etc/weasis.png")).orElseThrow().key()));
  }

  @Test
  void aSymbolicLinkDoesNotLeaveTheRoot(@TempDir Path root, @TempDir Path elsewhere)
      throws IOException {
    Path link = root.resolve("out");
    try {
      Files.createSymbolicLink(link, elsewhere);
    } catch (IOException | UnsupportedOperationException e) {
      return; // the file system does not support symbolic links
    }
    assertEquals(
        "files.write.roots",
        checkWrite(writingUnder(root), link.resolve("capture.png")).orElseThrow().key());
  }

  @Test
  void aShareIsNotALocalPlace(@TempDir Path root) {
    Path share = root.resolve("mnt");
    CommandPolicy policy = writingUnder(root, share);
    assertEquals(
        "files.write.networkFs", checkWrite(policy, share.resolve("x.png")).orElseThrow().key());
  }

  @Test
  void theFileNextToTheConfigurationIsReadAndAPropertyWins(@TempDir Path conf) throws Exception {
    Path base = conf.resolve("base.json");
    Files.writeString(base, "{}");
    Files.writeString(
        conf.resolve(CommandPolicy.FILE_NAME),
        "{\"remote\": {\"enabled\": true, \"namespaces\": [\"view2d\", \"dicom\"]},"
            + " \"files\": {\"write\": {\"roots\": [\"${user.home}\", \""
            + conf.toString().replace("\\", "\\\\")
            + "\"]}},"
            + " \"network\": {\"read\": \"allow\", \"readHosts\": [\"pacs.example.org\"]},"
            + " \"locked\": true}");
    String key = CommandPolicy.PROPERTY_PREFIX + "network.write";
    System.setProperty(key, "allow");
    try {
      CommandPolicy.Loaded loaded = CommandPolicy.fromConfiguration(List.of(base.toUri()));
      CommandPolicy policy = loaded.policy();
      assertAll(
          () -> assertTrue(loaded.source().endsWith(CommandPolicy.FILE_NAME + " and properties"), loaded.source()),
          () -> assertEquals(Set.of("view2d", "dicom"), policy.remote().namespaces()),
          () -> assertTrue(policy.network().read()),
          () -> assertEquals(Set.of("pacs.example.org"), policy.network().readHosts()),
          () -> assertTrue(policy.network().write(), "the property opened the writes"),
          () -> assertTrue(policy.files().writeRoots().contains(conf.toAbsolutePath().normalize())),
          () ->
              assertTrue(
                  policy.files().writeRoots().contains(Path.of(System.getProperty("user.home")).toAbsolutePath().normalize())),
          () -> assertTrue(policy.locked()));
      CommandPolicy.Loaded none = CommandPolicy.fromConfiguration(List.of(conf.resolve("other/base.json").toUri()));
      assertAll(
          () -> assertEquals("properties", none.source()),
          () -> assertFalse(none.policy().network().read()));
    } finally {
      System.clearProperty(key);
    }
  }

  @Test
  void theCheckpointBeforeARemoteBatchIsOnUnlessTheInstanceTurnsItOff() {
    CommandPolicy off =
        CommandPolicy.from(
            key -> "remote.checkpoint".equals(key) ? "false" : null, RemotePolicy.allowAll());
    assertAll(
        () -> assertTrue(CommandPolicy.defaults().remoteCheckpoint()),
        () -> assertFalse(off.remoteCheckpoint()),
        () -> assertTrue(off.withRemoteCheckpoint(true).remoteCheckpoint()));
  }

  @Test
  void theNetworkIsClosedUntilTheInstanceOpensIt() {
    CommandPolicy closed = CommandPolicy.defaults();
    CommandPolicy open =
        CommandPolicy.defaults()
            .withNetwork(new NetworkRules(true, Set.of("*.example.org"), false, Set.of()));
    Request fetch =
        new Request(
            Set.of(Capability.NETWORK_READ),
            List.of(),
            List.of(),
            List.of(RemoteUrl.parse("https://pacs.example.org/studies")));
    Request elsewhere =
        new Request(
            Set.of(Capability.NETWORK_READ),
            List.of(),
            List.of(),
            List.of(RemoteUrl.parse("https://pacs.elsewhere.com/studies")));
    Request plain =
        new Request(
            Set.of(Capability.NETWORK_READ),
            List.of(),
            List.of(),
            List.of(RemoteUrl.parse("http://pacs.example.org/studies")));
    assertAll(
        () ->
            assertEquals(
                "network.read", closed.check(CommandOrigin.REMOTE, fetch).orElseThrow().key()),
        () -> assertTrue(open.check(CommandOrigin.REMOTE, fetch).isEmpty()),
        () ->
            assertEquals(
                "network.readHosts",
                open.check(CommandOrigin.REMOTE, elsewhere).orElseThrow().key()),
        () ->
            assertEquals(
                "network.readHosts", open.check(CommandOrigin.REMOTE, plain).orElseThrow().key()),
        () ->
            assertEquals(
                "network.write",
                closed
                    .check(CommandOrigin.REMOTE, Request.of(Capability.NETWORK_WRITE))
                    .orElseThrow()
                    .key()));
  }

  @Test
  void aPageOfUnmaskedIdentityIsNeverOpenedForARemoteCaller() {
    Refusal refusal =
        CommandPolicy.defaults()
            .check(CommandOrigin.REMOTE, Request.of(Capability.UI_QUERY))
            .orElseThrow();
    assertAll(
        () -> assertEquals("ui.identity", refusal.key()),
        () -> assertTrue(refusal.message().startsWith("Refused by policy ui.identity:")));
  }

  @Test
  void stoppingTheApplicationIsConfirmedAndCanBeForbidden() {
    CommandPolicy policy = CommandPolicy.defaults();
    Request asked =
        new Request(Set.of(Capability.SHUTDOWN), List.of(), List.of(), List.of(), false, false);
    Request confirmed =
        new Request(Set.of(Capability.SHUTDOWN), List.of(), List.of(), List.of(), false, true);
    assertAll(
        () ->
            assertEquals(
                "application.shutdown",
                policy.check(CommandOrigin.REMOTE, asked).orElseThrow().key()),
        () ->
            assertTrue(
                policy.check(CommandOrigin.REMOTE, asked).orElseThrow().reason().contains("-y")),
        () -> assertTrue(policy.check(CommandOrigin.REMOTE, confirmed).isEmpty()),
        () ->
            assertEquals(
                "application.shutdown",
                policy
                    .withShutdown(false)
                    .check(CommandOrigin.REMOTE, confirmed)
                    .orElseThrow()
                    .key()),
        () -> assertTrue(policy.check(CommandOrigin.LAUNCH_ARGS, asked).isEmpty()));
  }

  @Test
  void attestingPixelsIsClosedToARemoteCallerUntilTheInstanceOpensIt() {
    CommandPolicy policy = CommandPolicy.defaults();
    Request attest = Request.of(Capability.PIXEL_ATTEST);
    assertAll(
        () -> assertFalse(policy.pixelAttestAllowed()),
        () ->
            assertEquals(
                "pixels.attest", policy.check(CommandOrigin.REMOTE, attest).orElseThrow().key()),
        () ->
            assertTrue(policy.withPixelAttest(true).check(CommandOrigin.REMOTE, attest).isEmpty()),
        () -> assertTrue(policy.check(CommandOrigin.LAUNCH_ARGS, attest).isEmpty()),
        () ->
            assertTrue(
                CommandPolicy.from(Map.of("pixels.attest", "allow")::get, RemotePolicy.allowAll())
                    .pixelAttestAllowed()));
  }

  @Test
  void theOtherOriginsAreTheIntegrationPathAndStayOpen() {
    CommandPolicy policy = CommandPolicy.defaults();
    Request everything =
        new Request(
            Set.of(Capability.UI_QUERY, Capability.NETWORK_READ, Capability.FILE_WRITE),
            List.of(),
            List.of(Path.of("/etc/weasis.png")),
            List.of(RemoteUrl.parse("http://host/x")));
    assertAll(
        () -> assertTrue(policy.check(CommandOrigin.LAUNCH_ARGS, everything).isEmpty()),
        () -> assertTrue(policy.check(CommandOrigin.URI, everything).isEmpty()));
  }

  @Test
  void aHostPatternMatchesItsDomainOnly() {
    assertAll(
        () -> assertTrue(CommandPolicy.matches("*.example.org", "pacs.example.org")),
        () -> assertTrue(CommandPolicy.matches("*.example.org", "example.org")),
        () -> assertFalse(CommandPolicy.matches("*.example.org", "notexample.org")),
        () -> assertTrue(CommandPolicy.matches("Pacs.Example.Org", "pacs.example.org")),
        () -> assertFalse(CommandPolicy.matches("pacs.example.org", "other.example.org")));
  }

  @Test
  void onePropertyChangesOneKey() {
    Map<String, String> properties =
        Map.of(
            "network.read", "allow",
            "network.readHosts", "pacs.example.org, *.chuv.ch",
            "confirmations.assumeYes", "refuse");
    CommandPolicy policy = CommandPolicy.from(properties::get, RemotePolicy.allowAll());
    assertAll(
        () -> assertTrue(policy.network().read()),
        () -> assertEquals(2, policy.network().readHosts().size()),
        () -> assertFalse(policy.network().write(), "what is not named keeps its closed default"),
        () -> assertFalse(policy.assumeYesAllowed()),
        () -> assertTrue(policy.shutdownAllowed(), "what is not named keeps its default"));
  }
}
