/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import javax.swing.ComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboPopup;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.SecretStore;
import org.weasis.core.api.service.WProperties;
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
import org.weasis.core.util.StreamUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.TransferSyntax;
import org.weasis.dicom.codec.utils.DicomResource;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.DicomWebNode.WebType;

/**
 * A DICOM node of the configuration: a remote AE, a calling AE, a printer or a DICOMweb service.
 * Each {@link Type} has its own document, layered the usual way: the site document ({@code
 * config/<name>.json} of the resources package, or the legacy {@code <name>.xml} of its root) under
 * the user document ({@code <name>.json} of the bundle data folder), merged by id. The user
 * document is written in JSON only; a legacy user XML is migrated once and kept.
 */
public abstract class AbstractDicomNode implements LayeredEntries.Entry {
  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDicomNode.class);

  protected static final String T_NODES = "nodes"; // NON-NLS
  protected static final String T_NODE = "node"; // NON-NLS

  protected static final String T_ID = "id"; // NON-NLS
  protected static final String T_DESCRIPTION = "description";
  protected static final String T_TYPE = "type";
  protected static final String T_USAGE_TYPE = "usageType";
  protected static final String T_TSUID = "tsuid"; // NON-NLS
  protected static final String T_HIDDEN = "hidden"; // NON-NLS
  protected static final String T_LOCKED = "locked"; // NON-NLS

  /** The kind of id derived for a legacy or unsaved node, see {@link EntryIds#derived}. */
  static final String ID_KIND = "node"; // NON-NLS

  private static final String WHAT = "DICOM node"; // NON-NLS

  public enum Type {
    DICOM(Messages.getString("AbstractDicomNode.dcm_node"), "dicomNodes.xml", "dicomNodes.json"),
    DICOM_CALLING(
        Messages.getString("AbstractDicomNode.dcm_calling_node"),
        DicomResource.CALLING_NODES.getPath(),
        "dicomCallingNodes.json"),
    PRINTER(
        Messages.getString("AbstractDicomNode.dcm_printer"),
        "dicomPrinterNodes.xml",
        "dicomPrinterNodes.json"),
    WEB(
        Messages.getString("AbstractDicomNode.dcm_web_node"),
        "dicomWebNodes.xml",
        "dicomWebNodes.json");

    final String title;
    final String filename;
    final String jsonFilename;

    Type(String title, String filename, String jsonFilename) {
      this.title = title;
      this.filename = filename;
      this.jsonFilename = jsonFilename;
    }

    @Override
    public String toString() {
      return title;
    }

    /** The file name of the legacy XML document. */
    public String getFilename() {
      return filename;
    }

    /** The file name of the JSON document. */
    public String getJsonFilename() {
      return jsonFilename;
    }
  }

  public enum UsageType {
    STORAGE(Messages.getString("AbstractDicomNode.storage")),
    RETRIEVE(Messages.getString("AbstractDicomNode.retrieve")),
    BOTH(Messages.getString("AbstractDicomNode.both"));

    final String title;

    UsageType(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  public enum RetrieveType {
    CMOVE("C-MOVE"), // NON-NLS
    CGET("C-GET"), // NON-NLS
    WADO("WADO-URI"); // NON-NLS

    final String title;

    RetrieveType(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  /**
   * The files one document kind may live in: the site JSON (null when the site ships none), the
   * legacy site XML, the user JSON and the legacy user XML.
   */
  record Documents(Path siteJson, Path siteXml, Path userJson, Path userXml) {}

  private String id;
  private String description;
  private TransferSyntax tsuid;

  private Type type;
  private UsageType usageType;
  private boolean local;
  private boolean hidden;
  private boolean locked;

  protected AbstractDicomNode(String description, Type type, UsageType usageType) {
    if (type == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }
    this.description = description;
    this.tsuid = TransferSyntax.NONE;
    this.type = type;
    this.usageType = usageType;
    this.local = true;
  }

  /** The entry id, null for a node not yet saved. */
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public boolean hidden() {
    return hidden;
  }

  @Override
  public boolean locked() {
    return locked;
  }

  /**
   * The id a legacy or unsaved node gets: {@code <prefix>node.<slug of its key>}, the key being
   * what identifies the node in its document (AE title, host and port, or URL).
   */
  public abstract String deriveId(String prefix);

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public String toString() {
    return description;
  }

  public TransferSyntax getTsuid() {
    return tsuid;
  }

  public void setTsuid(TransferSyntax tsuid) {
    this.tsuid = tsuid;
  }

  public String getToolTips() {
    return description;
  }

  public Type getType() {
    return type;
  }

  public void setType(Type type) {
    this.type = type == null ? Type.DICOM : type;
  }

  public UsageType getUsageType() {
    return usageType;
  }

  public void setUsageType(UsageType usageType) {
    this.usageType = usageType;
  }

  /** Whether the node comes from the user document, the only one the user may edit. */
  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  /** Kept out of the lists; still replaces the same id of the site document. */
  public boolean isHidden() {
    return hidden;
  }

  public void setHidden(boolean hidden) {
    this.hidden = hidden;
  }

  /** A site node the user document may not replace or hide. */
  public boolean isLocked() {
    return locked;
  }

  public void setLocked(boolean locked) {
    this.locked = locked;
  }

  // ---- JSON ---------------------------------------------------------------------------------

  /**
   * The JSON entry of the node. The defaults are left out: {@code usageType} when it is what the
   * reader would default to for this node ({@link #defaultUsageType()}) and {@code tsuid} when it
   * is {@link TransferSyntax#NONE}; the readers fall back to them, and still accept a document that
   * spells them out.
   *
   * @param userDocument when true the secrets of the node are written as empty values, because they
   *     live in the {@link SecretStore}
   */
  public JsonObject toJson(boolean userDocument) {
    JsonObjectBuilder b = Json.createObjectBuilder();
    JsonUtil.addIfPresent(b, T_ID, id);
    b.add(T_TYPE, type.name());
    JsonUtil.addIfPresent(b, T_DESCRIPTION, description);
    if (usageType != null && usageType != defaultUsageType()) {
      b.add(T_USAGE_TYPE, usageType.name());
    }
    if (tsuid != null && tsuid != TransferSyntax.NONE) {
      b.add(T_TSUID, tsuid.name());
    }
    if (hidden) {
      b.add(T_HIDDEN, true);
    }
    if (locked) {
      b.add(T_LOCKED, true);
    }
    writeJson(b, userDocument);
    return b.build();
  }

  /** Adds the members specific to the subclass. */
  protected abstract void writeJson(JsonObjectBuilder b, boolean userDocument);

  /**
   * The usage the reader gives this node when the document has none, so that writing leaves it out
   * only when nothing is lost: {@link UsageType#BOTH} unless a subclass derives it otherwise.
   */
  protected UsageType defaultUsageType() {
    return UsageType.BOTH;
  }

  /**
   * Puts the secrets of the node into the {@link SecretStore}, before the user document is saved.
   */
  protected void storeSecrets() {
    // Nothing by default
  }

  /** Takes the secrets of the node from the {@link SecretStore}, after a user document is read. */
  protected void loadSecrets() {
    // Nothing by default
  }

  /**
   * Reads the common members of a JSON entry, the id excepted: it is resolved by the reader, which
   * knows the layer and the ids already used.
   */
  protected void readJson(JsonObject json) {
    String ts = json.getString(T_TSUID, null);
    setTsuid(StringUtil.hasText(ts) ? TransferSyntax.getTransferSyntax(ts) : TransferSyntax.NONE);
    setHidden(JsonUtil.getBoolean(json, T_HIDDEN, false));
    setLocked(JsonUtil.getBoolean(json, T_LOCKED, false));
  }

  static UsageType usageType(JsonObject json, UsageType defaultValue) {
    String value = json.getString(T_USAGE_TYPE, null);
    if (StringUtil.hasText(value)) {
      try {
        return UsageType.valueOf(value);
      } catch (IllegalArgumentException e) {
        LOGGER.warn("Unknown usage type {}", value);
      }
    }
    return defaultValue;
  }

  /** The node of a JSON entry of a document of that type. */
  static AbstractDicomNode fromJson(JsonObject json, Type type) throws IOException {
    String t = json.getString(T_TYPE, null);
    if (StringUtil.hasText(t) && !type.name().equals(t)) {
      throw new IOException("Not a %s node: %s".formatted(type.name(), t));
    }
    AbstractDicomNode node =
        switch (type) {
          case WEB -> DicomWebNode.fromJson(json);
          case PRINTER -> DicomPrintNode.fromJson(json);
          default -> DefaultDicomNode.fromJson(json);
        };
    node.setType(type);
    return node;
  }

  // ---- Documents ----------------------------------------------------------------------------

  /** Where the documents of that type are, in this installation. */
  static Documents documents(Type type) {
    BundleContext context = AppProperties.getBundleContext(AbstractDicomNode.class);
    return new Documents(
        SiteDocuments.find(type.getJsonFilename()).orElse(null),
        ResourceUtil.getResource(type.getFilename()).toPath(),
        BundlePreferences.getFileInDataFolder(context, type.getJsonFilename()),
        BundlePreferences.getFileInDataFolder(context, type.getFilename()));
  }

  /** Registers the XML to JSON conversion of the four documents, for a site package export. */
  public static void registerLegacyConversions() {
    for (Type type : Type.values()) {
      LegacyConverters.register(
          new LegacyConverters.Conversion(
              type.getFilename(),
              type.getJsonFilename(),
              T_NODES,
              legacy -> convertLegacy(legacy, type, EntryIds.SITE_PREFIX, false)));
    }
  }

  /**
   * The JSON entries of a legacy XML document, with ids derived for the layer of that prefix.
   *
   * @param userDocument when true the secrets go to the {@link SecretStore} and the entries carry
   *     empty values in their place
   */
  static List<JsonObject> convertLegacy(Path legacy, Type type, String prefix, boolean userDocument)
      throws IOException {
    List<JsonObject> entries = new ArrayList<>();
    for (AbstractDicomNode node : readLegacy(legacy, type, prefix)) {
      if (userDocument) {
        node.storeSecrets();
      }
      entries.add(node.toJson(userDocument));
    }
    return entries;
  }

  public static void loadDicomNodes(JComboBox<AbstractDicomNode> comboBox, Type type) {
    loadDicomNodes(comboBox, type, UsageType.BOTH);
  }

  public static void loadDicomNodes(
      JComboBox<AbstractDicomNode> comboBox, Type type, UsageType usage) {
    loadDicomNodes(comboBox, type, usage, null);
  }

  public static void loadDicomNodes(
      JComboBox<AbstractDicomNode> comboBox, Type type, UsageType usage, WebType webType) {
    List<AbstractDicomNode> list = loadDicomNodes(type, usage, webType);
    for (AbstractDicomNode node : list) {
      comboBox.addItem(node);
    }
  }

  public static List<AbstractDicomNode> loadDicomNodes(Type type, UsageType usage) {
    return loadDicomNodes(type, usage, null);
  }

  /**
   * The nodes of that type the user sees: the site and user documents merged by id, the hidden ones
   * left out, filtered by usage and, for DICOMweb nodes, by service.
   *
   * @param usage the usage wanted, {@link UsageType#BOTH} for every node
   * @param webType the DICOMweb service wanted, null for every one; {@link WebType#DICOMWEB} nodes
   *     serve any service but WADO-URI
   */
  public static List<AbstractDicomNode> loadDicomNodes(
      Type type, UsageType usage, WebType webType) {
    return loadDicomNodes(documents(type), type, usage, webType);
  }

  static List<AbstractDicomNode> loadDicomNodes(
      Documents documents, Type type, UsageType usage, WebType webType) {
    List<AbstractDicomNode> list = new ArrayList<>();
    for (AbstractDicomNode node : loadMerged(documents, type).entries()) {
      if (node.isHidden()) {
        continue;
      }
      if (usage != null && usage != UsageType.BOTH) {
        UsageType u = node.getUsageType();
        if (u != UsageType.BOTH && u != usage) {
          continue;
        }
      }
      if (webType != null && node instanceof DicomWebNode webNode) {
        WebType wt = webNode.getWebType();
        if (webType == WebType.WADO && webType != wt || webType != wt && wt != WebType.DICOMWEB) {
          continue;
        }
      }
      list.add(node);
    }
    return list;
  }

  /** The site and user documents of that type merged by id, hidden nodes included. */
  static Merged<AbstractDicomNode> loadMerged(Documents documents, Type type) {
    List<AbstractDicomNode> site = readSite(documents, type);
    Map<String, JsonObject> siteJson = new HashMap<>();
    for (AbstractDicomNode node : site) {
      siteJson.putIfAbsent(node.getId(), node.toJson(false));
    }
    List<AbstractDicomNode> user =
        readUser(documents, type, id -> Optional.ofNullable(siteJson.get(id)));
    Merged<AbstractDicomNode> merged =
        LayeredEntries.merge(
            List.of(Layer.of(Origin.SITE, site), Layer.of(Origin.USER, user)), WHAT);
    for (AbstractDicomNode node : merged.entries()) {
      node.setLocal(merged.origin(node.getId()) == Origin.USER);
    }
    return merged;
  }

  private static List<AbstractDicomNode> readSite(Documents documents, Type type) {
    Path json = documents.siteJson();
    if (json != null && Files.isRegularFile(json)) {
      LOGGER.debug("Site {} nodes from {}", type.name(), json);
      return readJson(json, type, EntryIds.SITE_PREFIX, JsonExtends.NO_BASE, false);
    }
    Path xml = documents.siteXml();
    if (xml != null && Files.isRegularFile(xml)) {
      LOGGER.debug("Site {} nodes from the legacy {}", type.name(), xml);
      try {
        return readLegacy(xml, type, EntryIds.SITE_PREFIX);
      } catch (IOException e) {
        LOGGER.error("Cannot read the site DICOM nodes {}", xml, e);
      }
    }
    return List.of();
  }

  private static List<AbstractDicomNode> readUser(
      Documents documents, Type type, Function<String, Optional<JsonObject>> siteBase) {
    Path json = documents.userJson();
    if (json == null) {
      return List.of();
    }
    LegacyMigration.migrate(
        documents.userXml(),
        json,
        T_NODES,
        legacy -> convertLegacy(legacy, type, EntryIds.USER_PREFIX, true),
        type.name() + " nodes"); // NON-NLS
    if (!Files.isRegularFile(json)) {
      return List.of();
    }
    return readJson(json, type, EntryIds.USER_PREFIX, siteBase, true);
  }

  private static List<AbstractDicomNode> readJson(
      Path file,
      Type type,
      String prefix,
      Function<String, Optional<JsonObject>> lowerBase,
      boolean userDocument) {
    List<JsonObject> entries;
    try {
      entries = ListDocument.read(file, T_NODES).entries();
    } catch (IOException e) {
      LOGGER.error("Cannot read the DICOM nodes {}", file, e);
      return List.of();
    }
    entries = JsonExtends.resolve(hiddenExtendsBase(entries, lowerBase), lowerBase, WHAT);
    List<AbstractDicomNode> nodes = new ArrayList<>();
    Set<String> used = new HashSet<>();
    for (JsonObject json : entries) {
      try {
        AbstractDicomNode node = fromJson(json, type);
        String id = json.getString(T_ID, null);
        if (!StringUtil.hasText(id)) {
          id = node.deriveId(prefix);
        }
        id = EntryIds.unique(id, used);
        used.add(id);
        node.setId(id);
        if (userDocument) {
          node.loadSecrets();
        }
        nodes.add(node);
      } catch (IOException | RuntimeException e) {
        LOGGER.error("Cannot read a DICOM node of {}: {}", file, json, e);
      }
    }
    return nodes;
  }

  /**
   * A user entry that only hides a site node, {@code {"id": "...", "hidden": true}}, has no fields
   * of its own: it is read as extending the site node, so that it still parses.
   */
  private static List<JsonObject> hiddenExtendsBase(
      List<JsonObject> entries, Function<String, Optional<JsonObject>> lowerBase) {
    List<JsonObject> result = new ArrayList<>(entries.size());
    for (JsonObject json : entries) {
      String id = json.getString(T_ID, null);
      if (JsonUtil.getBoolean(json, T_HIDDEN, false)
          && !json.containsKey(JsonExtends.KEY)
          && id != null
          && lowerBase.apply(id).isPresent()) {
        json = Json.createObjectBuilder(json).add(JsonExtends.KEY, id).build();
      }
      result.add(json);
    }
    return result;
  }

  /** Writes the user nodes of the combo box into the user document of that type. */
  public static void saveDicomNodes(JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
    List<AbstractDicomNode> nodes = new ArrayList<>();
    for (int i = 0; i < comboBox.getItemCount(); i++) {
      nodes.add(comboBox.getItemAt(i));
    }
    saveDicomNodes(nodes, type, documents(type).userJson());
  }

  /**
   * Writes the user nodes of that type among the given ones: a node without id gets one derived
   * from its key, made unique within the document; the secrets go to the {@link SecretStore}.
   */
  static void saveDicomNodes(List<? extends AbstractDicomNode> nodes, Type type, Path userJson) {
    List<JsonObject> entries = new ArrayList<>();
    Set<String> used = new HashSet<>();
    for (AbstractDicomNode node : nodes) {
      if (!node.isLocal() || type != node.getType()) {
        continue;
      }
      try {
        String id = node.getId();
        if (!StringUtil.hasText(id)) {
          id = node.deriveId(EntryIds.USER_PREFIX);
        }
        id = EntryIds.unique(id, used);
        used.add(id);
        node.setId(id);
        node.storeSecrets();
        entries.add(node.toJson(true));
      } catch (RuntimeException e) {
        LOGGER.error("Cannot write the DICOM node {}", node, e);
      }
    }
    try {
      ListDocument.write(userJson, T_NODES, entries);
    } catch (IOException e) {
      LOGGER.error("Error on writing DICOM node file {}", userJson, e);
    }
  }

  // ---- Legacy XML ---------------------------------------------------------------------------

  /** The nodes of a legacy XML document, with ids derived for the layer of that prefix. */
  static List<AbstractDicomNode> readLegacy(Path xml, Type type, String prefix) throws IOException {
    List<AbstractDicomNode> list = new ArrayList<>();
    XMLStreamReader xmler = null;
    try (InputStream in = Files.newInputStream(xml)) {
      XMLInputFactory factory = XMLInputFactory.newInstance();
      // disable external entities for security
      factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
      factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
      xmler = factory.createXMLStreamReader(in);
      int eventType;
      while (xmler.hasNext()) {
        eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          readDicomNodes(xmler, list, type);
        }
      }
    } catch (XMLStreamException e) {
      throw new IOException("Invalid DICOM node file " + xml, e);
    } finally {
      StreamUtil.safeClose(xmler);
    }
    Set<String> used = new HashSet<>();
    for (AbstractDicomNode node : list) {
      String id = EntryIds.unique(node.deriveId(prefix), used);
      used.add(id);
      node.setId(id);
    }
    return list;
  }

  private static void readDicomNodes(XMLStreamReader xmler, List<AbstractDicomNode> list, Type type)
      throws XMLStreamException {
    String key = xmler.getName().getLocalPart();
    if (T_NODES.equals(key)) {
      while (xmler.hasNext()) {
        int eventType = xmler.next();
        if (eventType == XMLStreamConstants.START_ELEMENT) {
          readDicomNode(xmler, list, type);
        }
      }
    }
  }

  private static void readDicomNode(
      XMLStreamReader xmler, List<AbstractDicomNode> list, Type type) {
    String key = xmler.getName().getLocalPart();
    if (T_NODE.equals(key)) {
      try {
        Type t = Type.valueOf(xmler.getAttributeValue(null, T_TYPE));
        if (type != t) {
          return;
        }
        AbstractDicomNode node;
        if (AbstractDicomNode.Type.WEB == type) {
          node = DicomWebNode.buildDicomWebNode(xmler);
        } else if (AbstractDicomNode.Type.PRINTER == type) {
          node = DicomPrintNode.buildDicomPrintNode(xmler);
        } else {
          node = DefaultDicomNode.buildDicomNodeEx(xmler);
        }
        node.setType(t);
        list.add(node);
      } catch (Exception e) {
        LOGGER.error("Cannot read DicomNode", e);
      }
    }
  }

  // ---- Actions ------------------------------------------------------------------------------

  public static void addNodeActionPerformed(
      JComboBox<? extends AbstractDicomNode> comboBox, Type type) {
    JDialog dialog;
    if (Type.WEB == type) {
      dialog =
          new DicomWebNodeDialog(
              SwingUtilities.getWindowAncestor(comboBox),
              Type.WEB.toString(),
              null,
              (JComboBox<DicomWebNode>) comboBox);
    } else {
      dialog =
          new DicomNodeDialog(
              SwingUtilities.getWindowAncestor(comboBox),
              Type.DICOM.toString(),
              null,
              (JComboBox<DefaultDicomNode>) comboBox,
              type);
    }
    GuiUtils.showCenterScreen(dialog, comboBox);
  }

  public static void editNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
    AbstractDicomNode node = (AbstractDicomNode) comboBox.getSelectedItem();
    if (node != null) {
      if (node.isLocal()) {
        Type type = node.getType();
        JDialog dialog;
        if (Type.WEB == type) {
          dialog =
              new DicomWebNodeDialog(
                  SwingUtilities.getWindowAncestor(comboBox),
                  Type.WEB.toString(),
                  (DicomWebNode) node,
                  (JComboBox<DicomWebNode>) comboBox);
        } else {
          dialog =
              new DicomNodeDialog(
                  SwingUtilities.getWindowAncestor(comboBox),
                  Type.DICOM.toString(),
                  (DefaultDicomNode) node,
                  (JComboBox<DefaultDicomNode>) comboBox,
                  type);
        }
        GuiUtils.showCenterScreen(dialog, comboBox);
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("AbstractDicomNode.only_usr_cr_msg"),
            Messages.getString("DicomPrintDialog.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void deleteNodeActionPerformed(JComboBox<? extends AbstractDicomNode> comboBox) {
    int index = comboBox.getSelectedIndex();
    if (index >= 0) {
      AbstractDicomNode node = comboBox.getItemAt(index);
      if (node.isLocal()) {
        int response =
            JOptionPane.showConfirmDialog(
                comboBox,
                String.format(Messages.getString("AbstractDicomNode.delete_msg"), node),
                Type.DICOM.toString(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == 0) {
          comboBox.removeItemAt(index);
          if (StringUtil.hasText(node.getId())) {
            SecretStore.getInstance().remove(node.getId());
          }
          AbstractDicomNode.saveDicomNodes(comboBox, node.getType());
        }
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("AbstractDicomNode.only_usr_cr_msg"),
            Messages.getString("DicomPrintDialog.error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void addTooltipToComboList(final JComboBox<? extends AbstractDicomNode> combo) {
    Object comp = combo.getUI().getAccessibleChild(combo, 0);
    if (comp instanceof final BasicComboPopup popup) {
      popup
          .getList()
          .getSelectionModel()
          .addListSelectionListener(
              e -> {
                if (!e.getValueIsAdjusting()) {
                  ListSelectionModel model = (ListSelectionModel) e.getSource();
                  int first = model.getMinSelectionIndex();
                  if (first >= 0) {
                    AbstractDicomNode item = combo.getItemAt(first);
                    ((JComponent) combo.getRenderer()).setToolTipText(item.getToolTips());
                  }
                }
              });
    }
  }

  /**
   * Selects the node of that id or, for a preference written by an older version, of that
   * description.
   */
  public static void selectDicomNode(ComboBoxModel<AbstractDicomNode> model, String value) {
    if (model != null && StringUtil.hasText(value)) {
      for (int i = 0; i < model.getSize(); i++) {
        if (value.equals(model.getElementAt(i).getId())) {
          model.setSelectedItem(model.getElementAt(i));
          return;
        }
      }
      for (int i = 0; i < model.getSize(); i++) {
        if (value.equals(model.getElementAt(i).getDescription())) {
          model.setSelectedItem(model.getElementAt(i));
          return;
        }
      }
    }
  }

  /** Remembers the selected node by its id. */
  public static void nodeSelectionPersistence(
      WProperties prefs, AbstractDicomNode node, String key) {
    if (node != null && prefs != null) {
      prefs.setProperty(
          key, StringUtil.hasText(node.getId()) ? node.getId() : node.getDescription());
    }
  }

  public static void restoreNodeSelection(
      WProperties prefs, ComboBoxModel<AbstractDicomNode> model, String key) {
    if (prefs != null) {
      selectDicomNode(model, prefs.getProperty(key));
    }
  }
}
