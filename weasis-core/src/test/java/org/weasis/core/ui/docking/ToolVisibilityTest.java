/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.docking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.setting.Setting;
import org.weasis.core.api.setting.SettingCatalog;
import org.weasis.core.api.setting.SettingEnvironment;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.setting.Settings.Origin;

class ToolVisibilityTest {

  private final Map<String, String> site = new HashMap<>();

  @BeforeEach
  void installEmptyEnvironment() {
    site.clear();
    Settings.install(SettingEnvironment.inMemory(site));
    ToolVisibility.setSelectedViewer(Optional::empty);
  }

  @Test
  void theSettingIsDeclaredOnceContextualAndTrueByDefault() {
    Setting<Boolean> setting = ToolVisibility.setting("measure-x");
    assertEquals("weasis.tool.measure-x.visible", setting.key());
    assertTrue(setting.contextual());
    assertTrue(setting.defaultValue());
    assertSame(setting, ToolVisibility.setting("measure-x"));
    assertSame(setting, SettingCatalog.find(setting.key()).orElseThrow());
  }

  @Test
  void withoutAValueTheToolFlagAppliesAndTheLayersWinOverIt() {
    FakeTool tool = new FakeTool("display-x", 10);
    tool.setComponentEnabled(false);
    assertFalse(ToolVisibility.isVisible(tool), "the 4.x flag");
    assertEquals(Origin.DEFAULT, ToolVisibility.origin(tool));

    assertTrue(ToolVisibility.setVisible(tool, true));
    assertTrue(tool.isComponentEnabled());
    assertTrue(ToolVisibility.isVisible(tool));
    assertEquals(Origin.USER, ToolVisibility.origin(tool));

    try (Settings.Context ignored =
        Settings.applyContext("protocol", Map.of(ToolVisibility.setting("display-x"), false))) {
      assertFalse(ToolVisibility.isVisible(tool), "a protocol hides it while active");
      assertEquals(Origin.CONTEXT, ToolVisibility.origin(tool));
    }
    assertTrue(ToolVisibility.isVisible(tool), "the user's choice again");

    site.put(Settings.LOCKED_PROPERTY, ToolVisibility.key("display-x"));
    site.put(ToolVisibility.key("display-x"), "false");
    assertFalse(ToolVisibility.setVisible(tool, true), "locked by the site");
    assertFalse(ToolVisibility.isVisible(tool));
    assertEquals(Origin.SITE, ToolVisibility.origin(tool));
  }
}
