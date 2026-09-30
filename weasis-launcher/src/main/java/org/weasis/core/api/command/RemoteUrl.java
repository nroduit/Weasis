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

import java.net.URI;
import java.net.URISyntaxException;

/**
 * A location Weasis fetches from or sends to. The restriction policies decide whether a remote
 * caller may open it at all, and which hosts it may reach.
 */
public record RemoteUrl(URI value) implements SemanticType {

  private static final String EXPECTED = "an absolute URL, e.g. https://host/path";

  public RemoteUrl {
    if (value == null || value.getScheme() == null) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  public static RemoteUrl parse(String text) {
    if (text == null || text.isBlank()) {
      throw new IllegalArgumentException(EXPECTED);
    }
    try {
      return new RemoteUrl(new URI(text.trim()));
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(EXPECTED);
    }
  }

  /** The host it reaches, or an empty string for a location that has none, such as a file URL. */
  public String host() {
    return value.getHost() == null ? "" : value.getHost();
  }

  public String scheme() {
    return value.getScheme();
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
