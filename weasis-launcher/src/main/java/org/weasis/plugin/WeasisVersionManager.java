/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.plugin;

import org.pf4j.VersionManager;
import org.semver4j.Semver;
import org.weasis.launcher.Utils;

/**
 * Replaces {@link org.pf4j.DefaultVersionManager}, which relies on {@code java-semver}, by the
 * semver4j implementation used everywhere else in Weasis. Constraints follow the node-semver range
 * syntax.
 */
public class WeasisVersionManager implements VersionManager {

  @Override
  public boolean checkVersionConstraint(String version, String constraint) {
    return !Utils.hasText(constraint)
        || "*".equals(constraint)
        || new Semver(version).satisfies(constraint);
  }

  @Override
  public int compareVersions(String v1, String v2) {
    return new Semver(v1).compareTo(new Semver(v2));
  }
}
