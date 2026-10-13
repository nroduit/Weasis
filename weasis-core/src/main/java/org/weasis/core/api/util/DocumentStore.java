/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.setting.Scope;

/**
 * Where the user and machine layers of the configuration documents live, one folder per scope:
 *
 * <ul>
 *   <li>{@link Scope#USER}: {@code <prefDir>/documents/<name>}, per Weasis user and profile (window
 *       presets, color maps, masking, measurement profiles, DICOM and DICOMweb nodes, printers,
 *       authentication methods, search templates);
 *   <li>{@link Scope#MACHINE}: {@code <config>/machine/documents/<name>}, this workstation (calling
 *       nodes, launchers).
 * </ul>
 *
 * <p>The name of a user or machine document is the name of the site document it overrides, so a
 * document is identified by its file name in every layer. A registry asks for its file with {@link
 * #locate}, giving where the document was before: the first earlier file found is copied once into
 * the new place and left where it was, so that an older Weasis still finds it.
 *
 * <p>Every document asked for is declared here with its scope, which is what an export or a mirror
 * lists.
 */
public final class DocumentStore {
  private static final Logger LOGGER = LoggerFactory.getLogger(DocumentStore.class);

  /** The folder of the documents, in the preference directory and in {@code machine}. */
  public static final String FOLDER = "documents"; // NON-NLS

  /** A document known to the running installation. */
  public record Declared(String name, Scope scope, Path file) {}

  private static final Map<String, Declared> DECLARED = new ConcurrentSkipListMap<>();
  private static volatile Path userFolder;
  private static volatile Path machineFolder;
  private static final List<Runnable> USER_WRITTEN = new CopyOnWriteArrayList<>();

  private DocumentStore() {}

  /**
   * @param prefDir the preference directory of the current Weasis user and profile, or null
   * @param configDir the configuration directory of the OS account, or null
   */
  public static void configure(Path prefDir, Path configDir) {
    userFolder = prefDir == null ? null : prefDir.resolve(FOLDER);
    machineFolder = configDir == null ? null : configDir.resolve("machine").resolve(FOLDER);
  }

  /** The folder of a scope, null until configured or for {@link Scope#SITE}. */
  public static Path folder(Scope scope) {
    return switch (scope) {
      case USER -> userFolder;
      case MACHINE -> machineFolder;
      case SITE -> null;
    };
  }

  /**
   * The file of a user or machine document, declared with its scope. When the file does not exist
   * yet, the first of {@code earlier} that does is copied to it. Before {@link #configure} (a test,
   * a tool), the first earlier location is returned unchanged.
   *
   * @param name the file name, the same as the site document's
   * @param earlier where the document was kept by earlier versions, the newest first
   * @return the file, or null when there is no folder and no earlier location
   */
  public static Path locate(Scope scope, String name, Path... earlier) {
    Path folder = folder(scope);
    if (folder == null) {
      return earlier.length > 0 ? earlier[0] : null;
    }
    Path file = folder.resolve(name);
    DECLARED.put(scope.id() + "/" + name, new Declared(name, scope, file));
    if (!Files.exists(file)) {
      copyEarlier(file, earlier);
    }
    return file;
  }

  private static void copyEarlier(Path file, Path[] earlier) {
    for (Path old : earlier) {
      if (old != null && Files.isRegularFile(old)) {
        try {
          Files.createDirectories(file.getParent());
          Files.copy(old, file);
          LOGGER.info("Document {} copied from {}, which is left in place", file, old);
        } catch (IOException e) {
          LOGGER.error("Cannot copy {} into {}", old, file, e);
        }
        return;
      }
    }
  }

  /** Called after a document of the user folder has been written. */
  public static void onUserDocumentWritten(Runnable listener) {
    USER_WRITTEN.add(listener);
  }

  /** Told by every writer of a document; notifies when it is a user document. */
  static void written(Path file) {
    Path folder = userFolder;
    if (folder != null && file.toAbsolutePath().startsWith(folder.toAbsolutePath())) {
      USER_WRITTEN.forEach(Runnable::run);
    }
  }

  /** The documents asked for so far, sorted by scope then name. */
  public static List<Declared> declared() {
    return new ArrayList<>(DECLARED.values());
  }
}
