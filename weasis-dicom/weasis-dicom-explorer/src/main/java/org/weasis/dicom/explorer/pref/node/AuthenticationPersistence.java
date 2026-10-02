/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.pref.node;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.swing.JComboBox;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.net.auth.AuthMethod;
import org.weasis.core.api.net.auth.AuthProvider;
import org.weasis.core.api.net.auth.AuthRegistration;
import org.weasis.core.api.net.auth.DefaultAuthMethod;
import org.weasis.core.api.net.auth.OAuth2ServiceFactory;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.util.EntryIds;
import org.weasis.core.api.util.JsonExtends;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;
import org.weasis.core.api.util.LegacyConverters;
import org.weasis.core.api.util.LegacyMigration;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.SiteDocuments;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;

/**
 * The authentication methods the DICOMweb nodes reference, in two layers merged by entry id: the
 * site document {@value #FILENAME} of the resources package (or, until 5.2, the legacy {@value
 * #LEGACY_FILENAME} of its root), then the user document {@value #FILENAME} of the bundle data
 * folder, converted once from the legacy XML of that folder. A method is referenced by its {@code
 * uid}, kept verbatim through the conversion; the id is what the layers merge on, derived from the
 * uid when the document is the legacy XML.
 *
 * <p>The client secret of a user method is never written in the document, which is shared and
 * mirrored: it goes to the {@link SecretStore} under the method id. A site document may carry it as
 * is.
 */
