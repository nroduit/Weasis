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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BootstrapContractTest {

  @Test
  void requirementsAreCheckedAgainstTheRuntimeFacts() {
    List<String> reasons =
        BootstrapContract.unsatisfiedRequirements(
            "27", "java.base,jdk.incubator.vector", "2", 25, Set.of("java.base"), 1);
    assertEquals(
        List.of(
            "needs JDK 27, runs 25",
            "missing module jdk.incubator.vector",
            "needs bootstrap protocol [2], runs 1"),
        reasons);
    assertTrue(
        BootstrapContract.unsatisfiedRequirements(
                "25", "java.base", "1-2", 27, Set.of("java.base", "x"), 1)
            .isEmpty());
    assertTrue(
        BootstrapContract.unsatisfiedRequirements(null, null, null, 27, Set.of(), 1).isEmpty());
  }

  @Test
  void protocolsAndMajors() {
    assertEquals(Set.of(1, 2, 3), BootstrapContract.protocols("1-3"));
    assertEquals(Set.of(1, 4), BootstrapContract.protocols("1, 4"));
    assertEquals(27, BootstrapContract.major("27.0.1"));
    assertEquals(8, BootstrapContract.major("1.8"));
  }

  @Test
  void embeddedSuffixIsRemovedFromJarNames() {
    assertEquals(
        "weasis-core-5.0.0.jar", BootstrapContract.jarName("weasis-core-5.0.0.jar.embedded"));
    assertEquals("weasis-core-5.0.0.jar", BootstrapContract.jarName("weasis-core-5.0.0.jar"));
  }
}
