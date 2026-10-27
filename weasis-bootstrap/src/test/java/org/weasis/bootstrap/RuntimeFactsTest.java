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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RuntimeFactsTest {

  private static PackageConfig requires(String jdk, String modules, String bootstrap) {
    String json =
        """
        {"weasisPreferences": [
          {"code": "weasis.requires.jdk", "value": "%s"},
          {"code": "weasis.requires.modules", "value": "%s"},
          {"code": "weasis.requires.bootstrap", "value": "%s"}
        ]}
        """
            .formatted(jdk, modules, bootstrap);
    return PackageConfig.parse(json.getBytes(StandardCharsets.UTF_8), null);
  }

  @Test
  void namesEveryUnsatisfiedRequirement() {
    RuntimeFacts facts = new RuntimeFacts(25, Set.of("java.base"), 1);
    List<String> reasons = facts.unsatisfied(requires("27", "java.base,jdk.incubator.vector", "2"));
    assertEquals(3, reasons.size());
    assertEquals("needs JDK 27, runs 25", reasons.get(0));
    assertEquals("missing module jdk.incubator.vector", reasons.get(1));
    assertEquals("needs bootstrap protocol [2], speaks 1", reasons.get(2));
  }

  @Test
  void satisfiedWhenFactsCoverTheRequirements() {
    RuntimeFacts facts = new RuntimeFacts(27, Set.of("java.base", "java.desktop", "extra"), 1);
    assertTrue(facts.refusal(requires("25", "java.base java.desktop", "1-3")).isEmpty());
    assertTrue(facts.refusal(PackageConfig.empty()).isEmpty());
  }

  @Test
  void agentCarriesTheShortFacts() {
    RuntimeFacts facts =
        new RuntimeFacts(27, new java.util.TreeSet<>(Set.of("java.base", "java.desktop")), 1);
    String agent = facts.userAgent("5.0.0");
    assertTrue(agent.startsWith("Weasis/5.0.0 ("), agent);
    assertTrue(agent.contains("; jv:27; bp:1)"), agent);
    assertEquals("java.base,java.desktop", facts.modulesHeader());
    assertTrue(RuntimeFacts.current().modules().contains("java.base"));
  }
}
