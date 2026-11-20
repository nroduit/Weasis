/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.aiw;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.dcm4che3.data.Code;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomWebOrigin;

/**
 * The DICOMweb servers that take AI tasks, from the document {@code upsNodes.json} of the user (IHE
 * AIW-I: the catalog of task codes is per server and set by the site, RAD-80 §4.80.4.1.2.1). A
 * server is named by the id of its DICOMweb node of the preferences, or by its URL for a study
 * loaded without a node; a server that is not listed, or not enabled, shows nothing.
 *
 * <pre>
 * {"nodes": [{
 *   "id": "pacs",                       the id of the DICOMweb node, or any name with "url"
 *   "url": "https://pacs/dicomweb",     the root URL of the server, for a study without a node
 *   "enabled": true,
 *   "writeAllowed": true,               tasks may be created and canceled
 *   "requesterAet": "WEASIS",
 *   "requesterInPath": false,           true for dcm4chee-arc
 *   "pollSeconds": 30,                  how often pending tasks are read again, 0 for never
 *   "catalog": [{"value": "CT-ORGAN-SEG", "scheme": "99SITE", "meaning": "Organ segmentation"}]
 * }]}
 * </pre>
 */
public final class UpsNodes {

  public static final String DOCUMENT = "upsNodes.json"; // NON-NLS
  public static final String ENTRIES = "nodes"; // NON-NLS

  /** Never read the tasks of a server more often than this, whatever the document says. */
  static final int MIN_POLL_SECONDS = 10;

  private static final LayeredDocument NODES = new LayeredDocument(DOCUMENT, ENTRIES);
  private static boolean configured;

  private UpsNodes() {}

  /**
   * One server.
   *
   * @param id the id of its DICOMweb node, or the name of an entry that gives a URL
   * @param url its root URL without trailing slash, null when the id names a node
   * @param enabled false to ignore the entry
   * @param writeAllowed whether tasks may be created and canceled there
   * @param requesterAet the AE title of this user agent on that server
   * @param requesterInPath whether the AE title goes into the path of the subscriptions
   * @param pollSeconds the interval at which pending tasks are read again, 0 for never
   * @param catalog the tasks offered, in the order of the menu
   */
  public record Node(
      String id,
      String url,
      boolean enabled,
      boolean writeAllowed,
      String requesterAet,
      boolean requesterInPath,
      int pollSeconds,
      List<Code> catalog) {

    public Node {
      url = DicomWebOrigin.normalize(url);
      requesterAet = StringUtil.hasText(requesterAet) ? requesterAet : "WEASIS"; // NON-NLS
      pollSeconds = pollSeconds <= 0 ? 0 : Math.max(MIN_POLL_SECONDS, pollSeconds);
      catalog = List.copyOf(catalog);
    }

    static Node of(JsonObject json) {
      List<Code> codes = new ArrayList<>();
      JsonArray catalog = json.getJsonArray("catalog"); // NON-NLS
      if (catalog != null) {
        for (JsonValue value : catalog) {
          JsonObject c = value.asJsonObject();
          String code = c.getString("value", null); // NON-NLS
          String scheme = c.getString("scheme", null); // NON-NLS
          if (StringUtil.hasText(code) && StringUtil.hasText(scheme)) {
            codes.add(new Code(code, scheme, null, c.getString("meaning", code))); // NON-NLS
          }
        }
      }
      return new Node(
          json.getString("id", null), // NON-NLS
          json.getString("url", null), // NON-NLS
          json.getBoolean("enabled", false), // NON-NLS
          json.getBoolean("writeAllowed", false), // NON-NLS
          json.getString("requesterAet", null), // NON-NLS
          json.getBoolean("requesterInPath", false), // NON-NLS
          json.getInt("pollSeconds", 30), // NON-NLS
          codes);
    }

    boolean serves(DicomWebOrigin origin, String nodeId) {
      if (id != null && id.equals(nodeId)) {
        return true;
      }
      String base = origin.baseUrl();
      return url != null && (base.equals(url) || base.startsWith(url + "/"));
    }
  }

  private static synchronized LayeredDocument document() {
    if (!configured) {
      NODES.configure(Optional.empty(), DocumentStore.locate(Scope.USER, DOCUMENT));
      configured = true;
    }
    return NODES;
  }

  /** Uses another file, for tests. */
  static synchronized void install(Path file) {
    NODES.configure(Optional.empty(), file);
    configured = true;
  }

  /** Every server of the document, enabled or not. */
  public static List<Node> all() {
    return document().entries().stream().map(e -> Node.of(e.json())).toList();
  }

  /**
   * The enabled server a study came from: the entry of its DICOMweb node when the origin names one
   * or matches one by URL, else the entry whose URL is a prefix of the origin.
   */
  public static Optional<Node> forOrigin(DicomWebOrigin origin) {
    if (origin == null) {
      return Optional.empty();
    }
    List<Node> nodes = all().stream().filter(Node::enabled).toList();
    if (nodes.isEmpty()) {
      return Optional.empty();
    }
    String nodeId = origin.nodeId();
    if (nodeId == null && nodes.stream().anyMatch(n -> n.id() != null)) {
      try {
        nodeId = origin.findNode().map(n -> n.getId()).orElse(null);
      } catch (RuntimeException | LinkageError e) {
        nodeId = null; // no node preferences (a test, a headless start): the URL decides
      }
    }
    String id = nodeId;
    return nodes.stream().filter(n -> n.serves(origin, id)).findFirst();
  }
}
