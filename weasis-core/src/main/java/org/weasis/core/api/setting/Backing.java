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

/**
 * The store that holds the stored value of a setting today. A transition detail: the existing keys
 * live in three stores, and a declared setting keeps reading and writing where its value already
 * is. The unified user and machine stores replace these, and the declarations then drop their
 * backing; nothing outside this package should depend on it.
 */
public sealed interface Backing {

  /** {@code weasis.properties} of the preference directory, per Weasis user and profile. */
  Backing SYSTEM_PREFERENCES = new Named("system-preferences"); // NON-NLS

  /**
   * {@code UICore.getLocalPersistence()}: the values of {@code weasis-core} in the machine store,
   * local to the workstation.
   */
  Backing LOCAL_PERSISTENCE = new Named("local-persistence"); // NON-NLS

  /** No stored value: a site-only setting is read from the launch configuration. */
  Backing NONE = new Named("none"); // NON-NLS

  /**
   * A {@code java.util.prefs} node of the setting's group: {@code org/weasis/<group>/<path>}, the
   * group root when {@code path} is empty.
   */
  static Backing preferenceNode(String path) {
    return new PreferenceNode(path == null ? "" : path);
  }

  /** The name shown in the catalog. */
  String id();

  /** The default backing of a scope. */
  static Backing defaultOf(Scope scope) {
    return switch (scope) {
      case USER -> SYSTEM_PREFERENCES;
      case MACHINE -> LOCAL_PERSISTENCE;
      case SITE -> NONE;
    };
  }

  record Named(String id) implements Backing {}

  record PreferenceNode(String path) implements Backing {
    @Override
    public String id() {
      return path.isEmpty() ? "preference-node" : "preference-node:" + path; // NON-NLS
    }
  }
}
