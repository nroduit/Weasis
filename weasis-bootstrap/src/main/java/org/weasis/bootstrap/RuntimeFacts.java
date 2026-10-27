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

import java.lang.module.ModuleFinder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What the installed runtime is: JDK major, boot modules and bootstrap protocol. A package declares
 * requirements against these facts; the hub evaluates them before serving, stage 1 re-checks them
 * before choosing a launcher set, and stage 2 once more with the JVM it runs in.
 */
record RuntimeFacts(int jdk, Set<String> modules, int bootstrap) {

  /** The modules of the runtime image, not only those resolved for this launch. */
  static RuntimeFacts current() {
    Set<String> names =
        ModuleFinder.ofSystem().findAll().stream()
            .map(ref -> ref.descriptor().name())
            .collect(Collectors.toCollection(TreeSet::new));
    return new RuntimeFacts(Runtime.version().feature(), names, Contract.PROTOCOL);
  }

  /**
   * The requirements of {@code config} this runtime does not satisfy, in the words of a refusal.
   */
  List<String> unsatisfied(PackageConfig config) {
    List<String> reasons = new ArrayList<>();
    config
        .requiresJdk()
        .filter(required -> required > jdk)
        .ifPresent(required -> reasons.add("needs JDK " + required + ", runs " + jdk)); // NON-NLS
    List<String> missing =
        config.requiresModules().stream().filter(m -> !modules.contains(m)).toList();
    if (!missing.isEmpty()) {
      reasons.add("missing module " + String.join(", ", missing)); // NON-NLS
    }
    Set<Integer> protocols = config.requiresBootstrap();
    if (!protocols.isEmpty() && !protocols.contains(bootstrap)) {
      reasons.add("needs bootstrap protocol " + protocols + ", speaks " + bootstrap); // NON-NLS
    }
    return reasons;
  }

  Optional<String> refusal(PackageConfig config) {
    List<String> reasons = unsatisfied(config);
    return reasons.isEmpty() ? Optional.empty() : Optional.of(String.join("; ", reasons));
  }

  /**
   * The User-Agent of the configuration request: the P9 tokens plus {@code jv:} and {@code bp:}.
   */
  String userAgent(String appVersion) {
    return "Weasis/" // NON-NLS
        + appVersion
        + " ("
        + System.getProperty("os.name") // NON-NLS
        + "; "
        + System.getProperty("os.version") // NON-NLS
        + "; "
        + System.getProperty("os.arch") // NON-NLS
        + "; jv:" // NON-NLS
        + jdk
        + "; bp:" // NON-NLS
        + bootstrap
        + ") Java/" // NON-NLS
        + System.getProperty("java.version"); // NON-NLS
  }

  /** Value of the {@code Weasis-Runtime-Modules} header. */
  String modulesHeader() {
    return String.join(",", modules);
  }
}
