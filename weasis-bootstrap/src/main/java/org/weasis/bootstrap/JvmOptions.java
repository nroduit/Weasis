/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.bootstrap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The JVM options of stage 2. Options served by the hub are an allow-list: anything that loads code
 * outside the signed launcher set is refused, since {@code base.json} is not signed. {@code
 * $APPDIR} is the only substitution.
 */
final class JvmOptions {

  private static final Set<String> REFUSED_PREFIXES =
      Set.of(
          "-javaagent",
          "-agentpath",
          "-agentlib",
          "-Xbootclasspath",
          "--patch-module",
          "--module-path",
          "--upgrade-module-path",
          "-cp",
          "-classpath",
          "--class-path",
          "-p",
          "-XX:AOTCache",
          "-XX:AOTConfiguration",
          "-XX:SharedArchiveFile",
          "-XX:OnError",
          "-XX:OnOutOfMemoryError",
          "-Xshare:dump",
          "-Djava.library.path",
          "-Dsun.boot.library.path",
          "-Djava.system.class.loader",
          "-Dweasis.bootstrap.",
          "-Djava.class.path",
          "-Djdk.module.path");

  private static final List<String> ALLOWED_PREFIXES =
      List.of(
          "-X",
          "-D",
          "--enable-native-access",
          "--add-opens",
          "--add-exports",
          "--add-reads",
          "--enable-preview",
          "--sun-misc-unsafe-memory-access",
          "--illegal-native-access",
          "--finalization",
          "-splash:",
          "-ea",
          "-da",
          "-esa",
          "-dsa",
          "-verbose");

  private JvmOptions() {}

  /** The served options an unsigned configuration may add to the stage-2 command line. */
  static List<String> allowed(List<String> options, List<String> refusedOut) {
    List<String> accepted = new ArrayList<>();
    for (String option : options) {
      if (isAllowed(option)) {
        accepted.add(option);
      } else {
        refusedOut.add(option);
      }
    }
    return accepted;
  }

  static boolean isAllowed(String option) {
    String o = option.strip();
    if (o.isEmpty()) {
      return false;
    }
    for (String refused : REFUSED_PREFIXES) {
      if (o.equals(refused) || o.startsWith(refused + "=") || o.startsWith(refused + ":")) {
        return false;
      }
      if (refused.endsWith(".") && o.startsWith(refused)) {
        return false;
      }
    }
    // -Xbootclasspath/a: and friends carry their own separator
    if (o.startsWith("-Xbootclasspath")) {
      return false;
    }
    return ALLOWED_PREFIXES.stream().anyMatch(o::startsWith);
  }

  /** Replaces {@code $APPDIR} by the application directory, as the jpackage launcher does. */
  static List<String> substitute(List<String> options, String appDir) {
    return options.stream().map(o -> o.replace("$APPDIR", appDir)).toList(); // NON-NLS
  }

  /** Splits a value on whitespace; double quotes protect an option that contains spaces. */
  static List<String> tokenize(String value) {
    List<String> tokens = new ArrayList<>();
    if (value == null) {
      return tokens;
    }
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    boolean started = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '"') {
        quoted = !quoted;
        started = true;
      } else if (Character.isWhitespace(c) && !quoted) {
        if (started) {
          tokens.add(current.toString());
          current.setLength(0);
          started = false;
        }
      } else {
        current.append(c);
        started = true;
      }
    }
    if (started) {
      tokens.add(current.toString());
    }
    return tokens;
  }
}
