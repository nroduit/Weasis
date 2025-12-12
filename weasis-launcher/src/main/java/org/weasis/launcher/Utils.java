/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.LoggerFactory;
import org.weasis.core.util.StringUtil;

public class Utils {
  private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(Utils.class);

  private Utils() {}

  /**
   * Converts a string to a boolean, returning false if the string is null or empty.
   *
   * @param value the input string, may be null or empty
   * @return true if the string equals "true" (case-insensitive), false otherwise
   */
  public static boolean emptyToFalse(String value) {
    return StringUtil.hasText(value) && Boolean.parseBoolean(value);
  }

  /**
   * Converts a string to a boolean, returning true if the string is null or empty.
   *
   * @param value the input string, may be null or empty
   * @return false if the string equals "false" (case-insensitive), true otherwise
   */
  public static boolean emptyToTrue(String value) {
    return !StringUtil.hasText(value) || Boolean.parseBoolean(value);
  }

  public static boolean hasLength(CharSequence str) {
    return str != null && !str.isEmpty();
  }

  public static boolean hasText(CharSequence str) {
    return hasLength(str) && str.chars().anyMatch(c -> !Character.isWhitespace(c));
  }

  public static Pattern getWeasisProtocolPattern() {
    return Pattern.compile("^weasis(-.*)?://.*?");
  }

  public static int getWeasisProtocolIndex(String... params) {
    Pattern pattern = getWeasisProtocolPattern();
    for (int i = 0; i < params.length; i++) {
      if (pattern.matcher(params[i]).matches()) {
        return i;
      }
    }
    return -1;
  }

  public static String removeEnglobingQuotes(String value) {
    return value.replaceAll("(?:^\")|(?:\"$)", "");
  }

  public static String adaptPathToUri(String value) {
    return value.replace("\\", "/").replace(" ", "%20");
  }

  public static List<String> splitSpaceExceptInQuotes(String s) {
    if (s == null) {
      return Collections.emptyList();
    }
    List<String> matchList = new ArrayList<>();
    Pattern patternSpaceExceptQuotes = Pattern.compile("'[^']*'|\"[^\"]*\"|( )");
    Matcher m = patternSpaceExceptQuotes.matcher(s);
    StringBuilder b = new StringBuilder();
    while (m.find()) {
      if (m.group(1) == null) {
        m.appendReplacement(b, m.group(0));
        String arg = b.toString();
        b.setLength(0);
        if (Utils.hasText(arg)) {
          matchList.add(arg.trim());
        }
      }
    }
    b.setLength(0);
    m.appendTail(b);
    String arg = b.toString();
    if (Utils.hasText(arg)) {
      matchList.add(arg.trim());
    }
    return matchList;
  }

  public static void openInDefaultBrowser(URL url) {
    if (url != null) {
      if (SystemInfo.isLinux) {
        try {
          String[] cmd = new String[] {"xdg-open", url.toString()}; // NON-NLS
          Runtime.getRuntime().exec(cmd);
        } catch (IOException e) {
          LOGGER.error("Cannot open URL to the system browser", e);
        }
      } else if (Desktop.isDesktopSupported()) {
        final Desktop desktop = Desktop.getDesktop();
        if (desktop.isSupported(Desktop.Action.BROWSE)) {
          try {
            desktop.browse(url.toURI());
          } catch (IOException | URISyntaxException e) {
            LOGGER.error("Cannot open URL to the desktop browser", e);
          }
        }
      } else {
        LOGGER.warn("Cannot open URL to the system browser");
      }
    }
  }
}
