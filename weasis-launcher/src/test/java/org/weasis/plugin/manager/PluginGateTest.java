/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin.manager;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginDescriptor;
import org.weasis.launcher.integrity.Integrity;
import org.weasis.launcher.integrity.IntegrityTool;
import org.weasis.plugin.ManifestPluginDescriptor;
import org.weasis.plugin.WeasisManifestFinder;

/**
 * The gate decides from the source, the registry, the signature and the policy, before any class
 * loads.
 */
class PluginGateTest {

  static Path jar(Path dir, String name, String id, String version, Map<String, String> headers)
      throws IOException {
    Manifest manifest = new Manifest();
    Attributes a = manifest.getMainAttributes();
    a.put(Attributes.Name.MANIFEST_VERSION, "1.0");
    a.putValue("Plugin-Id", id); // NON-NLS
    a.putValue("Plugin-Version", version); // NON-NLS
    a.putValue("Plugin-Class", "org.example.Activator"); // NON-NLS
    a.putValue("Plugin-Requires", "5.0.0"); // NON-NLS
    headers.forEach(a::putValue);
    Path file = dir.resolve(name);
    Files.createDirectories(dir);
    try (OutputStream out = Files.newOutputStream(file);
        JarOutputStream jar = new JarOutputStream(out, manifest)) {
      jar.putNextEntry(new java.util.zip.ZipEntry("org/example/" + id + ".txt")); // NON-NLS
      jar.write(version.getBytes());
      jar.closeEntry();
    }
    return file;
  }

  static ManifestPluginDescriptor descriptor(
      String id, String version, Map<String, String> headers) {
    ManifestPluginDescriptor d =
        new ManifestPluginDescriptor(
            new DefaultPluginDescriptor(
                id, "", "org.example.Activator", version, "5.0.0", "x", "EPL")); // NON-NLS
    d.setCapabilities(headers.get(WeasisManifestFinder.WEASIS_CAPABILITIES));
    d.setNamespaces(headers.get(WeasisManifestFinder.WEASIS_NAMESPACES));
    d.setCommandApi(headers.get(WeasisManifestFinder.WEASIS_COMMAND_API));
    return d;
  }

  static PluginPolicy policy(
      PluginPolicy.Policy policy,
      Path dropIn,
      Set<String> deny,
      Publisher.Registry site,
      boolean lock,
      Path locked) {
    return new PluginPolicy(policy, lock, site, dropIn, deny, false, locked);
  }

