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
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
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
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.LayeredEntries;
import org.weasis.core.util.StringUtil;

/**
 * One masking configuration document: which {@link TagCategory} each tag belongs to and the {@link
 * MaskingProfile}s that decide what to do with each category. Several documents are merged by
 * {@link MaskingModelRegistry}.
 *
 * <p>Format: {@code {"schema": 1, "locked": false, "tags": [...], "profiles": [...], "masks":
 * [...], "sessionProfile": "display", "aiProfile": "ai-request"}}. Unknown fields are ignored, an
 * invalid entry is skipped with a warning, and a newer major schema is refused.
 *
 * @param tags classification rules, in document order
 * @param profiles profiles, in document order
 * @param masks pixel masks per acquisition device, in document order
 * @param sessionProfile id of the profile used by session masking, or null when not set here
 * @param aiProfile id of the profile used by the AI agent, or null when not set here
 * @param locked whether documents of a lower trust level (the user file) are ignored
 */
public record MaskingModel(
    List<TagRule> tags,
    List<MaskingProfile> profiles,
    List<PixelMask> masks,
    String sessionProfile,
    String aiProfile,
    boolean locked) {

  /** A document without pixel masks. */
  public MaskingModel(
      List<TagRule> tags,
      List<MaskingProfile> profiles,
      String sessionProfile,
      String aiProfile,
      boolean locked) {
    this(tags, profiles, List.of(), sessionProfile, aiProfile, locked);
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(MaskingModel.class);

  public static final int SCHEMA_VERSION = 1;

  /** Prefix of a Weasis-internal tag, resolved by keyword with {@link TagW#get(String)}. */
  public static final String INTERNAL_PREFIX = "weasis:"; // NON-NLS

  public static final MaskingModel EMPTY =
      new MaskingModel(List.of(), List.of(), List.of(), null, null, false);

  private static final Pattern HEX_TAG =
      Pattern.compile("\\(?\\s*([0-9A-Fa-f]{4})\\s*(?:,\\s*)?([0-9A-Fa-f]{4})\\s*\\)?");

  public MaskingModel {
    tags = List.copyOf(tags);
    profiles = List.copyOf(profiles);
    masks = List.copyOf(masks);
  }

  public MaskingModel withTags(List<TagRule> tags) {
    return new MaskingModel(tags, profiles, masks, sessionProfile, aiProfile, locked);
  }

  public MaskingModel withProfiles(List<MaskingProfile> profiles) {
    return new MaskingModel(tags, profiles, masks, sessionProfile, aiProfile, locked);
  }

  public MaskingModel withMasks(List<PixelMask> masks) {
    return new MaskingModel(tags, profiles, masks, sessionProfile, aiProfile, locked);
  }

  public MaskingModel withDefaults(String sessionProfile, String aiProfile) {
    return new MaskingModel(tags, profiles, masks, sessionProfile, aiProfile, locked);
  }

  /**
   * Classification of one tag.
   *
   * @param key normalized tag key, see {@link #normalizeKey(String, String)}
   */
  public record TagRule(String key, TagCategory category) implements LayeredEntries.Entry {

    public TagRule {
      Objects.requireNonNull(key);
      Objects.requireNonNull(category);
    }

    @Override
    public String id() {
      return key;
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

  /** Writes the document where {@link #read(Path)} can read it back. */
  public void write(Path file) throws IOException {
    JsonUtil.write(file, toJson());
  }

  /** The document as JSON, in the format {@link #read(InputStream)} accepts. */
  public JsonObject toJson() {
    JsonObjectBuilder root = Json.createObjectBuilder().add("schema", SCHEMA_VERSION); // NON-NLS
    if (locked) {
      root.add("locked", true); // NON-NLS
    }
    if (!tags.isEmpty()) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      tags.forEach(rule -> array.add(toJson(rule)));
      root.add("tags", array); // NON-NLS
    }
    if (!profiles.isEmpty()) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      profiles.forEach(profile -> array.add(toJson(profile)));
      root.add("profiles", array); // NON-NLS
    }
    if (!masks.isEmpty()) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      masks.forEach(mask -> array.add(toJson(mask)));
      root.add("masks", array); // NON-NLS
    }
    JsonUtil.addIfPresent(root, "sessionProfile", sessionProfile); // NON-NLS
    JsonUtil.addIfPresent(root, "aiProfile", aiProfile); // NON-NLS
    return root.build();
  }

  private static JsonObject toJson(TagRule rule) {
    // The key holds the private creator after '@', where the reader expects it back
    return Json.createObjectBuilder()
        .add("tag", rule.key()) // NON-NLS
        .add("category", rule.category().name()) // NON-NLS
        .build();
  }

  private static JsonObject toJson(MaskingProfile profile) {
    JsonObjectBuilder entry =
        Json.createObjectBuilder()
            .add("id", profile.id()) // NON-NLS
            .add("name", profile.name()); // NON-NLS
    addMap(entry, "label", profile.labels()); // NON-NLS
    JsonObjectBuilder actions = Json.createObjectBuilder();
    // Categories in their declaration order, so two writes of the same profile are identical
    for (TagCategory category : TagCategory.values()) {
      AnonymizationAction action = profile.actions().get(category);
      if (action != null) {
        actions.add(category.name(), action.name());
      }
    }
    entry.add("actions", actions); // NON-NLS
    Map<String, String> tagActions = new LinkedHashMap<>();
    profile.tagActions().forEach((tag, action) -> tagActions.put(tag, action.name()));
    addMap(entry, "tagActions", tagActions); // NON-NLS
    if (profile.shiftsDates()) {
      entry.add("shiftsDates", true); // NON-NLS
    }
    if (profile.offerInExport()) {
      entry.add("offerInExport", true); // NON-NLS
    }
    if (profile.locked()) {
      entry.add("locked", true); // NON-NLS
    }
    return entry.build();
  }

  private static JsonObject toJson(PixelMask mask) {
    JsonObjectBuilder entry =
        Json.createObjectBuilder()
            .add("id", mask.id()) // NON-NLS
            .add("name", mask.name()); // NON-NLS
    JsonObjectBuilder match = Json.createObjectBuilder();
    JsonUtil.addIfPresent(match, "modality", mask.match().modality()); // NON-NLS
    JsonUtil.addIfPresent(match, "stationName", mask.match().stationName()); // NON-NLS
    JsonUtil.addIfPresent(match, "manufacturer", mask.match().manufacturer()); // NON-NLS
    JsonUtil.addIfPresent(match, "modelName", mask.match().modelName()); // NON-NLS
    JsonUtil.addIfPresent(match, "institutionName", mask.match().institutionName()); // NON-NLS
    entry.add("match", match); // NON-NLS
    entry.add(
        "reference", // NON-NLS
        Json.createObjectBuilder()
            .add("columns", mask.reference().columns()) // NON-NLS
            .add("rows", mask.reference().rows())); // NON-NLS
    JsonArrayBuilder regions = Json.createArrayBuilder();
    mask.regions().forEach(region -> regions.add(toJson(region)));
    entry.add("regions", regions); // NON-NLS
    if (!mask.profiles().isEmpty()) {
      JsonArrayBuilder profileIds = Json.createArrayBuilder();
      mask.profiles().forEach(profileIds::add);
      entry.add("profiles", profileIds); // NON-NLS
    }
    if (!mask.enabled()) {
      entry.add("enabled", false); // NON-NLS
    }
    if (mask.locked()) {
      entry.add("locked", true); // NON-NLS
    }
    return entry.build();
  }

  private static JsonObject toJson(MaskRegion region) {
    JsonObjectBuilder entry =
        Json.createObjectBuilder()
            .add("type", region.kind()) // NON-NLS
            .add("category", region.category().name()); // NON-NLS
    switch (region) {
      case MaskRegion.Rect rect -> addBox(entry, rect.x(), rect.y(), rect.w(), rect.h());
      case MaskRegion.Ellipse e -> addBox(entry, e.x(), e.y(), e.w(), e.h());
      case MaskRegion.Polygon polygon -> {
        JsonArrayBuilder points = Json.createArrayBuilder();
        polygon.points().forEach(value -> points.add(JsonUtil.decimal(value.floatValue())));
        entry.add("points", points); // NON-NLS
      }
    }
    return entry.build();
  }

  private static void addBox(JsonObjectBuilder entry, double x, double y, double w, double h) {
    entry.add("x", JsonUtil.decimal((float) x)); // NON-NLS
    entry.add("y", JsonUtil.decimal((float) y)); // NON-NLS
    entry.add("w", JsonUtil.decimal((float) w)); // NON-NLS
    entry.add("h", JsonUtil.decimal((float) h)); // NON-NLS
  }

  private static void addMap(JsonObjectBuilder entry, String name, Map<String, String> values) {
    if (values.isEmpty()) {
      return;
    }
    JsonObjectBuilder builder = Json.createObjectBuilder();
    new TreeMap<>(values).forEach(builder::add);
    entry.add(name, builder);
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
    String creator = StringUtil.hasText(privateCreator) ? privateCreator.trim() : null; // NOSONAR
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
    List<PixelMask> masks = new ArrayList<>();
    for (JsonObject entry : JsonUtil.objects(array(root, "masks"))) { // NON-NLS
      readMask(entry, masks);
    }
    return new MaskingModel(
        tags,
        profiles,
        masks,
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
            JsonUtil.getBoolean(entry, "offerInExport", false), // NON-NLS
            JsonUtil.getBoolean(entry, "locked", false))); // NON-NLS
  }

  private static void readMask(JsonObject entry, List<PixelMask> masks) {
    String id = text(entry, "id"); // NON-NLS
    JsonObject reference = entry.get("reference") instanceof JsonObject o ? o : null; // NON-NLS
    if (id == null || reference == null) {
      LOGGER.warn("Skipping pixel mask without id or reference size: {}", entry);
      return;
    }
    try {
      List<MaskRegion> regions = new ArrayList<>();
      for (JsonObject region : JsonUtil.objects(array(entry, "regions"))) { // NON-NLS
        readRegion(region, regions, id);
      }
      JsonObject match = entry.get("match") instanceof JsonObject o ? o : null; // NON-NLS
      masks.add(
          new PixelMask(
              id,
              text(entry, "name"), // NON-NLS
              match == null
                  ? PixelMask.DeviceKey.ANY
                  : new PixelMask.DeviceKey(
                      text(match, "modality"), // NON-NLS
                      text(match, "stationName"), // NON-NLS
                      text(match, "manufacturer"), // NON-NLS
                      text(match, "modelName"), // NON-NLS
                      text(match, "institutionName")), // NON-NLS
              new PixelMask.Reference(
                  JsonUtil.getInt(reference, "columns", 0), // NON-NLS
                  JsonUtil.getInt(reference, "rows", 0)), // NON-NLS
              regions,
              JsonUtil.getStringList(entry, "profiles"), // NON-NLS
              JsonUtil.getBoolean(entry, "enabled", true), // NON-NLS
              JsonUtil.getBoolean(entry, "locked", false))); // NON-NLS
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Skipping invalid pixel mask {}: {}", id, e.getMessage());
    }
  }

  private static void readRegion(JsonObject entry, List<MaskRegion> regions, String maskId) {
    String kind = text(entry, "type"); // NON-NLS
    TagCategory category = parse(TagCategory.class, text(entry, "category")); // NON-NLS
    if (category == null) {
      category = TagCategory.DIRECT_ID;
    }
    try {
      switch (kind == null ? "" : kind.toLowerCase(Locale.ROOT)) {
        case MaskRegion.RECT -> regions.add(rect(entry, category, false));
        case MaskRegion.ELLIPSE -> regions.add(rect(entry, category, true));
        case MaskRegion.POLYGON ->
            regions.add(
                new MaskRegion.Polygon(
                    JsonUtil.getNumberList(entry, "points"), category)); // NON-NLS
        default ->
            LOGGER.warn("Skipping region of unknown type in pixel mask {}: {}", maskId, kind);
      }
    } catch (IllegalArgumentException e) {
      LOGGER.warn("Skipping invalid region in pixel mask {}: {}", maskId, e.getMessage());
    }
  }

  private static MaskRegion rect(JsonObject entry, TagCategory category, boolean ellipse) {
    double x = number(entry, "x"); // NON-NLS
    double y = number(entry, "y"); // NON-NLS
    double w = number(entry, "w"); // NON-NLS
    double h = number(entry, "h"); // NON-NLS
    if (w <= 0 || h <= 0) {
      throw new IllegalArgumentException("A region needs a positive width and height");
    }
    return ellipse
        ? new MaskRegion.Ellipse(x, y, w, h, category)
        : new MaskRegion.Rect(x, y, w, h, category);
  }

  /** Normalized coordinates are read as written, without going through a float. */
  private static double number(JsonObject entry, String name) {
    return entry.get(name) instanceof JsonNumber value ? value.doubleValue() : 0;
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
