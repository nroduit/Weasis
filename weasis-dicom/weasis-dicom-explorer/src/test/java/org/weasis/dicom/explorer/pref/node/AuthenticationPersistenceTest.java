/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.JsonObject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.AuthProvider;
import org.weasis.core.api.net.auth.AuthRegistration;
import org.weasis.core.api.net.auth.DefaultAuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.ListDocument;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence.Documents;
import org.weasis.dicom.explorer.pref.node.AuthenticationPersistence.Entry;

@DisplayNameGeneration(ReplaceUnderscores.class)
class AuthenticationPersistenceTest {

  private static final String UID_1 = "7d7c9b2e-1a6e-4b0f-9c3d-0e8f2a5b6c01";
  private static final String UID_2 = "2f1e0d9c-8b7a-4655-9e3f-1d2c3b4a5f02";
  private static final String SITE_ID_1 = "site.auth.7d7c9b2e-1a6e-4b0f-9c3d-0e8f2a5b6c01";

  @TempDir Path dir;

  @BeforeEach
  void secretStoreInTempDir() {
    SecretStore.useInstance(new SecretStore(dir.resolve(SecretStore.FILE)));
  }

  private Path fixture(String target) throws IOException {
    Path file = dir.resolve(target);
    try (InputStream in = getClass().getResourceAsStream("/config/authenticationNodes.xml")) {
      Files.createDirectories(file.getParent());
      Files.copy(in, file);
    }
    return file;
  }

  private static DefaultAuthMethod method(String uid, String name, String secret) {
    DefaultAuthMethod m =
        new DefaultAuthMethod(
            uid,
            new AuthProvider(
                name, "https://a.example.org/auth", "https://a.example.org/token", null, true),
            AuthRegistration.of("client-" + name, secret, "openid", null));
    m.setLocal(true);
    return m;
  }

  private static JsonObject json(Entry entry) {
    return AuthenticationPersistence.toJson(entry, true);
  }

  @Test
  void a_legacy_xml_converts_to_json_with_derived_ids_and_back_to_the_model() throws IOException {
    Path xml = fixture("site/authenticationNodes.xml");

    List<JsonObject> objects = AuthenticationPersistence.readLegacy(xml, EntryIds.SITE_PREFIX);
    Entry first = AuthenticationPersistence.fromJson(objects.get(0), false, new HashSet<>());
    Entry second = AuthenticationPersistence.fromJson(objects.get(1), false, new HashSet<>());
    AuthProvider p = first.method().getAuthProvider();
    AuthRegistration r = first.method().getAuthRegistration();

    assertAll(
        () -> assertEquals(2, objects.size()),
        () -> assertEquals(SITE_ID_1, first.id()),
        () -> assertEquals(UID_1, first.uid()),
        () -> assertEquals("refresh-abc", first.method().getCode()),
        () -> assertEquals("Keycloak HUG", p.name()),
        () ->
            assertEquals(
                "https://sso.example.org/realms/hug/protocol/openid-connect/auth",
                p.authorizationUri()),
        () ->
            assertEquals(
                "https://sso.example.org/realms/hug/protocol/openid-connect/token", p.tokenUri()),
        () ->
            assertEquals(
                "https://sso.example.org/realms/hug/protocol/openid-connect/revoke",
                p.revokeTokenUri()),
        () -> assertTrue(p.openId()),
        () -> assertEquals("weasis-client", r.clientId()),
        () -> assertEquals("s3cr3t-value", r.clientSecret()),
        () -> assertEquals("openid profile", r.scope()),
        () -> assertEquals("pacs-api", r.audience()),
        () -> assertEquals(AuthRegistration.CODE, r.getAuthorizationGrantType()),
        () -> assertFalse(first.method().isLocal()),
        () -> assertFalse(first.hidden()),
        () -> assertFalse(first.locked()),
        () -> assertEquals("site.auth." + UID_2, second.id()),
        () -> assertNull(second.method().getCode(), "an empty attribute is no code"),
        () -> assertFalse(second.method().getAuthProvider().openId()),
        () -> assertNull(second.method().getAuthProvider().authorizationUri()),
        () ->
            assertEquals(
                AuthRegistration.CLIENT_CREDENTIALS,
                second.method().getAuthRegistration().getAuthorizationGrantType()),
        () -> assertEquals(objects.get(0), json(first), "the JSON round-trips through the model"),
        () -> assertEquals(objects.get(1), json(second)));
  }

