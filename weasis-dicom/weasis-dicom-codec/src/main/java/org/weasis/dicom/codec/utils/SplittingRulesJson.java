/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.utils;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.JsonExtends;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.CompositeCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition.Type;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Or;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Rule;

/**
 * The JSON form of the series splitting rules, the site document {@value SplittingRules#SITE_FILE}:
 * a {@link ListDocument} whose {@value #ENTRIES} are one object per modality. The entry {@code id}
 * is the modality name, {@code splittingTags} the attributes whose change opens a new sub-series
 * and {@code multiframeSplittingTags} the same for the frames of a multi-frame file. Every tag is
 * an object {@code {"tag": "<keyword>"}}, with a {@code condition} when the split only applies
 * under one. A condition is a group, {@code allOf} or {@code anyOf}, nestable, of leaves that
 * compare one attribute with values:
 *
 * <pre>{@code
 * {"id": "CT", "extends": "DEFAULT",
 *  "splittingTags": [{"tag": "ImageType"}, {"tag": "SOPClassUID"}, {"tag": "ContrastBolusAgent"},
 *    {"tag": "ConvolutionKernel"},
 *    {"tag": "ImageOrientationPlane",
 *     "condition": {"allOf": [{"tag": "ImageType", "operator": "contains", "negate": true,
 *                              "ignoreCase": true, "values": ["PROJECTION"]}]}}]}
 * }</pre>
 *
 * <p>A leaf has {@code operator} {@code equals} or {@code contains}, {@code negate} and {@code
 * ignoreCase} (false when absent) and {@code values}, several meaning a multi-valued attribute for
 * {@code equals} and only the first being compared for {@code contains}.
 *
 * <p>{@code "extends"} starts from the lists of another modality of the document, or of the
 * built-in defaults, and a list given here replaces the whole list of the base (see {@link
 * JsonExtends}). The legacy {@code series-splitting-rules.xml} appended its tags to the inherited
 * list; {@link #convert} writes the inherited tags first so that both forms read into the same
 * {@link SplittingModalityRules}.
 *
 * <p>The readers also accept the first shape of the document: a tag given as a bare keyword, and a
 * leaf given as {@code "type"}, one of the {@link Type} names, with a string {@code "value"} whose
 * values are separated by {@code \}. The writers only write the shape above.
 *
 * <p>A site entry carrying {@code "locked": true} is honoured: {@link Document#locked()} lists it,
 * so that no later layer replaces it. {@code hidden} is not applicable to a modality entry, a
 * modality always has rules, and is ignored.
 */
public final class SplittingRulesJson {
  private static final Logger LOGGER = LoggerFactory.getLogger(SplittingRulesJson.class);

  /** The name of the array of entries in the envelope. */
  public static final String ENTRIES = "modalities"; // NON-NLS

  static final String ID = JsonExtends.ID;
  static final String SPLITTING_TAGS = "splittingTags"; // NON-NLS
  static final String MULTIFRAME_SPLITTING_TAGS = "multiframeSplittingTags"; // NON-NLS
  static final String TAG = "tag"; // NON-NLS
  static final String CONDITION = "condition"; // NON-NLS
  static final String ALL_OF = "allOf"; // NON-NLS
  static final String ANY_OF = "anyOf"; // NON-NLS
  static final String OPERATOR = "operator"; // NON-NLS
  static final String NEGATE = "negate"; // NON-NLS
  static final String IGNORE_CASE = "ignoreCase"; // NON-NLS
  static final String VALUES = "values"; // NON-NLS
  static final String EQUALS = "equals"; // NON-NLS
  static final String CONTAINS = "contains"; // NON-NLS
  static final String WHAT = "Splitting rules"; // NON-NLS

  /** First shape: the {@link Type} name. */
  static final String LEGACY_TYPE = "type"; // NON-NLS

  /** First shape: the values as one string. */
  static final String LEGACY_VALUE = "value"; // NON-NLS

  /** Separates the values of a multi-valued attribute, as in DICOM. */
  private static final String VALUE_SEPARATOR = "\\"; // NON-NLS

  private static final Pattern VALUE_SPLIT = Pattern.compile(Pattern.quote(VALUE_SEPARATOR));
  private static final String NOT = "not"; // NON-NLS
  private static final String IGNORE_CASE_SUFFIX = "IgnoreCase"; // NON-NLS

