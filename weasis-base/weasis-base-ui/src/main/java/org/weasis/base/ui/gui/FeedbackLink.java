/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.weasis.core.api.gui.util.AppProperties;

/**
 * The feedback channel of a preview: the GitHub issue form of the 5.0 preview, opened with the
 * version and the platform filled in, so that a tester's report carries what the triage needs. The
 * Help menu and {@code weasis:feedback} build the same link.
 */
public final class FeedbackLink {

  /** The issue form of the preview findings (its fields are the query parameters). */
  public static final String ISSUES = "https://github.com/nroduit/Weasis/issues/new"; // NON-NLS

  public static final String TEMPLATE = "preview_finding.yml"; // NON-NLS

  /** Questions and opinions, apart from the findings. */
  public static final String DISCUSSIONS =
      "https://github.com/nroduit/Weasis/discussions/categories/5-0-preview"; // NON-NLS

  private FeedbackLink() {}

  /** The link for this instance. */
  public static String url() {
    return url(AppProperties.WEASIS_VERSION, platform(), null);
  }

  /** The link with the given values; {@code feature} may be null. */
  public static String url(String version, String platform, String feature) {
    Map<String, String> query = new LinkedHashMap<>();
    query.put("template", TEMPLATE); // NON-NLS
    query.put("version", version); // NON-NLS
    query.put("platform", platform); // NON-NLS
    if (feature != null && !feature.isBlank()) {
      query.put("feature", feature); // NON-NLS
    }
    StringBuilder url = new StringBuilder(ISSUES);
    char sep = '?';
    for (Map.Entry<String, String> e : query.entrySet()) {
      if (e.getValue() == null) {
        continue;
      }
      url.append(sep).append(e.getKey()).append('=').append(encode(e.getValue()));
      sep = '&';
    }
    return url.toString();
  }

  /**
   * Operating system, architecture and the monitors: {@code Linux 6.8 amd64, 2 monitors 2560x1440
   * 1920x1080}.
   */
  public static String platform() {
    StringBuilder text =
        new StringBuilder()
            .append(System.getProperty("os.name", "")) // NON-NLS
            .append(' ')
            .append(System.getProperty("os.version", "")) // NON-NLS
            .append(' ')
            .append(System.getProperty("os.arch", "")); // NON-NLS
    if (!GraphicsEnvironment.isHeadless()) {
      try {
        GraphicsDevice[] screens =
            GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        text.append(", ")
            .append(screens.length)
            .append(screens.length == 1 ? " monitor" : " monitors"); // NON-NLS
        for (GraphicsDevice screen : screens) {
          Rectangle bounds = screen.getDefaultConfiguration().getBounds();
          text.append(' ').append(bounds.width).append('x').append(bounds.height);
        }
      } catch (RuntimeException e) {
        // no display: the operating system alone
      }
    }
    return text.toString().trim();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); // NON-NLS
  }
}
