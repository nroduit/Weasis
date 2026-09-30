/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.command;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** Which namespaces a remote origin may call; read from system properties by default. */
public record RemotePolicy(boolean enabled, Set<String> namespaces) {

  public static final String ENABLED_PROP = "weasis.commands.remote.enabled";
  public static final String NAMESPACES_PROP = "weasis.commands.remote.namespaces";
  public static final String ALL = "*";

  public static RemotePolicy allowAll() {
    return new RemotePolicy(true, Set.of(ALL));
  }

  public static RemotePolicy fromSystemProperties() {
    boolean enabled = !"false".equalsIgnoreCase(System.getProperty(ENABLED_PROP, "true"));
    String list = System.getProperty(NAMESPACES_PROP, ALL);
    Set<String> names =
        Arrays.stream(list.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toUnmodifiableSet());
    return new RemotePolicy(enabled, names.isEmpty() ? Set.of(ALL) : names);
  }

  public boolean allows(String namespace) {
    return enabled && (namespaces.contains(ALL) || namespaces.contains(namespace));
  }
}
