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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigSourceTest {

  private static final Path APP = Path.of("/opt/weasis/lib/app");

  @Test
  void commandsComeFromTheUriOrTheDollarWords() {
    List<String> fromUri =
        ConfigSource.commands(
            new String[] {
              "weasis://%24weasis%3Aconfig%20cdb%3Dhttps%3A%2F%2Fhub%2Fweasis%20%24dicom%3Aget%20-r%20x"
            });
    assertEquals(List.of("weasis:config cdb=https://hub/weasis", "dicom:get -r x"), fromUri);
    List<String> fromWords =
        ConfigSource.commands(
            new String[] {"$weasis:config", "cdb=https://hub/weasis", "$dicom:get", "-l", "f"});
    assertEquals(List.of("weasis:config cdb=https://hub/weasis", "dicom:get -l f"), fromWords);
  }

  @Test
  void configParamsAreGroupedByName() {
    Map<String, List<String>> params =
        ConfigSource.configParams(
            List.of(
                "weasis:config wcfg=\"https://hub/cfg?x=1\" pro=weasis.user alice pro=\"weasis.profile dicom\""));
    assertEquals(List.of("https://hub/cfg?x=1"), params.get("wcfg"));
    assertEquals(2, params.get("pro").size());
  }

  @Test
  void remoteCodebaseGivesTheConfigurationUri() {
    ConfigSource source =
        ConfigSource.resolve(new String[] {"$weasis:config", "cdb=https://hub/weasis"}, APP, null);
    assertTrue(source.isRemote());
    assertEquals(URI.create("https://hub/weasis/conf/base.json"), source.configUri().orElseThrow());
    assertEquals("https://hub/weasis", source.codebaseUrl());
    assertFalse(source.user().isEmpty());
  }

  @Test
  void noCodebaseMeansTheInstalledConfiguration() {
    ConfigSource source = ConfigSource.resolve(new String[] {"$dicom:get", "-l", "x"}, APP, null);
    assertFalse(source.isRemote());
    assertTrue(source.configUri().isEmpty());
    assertEquals("", source.codebaseUrl());
  }

  @Test
  void serviceUrlIsKeptForTheHandshakeWithoutFetching() {
    ConfigSource source =
        ConfigSource.resolve(
            new String[] {"$weasis:config", "wcfg=https://hub/launch?x=1"}, APP, null);
    assertEquals("https://hub/launch?x=1", source.configServiceUrl());
    assertFalse(source.isRemote(), "the service was not consulted: no codebase yet");
  }

  @Test
  void splitKeepsQuotedValuesTogether() {
    assertEquals(
        List.of("a=1", "b=\"x y\"", "c"), ConfigSource.splitSpaceExceptInQuotes("a=1 b=\"x y\" c"));
  }
}