  @Test
  void aPackageJarIsCoreAndAlwaysLoadedAUserJarNeedsAnEntryAndTheRightTier(@TempDir Path dir)
      throws Exception {
    Path cache = dir.resolve("cache").resolve("plugins");
    Path user = dir.resolve("config").resolve("plugins");
    Map<String, String> headers =
        Map.of(WeasisManifestFinder.WEASIS_CAPABILITIES, "FILE_READ, NETWORK_WRITE"); // NON-NLS
    Path packaged =
        jar(
            cache,
            "weasis-dicom-explorer-5.0.0.jar",
            "weasis-dicom-explorer",
            "5.0.0",
            Map.of()); // NON-NLS
    Path imported = jar(user, "acme-tool-1.0.jar", "acme-tool", "1.0", headers); // NON-NLS
    UserPluginRegistry registry = new UserPluginRegistry(user).load();
    PluginGate anyGate =
        new PluginGate(
            policy(PluginPolicy.Policy.ANY, user, Set.of(), Publisher.Registry.EMPTY, false, null),
            cache,
            registry,
            "2.29",
            null);

    PluginGate.Decision core =
        anyGate.decide(packaged, descriptor("weasis-dicom-explorer", "5.0.0", Map.of())); // NON-NLS
    PluginGate.Decision candidate =
        anyGate.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    registry.put(
        new UserPluginRegistry.Entry(
            "acme-tool-1.0.jar",
            "acme-tool",
            "1.0",
            Integrity.sha256(imported),
            null,
            "unverified",
            Instant.now().toString(),
            "doe",
            true)); // NON-NLS
    PluginGate.Decision unverified =
        anyGate.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    PluginGate siteGate =
        new PluginGate(
            policy(PluginPolicy.Policy.SITE, user, Set.of(), Publisher.Registry.EMPTY, false, null),
            cache,
            registry,
            "2.29",
            null);
    PluginGate.Decision belowPolicy =
        siteGate.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    PluginGate coreOnly =
        new PluginGate(
            policy(
                PluginPolicy.Policy.CORE_ONLY,
                user,
                Set.of(),
                Publisher.Registry.EMPTY,
                false,
                null),
            cache,
            registry,
            "2.29",
            null);
    PluginGate.Decision refusedByPolicy =
        coreOnly.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    PluginGate denying =
        new PluginGate(
            policy(
                PluginPolicy.Policy.ANY,
                user,
                Set.of("NETWORK_WRITE"),
                Publisher.Registry.EMPTY,
                false,
                null),
            cache,
            registry,
            "2.29",
            null); // NON-NLS
    PluginGate.Decision denied =
        denying.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    PluginGate.Decision otherApi =
        anyGate.decide(
            imported,
            descriptor(
                "acme-tool",
                "1.0",
                Map.of(WeasisManifestFinder.WEASIS_COMMAND_API, "3.0"))); // NON-NLS
    registry.put(registry.byId("acme-tool").orElseThrow().withEnabled(false)); // NON-NLS
    PluginGate.Decision disabled =
        anyGate.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS
    registry.put(registry.byId("acme-tool").orElseThrow().withEnabled(true)); // NON-NLS
    Files.writeString(imported, "tampered"); // NON-NLS
    PluginGate.Decision tampered =
        anyGate.decide(imported, descriptor("acme-tool", "1.0", headers)); // NON-NLS

    assertAll(
        () -> assertTrue(core.load()),
        () -> assertEquals(PluginPolicy.Tier.CORE, core.plugin().tier()),
        () -> assertEquals(LoadedPlugin.PACKAGE, core.plugin().source()),
        () -> assertFalse(candidate.load()),
        () -> assertEquals(LoadedPlugin.CANDIDATE, candidate.plugin().state()),
        () -> assertTrue(unverified.load()),
        () -> assertEquals(PluginPolicy.Tier.UNVERIFIED, unverified.plugin().tier()),
        () ->
            assertEquals(
                List.of("FILE_READ", "NETWORK_WRITE"),
                unverified.plugin().capabilities()), // NON-NLS
        () -> assertFalse(belowPolicy.load()),
        () ->
            assertTrue(
                belowPolicy.plugin().reason().contains("below the policy site"),
                belowPolicy.plugin().reason()),
        () -> assertFalse(refusedByPolicy.load()),
        () -> assertFalse(denied.load()),
        () ->
            assertTrue(
                denied.plugin().reason().contains("NETWORK_WRITE"), denied.plugin().reason()),
        () -> assertFalse(otherApi.load()),
        () -> assertEquals(LoadedPlugin.DISABLED, disabled.plugin().state()),
        () -> assertFalse(tampered.load()),
        () ->
            assertTrue(tampered.plugin().reason().contains("digest"), tampered.plugin().reason()));
  }

