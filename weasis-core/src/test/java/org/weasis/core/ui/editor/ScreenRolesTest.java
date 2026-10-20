/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.ui.editor.ScreenRoles.Mapping;
import org.weasis.core.ui.editor.ScreenRoles.Screen;

class ScreenRolesTest {

  private static final Screen LEFT = new Screen("left", new Rectangle(0, 0, 2560, 1440));
  private static final Screen MIDDLE = new Screen("middle", new Rectangle(2560, 0, 2560, 1440));
  private static final Screen RIGHT = new Screen("right", new Rectangle(5120, 0, 1440, 2560));

  @Test
  void oneScreenFillsEveryRole() {
    Mapping roles = ScreenRoles.assign("left", List.of(LEFT));
    assertEquals(ScreenRole.values().length, roles.screens().size());
    roles.screens().values().forEach(screen -> assertSame(LEFT, screen));
    assertTrue(roles.isFulfilled(ScreenRole.CONTROL));
    assertFalse(roles.isFulfilled(ScreenRole.DIAGNOSTIC_1));
    assertEquals(Optional.of(ScreenRole.CONTROL), roles.roleOf("left"));
  }

  @Test
  void noScreenGivesNoRole() {
    assertTrue(ScreenRoles.assign("left", List.of()).isEmpty());
  }

  @Test
  void theMainWindowScreenIsControlAndTheOtherIsEveryReadingRole() {
    Mapping roles = ScreenRoles.assign("middle", List.of(LEFT, MIDDLE));
    assertSame(MIDDLE, roles.screen(ScreenRole.CONTROL));
    assertSame(LEFT, roles.screen(ScreenRole.DIAGNOSTIC_1));
    assertSame(LEFT, roles.screen(ScreenRole.DIAGNOSTIC_2));
    assertSame(LEFT, roles.screen(ScreenRole.COLOR));
    assertSame(LEFT, roles.screen(ScreenRole.PORTRAIT));
    assertTrue(roles.isFulfilled(ScreenRole.DIAGNOSTIC_1));
    assertFalse(roles.isFulfilled(ScreenRole.DIAGNOSTIC_2));
    assertFalse(roles.isFulfilled(ScreenRole.PORTRAIT));
    assertEquals(Optional.of(ScreenRole.DIAGNOSTIC_1), roles.roleOf("left"));
  }

  @Test
  void threeScreensAreOrderedLeftToRightAndPortraitIsDetected() {
    Mapping roles = ScreenRoles.assign("middle", List.of(RIGHT, LEFT, MIDDLE));
    assertSame(MIDDLE, roles.screen(ScreenRole.CONTROL));
    assertSame(LEFT, roles.screen(ScreenRole.DIAGNOSTIC_1));
    assertSame(RIGHT, roles.screen(ScreenRole.DIAGNOSTIC_2));
    assertSame(RIGHT, roles.screen(ScreenRole.PORTRAIT));
    assertSame(LEFT, roles.screen(ScreenRole.COLOR));
    assertTrue(roles.isFulfilled(ScreenRole.PORTRAIT));
    assertEquals(Optional.of(ScreenRole.DIAGNOSTIC_2), roles.roleOf("right"));
  }

  @Test
  void anUnknownMainScreenMakesTheFirstScreenControl() {
    Mapping roles = ScreenRoles.assign("other", List.of(LEFT, MIDDLE));
    assertSame(LEFT, roles.screen(ScreenRole.CONTROL));
    assertSame(MIDDLE, roles.screen(ScreenRole.DIAGNOSTIC_1));
  }

  @Test
  void rolesParseByTheirKey() {
    assertEquals(ScreenRole.DIAGNOSTIC_1, ScreenRole.parse("diagnostic-1"));
    assertEquals(ScreenRole.CONTROL, ScreenRole.parse(" Control "));
    assertEquals("diagnostic-2", ScreenRole.DIAGNOSTIC_2.toString());
    assertThrows(IllegalArgumentException.class, () -> ScreenRole.parse("screen-3"));
  }

  @Test
  void anExplicitRoleWinsAndTheOthersAreDerivedFromTheFreeScreens() {
    Mapping roles =
        ScreenRoles.assign(
            "middle",
            List.of(LEFT, MIDDLE, RIGHT),
            Map.of("left", ScreenRole.COLOR, "nowhere", ScreenRole.PORTRAIT));
    assertSame(LEFT, roles.screen(ScreenRole.COLOR));
    assertTrue(roles.isExplicit(ScreenRole.COLOR));
    assertFalse(roles.isExplicit(ScreenRole.PORTRAIT), "an unknown screen id is ignored");
    assertSame(MIDDLE, roles.screen(ScreenRole.CONTROL));
    assertSame(RIGHT, roles.screen(ScreenRole.DIAGNOSTIC_1), "the free screen comes first");
    assertSame(RIGHT, roles.screen(ScreenRole.DIAGNOSTIC_2));
    assertFalse(roles.isFulfilled(ScreenRole.DIAGNOSTIC_2));
    assertEquals(Optional.of(ScreenRole.DIAGNOSTIC_1), roles.roleOf("right"));
    assertEquals(Optional.of(ScreenRole.COLOR), roles.roleOf("left"));
  }

  @Test
  void anExplicitControlMovesTheMainWindowScreen() {
    Mapping roles =
        ScreenRoles.assign("left", List.of(LEFT, MIDDLE), Map.of("middle", ScreenRole.CONTROL));
    assertSame(MIDDLE, roles.screen(ScreenRole.CONTROL));
    assertSame(LEFT, roles.screen(ScreenRole.DIAGNOSTIC_1));
    assertTrue(roles.isFulfilled(ScreenRole.DIAGNOSTIC_1));
  }
}
