/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.hp;

import java.nio.file.Path;
import java.util.Optional;
import org.weasis.core.api.setting.Scope;
import org.weasis.core.api.util.DocumentStore;
import org.weasis.core.api.util.LayeredDocument;
import org.weasis.core.api.util.ListDocument;
import org.weasis.core.api.util.SiteDocuments;

/**
 * Where the hanging protocols and the reading sessions are kept, before the engine that applies
 * them:
 *
 * <ul>
 *   <li>{@value #PROTOCOLS}: the protocols, entries of {@value #PROTOCOL_ENTRIES}; the site ships
 *       them as the folder {@code config/}{@value #SITE_FOLDER}, one protocol per file, and the
 *       user layer is {@code <prefDir>/documents/}{@value #PROTOCOLS}. A protocol lists the
 *       presets, color maps, measurement and masking profiles it uses in its {@code references}.
 *   <li>{@value #SESSIONS}: the sessions kept by the user, entries of {@value #SESSION_ENTRIES}; a
 *       user document only, private (it holds study identifiers): not in a settings package nor
 *       mirrored.
 * </ul>
 *
 * <p>Both documents keep their entries as JSON until the protocol model is final; see {@link
 * LayeredDocument}.
 */
public final class HangingProtocolDocuments {

  public static final String PROTOCOLS = "hp.protocols.json"; // NON-NLS
  public static final String PROTOCOL_ENTRIES = "protocols"; // NON-NLS
  public static final String SITE_FOLDER = "hanging-protocols"; // NON-NLS
  public static final String SESSIONS = "hp.sessions.json"; // NON-NLS
  public static final String SESSION_ENTRIES = "sessions"; // NON-NLS

  private static final LayeredDocument PROTOCOL_DOCUMENT =
      new LayeredDocument(PROTOCOLS, PROTOCOL_ENTRIES);
  private static final LayeredDocument SESSION_DOCUMENT =
      new LayeredDocument(SESSIONS, SESSION_ENTRIES);

  static {
    declare();
  }

  private HangingProtocolDocuments() {}

  /** Declares the site folder and the private document; idempotent. */
  public static void declare() {
    SiteDocuments.declareFolder(PROTOCOLS, SITE_FOLDER, PROTOCOL_ENTRIES);
    DocumentStore.declarePrivate(SESSIONS);
  }

  /** Reads both documents from the configured folders; called once the document store is set. */
  public static void configure() {
    Path protocols = DocumentStore.locate(Scope.USER, PROTOCOLS);
    PROTOCOL_DOCUMENT.configure(SiteDocuments.read(PROTOCOLS), protocols);
    SESSION_DOCUMENT.configure(Optional.empty(), DocumentStore.locate(Scope.USER, SESSIONS));
    SiteDocuments.onReload(
        PROTOCOLS, () -> PROTOCOL_DOCUMENT.reload(SiteDocuments.read(PROTOCOLS)));
    DocumentStore.registerIds(ListDocument.documentId(Path.of(PROTOCOLS)), PROTOCOL_DOCUMENT::ids);
  }

  public static LayeredDocument protocols() {
    return PROTOCOL_DOCUMENT;
  }

  public static LayeredDocument sessions() {
    return SESSION_DOCUMENT;
  }
}