public final class AuthenticationPersistence {
  private static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationPersistence.class);

  public static final String FILENAME = "authentication.json"; // NON-NLS
  public static final String LEGACY_FILENAME = "authenticationNodes.xml"; // NON-NLS

  /** The name of the array of entries in the envelope. */
  public static final String ENTRIES = "methods"; // NON-NLS

  /** The kind of the derived ids, see {@link EntryIds#derived}. */
  public static final String KIND = "auth"; // NON-NLS

  /** The name of the client secret in the {@link SecretStore}. */
  public static final String CLIENT_SECRET = "clientSecret"; // NON-NLS

  private static final String WHAT = "Authentication method"; // NON-NLS

  // JSON members
  private static final String ID = "id"; // NON-NLS
  private static final String UID = "uid"; // NON-NLS
  private static final String CODE = "code"; // NON-NLS
  private static final String PROVIDER = "provider"; // NON-NLS
  private static final String REGISTRATION = "registration"; // NON-NLS
  private static final String NAME = "name"; // NON-NLS
  private static final String AUTHORIZATION_URI = "authorizationUri"; // NON-NLS
  private static final String TOKEN_URI = "tokenUri"; // NON-NLS
  private static final String REVOKE_TOKEN_URI = "revokeTokenUri"; // NON-NLS
  private static final String OPEN_ID = "openId"; // NON-NLS
  private static final String CLIENT_ID = "clientId"; // NON-NLS
  private static final String SCOPE = "scope"; // NON-NLS
  private static final String AUDIENCE = "audience"; // NON-NLS
  private static final String GRANT_TYPE = "grantType"; // NON-NLS
  private static final String HIDDEN = "hidden"; // NON-NLS
  private static final String LOCKED = "locked"; // NON-NLS

  // Legacy XML elements and attributes
  private static final String T_NODE = "method"; // NON-NLS
  private static final String T_UID = "uid"; // NON-NLS
  private static final String T_CODE = "code"; // NON-NLS
  private static final String T_REGISTRATION = "registration"; // NON-NLS
  private static final String T_PROVIDER = "provider"; // NON-NLS
  private static final String T_CLIENT_ID = "id"; // NON-NLS
  private static final String T_CLIENT_SECRET = "secret"; // NON-NLS
  private static final String T_SCOPE = "scope"; // NON-NLS
  private static final String T_NAME = "name"; // NON-NLS
  private static final String T_AUTH_URI = "auth"; // NON-NLS
  private static final String T_TOKEN_URI = "token"; // NON-NLS
  private static final String T_REVOKE_URI = "revoke"; // NON-NLS
  private static final String T_OPENID = "openid"; // NON-NLS
  private static final String T_AUDIENCE = "audience"; // NON-NLS
  private static final String T_GRANT_TYPE = "grantType"; // NON-NLS

  /** The conversion of a site {@value #LEGACY_FILENAME} into {@value #FILENAME}, with site ids. */
  public static final LegacyConverters.Conversion CONVERSION =
      new LegacyConverters.Conversion(
          LEGACY_FILENAME, FILENAME, ENTRIES, xml -> readLegacy(xml, EntryIds.SITE_PREFIX));

  /**
   * One entry of the documents: the method with the id the layers merge on and the flags of the
   * document.
   */
  public record Entry(String id, AuthMethod method, boolean hidden, boolean locked)
      implements LayeredEntries.Entry {
    public String uid() {
      return method.getUid();
    }
  }

  /**
   * Where the documents of the two layers are; any of them may be null or absent. The site JSON
   * wins over the site XML when both exist.
   */
  public record Documents(Path siteJson, Path siteXml, Path userJson, Path userXml) {}

  private static final Map<String, Entry> entries = new LinkedHashMap<>(); // by uid
  private static Documents documents;

  static {
    SiteDocuments.onReload(FILENAME, AuthenticationPersistence::reload);
  }

  private AuthenticationPersistence() {}

  /** The method of that uid, or {@link OAuth2ServiceFactory#NO_AUTH}; a hidden one is found. */
  public static synchronized AuthMethod getAuthMethod(String serviceId) {
    ensureLoaded();
    Entry entry = serviceId == null ? null : entries.get(serviceId);
    return entry == null ? OAuth2ServiceFactory.NO_AUTH : entry.method();
  }

  /** The methods by uid in document order, the hidden ones left out. */
  public static synchronized Map<String, AuthMethod> getMethods() {
    ensureLoaded();
    Map<String, AuthMethod> visible = new LinkedHashMap<>();
    for (Entry entry : entries.values()) {
      if (!entry.hidden()) {
        visible.put(entry.uid(), entry.method());
      }
    }
    return Collections.unmodifiableMap(visible);
  }

  /** Reads the documents when not done yet, then returns the methods, hidden ones left out. */
  public static synchronized Collection<AuthMethod> loadMethods() {
    return getMethods().values();
  }

  public static synchronized void loadMethods(JComboBox<AuthMethod> comboBox) {
    comboBox.addItem(OAuth2ServiceFactory.NO_AUTH);
    for (AuthMethod method : loadMethods()) {
      comboBox.addItem(method);
    }
  }

  /** The entries after the merge, in document order, hidden ones included. */
  static synchronized List<Entry> entries() {
    ensureLoaded();
    return List.copyOf(entries.values());
  }

  /**
   * Replaces the method of that uid, or adds it with a new user id, and saves the user document.
   */
  public static synchronized void addOrUpdateMethod(AuthMethod method) {
    if (method == null || !StringUtil.hasText(method.getUid())) {
      return;
    }
    ensureLoaded();
    Entry previous = entries.get(method.getUid());
    Entry entry =
        previous == null
            ? new Entry(newUserId(method.getUid()), method, false, false)
            : new Entry(previous.id(), method, previous.hidden(), previous.locked());
    entries.put(method.getUid(), entry);
    OAuth2ServiceFactory.invalidateService(method.getUid());
    saveMethod();
  }

  /** Forgets the method of that uid, its secrets with it, and saves the user document. */
  public static synchronized void removeMethod(AuthMethod method) {
    if (method == null || !StringUtil.hasText(method.getUid())) {
      return;
    }
    ensureLoaded();
    Entry removed = entries.remove(method.getUid());
    if (removed != null) {
      OAuth2ServiceFactory.invalidateService(method.getUid());
      SecretStore.getInstance().remove(removed.id());
      saveMethod();
    }
  }

  /** Writes the user layer, the methods flagged {@link AuthMethod#isLocal() local}. */
  public static synchronized void saveMethod() {
    ensureLoaded();
    Path userJson = documents == null ? null : documents.userJson();
    if (userJson == null) {
      return;
    }
    List<Entry> user = entries.values().stream().filter(e -> e.method().isLocal()).toList();
    save(userJson, user);
  }

  /** Re-reads the documents of the installation; what was not saved is lost. */
  public static synchronized void reload() {
    reload(documents());
  }

  /** Re-reads the given documents and keeps them as the ones to save into. */
  static synchronized void reload(Documents docs) {
    documents = docs;
    entries.clear();
    for (Entry entry : load(docs).entries()) {
      entries.put(entry.uid(), entry);
    }
  }

  private static void ensureLoaded() {
    if (documents == null) {
      reload();
    }
  }

  private static String newUserId(String uid) {
    Set<String> used = new HashSet<>();
    entries.values().forEach(e -> used.add(e.id()));
    return EntryIds.unique(EntryIds.derived(EntryIds.USER_PREFIX, KIND, uid), used);
  }

  /** The documents of the running installation. */
  static Documents documents() {
    BundleContext context = AppProperties.getBundleContext(AuthenticationPersistence.class);
    return new Documents(
        SiteDocuments.find(FILENAME).orElse(null),
        ResourceUtil.getResource(LEGACY_FILENAME).toPath(),
        BundlePreferences.getFileInDataFolder(context, FILENAME),
        BundlePreferences.getFileInDataFolder(context, LEGACY_FILENAME));
  }

  /**
   * Reads and merges the two layers: the site document, then the user document, converted first
   * from its legacy XML when the JSON does not exist yet (the XML is kept). A user method takes its
   * client secret from the {@link SecretStore}.
   */
  static Merged<Entry> load(Documents docs) {
    List<JsonObject> siteObjects = readSite(docs);
    Map<String, JsonObject> siteById = new LinkedHashMap<>();
    siteObjects.forEach(o -> siteById.putIfAbsent(o.getString(ID, ""), o));
    List<Entry> site = parse(siteObjects, false);

    SecretStore store = SecretStore.getInstance();
    LegacyMigration.migrate(
        docs.userXml(),
        docs.userJson(),
        ENTRIES,
        xml -> withSecretsStored(readLegacy(xml, EntryIds.USER_PREFIX), store),
        WHAT);
    List<JsonObject> userObjects =
        JsonExtends.resolve(
            readJson(docs.userJson()), id -> Optional.ofNullable(siteById.get(id)), WHAT);
    List<Entry> user = parse(userObjects, true);

    return LayeredEntries.merge(
        List.of(Layer.of(Origin.SITE, site), Layer.of(Origin.USER, user)), WHAT);
  }

  private static List<JsonObject> readSite(Documents docs) {
    if (docs.siteJson() != null && Files.isRegularFile(docs.siteJson())) {
      return JsonExtends.resolve(readJson(docs.siteJson()), JsonExtends.NO_BASE, WHAT);
    }
    if (docs.siteXml() != null && Files.isRegularFile(docs.siteXml())) {
      try {
        return readLegacy(docs.siteXml(), EntryIds.SITE_PREFIX);
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read {}", docs.siteXml(), e);
      }
    }
    return List.of();
  }

  private static List<JsonObject> readJson(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return List.of();
    }
    try {
      return ListDocument.read(file, ENTRIES).entries();
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read {}", file, e);
      return List.of();
    }
  }

  private static List<Entry> parse(List<JsonObject> objects, boolean user) {
    List<Entry> result = new ArrayList<>(objects.size());
    Set<String> used = new HashSet<>();
    for (JsonObject object : objects) {
      try {
        Entry entry = fromJson(object, user, used);
        used.add(entry.id());
        result.add(entry);
      } catch (RuntimeException e) {
        LOGGER.error("Cannot read {} {}", WHAT, object.getString(ID, "?"), e);
      }
    }
    return result;
  }

  /**
   * Writes the user document, the client secrets going to the {@link SecretStore} under the method
   * ids instead of the file.
   */
  static void save(Path userJson, List<Entry> user) {
    SecretStore store = SecretStore.getInstance();
    List<JsonObject> objects = new ArrayList<>(user.size());
    for (Entry entry : user) {
      store.put(entry.id(), CLIENT_SECRET, entry.method().getAuthRegistration().clientSecret());
      objects.add(toJson(entry, false));
    }
    try {
      ListDocument.write(userJson, ENTRIES, objects);
    } catch (IOException e) {
      LOGGER.error("Cannot write {}", userJson, e);
    }
  }

  /**
   * The JSON of an entry.
   *
   * @param withSecret whether the client secret is written; never for the user document
   */
  static JsonObject toJson(Entry entry, boolean withSecret) {
    AuthMethod method = entry.method();
    AuthProvider p = method.getAuthProvider();
    AuthRegistration r = method.getAuthRegistration();

    JsonObjectBuilder provider = Json.createObjectBuilder();
    JsonUtil.addIfPresent(provider, NAME, p.name());
    JsonUtil.addIfPresent(provider, AUTHORIZATION_URI, p.authorizationUri());
    JsonUtil.addIfPresent(provider, TOKEN_URI, p.tokenUri());
    JsonUtil.addIfPresent(provider, REVOKE_TOKEN_URI, p.revokeTokenUri());
    provider.add(OPEN_ID, p.openId());

    JsonObjectBuilder registration = Json.createObjectBuilder();
    JsonUtil.addIfPresent(registration, CLIENT_ID, r.clientId());
    if (withSecret) {
      JsonUtil.addIfPresent(registration, CLIENT_SECRET, r.clientSecret());
    }
    JsonUtil.addIfPresent(registration, SCOPE, r.scope());
    JsonUtil.addIfPresent(registration, AUDIENCE, r.audience());
    JsonUtil.addIfPresent(registration, GRANT_TYPE, r.getAuthorizationGrantType());

    JsonObjectBuilder b = Json.createObjectBuilder().add(ID, entry.id());
    JsonUtil.addIfPresent(b, UID, method.getUid());
    JsonUtil.addIfPresent(b, CODE, method.getCode());
    b.add(PROVIDER, provider).add(REGISTRATION, registration);
    if (entry.hidden()) {
      b.add(HIDDEN, true);
    }
    if (entry.locked()) {
      b.add(LOCKED, true);
    }
    return b.build();
  }

  /**
   * The entry of a JSON object.
   *
   * @param user whether the object comes from the user document: the method is then local and takes
   *     its client secret from the {@link SecretStore}
   * @param used the ids already taken in the document, to derive a unique one when it has none
   */
  static Entry fromJson(JsonObject object, boolean user, Set<String> used) {
    String uid = object.getString(UID, null);
    String prefix = user ? EntryIds.USER_PREFIX : EntryIds.SITE_PREFIX;
    String id = object.getString(ID, null);
    if (!StringUtil.hasText(id)) {
      id = EntryIds.unique(EntryIds.derived(prefix, KIND, uid), used);
    }

    JsonObject p = object.get(PROVIDER) instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
    AuthProvider provider =
        new AuthProvider(
            p.getString(NAME, null),
            p.getString(AUTHORIZATION_URI, null),
            p.getString(TOKEN_URI, null),
            p.getString(REVOKE_TOKEN_URI, null),
            JsonUtil.getBoolean(p, OPEN_ID, false));

    JsonObject r =
        object.get(REGISTRATION) instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
    String secret = r.getString(CLIENT_SECRET, null);
    if (user) {
      secret = SecretStore.getInstance().get(id, CLIENT_SECRET).orElse(secret);
    }
    AuthRegistration registration =
        new AuthRegistration(
            r.getString(CLIENT_ID, null),
            secret,
            r.getString(SCOPE, null),
            r.getString(AUDIENCE, null),
            null,
            r.getString(GRANT_TYPE, null));

    DefaultAuthMethod method = new DefaultAuthMethod(uid, provider, registration);
    method.setCode(object.getString(CODE, null));
    method.setLocal(user);
    return new Entry(
        id,
        method,
        JsonUtil.getBoolean(object, HIDDEN, false),
        JsonUtil.getBoolean(object, LOCKED, false));
  }

  /** Moves the client secrets of converted user entries into the store, out of the JSON. */
  private static List<JsonObject> withSecretsStored(List<JsonObject> objects, SecretStore store) {
    List<JsonObject> result = new ArrayList<>(objects.size());
    for (JsonObject object : objects) {
      JsonObject registration =
          object.get(REGISTRATION) instanceof JsonObject o ? o : JsonValue.EMPTY_JSON_OBJECT;
      String secret = registration.getString(CLIENT_SECRET, null);
      if (StringUtil.hasText(secret)) {
        store.put(object.getString(ID), CLIENT_SECRET, secret);
        JsonObjectBuilder stripped = Json.createObjectBuilder(registration);
        stripped.remove(CLIENT_SECRET);
        result.add(Json.createObjectBuilder(object).add(REGISTRATION, stripped).build());
      } else {
        result.add(object);
      }
    }
    return result;
  }

  /**
   * Reads a legacy XML document into JSON entries, the id of each derived from its uid with the
   * given layer prefix; the client secret stays in the result.
   */
  static List<JsonObject> readLegacy(Path xml, String prefix) throws IOException {
    List<JsonObject> list = new ArrayList<>();
    Set<String> used = new HashSet<>();
    try (InputStream in = Files.newInputStream(xml)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      // disable external entities for security
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      XMLStreamReader xmler = factory.createXMLStreamReader(in);
      try {
        while (xmler.hasNext()) {
          if (xmler.next() == XMLStreamConstants.START_ELEMENT
              && T_NODE.equals(xmler.getName().getLocalPart())) {
            JsonObject method = readLegacyMethod(xmler, prefix, used);
            used.add(method.getString(ID));
            list.add(method);
          }
        }
      } finally {
        xmler.close();
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid document " + xml, e);
    }
    return list;
  }

  private static JsonObject readLegacyMethod(XMLStreamReader xmler, String prefix, Set<String> used)
      throws XMLStreamException {
    String uid = xmler.getAttributeValue(null, T_UID);
    String code = xmler.getAttributeValue(null, T_CODE);
    JsonObjectBuilder provider = Json.createObjectBuilder();
    JsonObjectBuilder registration = Json.createObjectBuilder();
    while (xmler.hasNext()) {
      int eventType = xmler.next();
      if (eventType == XMLStreamConstants.START_ELEMENT) {
        String key = xmler.getName().getLocalPart();
        if (T_PROVIDER.equals(key)) {
          JsonUtil.addIfPresent(provider, NAME, xmler.getAttributeValue(null, T_NAME));
          JsonUtil.addIfPresent(
              provider, AUTHORIZATION_URI, xmler.getAttributeValue(null, T_AUTH_URI));
          JsonUtil.addIfPresent(provider, TOKEN_URI, xmler.getAttributeValue(null, T_TOKEN_URI));
          JsonUtil.addIfPresent(
              provider, REVOKE_TOKEN_URI, xmler.getAttributeValue(null, T_REVOKE_URI));
          provider.add(OPEN_ID, LangUtil.emptyToFalse(xmler.getAttributeValue(null, T_OPENID)));
        } else if (T_REGISTRATION.equals(key)) {
          JsonUtil.addIfPresent(
              registration, CLIENT_ID, xmler.getAttributeValue(null, T_CLIENT_ID));
          JsonUtil.addIfPresent(
              registration, CLIENT_SECRET, xmler.getAttributeValue(null, T_CLIENT_SECRET));
          JsonUtil.addIfPresent(registration, SCOPE, xmler.getAttributeValue(null, T_SCOPE));
          JsonUtil.addIfPresent(registration, AUDIENCE, xmler.getAttributeValue(null, T_AUDIENCE));
          JsonUtil.addIfPresent(
              registration, GRANT_TYPE, xmler.getAttributeValue(null, T_GRANT_TYPE));
        }
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && T_NODE.equals(xmler.getName().getLocalPart())) {
        break;
      }
    }
    JsonObjectBuilder b =
        Json.createObjectBuilder()
            .add(ID, EntryIds.unique(EntryIds.derived(prefix, KIND, uid), used));
    JsonUtil.addIfPresent(b, UID, uid);
    JsonUtil.addIfPresent(b, CODE, code);
    return b.add(PROVIDER, provider).add(REGISTRATION, registration).build();
  }
}