  @Test
  void aSignedJarOfASitePublisherIsSiteTierAndALockedSetRefusesWhatItDoesNotList(@TempDir Path dir)
      throws Exception {
    Path cache = dir.resolve("cache").resolve("plugins");
    Path user = dir.resolve("config").resolve("plugins");
    Path keys = dir.resolve("keys");
    IntegrityTool.keygen(keys, "acme"); // NON-NLS
    Path imported = jar(user, "acme-tool-1.0.jar", "acme-tool", "1.0", Map.of()); // NON-NLS
    PluginTool.sign(imported, keys.resolve("acme.key"));
    Optional<Integrity.Key> verified = PluginTool.verify(imported, keys.resolve("acme.pub"));
    Publisher.Registry site =
        Publisher.parse(
            "{\"publishers\":["
                + PluginTool.publisherRecord(
                    keys.resolve("acme.pub"), "Acme", "https://acme.example", null)
                + "]}"); // NON-NLS
    UserPluginRegistry registry = new UserPluginRegistry(user).load();
    String digest = Integrity.sha256(imported);
    registry.put(
        new UserPluginRegistry.Entry(
            "acme-tool-1.0.jar",
            "acme-tool",
            "1.0",
            digest,
            verified.map(Integrity.Key::id).orElse(null),
            "site",
            Instant.now().toString(),
            "doe",
            true)); // NON-NLS
    PluginGate siteGate =
        new PluginGate(
            policy(PluginPolicy.Policy.SITE, user, Set.of(), site, false, null),
            cache,
            registry,
            "2.29",
            null);
    PluginGate.Decision trusted =
        siteGate.decide(imported, descriptor("acme-tool", "1.0", Map.of())); // NON-NLS

    Publisher.Registry revoked =
        Publisher.parse(
            "{\"publishers\":["
                + PluginTool.publisherRecord(keys.resolve("acme.pub"), "Acme", null, null)
                + "],\"revocations\":[{\"plugin\":\"acme-tool@1.0\",\"reason\":\"bug\"}]}"); // NON-NLS
    PluginGate revokedGate =
        new PluginGate(
            policy(PluginPolicy.Policy.SITE, user, Set.of(), revoked, false, null),
            cache,
            registry,
            "2.29",
            null);
    PluginGate.Decision afterRevocation =
        revokedGate.decide(imported, descriptor("acme-tool", "1.0", Map.of())); // NON-NLS

    Path packaged =
        jar(cache, "weasis-core-5.0.0.jar", "weasis-core", "5.0.0", Map.of()); // NON-NLS
    Map<String, String> locked = Map.of("weasis-core", Integrity.sha256(packaged)); // NON-NLS
    PluginGate lockedGate =
        new PluginGate(
            policy(PluginPolicy.Policy.ANY, user, Set.of(), site, true, null),
            cache,
            registry,
            "2.29",
            locked);
    PluginGate.Decision inSet =
        lockedGate.decide(packaged, descriptor("weasis-core", "5.0.0", Map.of())); // NON-NLS
    PluginGate.Decision outOfSet =
        lockedGate.decide(imported, descriptor("acme-tool", "1.0", Map.of())); // NON-NLS
    PluginGate changedGate =
        new PluginGate(
            policy(PluginPolicy.Policy.ANY, user, Set.of(), site, true, null),
            cache,
            registry,
            "2.29",
            Map.of("weasis-core", "deadbeef")); // NON-NLS
    PluginGate.Decision changed =
        changedGate.decide(packaged, descriptor("weasis-core", "5.0.0", Map.of())); // NON-NLS

    assertAll(
        () -> assertTrue(verified.isPresent(), "the tool verifies what it signed"),
        () -> assertTrue(trusted.load()),
        () -> assertEquals(PluginPolicy.Tier.SITE, trusted.plugin().tier()),
        () ->
            assertFalse(
                afterRevocation.load(), "revoked by the site: unverified, below the policy"),
        () -> assertTrue(inSet.load()),
        () -> assertFalse(outOfSet.load()),
        () ->
            assertTrue(
                outOfSet.plugin().reason().contains("locked set"), outOfSet.plugin().reason()),
        () -> assertFalse(changed.load()),
        () ->
            assertTrue(
                changed.plugin().reason().contains("another version"), changed.plugin().reason()));
  }

