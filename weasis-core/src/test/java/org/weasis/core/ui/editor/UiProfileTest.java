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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import java.io.StringReader;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.weasis.core.ui.docking.ShellState;
import org.weasis.core.ui.docking.ShellState.ShellSide;
import org.weasis.core.ui.docking.Slot;
import org.weasis.core.ui.editor.image.MouseActionType;
import org.weasis.core.ui.editor.image.MouseButton;
import org.weasis.core.ui.editor.image.ViewerToolBar;
import org.weasis.core.ui.editor.image.ZoomToolBar;
import org.weasis.core.ui.util.Toolbar;

class UiProfileTest {

  private static JsonObject json(String text) {
    return Json.createReader(new StringReader(text)).readObject();
  }

  @Test
  void jsonRoundTripKeepsEverySectionAndDropsTheSizesOfTheShell() {
    UiProfile profile =
        new UiProfile(
            "ct", // NON-NLS
            "CT", // NON-NLS
            new ShellState(
                null, new ShellSide(Arrays.asList("image", "hp-why"), 320, 0.6, false), "mini"),
            List.of("viewer", "measure"), // NON-NLS
            List.of("presets", "zoom"), // NON-NLS
            Map.of(
                MouseButton.LEFT,
                MouseActionType.WINDOW_LEVEL,
                MouseButton.WHEEL,
                MouseActionType.SEQUENCE),
            "cross-sectional"); // NON-NLS
    JsonObject json = profile.toJson();
    UiProfile parsed = UiProfile.fromJson(json);
    assertAll(
        () -> assertEquals(profile, parsed),
        () -> assertEquals(0, parsed.shell().tools().width(), "the width belongs to the user"),
        () -> assertEquals(0, parsed.shell().tools().ratio()),
        () -> assertNull(parsed.shell().explorer()),
        () -> assertEquals("hp-why", parsed.shell().tools().slot(Slot.BOTTOM)),
        () -> assertEquals("window-level", json.getJsonObject("mouse").getString("left")),
        () -> assertEquals("cross-sectional", parsed.measurementProfile()));
  }

  @Test
  void oneShotToolsGoBackToTheLeftBindingOfTheProfile() {
    UiProfile slide =
        UiProfile.fromJson(
            json("{\"id\": \"sm\", \"toolsOnce\": true, \"mouse\": {\"left\": \"pan\"}}"));
    UiProfile ct = UiProfile.fromJson(json("{\"id\": \"ct\", \"mouse\": {\"left\": \"zoom\"}}"));
    UiProfile bare = UiProfile.fromJson(json("{\"id\": \"x\", \"toolsOnce\": true}"));
    assertAll(
        () -> assertTrue(slide.toolsOnce()),
        () -> assertEquals("pan", slide.leftActionAfterDrawing().orElseThrow()),
        () -> assertTrue(slide.toJson().getBoolean("toolsOnce")),
        () -> assertEquals(slide, UiProfile.fromJson(slide.toJson())),
        () -> assertTrue(ct.leftActionAfterDrawing().isEmpty(), "tools stay selected"),
        () -> assertFalse(ct.toJson().containsKey("toolsOnce")),
        () -> assertTrue(bare.leftActionAfterDrawing().isEmpty(), "no left binding"));
  }

  @Test
  void aSectionLeftOutStatesNothing() {
    UiProfile parsed = UiProfile.fromJson(json("{\"id\": \"bare\"}"));
    assertAll(
        () -> assertNull(parsed.shell()),
        () -> assertNull(parsed.toolbars()),
        () -> assertNull(parsed.mouse()),
        () -> assertTrue(parsed.showsToolbar("anything")),
        () -> assertTrue(parsed.showsMenu("anything")),
        () -> assertEquals(0, parsed.rankOf("anything")),
        () -> assertEquals(1, parsed.toJson().size(), "only the id"));
  }

  @Test
  void aProfileThatListsToolbarsHidesTheOthersAndOrdersThem() {
    UiProfile parsed =
        UiProfile.fromJson(json("{\"id\": \"xr\", \"toolbars\": [\"measure\", \"viewer\"]}"));
    assertAll(
        () -> assertTrue(parsed.showsToolbar("viewer")),
        () -> assertFalse(parsed.showsToolbar("cine")),
        () -> assertEquals(0, parsed.rankOf("measure")),
        () -> assertEquals(1, parsed.rankOf("viewer")),
        () -> assertEquals(2, parsed.rankOf("cine")));
  }

  @Test
  void anUnknownButtonOrActionIsRefused() {
    assertThrows(
        IllegalArgumentException.class,
        () -> UiProfile.fromJson(json("{\"id\": \"x\", \"mouse\": {\"thumb\": \"zoom\"}}")));
    assertThrows(
        IllegalArgumentException.class,
        () -> UiProfile.fromJson(json("{\"id\": \"x\", \"mouse\": {\"left\": \"fly\"}}")));
    assertThrows(IllegalArgumentException.class, () -> UiProfile.fromJson(json("{}")));
  }

  @Test
  void theMouseActionsReadBothTheirNameAndTheActionCommand() {
    assertEquals(MouseActionType.WINDOW_LEVEL, MouseActionType.ofCommand("winLevel"));
    assertEquals(MouseActionType.WINDOW_LEVEL, MouseActionType.ofCommand("window-level"));
    assertEquals(MouseActionType.CONTEXT_MENU, MouseActionType.ofCommand("CONTEXT-MENU"));
    assertEquals(MouseButton.WHEEL, MouseButton.of("Wheel"));
    assertNull(MouseButton.of("thumb"));
  }

  @Test
  void theToolbarIdIsTheClassNameWithoutItsSuffixInKebabCase() {
    assertAll(
        () -> assertEquals("viewer", Toolbar.idOf(ViewerToolBar.class)),
        () -> assertEquals("zoom", Toolbar.idOf(ZoomToolBar.class)),
        () -> assertEquals("key-object", Toolbar.idOf(KeyObjectToolBar.class)),
        () -> assertEquals("basic-3d", Toolbar.idOf(Basic3DToolBar.class)),
        () -> assertEquals("hanging-protocol", Toolbar.idOf(HangingProtocolBar.class)),
        () -> assertEquals("view-3d", Toolbar.idOf(View3DToolbar.class)));
  }

  private static final class KeyObjectToolBar {}

  private static final class Basic3DToolBar {}

  private static final class HangingProtocolBar {}

  private static final class View3DToolbar {}
}