  @Test
  void the_user_layer_overrides_hides_and_is_refused_by_a_locked_site_entry() throws IOException {
    Path siteJson = dir.resolve("site/config/authentication.json");
    Entry siteA = new Entry("site.auth.a", method("uid-a", "Site A", "site-secret"), false, false);
    Entry siteB = new Entry("site.auth.b", method("uid-b", "Site B", null), false, true);
    Entry siteC = new Entry("site.auth.c", method("uid-c", "Site C", null), false, false);
    ListDocument.write(
        siteJson,
        AuthenticationPersistence.ENTRIES,
        List.of(json(siteA), json(siteB), json(siteC)));

    Path userJson = dir.resolve("user/authentication.json");
    Entry userA = new Entry("site.auth.a", method("uid-a", "My A", null), false, false);
    Entry userB = new Entry("site.auth.b", method("uid-b", "My B", null), false, false);
    Entry userC = new Entry("site.auth.c", method("uid-c", "Site C", null), true, false);
    Entry userD = new Entry("user.auth.d", method("uid-d", "Mine", null), false, false);
    ListDocument.write(
        userJson,
        AuthenticationPersistence.ENTRIES,
        List.of(json(userA), json(userB), json(userC), json(userD)));

    Documents docs = new Documents(siteJson, null, userJson, null);
    Merged<Entry> merged = AuthenticationPersistence.load(docs);
    AuthenticationPersistence.reload(docs);

    assertAll(
        () ->
            assertEquals(
                List.of("site.auth.a", "site.auth.b", "site.auth.c", "user.auth.d"),
                merged.entries().stream().map(Entry::id).toList()),
        () -> assertEquals(Origin.USER, merged.origin("site.auth.a")),
        () -> assertEquals("My A", merged.entries().get(0).method().getName()),
        () -> assertTrue(merged.entries().get(0).method().isLocal()),
        () -> assertEquals(Origin.SITE, merged.origin("site.auth.b"), "locked by the site"),
        () -> assertEquals("Site B", merged.entries().get(1).method().getName()),
        () -> assertTrue(merged.isLocked("site.auth.b")),
        () -> assertTrue(merged.entries().get(2).hidden()),
        () ->
            assertNull(
                merged.entries().get(0).method().getAuthRegistration().clientSecret(),
                "the user entry replaces the whole site entry, its secret included"),
        () ->
            assertEquals(
                List.of("uid-a", "uid-b", "uid-d"),
                List.copyOf(AuthenticationPersistence.getMethods().keySet()),
                "hidden methods stay out of the list"),
        () -> assertEquals("My A", AuthenticationPersistence.getAuthMethod("uid-a").getName()),
        () ->
            assertEquals(
                "Site C",
                AuthenticationPersistence.getAuthMethod("uid-c").getName(),
                "a node referencing a hidden method still finds it"),
        () ->
            assertSame(
                OAuth2ServiceFactory.NO_AUTH, AuthenticationPersistence.getAuthMethod("none")));
  }