  /**
   * A document read: the entries in order and the ids (modality names) the document locks.
   *
   * @param entries the rules of the document
   * @param locked the modality names of the entries flagged {@code locked}
   */
  public record Document(List<SplittingModalityRules> entries, Set<String> locked) {
    public static final Document EMPTY = new Document(List.of(), Set.of());

    public Document {
      entries = List.copyOf(entries);
      locked = Set.copyOf(locked);
    }
  }

  private SplittingRulesJson() {}

  /**
   * The entry of a modality, fully resolved: its lists, without {@code extends}, so that it can
   * serve as the base of another entry.
   */
  public static JsonObject toJson(SplittingModalityRules rules) {
    JsonObjectBuilder b = Json.createObjectBuilder().add(ID, rules.getModality().name());
    b.add(SPLITTING_TAGS, rules(rules.getSingleFrameRules()));
    b.add(MULTIFRAME_SPLITTING_TAGS, rules(rules.getMultiFrameRules()));
    return b.build();
  }

  /**
   * @throws IllegalArgumentException when the modality of the entry is unknown
   */
  public static SplittingModalityRules fromJson(JsonObject json) {
    String id = json.getString(ID, null);
    Modality modality = SplittingRules.getModality(id);
    if (modality == null) {
      throw new IllegalArgumentException("Unknown modality '%s'".formatted(id));
    }
    SplittingModalityRules rules = new SplittingModalityRules(modality);
    for (Rule rule : rules(id, json.get(SPLITTING_TAGS))) {
      rules.addSingleFrameTags(rule.getTag(), rule.getCondition());
    }
    for (Rule rule : rules(id, json.get(MULTIFRAME_SPLITTING_TAGS))) {
      rules.addMultiFrameTags(rule.getTag(), rule.getCondition());
    }
    return rules;
  }

  /**
   * Reads a document, an envelope or a bare array, resolving {@code extends} against the document
   * first, then {@code lowerBase}.
   *
   * @throws IOException when the file cannot be read, is not JSON or has a newer schema
   */
  public static Document read(Path file, Function<String, Optional<JsonObject>> lowerBase)
      throws IOException {
    return parse(ListDocument.read(file, ENTRIES).entries(), lowerBase);
  }

  /** As {@link #read(Path, Function)}, from a stream. */
  public static Document read(InputStream in, Function<String, Optional<JsonObject>> lowerBase) {
    return parse(ListDocument.read(in, ENTRIES).entries(), lowerBase);
  }

  /**
   * The entries, {@code extends} resolved; an entry of an unknown modality is skipped with an
   * error, the others are kept.
   */
  public static Document parse(
      List<JsonObject> objects, Function<String, Optional<JsonObject>> lowerBase) {
    List<SplittingModalityRules> entries = new ArrayList<>();
    Set<String> locked = new HashSet<>();
    for (JsonObject json : JsonExtends.resolve(objects, lowerBase, WHAT)) {
      try {
        SplittingModalityRules rules = fromJson(json);
        entries.add(rules);
        if (JsonUtil.getBoolean(json, JsonExtends.LOCKED, false)) {
          locked.add(rules.getModality().name());
        }
      } catch (IllegalArgumentException e) {
        LOGGER.error("{} entry skipped: {}", WHAT, e.getMessage());
      }
    }
    return new Document(entries, locked);
  }

  // ── legacy XML ──

  /**
   * Converts the legacy {@code series-splitting-rules.xml} into JSON entries. A modality with an
   * {@code extend} attribute becomes an entry with {@code "extends"}, and each list it gives is
   * written whole: the tags of the base (an earlier modality of the file, else a built-in default)
   * followed by its own, as the XML format documents it.
   *
   * @throws IOException when the file cannot be read or is not well-formed
   */
  public static List<JsonObject> convert(Path xml) throws IOException {
    return convert(xml, SplittingRules.builtInBases());
  }

