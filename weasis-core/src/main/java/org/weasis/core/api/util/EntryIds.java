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

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The ids of the configuration entries: {@code <layer>.<kind>.<slug>}, where the layer prefix is
 * {@value #BUILT_IN_PREFIX}, {@value #SITE_PREFIX} or {@value #USER_PREFIX}. A legacy XML document
 * has no ids: the readers derive one from what identifies the entry (the AE title, host and port of
 * a DICOM node, the URL of a DICOMweb node, the uid of an authentication method, the name of a
 * search template) so that a site XML file and its JSON conversion yield the same ids, and the user
 * overrides match either.
 */
public final class EntryIds {

  public static final String BUILT_IN_PREFIX = "weasis."; // NON-NLS
  public static final String SITE_PREFIX = "site."; // NON-NLS
  public static final String USER_PREFIX = "user."; // NON-NLS

  private EntryIds() {}

  /**
   * A slug of the parts: lower case, runs of anything but letters and digits replaced by one dash,
   * parts joined by a dash; empty parts are skipped.
   */
  public static String slug(String... parts) {
    String joined =
        Arrays.stream(parts)
            .filter(Objects::nonNull)
            .map(p -> p.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-")) // NON-NLS
            .map(p -> p.replaceAll("(^-+)|(-+$)", ""))
            .filter(p -> !p.isEmpty())
            .collect(Collectors.joining("-"));
    return joined.isEmpty() ? "entry" : joined; // NON-NLS
  }

  /** {@code <prefix><kind>.<slug>}, for an entry derived from a legacy document. */
  public static String derived(String prefix, String kind, String... parts) {
    return prefix + kind + "." + slug(parts);
  }

  /**
   * The id itself when unused, else the first of {@code id#2}, {@code id#3}... that is unused, so
   * that two legacy entries sharing the same key both survive the conversion.
   */
  public static String unique(String id, Set<String> used) {
    if (!used.contains(id)) {
      return id;
    }
    for (int n = 2; ; n++) {
      String candidate = id + "#" + n;
      if (!used.contains(candidate)) {
        return candidate;
      }
    }
  }

  /** Whether the id belongs to the user layer by its prefix. */
  public static boolean isUser(String id) {
    return id != null && id.startsWith(USER_PREFIX);
  }
}
