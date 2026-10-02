/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.qr;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComboBox;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.util.TagUtils;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.service.BundlePreferences;
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
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.param.DicomParam;
import org.weasis.dicom.qr.DicomQrView.Period;

/**
 * A saved query template: a name, a period and the DICOM parameters of the query. The templates
 * come in two layers merged by entry id: the optional site document {@value #FILENAME} of the
 * resources package (or, until 5.2, the legacy {@value #LEGACY_FILENAME} of its root), then the
 * user document {@value #FILENAME} of the bundle data folder, converted once from the legacy XML of
 * that folder. The id of a legacy template is derived from its name; a template created in the
 * session gets its user id when it is saved.
 *
 * <p>The tags of the parameters are written as DICOM keywords ({@code "PatientName"}), or as {@code
 * "(gggg,eeee)"} when the dictionary has no keyword for the tag (a private one); a reader accepts
 * both as well as the integer of the documents written before, and skips a parameter whose keyword
 * it does not know.
 */
public class SearchParameters implements LayeredEntries.Entry {
  private static final Logger LOGGER = LoggerFactory.getLogger(SearchParameters.class);

  public static final String FILENAME = "searchTemplates.json"; // NON-NLS
  public static final String LEGACY_FILENAME = "searchParameters.xml"; // NON-NLS

  /** The name of the array of entries in the envelope. */
  public static final String ENTRIES = "templates"; // NON-NLS

  /** The kind of the derived ids, see {@link EntryIds#derived}. */
  public static final String KIND = "search"; // NON-NLS

  private static final String WHAT = "Search template"; // NON-NLS

  // JSON members
  static final String ID = "id"; // NON-NLS
  static final String NAME = "name"; // NON-NLS
  static final String PERIOD = "period"; // NON-NLS
  static final String PARAMS = "params"; // NON-NLS
  static final String TAG = "tag"; // NON-NLS
  static final String VALUES = "values"; // NON-NLS
  static final String PARENT_SEQ_TAGS = "parentSeqTags"; // NON-NLS
  static final String HIDDEN = "hidden"; // NON-NLS
  static final String LOCKED = "locked"; // NON-NLS

  private static final Pattern HEX_TAG =
      Pattern.compile("\\(([0-9A-Fa-f]{4}),([0-9A-Fa-f]{4})\\)"); // NON-NLS

  // Legacy XML elements and attributes
  static final String T_NODE = "searchParameters"; // NON-NLS
  static final String T_NAME = "name"; // NON-NLS
  static final String T_PERIOD = "period"; // NON-NLS
  static final String T_PARAMS = "dicomParams"; // NON-NLS
  static final String T_PARAM = "dicomParam"; // NON-NLS
  static final String T_TAG = "tag"; // NON-NLS
  static final String T_VALUE = "value"; // NON-NLS
  static final String T_PARENT_SEQ = "parentSeqTags"; // NON-NLS

  /** The conversion of a site {@value #LEGACY_FILENAME} into {@value #FILENAME}, with site ids. */
  public static final LegacyConverters.Conversion CONVERSION =
      new LegacyConverters.Conversion(
          LEGACY_FILENAME, FILENAME, ENTRIES, xml -> readLegacy(xml, EntryIds.SITE_PREFIX));

  /**
   * Where the documents of the two layers are; any of them may be null or absent. The site JSON
   * wins over the site XML when both exist.
   */
  public record Documents(Path siteJson, Path siteXml, Path userJson, Path userXml) {}

  private String id;
  private String name;
  private Period period;
  private final ArrayList<DicomParam> parameters = new ArrayList<>();
  private boolean hidden;
  private boolean locked;
  private boolean local = true;

  public SearchParameters(String name) {
    setName(name);
  }

