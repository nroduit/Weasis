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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Rectangle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.command.ScreenRole;
import org.weasis.core.ui.editor.ScreenRoles.Screen;

class ScreenRolesTest {

  private static final Screen LEFT = new Screen("left", new Rectangle(0, 0, 2560, 1440));
  private static final Screen MIDDLE = new Screen("middle", new Rectangle(2560, 0, 2560, 1440));
  private static final Screen RIGHT = new Screen("right", new Rectangle(5120, 0, 1440, 2560));

  @Test
  void oneScreenFillsEveryRole() {
    Map<ScreenRole, Screen> roles = ScreenRoles.assign("left", List.of(LEFT));
    assertEquals(ScreenRole.values().length, roles.size());
    roles.values().forEach(screen -> assertSame(LEFT, screen));
  }

  @Test
  void noScreenGivesNoRole() {
    assertTrue(ScreenRoles.assign("left", List.of()).isEmpty());
  }

  @Test
  void theMainWindowScreenIsControlAndTheOtherIsEveryReadingRole() {
    Map<ScreenRole, Screen> roles = ScreenRoles.assign("middle", List.of(LEFT, MIDDLE));
    assertSame(MIDDLE, roles.get(ScreenRole.CONTROL));
    assertSame(LEFT, roles.get(ScreenRole.DIAGNOSTIC_1));
    assertSame(LEFT, roles.get(ScreenRole.DIAGNOSTIC_2));
    assertSame(LEFT, roles.get(ScreenRole.COLOR));
    assertSame(LEFT, roles.get(ScreenRole.PORTRAIT));
  }

  @Test
  void threeScreensAreOrderedLeftToRightAndPortraitIsDetected() {
    Map<ScreenRole, Screen> roles = ScreenRoles.assign("middle", List.of(RIGHT, LEFT, MIDDLE));
    assertSame(MIDDLE, roles.get(ScreenRole.CONTROL));
    assertSame(LEFT, roles.get(ScreenRole.DIAGNOSTIC_1));
    assertSame(RIGHT, roles.get(ScreenRole.DIAGNOSTIC_2));
    assertSame(RIGHT, roles.get(ScreenRole.PORTRAIT));
    assertSame(LEFT, roles.get(ScreenRole.COLOR));
  }

  @Test
  void anUnknownMainScreenMakesTheFirstScreenControl() {
    Map<ScreenRole, Screen> roles = ScreenRoles.assign("other", List.of(LEFT, MIDDLE));
    assertSame(LEFT, roles.get(ScreenRole.CONTROL));
    assertSame(MIDDLE, roles.get(ScreenRole.DIAGNOSTIC_1));
  }

  @Test
  void rolesParseByTheirKey() {
    assertEquals(ScreenRole.DIAGNOSTIC_1, ScreenRole.parse("diagnostic-1"));
    assertEquals(ScreenRole.CONTROL, ScreenRole.parse(" Control "));
    assertEquals("diagnostic-2", ScreenRole.DIAGNOSTIC_2.toString());
    assertThrows(IllegalArgumentException.class, () -> ScreenRole.parse("screen-3"));
  }
}