  @Test
  void a_user_xml_is_migrated_once_its_secrets_going_to_the_store_and_the_xml_kept()
      throws IOException {
    Path userXml = fixture("user/authenticationNodes.xml");
    Path userJson = userXml.resolveSibling(AuthenticationPersistence.FILENAME);
    Documents docs = new Documents(null, null, userJson, userXml);

    Merged<Entry> merged = AuthenticationPersistence.load(docs);
    String content = Files.readString(userJson);
    Entry first = merged.entries().get(0);

    assertAll(
        () -> assertTrue(Files.isRegularFile(userXml), "the XML is kept"),
        () -> assertTrue(Files.isRegularFile(userJson)),
        () -> assertFalse(content.contains("s3cr3t-value"), content),
        () -> assertTrue(content.contains("\"user.auth." + UID_1 + "\""), content),
        () ->
            assertEquals(
                Optional.of("s3cr3t-value"),
                SecretStore.getInstance()
                    .get("user.auth." + UID_1, AuthenticationPersistence.CLIENT_SECRET)),
        () -> assertEquals(2, merged.entries().size()),
        () -> assertEquals(Origin.USER, merged.origin(first.id())),
        () -> assertTrue(first.method().isLocal()),
        () ->
            assertEquals(
                "s3cr3t-value",
                first.method().getAuthRegistration().clientSecret(),
                "a user method takes its secret from the store"));

    Files.writeString(userXml, "<methods/>");
    assertEquals(
        2,
        AuthenticationPersistence.load(docs).entries().size(),
        "the XML is never read again while the JSON exists");
  }

  @Test
  void a_saved_user_method_keeps_its_secret_out_of_the_document() throws IOException {
    Path userJson = dir.resolve("user/authentication.json");
    AuthenticationPersistence.reload(new Documents(null, null, userJson, null));
    DefaultAuthMethod method = method("uid-new", "Mine", "top-secret");

    AuthenticationPersistence.addOrUpdateMethod(method);
    String content = Files.readString(userJson);
    AuthenticationPersistence.reload(new Documents(null, null, userJson, null));
    AuthMethod reloaded = AuthenticationPersistence.getAuthMethod("uid-new");

    assertAll(
        () -> assertFalse(content.contains("top-secret"), content),
        () -> assertTrue(content.contains("\"user.auth.uid-new\""), content),
        () -> assertTrue(content.contains("\"client-Mine\""), content),
        () ->
            assertEquals(
                Optional.of("top-secret"),
                SecretStore.getInstance()
                    .get("user.auth.uid-new", AuthenticationPersistence.CLIENT_SECRET)),
        () -> assertEquals("Mine", reloaded.getName()),
        () -> assertTrue(reloaded.isLocal()),
        () -> assertEquals("top-secret", reloaded.getAuthRegistration().clientSecret()),
        () ->
            assertEquals("user.auth.uid-new", AuthenticationPersistence.entries().getFirst().id()));

    AuthenticationPersistence.removeMethod(reloaded);

    assertAll(
        () ->
            assertSame(
                OAuth2ServiceFactory.NO_AUTH, AuthenticationPersistence.getAuthMethod("uid-new")),
        () -> assertTrue(SecretStore.getInstance().get("user.auth.uid-new").isEmpty()),
        () ->
            assertTrue(
                ListDocument.read(userJson, AuthenticationPersistence.ENTRIES)
                    .entries()
                    .isEmpty()));
  }

  @Test
  void a_site_method_is_not_written_in_the_user_document() throws IOException {
    Path siteJson = dir.resolve("site/config/authentication.json");
    ListDocument.write(
        siteJson,
        AuthenticationPersistence.ENTRIES,
        List.of(json(new Entry("site.auth.a", method("uid-a", "Site A", null), false, false))));
    Path userJson = dir.resolve("user/authentication.json");
    AuthenticationPersistence.reload(new Documents(siteJson, null, userJson, null));

    AuthenticationPersistence.addOrUpdateMethod(method("uid-mine", "Mine", null));
    List<JsonObject> written =
        ListDocument.read(userJson, AuthenticationPersistence.ENTRIES).entries();

    assertAll(
        () -> assertEquals(1, written.size()),
        () -> assertEquals("user.auth.uid-mine", written.getFirst().getString("id")),
        () -> assertEquals(2, AuthenticationPersistence.getMethods().size()));
  }
}