  /**
   * @param lowerBase the base of an {@code extend} that names no earlier modality of the file
   */
  public static List<JsonObject> convert(Path xml, Function<String, Optional<JsonObject>> lowerBase)
      throws IOException {
    List<XmlModality> modalities = readXml(xml);
    // The resolved lists of the converted modalities, the base of a later extend
    Map<String, JsonObject> resolved = new LinkedHashMap<>();
    List<JsonObject> result = new ArrayList<>(modalities.size());
    for (XmlModality modality : modalities) {
      JsonObject base = null;
      String extend = modality.extend();
      if (StringUtil.hasText(extend)) {
        base = resolved.get(extend);
        if (base == null) {
          base = lowerBase.apply(extend).orElse(null);
        }
        if (base == null) {
          LOGGER.warn(
              "{} '{}' extends '{}', which is not declared before: nothing inherited",
              WHAT,
              modality.name(),
              extend);
        }
      }
      JsonObjectBuilder b = Json.createObjectBuilder().add(ID, modality.name());
      JsonObjectBuilder full = Json.createObjectBuilder().add(ID, modality.name());
      if (base != null) {
        b.add(JsonExtends.KEY, extend);
      }
      for (String list : List.of(SPLITTING_TAGS, MULTIFRAME_SPLITTING_TAGS)) {
        JsonArray inherited = base != null && base.get(list) instanceof JsonArray a ? a : null;
        JsonArray own = modality.lists().get(list);
        if (own != null) {
          JsonArrayBuilder merged = Json.createArrayBuilder();
          if (inherited != null) {
            inherited.forEach(merged::add);
          }
          own.forEach(merged::add);
          JsonArray array = merged.build();
          b.add(list, array);
          full.add(list, array);
        } else if (inherited != null) {
          full.add(list, inherited);
        }
      }
      resolved.put(modality.name(), full.build());
      result.add(b.build());
    }
    return List.copyOf(result);
  }

  private record XmlModality(String name, String extend, Map<String, JsonArray> lists) {}