  @Test
  void theSignatureRoundTripsAndATamperedFieldDoesNotVerify(@TempDir Path dir) throws Exception {
    Path keys = dir.resolve("keys");
    IntegrityTool.keygen(keys, "pub"); // NON-NLS
    Path jar =
        jar(
            dir,
            "x-1.0.jar",
            "x",
            "1.0",
            Map.of(
                WeasisManifestFinder.WEASIS_CAPABILITIES,
                "FILE_READ",
                WeasisManifestFinder.WEASIS_COMMAND_API,
                "2.29")); // NON-NLS
    Path sidecar = PluginTool.sign(jar, keys.resolve("pub.key"));
    PluginSignature read = PluginSignature.read(jar).orElseThrow();
    Integrity.Key key =
        Integrity.key("?", Files.readString(keys.resolve("pub.pub")))
            .map(k -> new Integrity.Key(PluginSignature.keyId(k.publicKey()), k.publicKey()))
            .orElseThrow();
    PluginSignature forged =
        new PluginSignature(
            read.pluginId(),
            "2.0",
            read.sha256(),
            read.size(),
            read.requires(),
            read.commandApi(),
            read.capabilities(),
            read.publisherKeyId(),
            read.signed(),
            read.signature()); // NON-NLS
    assertAll(
        () -> assertTrue(Files.isRegularFile(sidecar)),
        () -> assertEquals("x", read.pluginId()),
        () -> assertEquals(List.of("FILE_READ"), read.capabilities()), // NON-NLS
        () -> assertEquals("2.29", read.commandApi()),
        () -> assertEquals(key.id(), read.publisherKeyId()),
        () -> assertTrue(read.verify(List.of(key)).isPresent()),
        () -> assertTrue(read.matches(jar)),
        () -> assertTrue(forged.verify(List.of(key)).isEmpty()),
        () -> assertEquals(read, PluginSignature.parse(read.toJson())));
  }

  @Test
  void theRegistryAndTheManifestRoundTrip(@TempDir Path dir) throws Exception {
    Path user = dir.resolve("plugins");
    jar(user, "a-1.jar", "a", "1", Map.of()); // NON-NLS
    jar(user, "b-1.jar", "b", "1", Map.of()); // NON-NLS
    UserPluginRegistry registry = new UserPluginRegistry(user).load();
    registry.put(
        new UserPluginRegistry.Entry(
            "a-1.jar",
            "a",
            "1",
            "abc",
            null,
            "unverified",
            "2026-11-07T10:00:00Z",
            "doe",
            true)); // NON-NLS
    registry.store();
    UserPluginRegistry again = new UserPluginRegistry(user).load();
    LoadedPlugins manifest =
        new LoadedPlugins(
            List.of(
                new LoadedPlugin(
                    "a",
                    "1",
                    LoadedPlugin.USER,
                    "a-1.jar",
                    "abc",
                    null,
                    PluginPolicy.Tier.UNVERIFIED,
                    List.of(),
                    LoadedPlugin.STARTED,
                    null), // NON-NLS
                new LoadedPlugin(
                    "b",
                    "1",
                    LoadedPlugin.USER,
                    "b-1.jar",
                    null,
                    null,
                    PluginPolicy.Tier.UNVERIFIED,
                    List.of(),
                    LoadedPlugin.CANDIDATE,
                    "not imported")), // NON-NLS
            PluginPolicy.Policy.ANY,
            false,
            dir.resolve("loaded-plugins.json"));
    manifest.store();
    List<LoadedPlugins.Line> lines = LoadedPlugins.parseLines(Files.readString(manifest.file()));
    assertAll(
        () -> assertEquals(1, again.entries().size()),
        () -> assertEquals("a", again.byFile("a-1.jar").orElseThrow().id()), // NON-NLS
        () -> assertEquals(List.of(user.resolve("b-1.jar")), again.candidates()), // NON-NLS
        () -> assertEquals(2, lines.size()),
        () -> assertEquals("abc", lines.getFirst().sha256()),
        () -> assertEquals(64, manifest.fingerprint().length()),
        () -> assertTrue(manifest.toJson().contains("\"state\": \"candidate\""), manifest.toJson()),
        () ->
            assertEquals(
                Map.of("a", "abc", "b", ""), PluginGate.readLockedSet(manifest.file()))); // NON-NLS
  }
}
