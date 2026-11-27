/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What the version string says about the build: a release ({@code 5.0.0}), a preview ({@code
 * 5.0.0-beta.1}, {@code 5.0.0-rc.2}) or a development build ({@code 5.0.0-SNAPSHOT}). A preview and
 * a development build are not ready for production: the application says so in its title, its About
 * box and once at start, {@code weasis:info} reports it, and the one-time migration of a 4.x
 * installation stays off so a preview never moves the settings of the release in use.
 *
 * @param version the full version string
 * @param stage {@code beta}, {@code rc}, {@code snapshot} or another word after the dash, null for
 *     a release
 * @param number the number after the stage ({@code beta.1} → 1), 0 when there is none
 */
public record Prerelease(String version, String stage, int number) {

  private static final Pattern SUFFIX =
      Pattern.compile("^[0-9]+(?:\\.[0-9]+)*-([A-Za-z]+)(?:[.-]?([0-9]+))?.*$");

  public static Prerelease of(String version) {
    if (version == null || version.isBlank()) {
      return new Prerelease("0.0.0", null, 0);
    }
    Matcher m = SUFFIX.matcher(version.trim());
    if (!m.matches()) {
      return new Prerelease(version.trim(), null, 0);
    }
    String stage = m.group(1).toLowerCase(Locale.ROOT);
    int number = m.group(2) == null ? 0 : Integer.parseInt(m.group(2));
    return new Prerelease(version.trim(), stage, number);
  }

  /** True for anything but a release: a preview or a development build. */
  public boolean isPreview() {
    return stage != null;
  }

  /** True for a development build. */
  public boolean isSnapshot() {
    return "snapshot".equals(stage); // NON-NLS
  }

  /** The version without its suffix: the package version ({@code 5.0.0}). */
  public String baseVersion() {
    int dash = version.indexOf('-');
    return dash < 0 ? version : version.substring(0, dash);
  }

  /** The stage and its number as the release name shows it: {@code beta 1}, {@code snapshot}. */
  public String label() {
    if (stage == null) {
      return "";
    }
    return number > 0 ? stage + " " + number : stage;
  }

  /** The suffix as the tag carries it: {@code beta.1}, {@code SNAPSHOT}; empty for a release. */
  public String suffix() {
    int dash = version.indexOf('-');
    return dash < 0 ? "" : version.substring(dash + 1);
  }
}
