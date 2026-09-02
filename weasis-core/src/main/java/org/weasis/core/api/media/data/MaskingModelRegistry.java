/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import jakarta.json.JsonException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.util.StringUtil;

/**
 * The masking model in force, merged from the bundled document, documents contributed by other
 * bundles (the DICOM tag classification), the site document given by {@value #CONFIG_PROPERTY} and
 * the user document {@value #USER_FILE} in the preference directory. A later document overrides an
 * earlier one for the same tag or profile id; a locked site document excludes the user document.
 *
 * <p>A profile that keeps direct identifiers, or a session or AI profile id that does not exist, is
 * refused with an error and the earlier definition stays, so a configuration mistake never turns
 * masking off.
 */
public final class MaskingModelRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingModelRegistry.class);

  public static final String BUILTIN_RESOURCE = "/identityMasking.json"; // NON-NLS
  public static final String USER_FILE = "identityMasking.json"; // NON-NLS

  /** Launcher preference holding the path or URL of the site document. */
  public static final String CONFIG_PROPERTY = "weasis.masking.config"; // NON-NLS

  private static final int CONNECT_TIMEOUT_MS = 5_000;
  private static final int READ_TIMEOUT_MS = 10_000;

  /** Used only when no document defines a usable profile. */
  private static final MaskingProfile FALLBACK =
      new MaskingProfile(
          MaskingProfile.DISPLAY_ID,
          "Display", // NON-NLS
          Map.of(),
          new EnumMap<>(
              Map.of(
                  TagCategory.DIRECT_ID, AnonymizationAction.PSEUDONYMIZE,
                  TagCategory.BIRTH_DATE, AnonymizationAction.REMOVE,
                  TagCategory.FREE_TEXT, AnonymizationAction.REMOVE)),
          Map.of(),
          false,
          true);

  private static volatile MaskingModelRegistry instance;

  private record Merged(
      Map<String, TagRule> tags,
      Map<String, MaskingProfile> profiles,
      MaskingProfile session,
      MaskingProfile ai) {}

  private final MaskingModel bundled;
  private final List<MaskingModel> contributed = new CopyOnWriteArrayList<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final Set<TagW> classifiedInternal = new HashSet<>();
  private String siteLocation;
  private Path userFile;
  private volatile Merged merged;

  public MaskingModelRegistry(MaskingModel bundled) {
    this.bundled = bundled == null ? MaskingModel.EMPTY : bundled;
    this.merged = merge();
  }

  /** The shared registry, created from the bundled document on first use. */
  public static MaskingModelRegistry getInstance() {
    MaskingModelRegistry result = instance;
    if (result == null) {
      synchronized (MaskingModelRegistry.class) {
        result = instance;
        if (result == null) {
          result = new MaskingModelRegistry(loadBuiltIn());
          result.addListener(result::applyInternalTags);
          result.addListener(IdentityMask::modelChanged);
          result.applyInternalTags();
          instance = result;
        }
      }
    }
    return result;
  }

  /** Installs the shared instance, for hosts that build their own such as tests. */
  public static void useInstance(MaskingModelRegistry registry) {
    synchronized (MaskingModelRegistry.class) {
      instance = registry;
    }
  }

  public static MaskingModel loadBuiltIn() {
    try (InputStream in = MaskingModelRegistry.class.getResourceAsStream(BUILTIN_RESOURCE)) {
      return in == null ? MaskingModel.EMPTY : MaskingModel.read(in);
    } catch (IOException | RuntimeException e) {
      LOGGER.error("Cannot read the built-in masking configuration", e);
      return MaskingModel.EMPTY;
    }
  }

  /**
   * Sets the site and user documents and reloads.
   *
   * @param siteLocation file path or URL of the site document, or null
   * @param userFile the user document, or null
   */
  public void configure(String siteLocation, Path userFile) {
    synchronized (this) {
      this.siteLocation = StringUtil.hasText(siteLocation) ? siteLocation.trim() : null;
      this.userFile = userFile;
    }
    reload();
  }

  /** Adds a document of another bundle, merged after the bundled one, and reloads. */
  public void contribute(MaskingModel model) {
    if (model != null) {
      contributed.add(model);
      reload();
    }
  }

  /** Re-reads the site and user documents, merges and notifies the listeners. */
  public void reload() {
    Merged result = merge();
    merged = result;
    listeners.forEach(Runnable::run);
  }

  /** Notified after every reload, on the thread that reloaded. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  public Collection<TagRule> tagRules() {
    return merged.tags().values();
  }

  public List<MaskingProfile> profiles() {
    return List.copyOf(merged.profiles().values());
  }

  public Optional<MaskingProfile> profile(String id) {
    return Optional.ofNullable(merged.profiles().get(id));
  }

  /** The profile of that id, or the session profile when it does not exist. */
  MaskingProfile require(String id) {
    return profile(id).orElseGet(this::sessionProfile);
  }

  /** Profiles offered by export dialogs. */
  public List<MaskingProfile> exportProfiles() {
    return merged.profiles().values().stream().filter(MaskingProfile::offerInExport).toList();
  }

  public MaskingProfile sessionProfile() {
    return merged.session();
  }

  public MaskingProfile aiProfile() {
    return merged.ai();
  }

  private synchronized Merged merge() {
    Map<String, TagRule> tags = new LinkedHashMap<>();
    Map<String, MaskingProfile> profiles = new LinkedHashMap<>();
    String[] ids = {MaskingProfile.DISPLAY_ID, MaskingProfile.AI_REQUEST_ID};

    mergeInto(bundled, "bundled", tags, profiles, ids); // NON-NLS
    contributed.forEach(m -> mergeInto(m, "contributed", tags, profiles, ids)); // NON-NLS
    MaskingModel site = siteLocation == null ? null : loadSite(siteLocation);
    if (site != null) {
      mergeInto(site, siteLocation, tags, profiles, ids);
    }
    if (site != null && site.locked()) {
      LOGGER.info("Masking configuration locked by {}, user file ignored", siteLocation);
    } else if (userFile != null && Files.isRegularFile(userFile)) {
      MaskingModel user = loadUser(userFile);
      if (user != null) {
        mergeInto(user, userFile.toString(), tags, profiles, ids);
      }
    }

    MaskingProfile first = profiles.values().stream().findFirst().orElse(FALLBACK);
    MaskingProfile session = resolve(profiles, ids[0], MaskingProfile.DISPLAY_ID, first);
    MaskingProfile ai = resolve(profiles, ids[1], MaskingProfile.AI_REQUEST_ID, session);
    return new Merged(
        Collections.unmodifiableMap(tags), Collections.unmodifiableMap(profiles), session, ai);
  }

  private static void mergeInto(
      MaskingModel model,
      String origin,
      Map<String, TagRule> tags,
      Map<String, MaskingProfile> profiles,
      String[] ids) {
    for (TagRule rule : model.tags()) {
      TagRule previous = tags.put(rule.key(), rule);
      if (previous != null && previous.category() != rule.category()) {
        LOGGER.info(
            "Masking category of {} changed from {} to {} by {}",
            rule.key(),
            previous.category(),
            rule.category(),
            origin);
      }
    }
    for (MaskingProfile profile : model.profiles()) {
      if (profile.hidesDirectIdentifiers()) {
        profiles.put(profile.id(), profile);
      } else {
        LOGGER.error(
            "Masking profile '{}' from {} keeps direct identifiers and is refused",
            profile.id(),
            origin);
      }
    }
    if (model.sessionProfile() != null) {
      ids[0] = model.sessionProfile();
    }
    if (model.aiProfile() != null) {
      ids[1] = model.aiProfile();
    }
  }

  private static MaskingProfile resolve(
      Map<String, MaskingProfile> profiles, String id, String defaultId, MaskingProfile fallback) {
    MaskingProfile profile = profiles.get(id);
    if (profile != null) {
      return profile;
    }
    LOGGER.error("Masking profile '{}' does not exist, using '{}'", id, defaultId);
    return profiles.getOrDefault(defaultId, fallback);
  }

  private static MaskingModel loadSite(String location) {
    try {
      if (location.contains("://") || location.startsWith("file:")) { // NON-NLS
        URLConnection connection = URI.create(location).toURL().openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        try (InputStream in = connection.getInputStream()) {
          return MaskingModel.read(in);
        }
      }
      return MaskingModel.read(Path.of(location));
    } catch (IOException | JsonException | IllegalArgumentException e) {
      LOGGER.error("Cannot read the site masking configuration: {}", location, e);
      return null;
    }
  }

  private static MaskingModel loadUser(Path file) {
    try {
      return MaskingModel.read(file);
    } catch (IOException e) {
      LOGGER.error("Cannot read the user masking configuration: {}", file, e);
      return null;
    }
  }

  /** Classifies the Weasis-internal tags; DICOM tags are classified by the DICOM codec. */
  private synchronized void applyInternalTags() {
    Set<TagW> applied = new HashSet<>();
    for (TagRule rule : tagRules()) {
      if (!rule.isInternal()) {
        continue;
      }
      TagW tag = TagW.get(rule.keyword());
      if (tag == null) {
        LOGGER.warn("Unknown internal tag in masking configuration: {}", rule.key());
        continue;
      }
      tag.setCategory(rule.category());
      applied.add(tag);
    }
    classifiedInternal.stream()
        .filter(t -> !applied.contains(t))
        .forEach(t -> t.setCategory(TagCategory.OTHER));
    classifiedInternal.clear();
    classifiedInternal.addAll(applied);
  }
}
