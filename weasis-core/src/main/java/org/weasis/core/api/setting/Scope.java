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
 * What a setting describes, which decides where its value is stored, whether it follows the user
 * through the preference service and whether it is exported.
 */
public enum Scope {
  /**
   * A choice of the person: stored per Weasis user and profile, follows them to any workstation.
   */
  USER,
  /** A fact of this workstation (screen, paths, capabilities): stored locally, never sent. */
  MACHINE,
  /** One value for everyone, set by the administrator only: read, never written by Weasis. */
  SITE;

  /** The name written in the catalog. */
  public String id() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
