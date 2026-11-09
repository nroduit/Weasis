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

import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.weasis.pref.AppPreferences;

/**
 * What stage 2 (this launcher) agrees on with stage 1 (the {@code weasis-bootstrap} jar of the
 * native image): the properties stage 1 sets, the READY line, the reserved exit code and the
 * protocol versions this launcher accepts. The bootstrap keeps the same names in {@code
 * org.weasis.bootstrap.Contract}.
 */
public final class BootstrapContract {

  /** The protocols this launcher set speaks; a bootstrap outside the range is refused. */
  public static final int PROTOCOL_MIN = 1;

  public static final int PROTOCOL_MAX = 1;

  /** Set by stage 1; absent in a development launch. */
  public static final String P_VERSION = "weasis.bootstrap.version";

  /** The application directory of the image, where {@code conf/} and {@code resources/} live. */
  public static final String P_APPDIR = "weasis.bootstrap.appdir";

  /** {@code hub} or {@code embedded}. */
  public static final String P_SET = "weasis.bootstrap.set";

  /** Why stage 1 fell back to the embedded set: stage 2 then runs the installed configuration. */
  public static final String P_FALLBACK = "weasis.bootstrap.fallback";

  /** Start time of stage 1, for the launch measurements. */
  public static final String P_T0 = "weasis.bootstrap.t0";

  /** The configuration stage 1 fetched, and the URI it came from. */
  public static final String P_CONFIG_CACHE = "weasis.config.cache";

  public static final String P_CONFIG_CACHE_URI = "weasis.config.cache.uri";

  public static final String REQUIRES_JDK = "weasis.requires.jdk";
  public static final String REQUIRES_MODULES = "weasis.requires.modules";
  public static final String REQUIRES_BOOTSTRAP = "weasis.requires.bootstrap";

  /** Printed on stdout once the plugin manager has started, when stage 1 is listening. */
  public static final String READY = "READY";

  /** Stage 2 refuses this environment: stage 1 shows the error, no recovery. */
  public static final int EXIT_REFUSED = 78;

  /**
   * Stage 2 has told the user that this system cannot run the application (a glibc below the floor
   * of the native libraries): stage 1 neither retries nor adds a dialog.
   */
  public static final int EXIT_UNSUPPORTED = 79;

  /** Suffix of the embedded set files, which keeps them off the class path jpackage builds. */
  public static final String EMBEDDED_SUFFIX = ".embedded";

  private BootstrapContract() {}

  /** True when stage 1 started this process. */
  public static boolean launchedByBootstrap() {
    return System.getProperty(P_VERSION) != null;
  }

  /** The bootstrap protocol stage 1 speaks, when it started this process. */
  public static Optional<Integer> bootstrapProtocol() {
    try {
      return Optional.ofNullable(System.getProperty(P_VERSION)).map(Integer::parseInt);
    } catch (NumberFormatException e) {
      return Optional.of(-1);
    }
  }

  /** The reason this launcher refuses the bootstrap that started it, if any. */
  public static Optional<String> refuseBootstrap() {
    return bootstrapProtocol()
        .filter(v -> v < PROTOCOL_MIN || v > PROTOCOL_MAX)
        .map(
            v ->
                "bootstrap protocol "
                    + v
                    + " is not supported by this launcher ("
                    + PROTOCOL_MIN
                    + "-"
                    + PROTOCOL_MAX
                    + "): update the installation");
  }

  /**
   * The requirements of the configuration this JVM does not satisfy ({@code weasis.requires.jdk},
   * {@code .modules}, {@code .bootstrap}), in the words of a refusal; empty when all hold.
   */
  public static List<String> unsatisfiedRequirements(AppPreferences preferences) {
    return unsatisfiedRequirements(
        preferences.getValue(REQUIRES_JDK),
        preferences.getValue(REQUIRES_MODULES),
        preferences.getValue(REQUIRES_BOOTSTRAP),
        Runtime.version().feature(),
        bootModules(),
        bootstrapProtocol().orElse(PROTOCOL_MAX));
  }

  static List<String> unsatisfiedRequirements(
      String requiresJdk,
      String requiresModules,
      String requiresBootstrap,
      int jdk,
      Set<String> modules,
      int bootstrap) {
    List<String> reasons = new ArrayList<>();
    if (Utils.hasText(requiresJdk)) {
      int required = major(requiresJdk);
      if (required > jdk) {
        reasons.add("needs JDK " + required + ", runs " + jdk);
      }
    }
    if (Utils.hasText(requiresModules)) {
      List<String> missing = new ArrayList<>();
      for (String m : requiresModules.split("[,\\s]+")) {
        if (!m.isBlank() && !modules.contains(m.strip())) {
          missing.add(m.strip());
        }
      }
      if (!missing.isEmpty()) {
        reasons.add("missing module " + String.join(", ", missing));
      }
    }
    if (Utils.hasText(requiresBootstrap)) {
      Set<Integer> protocols = protocols(requiresBootstrap);
      if (!protocols.isEmpty() && !protocols.contains(bootstrap)) {
        reasons.add("needs bootstrap protocol " + protocols + ", runs " + bootstrap);
      }
    }
    return reasons;
  }

  /** The modules of the runtime image, not only those resolved for this launch. */
  private static Set<String> bootModules() {
    Set<String> names = new LinkedHashSet<>();
    ModuleFinder.ofSystem().findAll().forEach(ref -> names.add(ref.descriptor().name()));
    return names;
  }

  /** {@code 1}, {@code 1-2} or {@code 1,2}. */
  static Set<Integer> protocols(String value) {
    Set<Integer> protocols = new LinkedHashSet<>();
    for (String part : value.split("[,\\s]+")) {
      if (part.isBlank()) {
        continue;
      }
      int dash = part.indexOf('-');
      if (dash > 0) {
        int from = Integer.parseInt(part.substring(0, dash).strip());
        int to = Integer.parseInt(part.substring(dash + 1).strip());
        for (int i = from; i <= to; i++) {
          protocols.add(i);
        }
      } else {
        protocols.add(Integer.parseInt(part.strip()));
      }
    }
    return protocols;
  }

  static int major(String version) {
    String v = version.strip();
    if (v.startsWith("1.")) {
      v = v.substring(2);
    }
    int end = 0;
    while (end < v.length() && Character.isDigit(v.charAt(end))) {
      end++;
    }
    return end == 0 ? 0 : Integer.parseInt(v.substring(0, end));
  }

  /** Tells stage 1 that the plugin manager has started; a no-op in a development launch. */
  public static void signalReady() {
    if (launchedByBootstrap()) {
      System.out.println(READY);
      System.out.flush();
    }
  }

  /** The jar name of a class path entry, the embedded suffix of the installer removed. */
  public static String jarName(String fileName) {
    return fileName.endsWith(EMBEDDED_SUFFIX)
        ? fileName.substring(0, fileName.length() - EMBEDDED_SUFFIX.length())
        : fileName;
  }
}