  private static List<XmlModality> readXml(Path xml) throws IOException {
    List<XmlModality> modalities = new ArrayList<>();
    XMLStreamReader xmler = null;
    try (InputStream in = Files.newInputStream(xml)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      // disable external entities for security
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      xmler = factory.createXMLStreamReader(in);
      while (xmler.hasNext()) {
        if (xmler.next() == XMLStreamConstants.START_ELEMENT
            && "modality".equals(xmler.getName().getLocalPart()) // NON-NLS
            && xmler.getAttributeCount() >= 1) {
          String name = xmler.getAttributeValue(null, "name"); // NON-NLS
          String extend = xmler.getAttributeValue(null, "extend"); // NON-NLS
          if (StringUtil.hasText(name)) {
            modalities.add(new XmlModality(name, extend, readModality(xmler)));
          }
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid document " + xml, e);
    } finally {
      StreamUtil.safeClose(xmler);
    }
    return modalities;
  }

  private static Map<String, JsonArray> readModality(XMLStreamReader xmler)
      throws XMLStreamException {
    Map<String, JsonArray> lists = new LinkedHashMap<>();
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.START_ELEMENT -> {
          String element = xmler.getName().getLocalPart();
          if (SPLITTING_TAGS.equals(element) || MULTIFRAME_SPLITTING_TAGS.equals(element)) {
            lists.put(element, readTags(xmler, element));
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if ("modality".equals(xmler.getName().getLocalPart())) { // NON-NLS
            return lists;
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return lists;
  }

  // Each child element is a tag keyword, holding its optional <conditions>
  private static JsonArray readTags(XMLStreamReader xmler, String endElement)
      throws XMLStreamException {
    JsonArrayBuilder tags = Json.createArrayBuilder();
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.START_ELEMENT -> {
          String keyword = xmler.getName().getLocalPart();
          JsonObject condition = readTag(xmler, keyword);
          JsonObjectBuilder item = Json.createObjectBuilder().add(TAG, keyword);
          if (condition != null) {
            item.add(CONDITION, condition);
          }
          tags.add(item);
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if (endElement.equals(xmler.getName().getLocalPart())) {
            return tags.build();
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return tags.build();
  }

  /** The root {@code <conditions>} of the tag element, as JSON, or null when it has none. */
  private static JsonObject readTag(XMLStreamReader xmler, String endElement)
      throws XMLStreamException {
    JsonObject root = null;
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.START_ELEMENT -> {
          if ("conditions".equals(xmler.getName().getLocalPart())) { // NON-NLS
            JsonObject group = readConditions(xmler);
            if (root == null) {
              root = group;
            }
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          if (endElement.equals(xmler.getName().getLocalPart())) {
            return root;
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return root;
  }

  // Called on the <conditions> start element, returns on its end element
  private static JsonObject readConditions(XMLStreamReader xmler) throws XMLStreamException {
    String group =
        "anyOf".equals(xmler.getAttributeValue(null, LEGACY_TYPE)) ? ANY_OF : ALL_OF; // NON-NLS
    JsonArrayBuilder children = Json.createArrayBuilder();
    String tag = null;
    String type = null;
    StringBuilder value = null;
    while (xmler.hasNext()) {
      switch (xmler.next()) {
        case XMLStreamConstants.CHARACTERS -> {
          if (value != null) {
            value.append(xmler.getText());
          }
        }
        case XMLStreamConstants.START_ELEMENT -> {
          String element = xmler.getName().getLocalPart();
          if ("condition".equals(element)) { // NON-NLS
            tag = xmler.getAttributeValue(null, TAG);
            type = xmler.getAttributeValue(null, LEGACY_TYPE);
            value = new StringBuilder();
          } else if ("conditions".equals(element)) { // NON-NLS
            children.add(readConditions(xmler));
          }
        }
        case XMLStreamConstants.END_ELEMENT -> {
          String element = xmler.getName().getLocalPart();
          if ("condition".equals(element)) { // NON-NLS
            children.add(
                leaf(
                    tag,
                    SplittingRules.getConditionType(type),
                    value == null ? null : value.toString()));
            tag = null;
            type = null;
            value = null;
          } else if ("conditions".equals(element)) { // NON-NLS
            return Json.createObjectBuilder().add(group, children).build();
          }
        }
        default -> {
          // nothing to do
        }
      }
    }
    return Json.createObjectBuilder().add(group, children).build();
  }

  // ── writing ──

  private static JsonArray rules(List<Rule> rules) {
    JsonArrayBuilder array = Json.createArrayBuilder();
    for (Rule rule : rules) {
      JsonObjectBuilder item = Json.createObjectBuilder().add(TAG, rule.getTag().getKeyword());
      if (rule.getCondition() != null) {
        item.add(CONDITION, condition(rule.getCondition()));
      }
      array.add(item);
    }
    return array.build();
  }

  private static JsonValue condition(Condition condition) {
    return switch (condition) {
      case And and -> group(ALL_OF, and);
      case Or or -> group(ANY_OF, or);
      case DefaultCondition c ->
          leaf(c.tag == null ? null : c.tag.getKeyword(), c.type, value(c.object));
      default -> {
        LOGGER.warn("{}: condition {} cannot be written", WHAT, condition.getClass().getName());
        yield JsonValue.NULL;
      }
    };
  }

  private static JsonObject group(String name, CompositeCondition composite) {
    JsonArrayBuilder children = Json.createArrayBuilder();
    for (Condition child : composite.childs) {
      JsonValue value = condition(child);
      if (value != JsonValue.NULL) {
        children.add(value);
      }
    }
    return Json.createObjectBuilder().add(name, children).build();
  }

  /**
   * A leaf as the three controls of the editor: the operator, negated or not, case sensitive or
   * not, and the values.
   *
   * @param values the values as one string, separated by {@value #VALUE_SEPARATOR}, or null
   */
  private static JsonObject leaf(String keyword, Type type, String values) {
    JsonObjectBuilder b = Json.createObjectBuilder();
    JsonUtil.addIfPresent(b, TAG, keyword);
    if (type != null) {
      String name = type.name();
      boolean negate = name.startsWith(NOT);
      boolean ignoreCase = name.endsWith(IGNORE_CASE_SUFFIX);
      b.add(OPERATOR, name.toLowerCase(Locale.ROOT).contains(CONTAINS) ? CONTAINS : EQUALS);
      if (negate) {
        b.add(NEGATE, true);
      }
      if (ignoreCase) {
        b.add(IGNORE_CASE, true);
      }
    }
    JsonArrayBuilder array = Json.createArrayBuilder();
    if (StringUtil.hasText(values)) {
      Arrays.stream(VALUE_SPLIT.split(values.trim(), -1)).forEach(array::add);
    }
    b.add(VALUES, array);
    return b.build();
  }

  /** The value a condition was read from: several values joined by {@value #VALUE_SEPARATOR}. */
  private static String value(Object object) {
    if (object == null) {
      return null;
    }
    if (object.getClass().isArray()) {
      return IntStream.range(0, Array.getLength(object))
          .mapToObj(i -> String.valueOf(Array.get(object, i)))
          .collect(Collectors.joining(VALUE_SEPARATOR));
    }
    return String.valueOf(object);
  }

  // ── reading ──

  private static List<Rule> rules(String id, JsonValue value) {
    List<Rule> rules = new ArrayList<>();
    if (!(value instanceof JsonArray array)) {
      return rules;
    }
    for (JsonValue item : array) {
      String keyword;
      Condition condition = null;
      switch (item) {
        case JsonString text -> keyword = text.getString();
        case JsonObject object -> {
          keyword = object.getString(TAG, null);
          condition = condition(id, object.get(CONDITION));
        }
        default -> {
          LOGGER.warn("{} '{}': a tag is neither an object nor a keyword, ignored", WHAT, id);
          continue;
        }
      }
      TagW tag = tag(id, keyword);
      if (tag != null) {
        rules.add(new Rule(tag, condition));
      }
    }
    return rules;
  }

  private static Condition condition(String id, JsonValue value) {
    if (!(value instanceof JsonObject json)) {
      return null;
    }
    if (json.containsKey(ALL_OF) || json.containsKey(ANY_OF)) {
      boolean any = json.containsKey(ANY_OF);
      CompositeCondition group = any ? new Or() : new And();
      if (json.get(any ? ANY_OF : ALL_OF) instanceof JsonArray children) {
        for (JsonValue child : children) {
          Condition condition = condition(id, child);
          if (condition != null) {
            group.addChild(condition);
          }
        }
      }
      return group;
    }
    TagW tag = tag(id, json.getString(TAG, null));
    Type type = type(id, json);
    if (tag == null || type == null) {
      LOGGER.error("{} '{}': a condition needs a tag and an operator, ignored", WHAT, id);
      return null;
    }
    return new DefaultCondition(tag, type, values(json));
  }

  /** The {@link Type} of the operator, negate and ignoreCase; of the legacy {@code type} else. */
  private static Type type(String id, JsonObject json) {
    String operator = json.getString(OPERATOR, null);
    if (StringUtil.hasText(operator)) {
      String base =
          switch (operator.trim().toLowerCase(Locale.ROOT)) {
            case EQUALS -> EQUALS;
            case CONTAINS -> CONTAINS;
            default -> null;
          };
      if (base == null) {
        LOGGER.error("{} '{}': '{}' is not a valid operator", WHAT, id, operator);
        return null;
      }
      boolean negate = JsonUtil.getBoolean(json, NEGATE, false);
      boolean ignoreCase = JsonUtil.getBoolean(json, IGNORE_CASE, false);
      String name =
          (negate ? NOT + Character.toUpperCase(base.charAt(0)) + base.substring(1) : base)
              + (ignoreCase ? IGNORE_CASE_SUFFIX : "");
      return Type.valueOf(name);
    }
    String legacy = json.getString(LEGACY_TYPE, null);
    return legacy == null ? null : SplittingRules.getConditionType(legacy);
  }

  /**
   * The values as one string, joined by {@value #VALUE_SEPARATOR}; the legacy {@code value} else.
   */
  private static String values(JsonObject json) {
    JsonValue value = json.containsKey(VALUES) ? json.get(VALUES) : json.get(LEGACY_VALUE);
    return switch (value) {
      case null -> null;
      case JsonString text -> text.getString();
      case JsonArray array ->
          array.stream()
              .filter(v -> v != JsonValue.NULL)
              .map(v -> v instanceof JsonString s ? s.getString() : v.toString())
              .collect(Collectors.joining(VALUE_SEPARATOR));
      default -> value.toString();
    };
  }

  private static TagW tag(String id, String keyword) {
    if (!StringUtil.hasText(keyword)) {
      return null;
    }
    TagW tag = TagW.get(keyword.trim());
    if (tag == null) {
      LOGGER.error("{} '{}': cannot find a tag with the keyword {}", WHAT, id, keyword);
    }
    return tag;
  }
}
