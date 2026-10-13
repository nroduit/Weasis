/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.weasis.core.api.setting.SettingsNode.Data;
import org.weasis.core.util.PropertiesUtil;
import org.xml.sax.SAXException;

/**
 * The stores that held a group's settings before the {@link SettingsFileStore}, read once when the
 * group has no file yet. Nothing is written back to them.
 */
final class LegacySettings {
  private static final Logger LOGGER = LoggerFactory.getLogger(LegacySettings.class);

  /** The 4.x modules whose settings went into a 5.0 group, the newest first. */
  private static final Map<String, List<String>> MODULES_4X =
      Map.of("weasis-core", List.of("weasis-core", "weasis-core-ui")); // NON-NLS

  /** The data folders whose {@code .properties} went into a 5.0 group, the newest first. */
  private static final Map<String, List<String>> DATA_FOLDERS_4X =
      Map.of(
          "weasis-core", List.of("weasis-core", "weasis-core-ui"), // NON-NLS
          "weasis-dicom-isowriter",
              List.of("weasis-dicom-isowriter", "weasis-isowriter")); // NON-NLS

  /** The file of the core data folder holding its values; the second is a misspelling of 4.x. */
  private static final List<String> CORE_FILES =
      List.of("persistence.properties", "persitence.properties"); // NON-NLS

  /** Keys not carried over: the proxy credentials, no longer persisted. */
  private static final String DROPPED_PREFIX = "proxy.auth."; // NON-NLS

  private static final String PROPERTIES = ".properties"; // NON-NLS

  /** One earlier store: copies what it holds for a group, true when it held anything. */
  @FunctionalInterface
  interface Source {
    boolean copy(String group, Data into) throws Exception;

    /**
     * A store reached as {@code java.util.prefs} nodes, one per group (null when none).
     *
     * @param label the store, for the log
     */
    static Source of(String label, Function<String, Preferences> nodes) {
      return (group, into) -> {
        Preferences node = nodes.apply(group);
        if (node != null && copyNode(node, into)) {
          LOGGER.info("Settings of {} copied from {} {}", group, label, node.absolutePath());
          return true;
        }
        return false;
      };
    }
  }

  private LegacySettings() {}

  /**
   * The sources of a preference directory, in the order they are copied; a value copied first is
   * kept. The JDK store of the earlier 5.0 builds comes first, being the newest, then the 4.x
   * files.
   */
  static List<Source> of(Path prefDir) {
    return List.of(
        Source.of("the JDK store", SettingsFileStore::jdkNode), // NON-NLS
        (group, into) -> copy4x(prefDir, group, into));
  }

  /**
   * The source of the machine store: the {@code .properties} files of a plugin's data folder,
   * {@code <dataDir>/<pluginId>}. In {@code weasis-core}, {@code persistence.properties} holds the
   * values of the group; any other file becomes the node named after it ({@code
   * import-export.properties} → {@code import-export}).
   */
  static Source ofDataFolder(Path dataDir) {
    return (group, into) -> copyDataFolders(dataDir, group, into);
  }

  static boolean copyDataFolders(Path dataDir, String group, Data into) throws IOException {
    boolean found = false;
    for (String module : DATA_FOLDERS_4X.getOrDefault(group, List.of(group))) {
      Path folder = dataDir.resolve(module);
      if (!Files.isDirectory(folder)) {
        continue;
      }
      List<Path> files;
      try (var list = Files.list(folder)) {
        files =
            list.filter(f -> f.getFileName().toString().endsWith(PROPERTIES))
                .filter(Files::isRegularFile)
                .sorted(LegacySettings::coreFilesFirst)
                .toList();
      }
      for (Path file : files) {
        String name = file.getFileName().toString();
        Data target =
            CORE_FILES.contains(name)
                ? into
                : into.children.computeIfAbsent(
                    name.substring(0, name.length() - PROPERTIES.length()), n -> new Data());
        if (copyProperties(file, target)) {
          found = true;
          LOGGER.info("Settings of {} copied from {}", group, file);
        }
      }
    }
    return found;
  }

  private static int coreFilesFirst(Path a, Path b) {
    int ia = CORE_FILES.indexOf(a.getFileName().toString());
    int ib = CORE_FILES.indexOf(b.getFileName().toString());
    ia = ia < 0 ? CORE_FILES.size() : ia;
    ib = ib < 0 ? CORE_FILES.size() : ib;
    return ia != ib ? Integer.compare(ia, ib) : a.compareTo(b);
  }

  private static boolean copyProperties(Path file, Data data) {
    Properties properties = PropertiesUtil.loadProperties(file);
    boolean found = false;
    for (String key : properties.stringPropertyNames()) {
      if (!key.startsWith(DROPPED_PREFIX)) {
        data.values.putIfAbsent(key, properties.getProperty(key));
        found = true;
      }
    }
    return found;
  }

  /** Copies a node into data without replacing a value already there; true if any was found. */
  static boolean copyNode(Preferences node, Data data) throws BackingStoreException {
    boolean found = false;
    for (String key : node.keys()) {
      String value = node.get(key, null);
      if (value != null) {
        data.values.putIfAbsent(key, value);
        found = true;
      }
    }
    for (String name : node.childrenNames()) {
      found |= copyNode(node.node(name), data.children.computeIfAbsent(name, n -> new Data()));
    }
    return found;
  }

  /**
   * Copies the 4.x file of a group, {@code <prefDir>/<module>.xml}: the Felix preference format, in
   * which an element with child elements is a node and an element holding text is a key.
   */
  static boolean copy4x(Path prefDir, String group, Data into) {
    boolean found = false;
    for (String module : MODULES_4X.getOrDefault(group, List.of(group))) {
      Path file = prefDir.resolve(module + ".xml"); // NON-NLS
      if (Files.isRegularFile(file)) {
        try (InputStream in = Files.newInputStream(file)) {
          Element root = newBuilder().parse(in).getDocumentElement();
          if (copyElement(root, into)) {
            found = true;
            LOGGER.info("Settings of {} copied from {}", group, file);
          }
        } catch (IOException | SAXException | ParserConfigurationException e) {
          LOGGER.warn("Cannot read the 4.x settings {}", file, e);
        }
      }
    }
    return found;
  }

  private static boolean copyElement(Element element, Data data) {
    boolean found = false;
    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      if (children.item(i) instanceof Element child) {
        String name = child.getTagName();
        if (hasElementChild(child)) {
          found |= copyElement(child, data.children.computeIfAbsent(name, n -> new Data()));
        } else if (!child.getTextContent().isEmpty()) {
          data.values.putIfAbsent(name, child.getTextContent());
          found = true;
        }
      }
    }
    return found;
  }

  private static boolean hasElementChild(Element element) {
    for (Node n = element.getFirstChild(); n != null; n = n.getNextSibling()) {
      if (n.getNodeType() == Node.ELEMENT_NODE) {
        return true;
      }
    }
    return false;
  }

  private static DocumentBuilder newBuilder() throws ParserConfigurationException {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); // NON-NLS
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder();
  }
}
