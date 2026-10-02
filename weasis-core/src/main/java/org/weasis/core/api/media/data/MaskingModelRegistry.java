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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.MaskingModel.TagRule;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.api.util.LayeredEntries.Layer;
import org.weasis.core.api.util.LayeredEntries.Merged;
import org.weasis.core.api.util.LayeredEntries.Origin;

/**
 * The masking model in force, merged from the bundled document, documents contributed by other
 * bundles (the DICOM tag classification), the site document {@value #SITE_FILE} of the resources
 * package (see {@code SiteDocuments}) and the user document {@value #USER_FILE} in the preference
 * directory. A later document overrides an earlier one for the same tag or profile id, unless the
 * site locked that profile or mask (see {@code LayeredEntries}); a locked site document excludes
 * the user document altogether.
 *
 * <p>A profile that keeps direct identifiers, or a session or AI profile id that does not exist, is
 * refused with an error and the earlier definition stays, so a configuration mistake never turns
 * masking off.
 */
public final class MaskingModelRegistry {

  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingModelRegistry.class);

  public static final String BUILTIN_RESOURCE = "/identityMasking.json"; // NON-NLS
  public static final String USER_FILE = "identityMasking.json"; // NON-NLS

  /** File name of the site document in the {@code config} folder of the resources package. */
  public static final String SITE_FILE = "identityMasking.json"; // NON-NLS

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

  private static volatile MaskingModelRegistry instance; // NOSONAR double-checked locking

  /** The merged documents: one layered result per kind of entry and the resolved profiles. */
  private record Model(
      Merged<TagRule> tags,
      Merged<MaskingProfile> profiles,
      Merged<PixelMask> masks,
      MaskingProfile session,
      MaskingProfile ai,
      boolean locked) {}

  private final MaskingModel bundled;
  private final List<MaskingModel> contributed = new CopyOnWriteArrayList<>();
  private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
  private final Set<TagW> classifiedInternal = new HashSet<>();
  private Path siteFile;
  private Path userFile;
  private Consumer<Path> remoteStore;
  private MaskingModel site = MaskingModel.EMPTY;
  private MaskingModel user = MaskingModel.EMPTY;
  private boolean userUnreadable;
  private volatile Model merged; // NOSONAR immutable snapshot

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
   * @param siteFile the site document, or null
   * @param userFile the user document, or null
   */
  public void configure(Path siteFile, Path userFile) {
    configure(siteFile, userFile, null);
  }

  /**
   * @param remoteStore called with the user document after it is written, to mirror it remotely
   */
  public void configure(Path siteFile, Path userFile, Consumer<Path> remoteStore) {
    synchronized (this) {
      this.siteFile = siteFile;
      this.userFile = userFile;
      this.remoteStore = remoteStore;
      this.site = MaskingModel.EMPTY;
      this.user = MaskingModel.EMPTY;
    }
    reload();
  }

  /** Adds a document of another bundle, merged after the bundled one, and merges. */
  public void contribute(MaskingModel model) {
    if (model != null) {
      contributed.add(model);
      apply();
    }
  }

  /**
   * Re-reads the site and user documents, merges and notifies the listeners. A site document that
   * cannot be read leaves the previous one in place.
   */
  public void reload() {
    synchronized (this) {
      if (siteFile != null) {
        MaskingModel fetched = loadSite(siteFile);
        if (fetched != null) {
          site = fetched;
        }
      }
      readUser();
    }
    apply();
  }

  private void apply() {
    merged = merge();
    listeners.forEach(Runnable::run);
  }

  private synchronized void readUser() {
    MaskingModel model =
        userFile != null && Files.isRegularFile(userFile) ? loadUser(userFile) : MaskingModel.EMPTY;
    userUnreadable = model == null;
    user = model == null ? MaskingModel.EMPTY : model;
  }

  /** Notified after every reload, on the thread that reloaded. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** The merged tag rules, with the document each comes from. */
  public Merged<TagRule> tags() {
    return merged.tags();
  }

  /** The merged profiles in document order, with their origin and what the site locked. */
  public Merged<MaskingProfile> profiles() {
    return merged.profiles();
  }

  public Optional<MaskingProfile> profile(String id) {
    return find(merged.profiles(), id);
  }

  /** The profile of that id, or the session profile when it does not exist. */
  MaskingProfile require(String id) {
    return profile(id).orElseGet(this::sessionProfile);
  }

  /** Profiles offered by export dialogs. */
  public List<MaskingProfile> exportProfiles() {
    return merged.profiles().entries().stream().filter(MaskingProfile::offerInExport).toList();
  }

  public MaskingProfile sessionProfile() {
    return merged.session();
  }

  public MaskingProfile aiProfile() {
    return merged.ai();
  }

  /** Every merged pixel mask, in document order, disabled ones included. */
  public Merged<PixelMask> masks() {
    return merged.masks();
  }

  public Optional<PixelMask> pixelMask(String id) {
    return find(merged.masks(), id);
  }

  /** Whether the site document excludes the user document, making the model read-only. */
  public boolean isLocked() {
    return merged.locked();
  }

  /** Where the user document is written, or null when the host set none. */
  public synchronized Path userFile() {
    return userFile;
  }

  /** The site document, or null when the package ships none. */
  public synchronized Path siteFile() {
    return siteFile;
  }

  /** Path of the site document as text, for messages; null when there is none. */
  public synchronized String siteLocation() {
    return siteFile == null ? null : siteFile.toString();
  }

  /** The user document, empty when there is none. */
  public synchronized MaskingModel userModel() {
    return user;
  }

  /**
   * Replaces the user document, writes it and merges.
   *
   * @throws IllegalArgumentException when a profile of the document keeps direct identifiers
   * @throws IllegalStateException when the site document locks the configuration
   */
  public void saveUser(MaskingModel model) throws IOException {
    Objects.requireNonNull(model);
    for (MaskingProfile profile : model.profiles()) {
      if (!profile.hidesDirectIdentifiers()) {
        throw new IllegalArgumentException(
            "Masking profile '%s' keeps direct identifiers".formatted(profile.id()));
      }
    }
    if (isLocked()) {
      throw new IllegalStateException("The masking configuration is locked by " + siteLocation());
    }
    synchronized (this) {
      user = model;
      persistUser();
    }
    apply();
  }

  /** Adds or replaces the user profile of that id. */
  public void saveUserProfile(MaskingProfile profile) throws IOException {
    Objects.requireNonNull(profile);
    MaskingModel current = userModel();
    List<MaskingProfile> profiles = new ArrayList<>(current.profiles());
    profiles.removeIf(p -> p.id().equals(profile.id()));
    profiles.add(profile);
    saveUser(current.withProfiles(profiles));
  }

  /**
   * Removes the user profile of that id.
   *
   * @return whether one existed
   */
  public boolean deleteUserProfile(String id) throws IOException {
    MaskingModel current = userModel();
    List<MaskingProfile> profiles = new ArrayList<>(current.profiles());
    if (!profiles.removeIf(p -> p.id().equals(id))) {
      return false;
    }
    saveUser(current.withProfiles(profiles));
    return true;
  }

  /** Adds or replaces the user classification of that tag. */
  public void saveUserTag(String tag, TagCategory category) throws IOException {
    String key = MaskingModel.normalizeKey(tag, null);
    if (key == null) {
      throw new IllegalArgumentException("Not a tag: " + tag);
    }
    MaskingModel current = userModel();
    List<TagRule> tags = new ArrayList<>(current.tags());
    tags.removeIf(rule -> rule.key().equals(key));
    tags.add(new TagRule(key, Objects.requireNonNull(category)));
    saveUser(current.withTags(tags));
  }

  /**
   * Removes the user classification of that normalized tag key.
   *
   * @return whether one existed
   */
  public boolean deleteUserTag(String key) throws IOException {
    MaskingModel current = userModel();
    List<TagRule> tags = new ArrayList<>(current.tags());
    if (!tags.removeIf(rule -> rule.key().equals(key))) {
      return false;
    }
    saveUser(current.withTags(tags));
    return true;
  }

  /** Adds or replaces the user pixel mask of that id. */
  public void saveUserMask(PixelMask mask) throws IOException {
    Objects.requireNonNull(mask);
    MaskingModel current = userModel();
    List<PixelMask> masks = new ArrayList<>(current.masks());
    masks.removeIf(m -> m.id().equals(mask.id()));
    masks.add(mask);
    saveUser(current.withMasks(masks));
  }

  /**
   * Removes the user pixel mask of that id.
   *
   * @return whether one existed
   */
  public boolean deleteUserMask(String id) throws IOException {
    MaskingModel current = userModel();
    List<PixelMask> masks = new ArrayList<>(current.masks());
    if (!masks.removeIf(m -> m.id().equals(id))) {
      return false;
    }
    saveUser(current.withMasks(masks));
    return true;
  }

  /** Reads a document to import, without installing it. */
  public static MaskingModel readImport(Path file) throws IOException {
    MaskingModel model = MaskingModel.read(file);
    if (model.profiles().isEmpty() && model.tags().isEmpty() && model.masks().isEmpty()) {
      throw new IOException("No masking profile, tag or mask in " + file);
    }
    return model;
  }

  private void persistUser() throws IOException {
    if (userFile == null) {
      return;
    }
    Path parent = userFile.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    if (userUnreadable && Files.isRegularFile(userFile)) {
      Path backup = userFile.resolveSibling(userFile.getFileName() + ".bak"); // NON-NLS
      Files.move(userFile, backup, StandardCopyOption.REPLACE_EXISTING);
      LOGGER.warn("The unreadable user masking configuration is kept in {}", backup);
    }
    userUnreadable = false;
    user.write(userFile);
    if (remoteStore != null) {
      remoteStore.accept(userFile);
    }
  }

  private synchronized Model merge() {
    List<Document> documents = new ArrayList<>();
    documents.add(new Document(Origin.BUILT_IN, bundled));
    contributed.forEach(m -> documents.add(new Document(Origin.CONTRIBUTED, m)));
    documents.add(new Document(Origin.SITE, site));
    boolean locked = site.locked();
    if (locked) {
      LOGGER.info("Masking configuration locked by {}, user file ignored", siteFile);
    } else {
      documents.add(new Document(Origin.USER, user));
    }
    Merged<TagRule> tags =
        LayeredEntries.merge(layers(documents, MaskingModel::tags), "Masking tag"); // NON-NLS
    Merged<MaskingProfile> profiles =
        LayeredEntries.merge(profileLayers(documents), "Masking profile"); // NON-NLS
    Merged<PixelMask> masks =
        LayeredEntries.merge(layers(documents, MaskingModel::masks), "Pixel mask"); // NON-NLS
    String sessionId = MaskingProfile.DISPLAY_ID;
    String aiId = MaskingProfile.AI_REQUEST_ID;
    for (Document document : documents) {
      if (document.model().sessionProfile() != null) {
        sessionId = document.model().sessionProfile();
      }
      if (document.model().aiProfile() != null) {
        aiId = document.model().aiProfile();
      }
    }
    MaskingProfile first = profiles.entries().stream().findFirst().orElse(FALLBACK);
    MaskingProfile session = resolve(profiles, sessionId, MaskingProfile.DISPLAY_ID, first);
    MaskingProfile ai = resolve(profiles, aiId, MaskingProfile.AI_REQUEST_ID, session);
    return new Model(tags, profiles, masks, session, ai, locked);
  }

  private record Document(Origin origin, MaskingModel model) {}

  private static <T extends LayeredEntries.Entry> List<Layer<T>> layers(
      List<Document> documents, Function<MaskingModel, List<T>> entries) {
    return documents.stream().map(d -> Layer.of(d.origin(), entries.apply(d.model()))).toList();
  }

  // A profile that keeps direct identifiers is refused so that a document mistake never unmasks
  private static List<Layer<MaskingProfile>> profileLayers(List<Document> documents) {
    List<Layer<MaskingProfile>> layers = new ArrayList<>();
    for (Document document : documents) {
      List<MaskingProfile> accepted = new ArrayList<>();
      for (MaskingProfile profile : document.model().profiles()) {
        if (profile.hidesDirectIdentifiers()) {
          accepted.add(profile);
        } else {
          LOGGER.error(
              "Masking profile '{}' of the {} document keeps direct identifiers and is refused",
              profile.id(),
              document.origin());
        }
      }
      layers.add(Layer.of(document.origin(), accepted));
    }
    return layers;
  }

  private static <T extends LayeredEntries.Entry> Optional<T> find(Merged<T> merged, String id) {
    return id == null
        ? Optional.empty()
        : merged.entries().stream().filter(e -> e.id().equals(id)).findFirst();
  }

  private static MaskingProfile resolve(
      Merged<MaskingProfile> profiles, String id, String defaultId, MaskingProfile fallback) {
    Optional<MaskingProfile> profile = find(profiles, id);
    if (profile.isPresent()) {
      return profile.get();
    }
    LOGGER.error("Masking profile '{}' does not exist, using '{}'", id, defaultId);
    return find(profiles, defaultId).orElse(fallback);
  }

  private static MaskingModel loadSite(Path file) {
    try {
      return MaskingModel.read(file);
    } catch (IOException | JsonException | IllegalArgumentException e) {
      LOGGER.error("Cannot read the site masking configuration: {}", file, e);
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
    for (TagRule rule : tags().entries()) {
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
