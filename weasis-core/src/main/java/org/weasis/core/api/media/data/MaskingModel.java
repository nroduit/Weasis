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

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonException;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.StringUtil;

/**
 * One masking configuration document: which {@link TagCategory} each tag belongs to and the {@link
 * MaskingProfile}s that decide what to do with each category. Several documents are merged by
 * {@link MaskingModelRegistry}.
 *
 * <p>Format: {@code {"schema": 1, "locked": false, "tags": [...], "profiles": [...],
 * "sessionProfile": "display", "aiProfile": "ai-request"}}. Unknown fields are ignored, an invalid
 * entry is skipped with a warning, and a newer major schema is refused.
 *
 * @param tags classification rules, in document order
 * @param profiles profiles, in document order
 * @param sessionProfile id of the profile used by session masking, or null when not set here
 * @param aiProfile id of the profile used by the AI agent, or null when not set here
 * @param locked whether documents of a lower trust level (the user file) are ignored
 */
public record MaskingModel(
    List<TagRule> tags,
    List<MaskingProfile> profiles,
    String sessionProfile,
    String aiProfile,
    boolean locked) {

  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingModel.class);

  public static final int SCHEMA_VERSION = 1;

  /** Prefix of a Weasis-internal tag, resolved by keyword with {@link TagW#get(String)}. */
  public static final String INTERNAL_PREFIX = "weasis:"; // NON-NLS

  public static final MaskingModel EMPTY =
      new MaskingModel(List.of(), List.of(), null, null, false);

  private static final Pattern HEX_TAG =
      Pattern.compile("\\(?\\s*([0-9A-Fa-f]{4})\\s*,?\\s*([0-9A-Fa-f]{4})\\s*\\)?");

  public MaskingModel {
    tags = List.copyOf(tags);
    profiles = List.copyOf(profiles);
  }

  /**
   * Classification of one tag.
   *
   * @param key normalized tag key, see {@link #normalizeKey(String, String)}
   */
  public record TagRule(String key, TagCategory category) {

    public TagRule {
      Objects.requireNonNull(key);
      Objects.requireNonNull(category);
    }

    public boolean isInternal() {
      return key.startsWith(INTERNAL_PREFIX);
    }

    /** The keyword of an internal tag, or of a DICOM tag given by keyword; null for a hex key. */
    public String keyword() {
      if (isInternal()) {
        return key.substring(INTERNAL_PREFIX.length());
      }
      return tagId().isPresent() ? null : key;
    }

    /** The DICOM tag number of a key given in hexadecimal. */
    public OptionalInt tagId() {
      if (key.length() < 8 || isInternal()) {
        return OptionalInt.empty();
      }
      String hex = key.substring(0, 8);
      return hex.chars().allMatch(c -> Character.digit(c, 16) >= 0)
              && (key.length() == 8 || key.charAt(8) == '@')
          ? OptionalInt.of(Integer.parseUnsignedInt(hex, 16))
          : OptionalInt.empty();
    }

    /** The private creator of a private tag key, or null. */
    public String privateCreator() {
      int at = key.indexOf('@');
      return at < 0 ? null : key.substring(at + 1);
    }
  }

  /**
   * Normalizes a tag written in a configuration file: a DICOM keyword is kept as is, {@code
   * (gggg,eeee)} or {@code ggggeeee} becomes {@code GGGGEEEE}, followed by {@code @creator} for a
   * private tag, and {@code weasis:keyword} is kept as is.
   *
   * @return the key, or null when the tag is empty or a private tag has no creator
   */
  public static String normalizeKey(String tag, String privateCreator) {
    if (!StringUtil.hasText(tag)) {
      return null;
    }
    String text = tag.trim();
    String creator = StringUtil.hasText(privateCreator) ? privateCreator.trim() : null;
    int at = text.lastIndexOf('@');
    if (at > 0 && creator == null) {
      creator = text.substring(at + 1).trim();
      text = text.substring(0, at).trim();
    }
    Matcher matcher = HEX_TAG.matcher(text);
    if (!matcher.matches()) {
      return text;
    }
    String group = matcher.group(1).toUpperCase(Locale.ROOT);
    String hex = group + matcher.group(2).toUpperCase(Locale.ROOT);
    boolean privateTag = (Integer.parseInt(group, 16) & 1) == 1;
    if (!privateTag) {
      return hex;
    }
    // A private element number means nothing without its creator (PS3.5 section 7.8)
    return StringUtil.hasText(creator) ? hex + "@" + creator : null;
  }

  public static MaskingModel read(Path path) throws IOException {
    try (InputStream in = Files.newInputStream(path)) {
      return read(in);
    } catch (JsonException | IllegalArgumentException e) {
      throw new IOException("Invalid masking configuration: " + path, e);
    }
  }

  /**
   * @throws IllegalArgumentException when the schema is newer than {@link #SCHEMA_VERSION}
   * @throws JsonException when the content is not a JSON object
   */
  public static MaskingModel read(InputStream in) {
    JsonObject root;
    try (JsonReader reader = Json.createReader(in)) {
      root = reader.readObject();
    }
    int schema = JsonUtil.getInt(root, "schema", SCHEMA_VERSION); // NON-NLS
    if (schema > SCHEMA_VERSION) {
      throw new IllegalArgumentException(
          "Masking schema %d is newer than the supported %d".formatted(schema, SCHEMA_VERSION));
    }
    List<TagRule> tags = new ArrayList<>();
    for (JsonObject entry : JsonUtil.objects(array(root, "tags"))) { // NON-NLS
      readTag(entry, tags);
    }
    List<MaskingProfile> profiles = new ArrayList<>();
    for (JsonObject entry : JsonUtil.objects(array(root, "profiles"))) { // NON-NLS
      readProfile(entry, profiles);
    }
    return new MaskingModel(
        tags,
        profiles,
        text(root, "sessionProfile"), // NON-NLS
        text(root, "aiProfile"), // NON-NLS
        JsonUtil.getBoolean(root, "locked", false)); // NON-NLS
  }

  private static void readTag(JsonObject entry, List<TagRule> tags) {
    String tag = text(entry, "tag"); // NON-NLS
    String key = normalizeKey(tag, text(entry, "privateCreator")); // NON-NLS
    TagCategory category = parse(TagCategory.class, text(entry, "category")); // NON-NLS
    if (key == null || category == null) {
      LOGGER.warn("Skipping invalid masking tag entry: {}", entry);
      return;
    }
    tags.add(new TagRule(key, category));
  }

  private static void readProfile(JsonObject entry, List<MaskingProfile> profiles) {
    String id = text(entry, "id"); // NON-NLS
    if (id == null) {
      LOGGER.warn("Skipping masking profile without id: {}", entry);
      return;
    }
    Map<TagCategory, AnonymizationAction> actions = new EnumMap<>(TagCategory.class);
    JsonUtil.getStringMap(entry, "actions") // NON-NLS
        .forEach(
            (category, action) ->
                putValid(actions, parse(TagCategory.class, category), action, id));
    Map<String, AnonymizationAction> tagActions = new LinkedHashMap<>();
    JsonUtil.getStringMap(entry, "tagActions") // NON-NLS
        .forEach((tag, action) -> putValid(tagActions, normalizeKey(tag, null), action, id));
    String name = text(entry, "name"); // NON-NLS
    profiles.add(
        new MaskingProfile(
            id,
            name == null ? id : name,
            JsonUtil.getStringMap(entry, "label"), // NON-NLS
            actions,
            tagActions,
            JsonUtil.getBoolean(entry, "shiftsDates", false), // NON-NLS
            JsonUtil.getBoolean(entry, "offerInExport", false))); // NON-NLS
  }

  private static <K> void putValid(
      Map<K, AnonymizationAction> map, K key, String action, String profileId) {
    AnonymizationAction value = parse(AnonymizationAction.class, action);
    if (key == null || value == null) {
      LOGGER.warn("Skipping invalid action in masking profile {}: {}", profileId, action);
      return;
    }
    map.put(key, value);
  }

  private static <E extends Enum<E>> E parse(Class<E> type, String name) {
    if (name == null) {
      return null;
    }
    try {
      return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  /** The member as an array, or null when absent or of another type (logged, not thrown). */
  private static JsonArray array(JsonObject json, String name) {
    JsonValue value = json.get(name);
    if (value == null || value instanceof JsonArray) {
      return (JsonArray) value;
    }
    LOGGER.warn("Ignoring masking member '{}': not an array", name);
    return null;
  }

  private static String text(JsonObject json, String name) {
    return json.get(name) instanceof JsonString value && StringUtil.hasText(value.getString())
        ? value.getString().trim()
        : null;
  }
}
