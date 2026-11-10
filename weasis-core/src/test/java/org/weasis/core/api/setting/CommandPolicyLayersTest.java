/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.setting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.CommandPolicy;

/** The user's values sit on top of the configuration's policy; an empty value changes nothing. */
class CommandPolicyLayersTest {

  @Test
  void emptyValuesKeepTheConfigurationAndWordsChangeIt() {
    CommandPolicy base = CommandPolicy.defaults();
    CommandPolicy same = CommandPolicyLayers.derive(base, "", "", "", "", "");
    Path a = Path.of("a").toAbsolutePath().normalize();
    Path b = Path.of("b").toAbsolutePath().normalize();
    CommandPolicy changed =
        CommandPolicyLayers.derive(
            base,
            "allow",
            "pacs.example.org, hub.example.org",
            "refuse",
            "",
            "a" + File.pathSeparator + "b");
    assertAll(
        () -> assertEquals(base, same),
        () -> assertTrue(changed.network().read()),
        () ->
            assertEquals(
                Set.of("pacs.example.org", "hub.example.org"), changed.network().readHosts()),
        () -> assertFalse(changed.network().write()),
        () -> assertEquals(base.network().writeHosts(), changed.network().writeHosts()),
        () -> assertEquals(java.util.List.of(a, b), changed.files().writeRoots()),
        () -> assertEquals(base.remote(), changed.remote()),
        () -> assertEquals(base.locked(), changed.locked()));
  }
}