  /** The id the layers merge on; null for a template created in the session and not saved yet. */
  @Override
  public String id() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    if (StringUtil.hasText(name)) {
      this.name = name;
    }
  }

  public List<DicomParam> getParameters() {
    return parameters;
  }

  public Period getPeriod() {
    return period;
  }

  public void setPeriod(Period period) {
    this.period = period;
  }

  @Override
  public boolean hidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  @Override
  public boolean locked() {
    return locked;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  /** Whether the template belongs to the user layer, the one that is saved; true by default. */
  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  @Override
  public String toString() {
    return name;
  }

  /**
   * The JSON of the template, its id included when it has one; the values and parent sequence tags
   * of a parameter are written when there are some, as {@link DicomParam} keeps none as empty. The
   * tags are written by {@link #tagName}.
   */
  public JsonObject toJson() {
    JsonObjectBuilder b = Json.createObjectBuilder();
    JsonUtil.addIfPresent(b, ID, id);
    b.add(NAME, name);
    if (period != null) {
      b.add(PERIOD, period.name());
    }
    JsonArrayBuilder params = Json.createArrayBuilder();
    for (DicomParam p : parameters) {
      JsonObjectBuilder pb = Json.createObjectBuilder().add(TAG, tagName(p.getTag()));
      if (p.getValues() != null && p.getValues().length > 0) {
        JsonArrayBuilder values = Json.createArrayBuilder();
        for (String value : p.getValues()) {
          if (value != null) {
            values.add(value);
          }
        }
        pb.add(VALUES, values);
      }
      if (p.getParentSeqTags() != null && p.getParentSeqTags().length > 0) {
        JsonArrayBuilder parents = Json.createArrayBuilder();
        for (int tag : p.getParentSeqTags()) {
          parents.add(tagName(tag));
        }
        pb.add(PARENT_SEQ_TAGS, parents);
      }
      params.add(pb);
    }
    b.add(PARAMS, params);
    if (hidden) {
      b.add(HIDDEN, true);
    }
    if (locked) {
      b.add(LOCKED, true);
    }
    return b.build();
  }

  /**
   * The template of a JSON object.
   *
   * @param user whether the object comes from the user document
   * @param used the ids already taken in the document, to derive a unique one when it has none
   * @throws IllegalArgumentException when the object has no name
   * @see #tagOf(JsonValue)
   */
  public static SearchParameters fromJson(JsonObject object, boolean user, Set<String> used) {
    String name = object.getString(NAME, null);
    if (!StringUtil.hasText(name)) {
      throw new IllegalArgumentException("A search template needs a name");
    }
    SearchParameters template = new SearchParameters(name);
    String id = object.getString(ID, null);
    if (!StringUtil.hasText(id)) {
      String prefix = user ? EntryIds.USER_PREFIX : EntryIds.SITE_PREFIX;
      id = EntryIds.unique(EntryIds.derived(prefix, KIND, name), used);
    }
    template.setId(id);
    template.setPeriod(Period.getPeriod(object.getString(PERIOD, null)));
    for (JsonObject p : JsonUtil.objects(object.getJsonArray(PARAMS))) {
      int tag = tagOf(p.get(TAG));
      int[] parents = tagsOf(p.get(PARENT_SEQ_TAGS));
      if (tag < 0 || parents == null) {
        LOGGER.warn("{} '{}': unknown tag in {}, parameter skipped", WHAT, name, p);
        continue;
      }
      String[] values = JsonUtil.getStringList(p, VALUES).toArray(new String[0]);
      template.getParameters().add(new DicomParam(parents, tag, values));
    }
    template.setHidden(JsonUtil.getBoolean(object, HIDDEN, false));
    template.setLocked(JsonUtil.getBoolean(object, LOCKED, false));
    template.setLocal(user);
    return template;
  }

  /**
   * The DICOM keyword of the tag, or {@code (gggg,eeee)} when the standard dictionary has none for
   * it.
   */
  static String tagName(int tag) {
    String keyword = ElementDictionary.getStandardElementDictionary().keywordOf(tag);
    return StringUtil.hasText(keyword) ? keyword : TagUtils.toString(tag);
  }

  /**
   * The tag of a JSON member: a keyword, the {@code (gggg,eeee)} form or the integer of the
   * documents written before 5.1.
   *
   * @return the tag, or -1 when the member is absent or its keyword is unknown
   */
  static int tagOf(JsonValue value) {
    return switch (value) {
      case JsonNumber number -> number.intValue();
      case JsonString text -> tagOf(text.getString());
      case null, default -> -1;
    };
  }

  /** The tag of a keyword or of its {@code (gggg,eeee)} form; -1 when unknown. */
  static int tagOf(String text) {
    if (!StringUtil.hasText(text)) {
      return -1;
    }
    Matcher m = HEX_TAG.matcher(text.trim());
    if (m.matches()) {
      return TagUtils.toTag(Integer.parseInt(m.group(1), 16), Integer.parseInt(m.group(2), 16));
    }
    return ElementDictionary.tagForKeyword(text.trim(), null);
  }

  /** The tags of a JSON array; empty when absent, null when one of them is unknown. */
  private static int[] tagsOf(JsonValue value) {
    if (!(value instanceof JsonArray array)) {
      return new int[0];
    }
    int[] tags = new int[array.size()];
    for (int i = 0; i < tags.length; i++) {
      tags[i] = tagOf(array.get(i));
      if (tags[i] < 0) {
        return null;
      }
    }
    return tags;
  }

  public static void loadSearchParameters(JComboBox<SearchParameters> comboBox) {
    SearchParameters emptyParameters = new SearchParameters(Messages.getString("empty"));
    comboBox.addItem(emptyParameters);

    SearchParameters todayParameters = new SearchParameters(Period.TODAY.toString());
    todayParameters.setPeriod(Period.TODAY);
    comboBox.addItem(todayParameters);

    for (SearchParameters node : loadSearchParameters()) {
      comboBox.addItem(node);
    }
  }

  /** The merged templates of the installation in document order, the hidden ones left out. */
  public static List<SearchParameters> loadSearchParameters() {
    return load(documents()).entries().stream().filter(t -> !t.hidden()).toList();
  }

  /** Writes the user layer: the templates flagged {@link #isLocal() local}. */
  public static void saveSearchParameters(List<? extends SearchParameters> templates) {
    save(documents().userJson(), templates);
  }

  /** The documents of the running installation. */
  static Documents documents() {
    BundleContext context = AppProperties.getBundleContext(SearchParameters.class);
    return new Documents(
        SiteDocuments.find(FILENAME).orElse(null),
        ResourceUtil.getResource(LEGACY_FILENAME).toPath(),
        BundlePreferences.getFileInDataFolder(context, FILENAME),
        BundlePreferences.getFileInDataFolder(context, LEGACY_FILENAME));
  }

  /**
   * Reads and merges the two layers: the site document, then the user document, converted first
   * from its legacy XML when the JSON does not exist yet (the XML is kept).
   */
  static Merged<SearchParameters> load(Documents docs) {
    List<JsonObject> siteObjects = readSite(docs);
    Map<String, JsonObject> siteById = new LinkedHashMap<>();
    siteObjects.forEach(o -> siteById.putIfAbsent(o.getString(ID, ""), o));
    List<SearchParameters> site = parse(siteObjects, false);

    LegacyMigration.migrate(
        docs.userXml(),
        docs.userJson(),
        ENTRIES,
        xml -> readLegacy(xml, EntryIds.USER_PREFIX),
        WHAT);
    List<JsonObject> userObjects =
        JsonExtends.resolve(
            readJson(docs.userJson()), id -> Optional.ofNullable(siteById.get(id)), WHAT);
    List<SearchParameters> user = parse(userObjects, true);

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

  private static List<SearchParameters> parse(List<JsonObject> objects, boolean user) {
    List<SearchParameters> result = new ArrayList<>(objects.size());
    Set<String> used = new HashSet<>();
    for (JsonObject object : objects) {
      try {
        SearchParameters template = fromJson(object, user, used);
        used.add(template.id());
        result.add(template);
      } catch (RuntimeException e) {
        LOGGER.error("Cannot read {} {}", WHAT, object.getString(ID, "?"), e);
      }
    }
    return result;
  }

  /**
   * Writes the user document with the local templates of the list; a template without id gets one
   * derived from its name.
   */
  static void save(Path userJson, List<? extends SearchParameters> templates) {
    if (userJson == null) {
      return;
    }
    Set<String> used = new HashSet<>();
    templates.stream().map(SearchParameters::id).filter(StringUtil::hasText).forEach(used::add);
    List<JsonObject> objects = new ArrayList<>();
    for (SearchParameters template : templates) {
      if (!template.isLocal()) {
        continue;
      }
      if (!StringUtil.hasText(template.id())) {
        String id =
            EntryIds.unique(EntryIds.derived(EntryIds.USER_PREFIX, KIND, template.getName()), used);
        used.add(id);
        template.setId(id);
      }
      objects.add(template.toJson());
    }
    try {
      ListDocument.write(userJson, ENTRIES, objects);
    } catch (IOException e) {
      LOGGER.error("Cannot write {}", userJson, e);
    }
  }

  /**
   * Reads a legacy XML document into JSON entries, the id of each derived from its name with the
   * given layer prefix.
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
            JsonObject template = readLegacyTemplate(xmler, prefix, used);
            used.add(template.getString(ID));
            list.add(template);
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

  private static JsonObject readLegacyTemplate(
      XMLStreamReader xmler, String prefix, Set<String> used) throws XMLStreamException {
    String name = xmler.getAttributeValue(null, T_NAME);
    String period = xmler.getAttributeValue(null, T_PERIOD);
    JsonArrayBuilder params = Json.createArrayBuilder();
    while (xmler.hasNext()) {
      int eventType = xmler.next();
      if (eventType == XMLStreamConstants.START_ELEMENT
          && T_PARAM.equals(xmler.getName().getLocalPart())) {
        params.add(readLegacyParam(xmler));
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && T_NODE.equals(xmler.getName().getLocalPart())) {
        break;
      }
    }
    JsonObjectBuilder b =
        Json.createObjectBuilder()
            .add(ID, EntryIds.unique(EntryIds.derived(prefix, KIND, name), used));
    JsonUtil.addIfPresent(b, NAME, name);
    JsonUtil.addIfPresent(b, PERIOD, period);
    return b.add(PARAMS, params).build();
  }

  private static JsonObject readLegacyParam(XMLStreamReader xmler) throws XMLStreamException {
    JsonObjectBuilder b =
        Json.createObjectBuilder()
            .add(TAG, tagName(StringUtil.getInt(xmler.getAttributeValue(null, T_TAG))));
    int[] parents = StringUtil.getIntegerArray(xmler.getAttributeValue(null, T_PARENT_SEQ), ",");
    if (parents != null && parents.length > 0) {
      JsonArrayBuilder array = Json.createArrayBuilder();
      for (int tag : parents) {
        array.add(tagName(tag));
      }
      b.add(PARENT_SEQ_TAGS, array);
    }
    JsonArrayBuilder values = Json.createArrayBuilder();
    boolean hasValues = false;
    while (xmler.hasNext()) {
      int eventType = xmler.next();
      if (eventType == XMLStreamConstants.START_ELEMENT
          && T_VALUE.equals(xmler.getName().getLocalPart())) {
        values.add(xmler.getElementText());
        hasValues = true;
      } else if (eventType == XMLStreamConstants.END_ELEMENT
          && T_PARAM.equals(xmler.getName().getLocalPart())) {
        break;
      }
    }
    if (hasValues) {
      b.add(VALUES, values);
    }
    return b.build();
  }
}
